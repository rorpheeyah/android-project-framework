# 26 · PIN UX and Session Timeout

> **PIN:** 4-digit, HMAC-hashed, brute-force-locked, biometric-bound, OTP-recoverable.
> **Session timeout:** inactivity-driven, tenant/regulator-configurable, fail-to-logout.
> **PRD items:** 2 (sign-up PIN setup), 5 (PIN access), 6 (forgot PIN via phone), 68 (account security toggles), 3 (sign-in with session timeout).

---

## 1. Why PIN Is a First-Class Surface

Banking apps use PIN for three reasons: it works without biometric hardware (older devices), it's regulator-accepted, and it's the second factor after device-binding. The framework provides it as a first-class capability with its own Compose primitive, secure storage, lockout policy, and recovery flow.

The PRD specifies **4 digits**. The framework does not parameterize PIN length per-tenant — that's a regulator-wide rule baked into the `PinPolicy` constant. If a future tenant needs 6 digits, escalate to a `PinPolicy` interface; until then, hardcode 4.

---

## 2. The Compose Primitives (`:design-system`)

```kotlin
// :design-system/components/input/CompassPinInput.kt
@Composable
fun CompassPinInput(
    digits: String,                          // current entry, e.g., "" → "1" → "12" → "1234"
    maxLength: Int = 4,
    onDigitEntered: (String) -> Unit,
    obscure: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CompassPinDots(filled = digits.length, total = maxLength, obscure = obscure)
        CompassPinKeypad(
            onDigit = { d -> if (digits.length < maxLength) onDigitEntered(digits + d) },
            onBackspace = { onDigitEntered(digits.dropLast(1)) },
            onBiometric = null,              // hook up via CompassPinScreen wrapper
        )
    }
}

// :design-system/components/input/CompassPinDots.kt
@Composable
fun CompassPinDots(filled: Int, total: Int, obscure: Boolean) {
    Row {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (i < filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// :design-system/components/input/CompassPinKeypad.kt
@Composable
fun CompassPinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,              // null = no biometric button shown
)
```

Visibility-by-default is obscured (•••). A "show PIN" toggle is **not** offered — banking PINs should never be revealed on-screen.

---

## 3. PIN Storage (HMAC, Never Plaintext)

The PIN is hashed with HMAC-SHA-256 using a per-user salt derived from the Android Keystore. The hash is stored in `EncryptedSharedPreferences`; the salt-source never leaves Keystore.

```kotlin
// :aos-sdk/security/PinStore.kt
class PinStore @Inject constructor(
    private val encryptedPrefs: EncryptedPrefs,
    private val keystoreManager: KeystoreManager,
) {
    fun set(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() }) { "PIN must be $PIN_LENGTH digits" }
        val salt = keystoreManager.getOrCreateHmacKey(KEY_ALIAS_PIN_SALT)
        val hash = hmacSha256(salt, pin)
        encryptedPrefs.put(KEY_PIN_HASH, hash)
        encryptedPrefs.put(KEY_PIN_SET_AT, Clock.System.now().toEpochMilliseconds())
    }

    fun verify(pin: String): VerifyResult {
        val stored = encryptedPrefs.getString(KEY_PIN_HASH) ?: return VerifyResult.NotSet
        val salt = keystoreManager.getHmacKey(KEY_ALIAS_PIN_SALT) ?: return VerifyResult.NotSet
        return if (hmacSha256(salt, pin) == stored) VerifyResult.Match
               else VerifyResult.Mismatch
    }

    fun clear() {
        encryptedPrefs.remove(KEY_PIN_HASH)
        encryptedPrefs.remove(KEY_PIN_SET_AT)
        // Note: do NOT delete the Keystore HMAC key; it would invalidate stored credentials cryptographically
        // (good if that's the intent on factory reset; bad if the user just changed their PIN).
    }

    sealed interface VerifyResult {
        object Match : VerifyResult
        object Mismatch : VerifyResult
        object NotSet : VerifyResult
    }

    private companion object {
        const val PIN_LENGTH = 4
        const val KEY_ALIAS_PIN_SALT = "compass.pin.salt"
        const val KEY_PIN_HASH = "pin.hash"
        const val KEY_PIN_SET_AT = "pin.set_at"
    }
}
```

**Why HMAC, not plain SHA-256?** A plain hash is brute-forceable by computing SHA-256 of every 4-digit combination (10,000 options) — trivial. HMAC keyed with a Keystore-resident secret makes the brute-force infeasible without root + Keystore extraction.

**Why not store the PIN encrypted-and-reversible?** The framework should not have a way to recover the original PIN — only to verify it. Storing it as a one-way hash is the standard.

---

## 4. Brute-Force Lockout

```kotlin
// :aos-sdk/security/PinLockout.kt
class PinLockout @Inject constructor(private val prefs: EncryptedPrefs, private val clock: Clock) {
    private val failureCount get() = prefs.getInt(KEY_FAILURES, 0)
    private val lockedUntil  get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)

    fun status(): LockoutStatus {
        val now = clock.now().toEpochMilliseconds()
        return when {
            now < lockedUntil      -> LockoutStatus.Locked(remainingMs = lockedUntil - now)
            failureCount >= maxAbsoluteFailures -> LockoutStatus.PermanentLockout  // user must re-login from scratch
            else                   -> LockoutStatus.Unlocked
        }
    }

    fun recordFailure() {
        prefs.put(KEY_FAILURES, failureCount + 1)
        val delayMs = backoffMs(failureCount + 1)
        if (delayMs > 0) {
            prefs.put(KEY_LOCKED_UNTIL, clock.now().toEpochMilliseconds() + delayMs)
        }
    }

    fun recordSuccess() {
        prefs.put(KEY_FAILURES, 0)
        prefs.put(KEY_LOCKED_UNTIL, 0L)
    }

    private fun backoffMs(failureCount: Int): Long = when (failureCount) {
        in 0..2  -> 0
        3        -> 10_000             // 10s
        4        -> 60_000             // 1min
        5        -> 600_000            // 10min
        6        -> 3_600_000          // 1h
        else     -> Long.MAX_VALUE     // → PermanentLockout
    }

    sealed interface LockoutStatus {
        object Unlocked : LockoutStatus
        data class Locked(val remainingMs: Long) : LockoutStatus
        object PermanentLockout : LockoutStatus
    }

    private companion object {
        const val KEY_FAILURES = "pin.failures"
        const val KEY_LOCKED_UNTIL = "pin.locked_until"
        const val maxAbsoluteFailures = 7
    }
}
```

`PermanentLockout` forces the user back through full login (phone + OTP). It is the safety net against unlimited brute-force.

---

## 5. Biometric-Bound Tier

The strongest tier binds the PIN-protected resource (e.g., a stored auth token) to a Keystore key with `setUserAuthenticationRequired(true)`. This means decryption only succeeds after a successful biometric (or PIN-via-system-dialog).

```kotlin
// :aos-sdk/security/BiometricBoundStore.kt
class BiometricBoundStore @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val biometric: BiometricAuthenticator,
) {
    suspend fun store(host: Activity, key: String, value: String): Result<Unit> = runCatching {
        biometric.authenticate(host, title = "Confirm to save").getOrThrow()
        val cipher = keystoreManager.getOrCreateBiometricBoundCipher(key)
        // encrypt and persist
    }

    suspend fun retrieve(host: Activity, key: String): Result<String> = runCatching {
        biometric.authenticate(host, title = "Confirm to access").getOrThrow()
        val cipher = keystoreManager.getBiometricBoundCipher(key) ?: error("Key not set")
        // decrypt
    }
}
```

Used for: sign-in with biometric (no PIN entry needed if biometric is available), high-risk action confirmation (large repayment).

---

## 6. Forgot-PIN Recovery (Phone + OTP)

PRD item 6. The recovery flow reuses the OTP infrastructure:

```kotlin
// :features/auth/pin/forgot/ForgotPinViewModel.kt
@HiltViewModel
internal class ForgotPinViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val pinStore: PinStore,
) : MviViewModel<ForgotPinState, ForgotPinEvent, ForgotPinEffect>(initial = ForgotPinState.EnterPhone) {

    override fun onEvent(event: ForgotPinEvent) = when (event) {
        is ForgotPinEvent.PhoneSubmitted -> requestOtp(event.phone)
        is ForgotPinEvent.OtpVerified    -> proceedToNewPin(event.handle, event.code)
        is ForgotPinEvent.NewPinChosen   -> setNewPin(event.pin)
    }

    private fun requestOtp(phone: String) = viewModelScope.launch {
        authRepo.requestOtp(phone)
            .onSuccess { handle -> setState { ForgotPinState.EnterOtp(handle) } }
            .onFailure { … }
    }

    private fun proceedToNewPin(handle: OtpHandle, code: String) = viewModelScope.launch {
        authRepo.verifyOtp(handle, code)
            .onSuccess { setState { ForgotPinState.SetNewPin } }
            .onFailure { … }
    }

    private fun setNewPin(pin: String) = viewModelScope.launch {
        pinStore.set(pin)
        emitEffect(ForgotPinEffect.NavigateToHome)
    }
}
```

Recovery does **not** reveal the old PIN. The server validates the OTP and confirms the user owns the phone; the client then sets a fresh PIN hash. Old hash is overwritten.

---

## 7. Session Timeout (Inactivity Detector)

PRD item 3: "Session managed with configurable timeout." Compliance auditors flag tab-and-forget sessions; the framework must enforce inactivity-driven logout.

### 7.1 The policy contract

```kotlin
// :core/policy/SessionTimeoutPolicy.kt
interface SessionTimeoutPolicy {
    /** Inactivity duration after which auto-logout fires. */
    val inactivityTimeout: Duration
    /** Whether biometric/PIN re-auth is required instead of full logout. */
    val reAuthInsteadOfLogout: Boolean
}

// :tenants:cambodia:base/policy/KhSessionTimeoutPolicy.kt
internal class KhSessionTimeoutPolicy : SessionTimeoutPolicy {
    override val inactivityTimeout = 5.minutes              // NBC-recommended baseline
    override val reAuthInsteadOfLogout = true               // re-auth tier, not full logout
}
```

Tenant/regulator-configurable. A consumer-facing product may use 5 minutes; a B2B treasury product may use 1 minute.

### 7.2 The detector

```kotlin
// :app/session/InactivityDetector.kt
@Singleton
class InactivityDetector @Inject constructor(
    private val componentManager: LoggedInComponentManager,
    private val policyProvider: Provider<SessionTimeoutPolicy>,    // resolved via TenantResolverModule
    private val onTimeout: SessionTimeoutHandler,
    private val clock: Clock,
) {
    private val lastInteractionAt = AtomicLong(clock.now().toEpochMilliseconds())
    private var watchdog: Job? = null

    fun recordInteraction() {
        lastInteractionAt.set(clock.now().toEpochMilliseconds())
    }

    fun start(scope: CoroutineScope) {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(15_000)                          // check every 15s; precise enough for minute-scale timeouts
                val policy = runCatching { policyProvider.get() }.getOrNull() ?: continue   // not logged in
                val idleMs = clock.now().toEpochMilliseconds() - lastInteractionAt.get()
                if (idleMs >= policy.inactivityTimeout.inWholeMilliseconds) {
                    onTimeout.fire(reAuth = policy.reAuthInsteadOfLogout)
                    watchdog?.cancel()
                    break
                }
            }
        }
    }

    fun stop() {
        watchdog?.cancel()
    }
}
```

The detector is started by `MainActivity.onResume()` and stopped on `onPause()` — it doesn't run when the app is fully backgrounded.

### 7.3 Capturing touch events

```kotlin
// :app/MainActivity.kt
class MainActivity : ComponentActivity() {
    @Inject lateinit var inactivityDetector: InactivityDetector

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        inactivityDetector.recordInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() { super.onResume(); inactivityDetector.start(lifecycleScope) }
    override fun onPause()  { super.onPause();  inactivityDetector.stop() }
}
```

Every touch resets the inactivity clock. Scroll, type, button-press — all count.

### 7.4 The timeout handler

```kotlin
// :app/session/SessionTimeoutHandler.kt
@Singleton
class SessionTimeoutHandler @Inject constructor(
    private val logoutHandler: LogoutHandler,
    private val navigator: GlobalNavigator,
) {
    fun fire(reAuth: Boolean) {
        if (reAuth) {
            navigator.navigate(Route.PinReAuth, popUpToCurrent = false)
            // PinReAuth screen wraps the app behind a PIN prompt; on success, navigation returns to where it was.
        } else {
            runBlocking { logoutHandler.logout() }
        }
    }
}
```

The `re-auth` path is the better UX for consumer banking: the user re-enters PIN and continues where they left off. The `full-logout` path is regulator-mandated for higher-risk products.

---

## 8. The Active-Sessions List (PRD Item 68)

For "view active sessions" in security settings: the server is the source of truth (it knows about every device that has issued requests with this user's tokens). The mobile app calls `AuthRepository.activeSessions()` and renders the list with a "revoke" button per entry.

The framework does not need a special primitive for this — it's a standard server-backed list with action buttons.

---

## 9. What Does NOT Belong Here

| ❌ Wrong | ✅ Right |
|---|---|
| Storing the PIN as plaintext or reversibly-encrypted | HMAC-SHA-256 hash, Keystore-keyed salt |
| Resetting `PinLockout.failureCount` on app restart | Failures persist across restarts; clearing only on `recordSuccess()` |
| Showing the PIN onscreen via a toggle | Always obscured |
| Allowing biometric-only auth without a PIN fallback | PIN is the always-available fallback |
| Per-tenant PIN length policy in `:core` | Hardcoded 4-digit until a real second tenant needs different |
| `InactivityDetector` ticking every 1 second | 15s polling is fine for minute-scale timeouts; saves battery |
| Touching `lastInteractionAt` from compose `clickable {}` callbacks | The single source of truth is `dispatchTouchEvent` — covers all UI |

---

## 10. Cross-references

- The Keystore wrapper: [02 — `:aos-sdk`](02-aos-core.md) (`security/KeystoreManager`)
- The biometric authenticator: [02 — `:aos-sdk`](02-aos-core.md) (`security/BiometricAuthenticator`)
- The encrypted prefs: [02 — `:aos-sdk`](02-aos-core.md) (`storage/EncryptedPrefs`)
- The logout flow that the timeout fires: [10 — Boot Phases § 6](10-boot-phases.md)
- The OTP repository used in forgot-PIN: [03 — `:core`](03-core.md), [07 — `:tenants:*`](07-variants.md) (`OtpDeliveryPolicy`)
- The tenant policy resolver: [19 — Tenants and Regions § 8](19-tenants-and-variants.md)
