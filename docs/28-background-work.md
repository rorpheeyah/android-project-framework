# 28 · Background Work (WorkManager)

> **Mechanism:** WorkManager with Hilt-integrated `WorkerFactory`.
> **Where it lives:** `:aos-sdk/work/BackgroundWorkScheduler` (contract + scheduler) + per-feature workers in the consuming modules.
> **Used for:** resumable uploads (KYC, statements), scheduled reminders, FCM token refresh, draft application sync, MG stale-config retry.

---

## 1. Why WorkManager

The framework needs background tasks with these properties: survive process death, retry with exponential backoff, run on a constraint (network connected, charging), unique per logical job (deduplicate). WorkManager is the standard Android answer; rolling our own would re-invent every guarantee.

The wrapper in `:aos-sdk/work/` exists for two reasons:

1. **Hilt integration boilerplate** is non-trivial — a custom `WorkerFactory`, registration at `Application.onCreate()`, careful initialization order. The wrapper hides it.
2. **Job naming and tagging conventions** matter for the framework's logout-safety story (see §6). The wrapper enforces them.

---

## 2. The Scheduler Contract

```kotlin
// :aos-sdk/work/BackgroundWorkScheduler.kt
interface BackgroundWorkScheduler {
    /** Enqueue a unique job. If a job with the same uniqueName is already enqueued, this no-ops. */
    fun enqueue(
        uniqueName: String,
        worker: KClass<out CoroutineWorker>,
        input: Data = Data.EMPTY,
        constraints: Constraints = Constraints.NONE,
        backoff: BackoffPolicy = BackoffPolicy.EXPONENTIAL_DEFAULT,
        tags: Set<String> = emptySet(),
    ): WorkRequestId

    /** Observe the status of a previously enqueued job. */
    fun observe(id: WorkRequestId): Flow<WorkStatus>

    /** Cancel all jobs tagged with the given tag (used at logout — see §6). */
    fun cancelAllByTag(tag: String)
}

@JvmInline value class WorkRequestId(val value: String)

sealed interface WorkStatus {
    object Enqueued : WorkStatus
    object Running : WorkStatus
    object Succeeded : WorkStatus
    data class Failed(val message: String) : WorkStatus
    object Cancelled : WorkStatus
}
```

Lives in `:aos-sdk` because no banking concept is required — every consuming product needs background work.

---

## 3. The Standard Worker Types

Each consuming module contributes its workers. Inventory (from the PRD's needs):

| Worker | Module | Purpose | Constraints |
|---|---|---|---|
| `KycUploadWorker` | `:features-kyc` | Resumable upload of captured ID/selfie bitmaps | NETWORK_CONNECTED |
| `ContractDownloadWorker` | `:features/loan/contract/` | Download loan-contract PDF | NETWORK_CONNECTED |
| `RefreshFcmTokenWorker` | `:app` | Re-register FCM token after rotation | NETWORK_CONNECTED |
| `SyncDraftApplicationWorker` | `:features/loan/apply/` | Persist multi-step apply-form drafts to server periodically | NETWORK_CONNECTED, BATTERY_NOT_LOW |
| `MgRetryWorker` | `:app/boot/` | Retry MG fetch while stale-config fallback is active | NETWORK_CONNECTED |
| `RepaymentReminderWorker` | `:app` | Schedule local notifications for upcoming installments | (none — runs locally) |
| `BankStatementUploadWorker` | `:features/loan/apply/` | Upload statement PDF for income assessment | NETWORK_CONNECTED, BATTERY_NOT_LOW |

---

## 4. The Hilt-Integrated WorkerFactory

```kotlin
// :app/CompassApplication.kt
@HiltAndroidApp
class CompassApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration get() =
        Configuration.Builder().setWorkerFactory(workerFactory).build()

    // … other onCreate logic
}
```

This is the **canonical** Hilt + WorkManager integration. Required because workers need to be constructed with dependencies (`Session`, `LoanApplicationRepository`, etc.), and WorkManager's default `WorkerFactory` can't resolve them.

Each worker uses `@HiltWorker`:

```kotlin
// :features-kyc/upload/KycUploadWorker.kt
@HiltWorker
internal class KycUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val kycRepo: KycRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val captureId = inputData.getString(KEY_CAPTURE_ID) ?: return Result.failure()
        val request = readCaptureFromCache(captureId) ?: return Result.failure()

        return kycRepo.submitDocument(request).fold(
            onSuccess = { submissionId -> Result.success(workDataOf(KEY_SUBMISSION_ID to submissionId.value)) },
            onFailure = { error -> if (error.isTransient()) Result.retry() else Result.failure() },
        )
    }

    private companion object {
        const val KEY_CAPTURE_ID = "captureId"
        const val KEY_SUBMISSION_ID = "submissionId"
    }
}
```

The `@AssistedInject` + `@HiltWorker` pattern is required because the `Context` and `WorkerParameters` come from WorkManager, not Hilt.

---

## 5. The Scheduler Implementation

```kotlin
// :aos-sdk/work/WorkManagerScheduler.kt
internal class WorkManagerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : BackgroundWorkScheduler {

    override fun enqueue(
        uniqueName: String,
        worker: KClass<out CoroutineWorker>,
        input: Data,
        constraints: Constraints,
        backoff: BackoffPolicy,
        tags: Set<String>,
    ): WorkRequestId {
        val request = OneTimeWorkRequest.Builder(worker.java)
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(backoff, 30, TimeUnit.SECONDS)
            .apply { tags.forEach(::addTag) }
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        return WorkRequestId(request.id.toString())
    }

    override fun observe(id: WorkRequestId): Flow<WorkStatus> =
        workManager.getWorkInfoByIdLiveData(UUID.fromString(id.value))
            .asFlow()
            .filterNotNull()
            .map { info ->
                when (info.state) {
                    WorkInfo.State.ENQUEUED  -> WorkStatus.Enqueued
                    WorkInfo.State.RUNNING   -> WorkStatus.Running
                    WorkInfo.State.SUCCEEDED -> WorkStatus.Succeeded
                    WorkInfo.State.FAILED    -> WorkStatus.Failed(info.outputData.getString("error") ?: "Unknown")
                    WorkInfo.State.CANCELLED -> WorkStatus.Cancelled
                    WorkInfo.State.BLOCKED   -> WorkStatus.Enqueued
                }
            }

    override fun cancelAllByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag)
    }
}
```

`ExistingWorkPolicy.KEEP` is the right default — if the user re-enters the apply flow and re-enqueues `sync-draft-application`, the in-flight one continues rather than being cancelled and restarted. For replay-required cases (re-trying after a manual user action), use `ExistingWorkPolicy.REPLACE` explicitly.

---

## 6. Logout Safety: Tag-By-UserId

Background jobs enqueued by user A must not run after user B logs in. The framework's pattern: **tag every user-scoped job with the user id**, then `cancelAllByTag(userId)` at logout.

```kotlin
// :features-kyc/upload/KycUploadScheduler.kt
@Singleton
internal class KycUploadScheduler @Inject constructor(
    private val scheduler: BackgroundWorkScheduler,
    private val session: Session,
) {
    fun enqueueUpload(captureId: String): WorkRequestId = scheduler.enqueue(
        uniqueName = "kyc-upload-$captureId",
        worker = KycUploadWorker::class,
        input = workDataOf("captureId" to captureId),
        constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        tags = setOf("user-${session.userSession.userId.value}", "kyc-upload"),
    )
}

// :app/session/LogoutHandler.kt — extended
suspend fun logout() {
    val userId = componentManager.current()?.let { /* read Session userId */ }
    encryptedPrefs.clearSessionScope()
    httpClient.cache?.evictAll()
    componentManager.drop()
    userId?.let { workScheduler.cancelAllByTag("user-${it.value}") }
    navigator.navigate(Route.Login, popUpToRoot = true)
}
```

Additionally, every worker's first action should be a session-validity check:

```kotlin
override suspend fun doWork(): Result {
    val expectedUserId = inputData.getString("userId")
    val currentSession = sessionProvider.currentOrNull()
    if (currentSession == null || currentSession.userSession.userId.value != expectedUserId) {
        return Result.failure(workDataOf("reason" to "session_mismatch"))
    }
    // … proceed
}
```

Two layers of defense: cancellation at logout (proactive), plus mismatch-check in the worker (defensive). If a worker survives a logout race, it self-aborts.

---

## 7. The MG Stale-Config Retry Worker

When the stale-config fallback is active (see [11 § 7](11-mg-and-runtime-config.md)), `MgRetryWorker` retries MG every 15 minutes until success:

```kotlin
// :app/boot/MgRetryWorker.kt
@HiltWorker
internal class MgRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mgClient: MgClient,
    private val staleConfigFallback: StaleConfigFallback,
    private val runtimeConfigStore: RuntimeConfigStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val fresh = mgClient.fetch()
        staleConfigFallback.cache(fresh)
        runtimeConfigStore.commit(fresh)
        Result.success()
    }.getOrElse {
        Result.retry()                             // exponential backoff continues
    }
}

// Enqueued by BootCoordinator when fallback is triggered
fun scheduleMgRetry() = scheduler.enqueue(
    uniqueName = "mg-retry",
    worker = MgRetryWorker::class,
    constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
    backoff = BackoffPolicy.EXPONENTIAL_DEFAULT,
    tags = setOf("mg-retry"),
)
```

Once `MgRetryWorker` succeeds, the banner that says "Using last known configuration" dismisses automatically (the boot screen's ViewModel observes `runtimeConfigStore` and reacts).

---

## 8. Periodic Work vs One-Time Work

| Pattern | Use case |
|---|---|
| `OneTimeWorkRequest` with `ExistingWorkPolicy.KEEP` and re-enqueue on user action | Most user-triggered work (uploads, sync) |
| `PeriodicWorkRequest` with `ExistingPeriodicWorkPolicy.KEEP` | Recurring background tasks (clean-up of stale drafts older than 30 days) |
| `OneTimeWorkRequest` chained via `then()` | Multi-step pipelines (compress image → upload → notify) |

The framework prefers `OneTimeWorkRequest` over `PeriodicWorkRequest` where possible — periodic work has weaker constraint guarantees and runs on a system-decided cadence. For "every N hours" requirements, schedule a chain of one-time jobs.

---

## 9. What Does NOT Belong Here

| ❌ Wrong | ✅ Right |
|---|---|
| Long-running tasks in `Service` / `IntentService` | WorkManager |
| `AlarmManager` for repayment reminders | `WorkManager` periodic work (more battery-friendly + survives reboot) |
| Foreground service for arbitrary background work | Foreground services only for genuinely user-perceivable ongoing actions (e.g., chat call) |
| Workers that bypass the `BackgroundWorkScheduler` wrapper | Always go through the wrapper for consistent tagging |
| Worker that ignores the session-validity check | Mandatory first-action — see §6 |
| User-scoped work without `user-{id}` tag | Tag enables logout-cancellation |

---

## 10. Cross-references

- The KYC upload that uses this scheduler: [23 — KYC Capture](23-kyc-capture.md)
- The MG stale-config flow: [11 — MG and Runtime Config § 7](11-mg-and-runtime-config.md)
- The logout handler that cancels user-tagged work: [10 — Boot Phases § 6.3](10-boot-phases.md)
- The Sendbird chat that does NOT use this scheduler (its own retry): [20 — Chat](20-chat.md)
- The PDF downloader wrapper used by the contract worker: [24 — PDF](24-pdf.md)
