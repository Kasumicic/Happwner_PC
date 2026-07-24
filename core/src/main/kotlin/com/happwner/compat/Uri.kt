package com.happwner.compat

import java.io.ByteArrayOutputStream
import java.net.URI

class Uri private constructor(private val value: String) {
    fun getQueryParameter(name: String): String? {
        val query = runCatching { URI(value).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstNotNullOfOrNull { pair ->
            val separator = pair.indexOf('=')
            val rawName = if (separator >= 0) pair.substring(0, separator) else pair
            if (decode(rawName) == name) decode(if (separator >= 0) pair.substring(separator + 1) else "") else null
        }
    }

    companion object {
        fun parse(value: String): Uri = Uri(value)

        fun decode(value: String): String {
            val result = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                if (value[index] != '%') {
                    result.append(value[index++])
                    continue
                }
                val bytes = ByteArrayOutputStream()
                while (index + 2 < value.length && value[index] == '%') {
                    val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                    bytes.write(byte)
                    index += 3
                }
                if (bytes.size() == 0) {
                    result.append(value[index++])
                } else {
                    result.append(bytes.toString(Charsets.UTF_8.name()))
                }
            }
            return result.toString()
        }
    }
}
