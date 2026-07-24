package com.happwner.compat

import java.util.logging.Level
import java.util.logging.Logger

object Log {
    private fun logger(tag: String) = Logger.getLogger(tag)
    fun d(tag: String, message: String): Int { logger(tag).fine(message); return 0 }
    fun w(tag: String, message: String): Int { logger(tag).warning(message); return 0 }
    fun e(tag: String, message: String): Int { logger(tag).severe(message); return 0 }
    fun e(tag: String, message: String, error: Throwable): Int {
        logger(tag).log(Level.SEVERE, message, error)
        return 0
    }
}
