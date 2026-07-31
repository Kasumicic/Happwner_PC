package com.happwner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.happwner.ServerSettings
import com.happwner.StoredState
import com.happwner.Subscription
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface SubscriptionCheckState {
    data object Running : SubscriptionCheckState
    data class Success(
        val statusCode: Int?,
        val sizeBytes: Int,
        val inspection: SubscriptionInspection,
    ) : SubscriptionCheckState
    data class NoProfiles(
        val statusCode: Int?,
        val sizeBytes: Int,
        val preview: String,
    ) : SubscriptionCheckState
    data class Error(val message: String) : SubscriptionCheckState
}

sealed interface ProfileCopyState {
    data object Running : ProfileCopyState
    data class Success(val profileCount: Int, val sizeBytes: Int) : ProfileCopyState
    data object NoProfiles : ProfileCopyState
    data object ClipboardError : ProfileCopyState
    data class Error(val message: String) : ProfileCopyState
}

class AppViewModel(
    private val repository: StateRepository = StateRepository(),
    private val subscriptionFetcher: SubscriptionFetcher = SubscriptionFetcher(),
    private val clipboardWriter: (String) -> Boolean = ::writeSystemClipboard,
) : AutoCloseable {
    var state by mutableStateOf(repository.load())
        private set
    var serverError by mutableStateOf<String?>(null)
        private set
    var subscriptionChecks by mutableStateOf<Map<String, SubscriptionCheckState>>(emptyMap())
        private set
    var lastSubscriptionRequests by mutableStateOf<Map<String, SubscriptionRequestRecord>>(emptyMap())
        private set
    var subscriptionActivity by mutableStateOf<List<SubscriptionActivity>>(emptyList())
        private set
    var profileCopyStates by mutableStateOf<Map<String, ProfileCopyState>>(emptyMap())
        private set

    private val server = BridgeServer(
        stateProvider = { state },
        onSubscriptionResult = { id, result ->
            EventQueue.invokeLater {
                if (!closed.get() && state.subscriptions.any { it.id == id }) {
                    recordActivity(id, result)
                }
            }
        },
    )
    private val checkExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "happwner-subscription-check").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)

    val serverRunning: Boolean get() = server.running

    init {
        if (state.settings.serverEnabled) restartServer()
    }

    fun saveSubscription(subscription: Subscription) {
        require(InputValidator.sourceIssue(subscription.source) == null) {
            "Некорректная исходная ссылка"
        }
        val current = state.subscriptions
        val updated = if (current.any { it.id == subscription.id }) {
            current.map { if (it.id == subscription.id) subscription else it }
        } else {
            current + subscription
        }
        val settings = if (subscription.hwid.isNotBlank()) {
            state.settings.copy(lastHwid = subscription.hwid)
        } else {
            state.settings
        }
        commit(state.copy(settings = settings, subscriptions = updated))
    }

    fun deleteSubscription(id: String) = commit(
        state.copy(subscriptions = state.subscriptions.filterNot { it.id == id }),
    ).also {
        subscriptionChecks = subscriptionChecks - id
        profileCopyStates = profileCopyStates - id
        lastSubscriptionRequests = lastSubscriptionRequests - id
        subscriptionActivity = subscriptionActivity.filterNot { activity -> activity.subscriptionId == id }
    }

    fun checkSubscription(subscription: Subscription) {
        if (closed.get()) return
        subscriptionChecks = subscriptionChecks + (subscription.id to SubscriptionCheckState.Running)
        checkExecutor.submit {
            val startedAt = System.nanoTime()
            val result = runCatching { subscriptionFetcher.fetch(subscription) }.fold(
                onSuccess = {
                    val inspection = SubscriptionInspector.inspect(it.body)
                    if (inspection.profileCount > 0) {
                        SubscriptionCheckState.Success(
                            statusCode = it.statusCode,
                            sizeBytes = it.body.size,
                            inspection = inspection,
                        )
                    } else {
                        SubscriptionCheckState.NoProfiles(
                            statusCode = it.statusCode,
                            sizeBytes = it.body.size,
                            preview = inspection.preview,
                        )
                    }
                },
                onFailure = {
                    SubscriptionCheckState.Error(it.message ?: "Неизвестная ошибка")
                },
            )
            EventQueue.invokeLater {
                if (!closed.get() && state.subscriptions.any { it.id == subscription.id }) {
                    subscriptionChecks = subscriptionChecks + (subscription.id to result)
                    recordActivity(
                        subscription.id,
                        result.toRequestRecord(subscription, elapsedMillis(startedAt)),
                    )
                }
            }
        }
    }

    fun copyProfiles(subscription: Subscription) {
        if (closed.get()) return
        profileCopyStates = profileCopyStates + (subscription.id to ProfileCopyState.Running)
        checkExecutor.submit {
            val startedAt = System.nanoTime()
            val result = runCatching { subscriptionFetcher.fetch(subscription) }
            EventQueue.invokeLater {
                if (closed.get() || state.subscriptions.none { it.id == subscription.id }) return@invokeLater
                result.fold(
                    onSuccess = { response ->
                        recordActivity(
                            subscription.id,
                            SubscriptionRequestRecord.success(
                                response = response,
                                subscription = subscription,
                                durationMillis = elapsedMillis(startedAt),
                                origin = SubscriptionRequestOrigin.PROFILE_COPY,
                            ),
                        )
                        profileCopyStates = profileCopyStates + (
                            subscription.id to SubscriptionClipboard.copy(response, clipboardWriter)
                        )
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Неизвестная ошибка"
                        recordActivity(
                            subscription.id,
                            SubscriptionRequestRecord.failure(
                                message = message,
                                subscription = subscription,
                                durationMillis = elapsedMillis(startedAt),
                                origin = SubscriptionRequestOrigin.PROFILE_COPY,
                            ),
                        )
                        profileCopyStates = profileCopyStates + (
                            subscription.id to ProfileCopyState.Error(
                                DiagnosticSanitizer.errorMessage(message, subscription),
                            )
                        )
                    },
                )
            }
        }
    }

    fun updateSettings(settings: ServerSettings, updateAutostart: Boolean = false) {
        require(settings.port in 1024..65535) { "Порт должен быть от 1024 до 65535" }
        val previous = state.settings
        if (updateAutostart && settings.launchAtLogin != previous.launchAtLogin) {
            runCatching { AutostartManager.setEnabled(settings.launchAtLogin) }
                .onFailure { serverError = it.message }
        }
        commit(state.copy(settings = settings))
        val serverChanged =
            settings.bindMode != previous.bindMode ||
                settings.lanAddress != previous.lanAddress ||
                settings.port != previous.port ||
                settings.serverEnabled != previous.serverEnabled
        when {
            !settings.serverEnabled -> server.stop()
            serverChanged -> restartServer()
        }
    }

    fun restartServer() {
        runCatching { server.start(state.settings) }
            .onSuccess { serverError = null }
            .onFailure { serverError = PortDiagnostics.describe(it, state.settings) }
    }

    fun activeBaseUrl(): String {
        val host = if (state.settings.bindMode == com.happwner.BindMode.LOCAL) {
            "127.0.0.1"
        } else {
            state.settings.lanAddress.ifBlank {
                NetworkAddresses.privateIpv4().firstOrNull() ?: "127.0.0.1"
            }
        }
        return "http://$host:${state.settings.port}"
    }

    fun clearDiagnostics() {
        subscriptionActivity = emptyList()
    }

    fun copyDiagnosticReport(): Boolean = clipboardWriter(
        DiagnosticReport.create(
            settings = state.settings,
            serverRunning = serverRunning,
            activities = subscriptionActivity,
            language = state.settings.language,
        ),
    )

    private fun commit(updated: StoredState) {
        state = updated
        runCatching { repository.save(updated) }
            .onFailure { serverError = "Не удалось сохранить настройки: ${it.message}" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        checkExecutor.shutdownNow()
    }

    private fun recordActivity(id: String, request: SubscriptionRequestRecord) {
        val subscription = state.subscriptions.firstOrNull { it.id == id } ?: return
        lastSubscriptionRequests = lastSubscriptionRequests + (id to request)
        subscriptionActivity = (
            listOf(SubscriptionActivity(id, subscription.name, request)) + subscriptionActivity
        ).take(MAX_ACTIVITY_ENTRIES)
    }

    private fun SubscriptionCheckState.toRequestRecord(
        subscription: Subscription,
        durationMillis: Long,
    ): SubscriptionRequestRecord = when (this) {
        SubscriptionCheckState.Running -> error("Running check has no completed request record")
        is SubscriptionCheckState.Success -> SubscriptionRequestRecord(
            completedAtMillis = System.currentTimeMillis(),
            statusCode = statusCode,
            servedStatusCode = 200,
            sizeBytes = sizeBytes,
            profileCount = inspection.profileCount,
            protocols = inspection.protocols,
            durationMillis = durationMillis,
            origin = SubscriptionRequestOrigin.MANUAL_CHECK,
            transformations = subscription.enabledTransformations(),
        )
        is SubscriptionCheckState.NoProfiles -> SubscriptionRequestRecord(
            completedAtMillis = System.currentTimeMillis(),
            statusCode = statusCode,
            servedStatusCode = 200,
            sizeBytes = sizeBytes,
            profileCount = 0,
            durationMillis = durationMillis,
            origin = SubscriptionRequestOrigin.MANUAL_CHECK,
            transformations = subscription.enabledTransformations(),
        )
        is SubscriptionCheckState.Error -> SubscriptionRequestRecord.failure(
            message = message,
            subscription = subscription,
            durationMillis = durationMillis,
            origin = SubscriptionRequestOrigin.MANUAL_CHECK,
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    private companion object {
        const val MAX_ACTIVITY_ENTRIES = 100
    }
}
