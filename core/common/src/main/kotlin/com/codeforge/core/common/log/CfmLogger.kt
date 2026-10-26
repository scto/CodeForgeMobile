package com.codeforge.core.common.log

import java.io.File

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

interface CfmLogger {
    companion object {
        const val LOG_DIR_NAME = "log"
        const val CRASH_DIR_NAME = "crash"
        const val CFM_DIR_NAME = ".cfm"
        const val RETENTION_DAYS = 14
        const val MAX_LOG_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MiB
        const val MAX_CRASH_LOGS = 20
    }

    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}
