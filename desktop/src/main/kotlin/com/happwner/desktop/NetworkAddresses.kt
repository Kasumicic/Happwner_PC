package com.happwner.desktop

import java.net.NetworkInterface

object NetworkAddresses {
    fun privateIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it.address.size == 4 && it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}
