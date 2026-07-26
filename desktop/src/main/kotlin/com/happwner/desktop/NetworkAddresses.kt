package com.happwner.desktop

import java.net.NetworkInterface

data class LanInterface(
    val systemName: String,
    val displayName: String,
    val address: String,
)

object NetworkAddresses {
    fun privateIpv4Interfaces(): List<LanInterface> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filter { it.address.size == 4 && it.isSiteLocalAddress }
                    .map { address ->
                        LanInterface(
                            systemName = network.name,
                            displayName = network.displayName?.takeIf(String::isNotBlank) ?: network.name,
                            address = address.hostAddress,
                        )
                    }
            }
            .distinctBy { it.address }
            .sortedWith(compareBy<LanInterface> { it.displayName.lowercase() }.thenBy { it.address })
    }.getOrDefault(emptyList())

    fun privateIpv4(): List<String> = privateIpv4Interfaces().map(LanInterface::address)
}
