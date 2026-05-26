# 21 · Push Notification Channels

> **Mechanism:** FCM + Android `NotificationChannel` registry, with three first-class categories: **reminder**, **transaction**, **announcement**.
> **Where it lives:** `:aos-sdk/firebase/NotificationChannelRegistry` (channel definitions) + `:aos-sdk/firebase/MessagingService` (FCM receiver).
> **Permission:** `POST_NOTIFICATIONS` runtime permission for Android 13+.

> **PRD scope:** item 64 — "Push notifications have 3 category (reminder, transaction, announcement)". Without per-category channels, users cannot mute announcements while keeping repayment reminders. This is mandatory for compliant UX, not optional polish.

---

## 1. The Three Channels

| Channel id | User-visible label | Default importance | Sound | Vibration | Examples |
|---|---|---|---|---|---|
| `reminder` | "Repayment reminders" | `IMPORTANCE_HIGH` | default | yes | "Repayment due in 3 days", "Payment failed — retry now" |
| `transaction` | "Payment activity" | `IMPORTANCE_HIGH` | default | yes | "Disbursement received", "Payment confirmed", "Refund processed" |
| `announcement` | "News and offers" | `IMPORTANCE_LOW` | none | no | "New loan product available", "Branch hours changed" |

**Why separate channels?** Android 8+ requires every notification to be posted to a channel, and users control per-channel preferences from system settings. A single catch-all channel means users either get every announcement on the lock screen or mute repayment reminders too. Per-category channels are the only correct design.

**Channel importance is a default**, not a hard setting — users can override per-channel from system Settings. The defaults are the starting point.

---

## 2. The Registry

```kotlin
// :aos-sdk/firebase/NotificationChannelRegistry.kt
class NotificationChannelRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeConfig: RuntimeConfigStore,
) {
    fun registerAll() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(channels())
    }

    private fun channels(): List<NotificationChannel> = listOf(
        channel(
            id = ChannelIds.REMINDER,
            nameRes = R.string.channel_reminder_name,
            descriptionRes = R.string.channel_reminder_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            withSound = true,
            withVibration = true,
        ),
        channel(
            id = ChannelIds.TRANSACTION,
            nameRes = R.string.channel_transaction_name,
            descriptionRes = R.string.channel_transaction_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            withSound = true,
            withVibration = true,
        ),
        channel(
            id = ChannelIds.ANNOUNCEMENT,
            nameRes = R.string.channel_announcement_name,
            descriptionRes = R.string.channel_announcement_description,
            importance = NotificationManager.IMPORTANCE_LOW,
            withSound = false,
            withVibration = false,
        ),
    )

    private fun channel(
        id: String,
        @StringRes nameRes: Int,
        @StringRes descriptionRes: Int,
        importance: Int,
        withSound: Boolean,
        withVibration: Boolean,
    ): NotificationChannel = NotificationChannel(id, context.getString(nameRes), importance).apply {
        description = context.getString(descriptionRes)
        if (!withSound) setSound(null, null)
        enableVibration(withVibration)
        enableLights(true)
        setShowBadge(true)
    }

    object ChannelIds {
        const val REMINDER = "reminder"
        const val TRANSACTION = "transaction"
        const val ANNOUNCEMENT = "announcement"
    }
}
```

Channel names and descriptions live in `strings.xml` — they're user-facing and must be localized per [25 — Locale](25-locale.md).

**Channel registration is idempotent.** Calling `createNotificationChannels` repeatedly with the same channel id has no effect; calling it with a different importance does *not* downgrade an existing channel (Android intentionally protects user preferences). Channels should be registered once at `Application.onCreate()`.

```kotlin
// :app/CompassApplication.kt
override fun onCreate() {
    super.onCreate()
    loggerInit.install()
    securityProvider.runColdStartChecks()
    notificationChannelRegistry.registerAll()      // ← here
}
```

---

## 3. The `POST_NOTIFICATIONS` Permission (Android 13+)

```xml
<!-- :app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Runtime request — the user must grant it for any notification to be posted on API 33+:

```kotlin
// :aos-sdk/permissions/PermissionRequester.kt
class PermissionRequester {
    fun requestPostNotifications(host: Activity, onResult: (granted: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult(true); return                  // pre-Android-13: granted by manifest
        }
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            onResult(true); return
        }
        // ActivityResultLauncher must be registered before onStart; this is the trigger.
        host.findViewById<View>(android.R.id.content).postNotificationsLauncher.launch(
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}
```

**When to request:** at the natural ask-for-trust moment, typically right after first login or when the user enables a notification preference, **not** at cold start (annoying, low-conversion). If the user declines twice, the system pre-empts further requests; the app should fall back to in-app reminders.

---

## 4. FCM Receiving

```kotlin
// :aos-sdk/firebase/MessagingService.kt
class MessagingService : FirebaseMessagingService() {

    @Inject lateinit var router: PushRouter

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.from(message.data) ?: return    // malformed → drop, log to Crashlytics non-fatal
        router.route(payload)
    }

    override fun onNewToken(token: String) {
        // Re-register with backend; if no active session, persist for next login
        router.onTokenRefresh(token)
    }
}
```

The router lives in `:app` (banking-aware) and dispatches by `payload.channelId`:

```kotlin
// :app/push/PushRouter.kt
@Singleton
class PushRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val deepLinkResolver: DeepLinkResolver,
) {
    fun route(payload: PushPayload) {
        val builder = NotificationCompat.Builder(context, payload.channelId)
            .setSmallIcon(R.drawable.ic_compass_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setAutoCancel(true)

        payload.deepLink?.let { uri ->
            val intent = deepLinkResolver.intentFor(uri)        // see [22 — Deeplinks]
            builder.setContentIntent(PendingIntent.getActivity(
                context, payload.id.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        }

        notificationManager.notify(payload.id.hashCode(), builder.build())
    }

    fun onTokenRefresh(token: String) { /* re-register with server */ }
}

// :aos-sdk/firebase/PushPayload.kt
data class PushPayload(
    val id: String,
    val channelId: String,                    // "reminder" | "transaction" | "announcement"
    val title: String,
    val body: String,
    val deepLink: String?,                    // optional URI for tap action
) {
    companion object {
        fun from(data: Map<String, String>): PushPayload? = runCatching {
            PushPayload(
                id = data.getValue("id"),
                channelId = data.getValue("channel"),
                title = data.getValue("title"),
                body = data.getValue("body"),
                deepLink = data["deeplink"],
            )
        }.getOrNull()
    }
}
```

Server contract: every push message MUST include `channel` set to one of the three known ids. Unknown channel ids are dropped and logged.

---

## 5. Topic Subscription on Login

For tenant-scoped broadcasts (e.g., "all NH-KH users get this announcement"), subscribe to FCM topics at login and unsubscribe at logout:

```kotlin
// :app/session/PushTopicManager.kt
class PushTopicManager @Inject constructor(
    private val firebaseMessaging: FirebaseMessaging,
) {
    fun subscribeForSession(tenantId: TenantId, userId: UserId) {
        firebaseMessaging.subscribeToTopic("tenant_${tenantId.value.replace(":", "_")}")
        firebaseMessaging.subscribeToTopic("user_${userId.value}")
    }

    fun unsubscribeForSession(tenantId: TenantId, userId: UserId) {
        firebaseMessaging.unsubscribeFromTopic("tenant_${tenantId.value.replace(":", "_")}")
        firebaseMessaging.unsubscribeFromTopic("user_${userId.value}")
    }
}
```

Called from `BootCoordinator.onLoginSuccess(...)` and `LogoutHandler.logout()` respectively. The `:` in tenant ids is replaced with `_` because FCM topic names disallow colons.

---

## 6. Push and the Deeplink System

Push notifications carry an optional `deeplink` URI. Tapping the notification triggers an Intent built from the URI via the framework's `DeepLinkResolver`. See [22 — Deeplinks](22-deeplinks.md) for the URI schema (e.g., `compass://repayment/installment/123` → `RepaymentDetailScreen`).

The router does not need to know which screen the URI maps to — that's the resolver's job.

---

## 7. What Does NOT Belong Here

| ❌ Not in this layer | ✅ Belongs in |
|---|---|
| Hardcoded user-facing channel names | `strings.xml` (localized per [25 — Locale](25-locale.md)) |
| Importance overridden at runtime | Users own per-channel importance; respect their choice |
| A 4th channel "miscellaneous" | Categorize properly into reminder/transaction/announcement |
| Push payload deserialization in `:features` | `:aos-sdk/firebase/PushPayload` + `:app/push/PushRouter` only |
| FCM topic strings constructed ad-hoc in feature code | Encapsulated in `PushTopicManager` |
| Deeplink → screen routing | `DeepLinkResolver` ([22](22-deeplinks.md)) |

---

## 8. Cross-references

- The FCM wrapper: [02 — `:aos-sdk`](02-aos-core.md)
- Deeplink URI resolution: [22 — Deeplinks](22-deeplinks.md)
- The chat integration that uses the `reminder` channel: [20 — Chat](20-chat.md)
- The runtime permission flow: `:aos-sdk/permissions/PermissionRequester`
- Localization of channel labels: [25 — Locale](25-locale.md)
