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

class AppViewModel(
    private val repository: StateRepository = StateRepository(),
    private val subscriptionFetcher: SubscriptionFetcher = SubscriptionFetcher(),
) : AutoCloseable {
    var state by mutableStateOf(repository.load())
        private set
    var serverError by mutableStateOf<String?>(null)
        private set
    var subscriptionChecks by mutableStateOf<Map<String, SubscriptionCheckState>>(emptyMap())
        private set
    var lastSubscriptionRequests by mutableStateOf<Map<String, SubscriptionRequestRecord>>(emptyMap())
        private set

    private val server = BridgeServer(
        stateProvider = { state },
        onSubscriptionResult = { id, result ->
            EventQueue.invokeLater {
                if (!closed.get() && state.subscriptions.any { it.id == id }) {
                    lastSubscriptionRequests = lastSubscriptionRequests + (id to result)
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
        commit(state.copy(subscriptions = updated))
    }

    fun deleteSubscription(id: String) = commit(
        state.copy(subscriptions = state.subscriptions.filterNot { it.id == id }),
    ).also {
        subscriptionChecks = subscriptionChecks - id
        lastSubscriptionRequests = lastSubscriptionRequests - id
    }

    fun checkSubscription(subscription: Subscription) {
        if (closed.get()) return
        subscriptionChecks = subscriptionChecks + (subscription.id to SubscriptionCheckState.Running)
        checkExecutor.submit {
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
                    lastSubscriptionRequests = lastSubscriptionRequests + (
                        subscription.id to result.toRequestRecord()
                    )
                }
            }
        }
    }

    fun updateSettings(settings: ServerSettings, updateAutostart: Boolean = false) {
        require(settings.port in 1024..65535) { "Порт должен быть от 1024 до 65535" }
        if (updateAutostart && settings.launchAtLogin != state.settings.launchAtLogin) {
            runCatching { AutostartManager.setEnabled(settings.launchAtLogin) }
                .onFailure { serverError = it.message }
        }
        commit(state.copy(settings = settings))
        if (settings.serverEnabled) restartServer() else server.stop()
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
            NetworkAddresses.privateIpv4().firstOrNull() ?: "127.0.0.1"
        }
        return "http://$host:${state.settings.port}"
    }

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

    private fun SubscriptionCheckState.toRequestRecord(): SubscriptionRequestRecord = when (this) {
        SubscriptionCheckState.Running -> error("Running check has no completed request record")
        is SubscriptionCheckState.Success -> SubscriptionRequestRecord(
            completedAtMillis = System.currentTimeMillis(),
            statusCode = statusCode,
            sizeBytes = sizeBytes,
            profileCount = inspection.profileCount,
        )
        is SubscriptionCheckState.NoProfiles -> SubscriptionRequestRecord(
            completedAtMillis = System.currentTimeMillis(),
            statusCode = statusCode,
            sizeBytes = sizeBytes,
            profileCount = 0,
        )
        is SubscriptionCheckState.Error -> SubscriptionRequestRecord.failure(message)
    }
}
