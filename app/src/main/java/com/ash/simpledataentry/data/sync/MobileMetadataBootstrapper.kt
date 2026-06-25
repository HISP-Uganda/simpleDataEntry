package com.ash.simpledataentry.data.sync

import android.content.Context
import android.util.Log
import com.ash.simpledataentry.data.AccountManager
import com.ash.simpledataentry.data.DatabaseManager
import com.ash.simpledataentry.data.RoomHydrationMode
import com.ash.simpledataentry.data.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Completable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

enum class BootstrapStage(val progress: Int, val label: String) {
    VERIFY_SESSION(5, "Verifying session"),
    DOWNLOAD_METADATA(55, "Downloading metadata"),
    PREPARE_LISTS(78, "Preparing program lists"),
    PREPARE_FORMS(92, "Preparing forms"),
    COMPLETED(100, "Metadata ready")
}

data class BootstrapProgress(
    val stage: BootstrapStage,
    val progress: Int = stage.progress,
    val message: String = stage.label
)

data class MobileBootstrapResult(
    val success: Boolean,
    val stage: BootstrapStage,
    val message: String,
    val diagnosticMessage: String? = null,
    val retryable: Boolean = true
)

@Singleton
class MobileMetadataBootstrapper @Inject constructor(
    private val sessionManager: SessionManager,
    private val accountManager: AccountManager,
    private val databaseManager: DatabaseManager,
    @ApplicationContext private val appContext: Context
) {

    companion object {
        private const val TAG = "MobileMetadataBootstrapper"
    }

    suspend fun bootstrap(
        onProgress: suspend (BootstrapProgress) -> Unit
    ): MobileBootstrapResult = withContext(Dispatchers.IO) {
        val d2 = sessionManager.getD2()
            ?: return@withContext MobileBootstrapResult(
                success = false,
                stage = BootstrapStage.VERIFY_SESSION,
                message = "Metadata sync requires an active session.",
                diagnosticMessage = "D2 runtime not initialized",
                retryable = false
            )

        val activeAccount = accountManager.getActiveAccount(appContext)
            ?: return@withContext MobileBootstrapResult(
                success = false,
                stage = BootstrapStage.VERIFY_SESSION,
                message = "Metadata sync requires an active account.",
                diagnosticMessage = "No active account bound in AccountManager",
                retryable = false
            )

        var currentStage = BootstrapStage.VERIFY_SESSION

        return@withContext try {
            onProgress(BootstrapProgress(BootstrapStage.VERIFY_SESSION))
            if (!sessionManager.isSessionActive()) {
                return@withContext MobileBootstrapResult(
                    success = false,
                    stage = BootstrapStage.VERIFY_SESSION,
                    message = "Session expired. Sign in again to refresh metadata.",
                    diagnosticMessage = "SessionManager reported inactive session",
                    retryable = false
                )
            }

            currentStage = BootstrapStage.DOWNLOAD_METADATA
            onProgress(BootstrapProgress(BootstrapStage.DOWNLOAD_METADATA, 12, "Starting metadata download"))

            D2SdkOperationLocks.withSdkOp("mobile-bootstrap-metadata") {
                try {
                    val downloadObservable = d2.metadataModule()
                        .download()
                        .doOnNext { progress ->
                            val rawPercent = (progress.percentage() ?: 0.0).coerceIn(0.0, 100.0)
                            val mappedPercent = 12 + (rawPercent * 0.70).toInt()
                            kotlinx.coroutines.runBlocking {
                                onProgress(
                                    BootstrapProgress(
                                        stage = BootstrapStage.DOWNLOAD_METADATA,
                                        progress = mappedPercent.coerceIn(12, 82),
                                        message = "Downloading metadata ${rawPercent.toInt()}%"
                                    )
                                )
                            }
                        }

                    Completable.fromObservable(downloadObservable).blockingAwait()
                } catch (error: Throwable) {
                    if (isOptionalUseCasesMissing(error)) {
                        Log.w(TAG, "Optional USE_CASES metadata missing; continuing")
                    } else {
                        throw error
                    }
                }
            }

            val hasMinimumSdkMetadata = coroutineScope {
                val orgUnitsDeferred = async { d2.organisationUnitModule().organisationUnits().blockingCount() > 0 }
                val programsDeferred = async { d2.programModule().programs().blockingCount() > 0 }
                val datasetsDeferred = async { d2.dataSetModule().dataSets().blockingCount() > 0 }

                val hasOrgUnits = orgUnitsDeferred.await()
                val hasPrograms = programsDeferred.await()
                val hasDatasets = datasetsDeferred.await()

                hasOrgUnits && (hasPrograms || hasDatasets)
            }

            if (!hasMinimumSdkMetadata) {
                return@withContext MobileBootstrapResult(
                    success = false,
                    stage = BootstrapStage.DOWNLOAD_METADATA,
                    message = "Metadata download finished, but program list metadata is incomplete.",
                    diagnosticMessage = "SDK tables missing organisation units or programs/datasets",
                    retryable = true
                )
            }

            currentStage = BootstrapStage.PREPARE_LISTS
            onProgress(BootstrapProgress(BootstrapStage.PREPARE_LISTS))
            D2SdkOperationLocks.withSdkOp("mobile-bootstrap-list-hydration") {
                val db = databaseManager.getDatabaseForAccount(appContext, activeAccount)
                sessionManager.hydrateRoomFromSdk(
                    context = appContext,
                    db = db,
                    mode = RoomHydrationMode.MINIMAL
                )
            }

            val listReady = sessionManager.isListMetadataReadyForActiveAccount(appContext)
            if (!listReady) {
                return@withContext MobileBootstrapResult(
                    success = false,
                    stage = BootstrapStage.PREPARE_LISTS,
                    message = "Program list metadata is still incomplete after sync.",
                    diagnosticMessage = "List readiness check failed after Room hydration",
                    retryable = true
                )
            }

            currentStage = BootstrapStage.PREPARE_FORMS
            onProgress(BootstrapProgress(BootstrapStage.PREPARE_FORMS))
            D2SdkOperationLocks.withSdkOp("mobile-bootstrap-form-hydration") {
                val db = databaseManager.getDatabaseForAccount(appContext, activeAccount)
                sessionManager.hydrateRoomFromSdk(
                    context = appContext,
                    db = db,
                    mode = RoomHydrationMode.FORM_METADATA
                )
            }

            val metadataReady = sessionManager.isMinimumMetadataReadyForActiveAccount(appContext)
            if (!metadataReady) {
                return@withContext MobileBootstrapResult(
                    success = false,
                    stage = BootstrapStage.PREPARE_FORMS,
                    message = "Form metadata is still incomplete after sync.",
                    diagnosticMessage = "Form readiness check failed after deferred hydration",
                    retryable = true
                )
            }

            onProgress(BootstrapProgress(BootstrapStage.COMPLETED))
            MobileBootstrapResult(
                success = true,
                stage = BootstrapStage.COMPLETED,
                message = "Capture metadata is ready for this account.",
                diagnosticMessage = null,
                retryable = false
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Bootstrap failed at ${currentStage.name}: ${t.message}", t)
            MobileBootstrapResult(
                success = false,
                stage = currentStage,
                message = friendlyFailureMessage(currentStage, t),
                diagnosticMessage = t.message,
                retryable = isRetryable(t)
            )
        }
    }

    private fun isOptionalUseCasesMissing(error: Throwable?): Boolean {
        val message = error?.message ?: return false
        return message.contains("stockUseCases", ignoreCase = true) ||
            message.contains("USE_CASES", ignoreCase = true) ||
            message.contains("E1005", ignoreCase = true)
    }

    private fun friendlyFailureMessage(stage: BootstrapStage, throwable: Throwable): String {
        val lowerMessage = throwable.message.orEmpty().lowercase()
        return when {
            "timeout" in lowerMessage ->
                "${stage.label} timed out. Retry metadata sync."
            "socket" in lowerMessage || "network" in lowerMessage || "unable to resolve host" in lowerMessage ->
                "Connection lost while ${stage.label.lowercase()}. Retry when internet is stable."
            "401" in lowerMessage || "403" in lowerMessage ->
                "Your session no longer has access to ${stage.label.lowercase()}. Sign in again."
            else ->
                "${stage.label} failed. Retry metadata sync."
        }
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        val lowerMessage = throwable.message.orEmpty().lowercase()
        return !(
            "401" in lowerMessage ||
                "403" in lowerMessage ||
                "not initialized" in lowerMessage
            )
    }
}
