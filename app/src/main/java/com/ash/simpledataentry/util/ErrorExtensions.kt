package com.ash.simpledataentry.util

import com.ash.simpledataentry.presentation.core.UiError
import org.hisp.dhis.android.core.maintenance.D2Error
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Extension function to convert exceptions to classified UI errors
 * Provides user-friendly messages and smart retry logic
 */
fun Throwable.toUiError(): UiError {
    return when (this) {
        // Network connectivity errors
        is UnknownHostException, is SocketTimeoutException ->
            UiError.Network(
                message = "No internet connection. Please check your network.",
                canRetry = true
            )

        // Generic IO errors (connection issues)
        is IOException ->
            UiError.Network(
                message = "Connection error: ${this.message ?: "Unable to connect to server"}",
                canRetry = true
            )

        // DHIS2-specific errors
        is D2Error -> when {
            // Authentication errors
            errorCode().toString().contains("AUTH", ignoreCase = true) ||
            errorCode().toString().contains("UNAUTHORIZED", ignoreCase = true) ->
                UiError.Authentication(
                    message = "Session expired. Please login again."
                )

            // Server errors
            isServerError() ->
                UiError.Server(
                    message = errorDescription() ?: "Server error occurred. Please try again later.",
                    statusCode = httpErrorCode()
                )

            // Validation errors
            errorCode().toString().contains("VALIDATION", ignoreCase = true) ->
                UiError.Validation(
                    message = errorDescription() ?: "Data validation failed"
                )

            // Other DHIS2 errors (local)
            else ->
                UiError.Local(
                    message = errorDescription() ?: "An error occurred",
                    cause = errorCode().toString()
                )
        }

        // Fallback for unknown errors
        else -> UiError.Local(
            message = this.message ?: "An unexpected error occurred",
            cause = javaClass.simpleName
        )
    }
}

/**
 * Helper to determine if D2Error is a server error
 */
private fun D2Error.isServerError(): Boolean {
    val code = httpErrorCode()
    return code != null && code >= 500
}
