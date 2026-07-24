package com.happwner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.happwner.ServerSettings
import com.happwner.StoredState
import com.happwner.Subscription

class AppViewModel(
    private val repository: StateRepository = StateRepository(),
) : AutoCloseable {
    var state by mutableStateOf(repository.load())
        private set
    var serverError by mutableStateOf<String?>(null)
        private set

    private val server = BridgeServer(stateProvider = { state })

    val serverRunning: Boolean get() = server.running

    init {
        if (state.settings.serverEnabled) restartServer()
    }

    fun saveSubscription(subscription: Subscription) {
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
    )

    fun updateSettings(settings: ServerSettings, updateAutostart: Boolean = false) {
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
            .onFailure { serverError = it.message ?: "Не удалось запустить сервер" }
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

    override fun close() = server.close()
}
