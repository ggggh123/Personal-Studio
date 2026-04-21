package com.example.personal_studio.core.common

/** All user-surfaceable errors funnel through this hierarchy. */
sealed class AppError(val message: String, val cause: Throwable? = null) {
    class Network(message: String, cause: Throwable? = null) : AppError(message, cause)
    class LlmProvider(message: String, cause: Throwable? = null) : AppError(message, cause)
    class NotConfigured(message: String) : AppError(message)
    class Storage(message: String, cause: Throwable? = null) : AppError(message, cause)
    class Unknown(message: String, cause: Throwable? = null) : AppError(message, cause)
}
