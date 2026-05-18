# 02 · `:aos-core` — Infrastructure Layer

> **Type:** Git submodule · Android library
> **Stability:** High · independent of expense-management business rules
> **Reuse target:** Multiple projects share this module verbatim
> **Today in Bizplay:** the closest existing analogue is `SMART_LIB_STUDIO_CAMBODIA/smart_lib` (the Git submodule already in `.gitmodules`), which contains `RetrofitService`, `RetrofitNetwork`, `PreferenceDelegator`, `MemoryPreferenceDelegator`, `BizLocationManager`, `FlexibleToolBar`, etc. `:aos-core` is what that submodule grows into once boundaries are tightened.

---

## 1. Purpose

`:aos-core` is the project-agnostic plumbing layer. Anything a serious Android app needs that is **not specific to corporate expense management** lives here. It is a Git submodule so that bug fixes and security patches propagate to all dependent projects without per-project rework.

If you find yourself writing something that mentions `Receipt`, `Expense`, `Approval`, `Card`, `BizDoc`, `Variant`, `Tenant`, or a corporate-customer ID — **it does not belong in `:aos-core`**.

---

## 2. Contents

### 2.1 `network/`

| Class | Responsibility |
|---|---|
| `HttpClient` | Configures OkHttp with certificate pinning, connection timeouts, retry policy, TLS settings |
| `BaseApiResponse<T>` | Generic JSON envelope. In Bizplay's typed shape this maps to the existing `*_RES` skeleton (`result code`, `message`, `data`) — `:aos-core` owns the *generic* wrapper; the IPPP-specific fields live in `:data` DTOs. |
| `BaseUrlInterceptor` | Rewrites the request URL using a `BaseUrlProvider` resolved at call time. Enables MgGate-driven URL changes without rebuilding the OkHttp client. See [11 — MG and Runtime Config](11-mg-and-runtime-config.md). |
| `AuthHeaderInterceptor` | Attaches bearer/session tokens; reads from `EncryptedPrefs` |
| `RetrofitFactory` | Builds `Retrofit` instances with Moshi converters; consumed by `:data` |

```kotlin
// :aos-core/network/AuthHeaderInterceptor.kt
internal class AuthHeaderInterceptor @Inject constructor(
    private val encryptedPrefs: EncryptedPrefs,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = encryptedPrefs.getString(Keys.AUTH_TOKEN)
        val request = if (token.isNullOrEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
```

The interceptor reads from `EncryptedPrefs` per request, so token rotation (re-login, refresh) is picked up without rebuilding the OkHttp client.

> **Replaces today's `ComTran`:** the existing Bizplay codebase wraps networking in `comm/tran/ComTran` with a `mComTran.requestData(tranCode, url, params, onComTranListener)` callback shape. `:aos-core/network/` does not *contain* `ComTran` — it provides the Retrofit foundation that `:data` builds its `Ippp*Api` interfaces on top of, replacing the `tranCode`-keyed lookup with typed suspend functions.

### 2.2 `security/`

| Class | Responsibility |
|---|---|
| `SecurityProvider` | Root/jailbreak detection, debugger detection, integrity checks, and **malware scanning via the existing mVaccine SDK** (`core_b2b2c_*.jar`). Failure = abort cold start. |
| `BiometricAuthenticator` | Wraps `androidx.biometric` for fingerprint / face authentication prompts |
| `SecureKeypad` | Wraps the existing **TransKey SDK** (`mtk_v*.jar`). Provides a Compose `SecureKeypadField` Composable for password / PIN entry that routes keystrokes through TransKey rather than the system keyboard. |
| `EdgeCrypto` | Wraps the existing **Secucen library** (`libEdgeCrypto.so`). Native AES-GCM / RSA helpers for at-rest encryption (PIN, session tokens). |
| `EncryptionUtils` | Higher-level AES-GCM and RSA helpers; uses `EdgeCrypto` where available, falls back to platform crypto otherwise. |
| `KeystoreManager` | Provisions and rotates Android Keystore-backed keys |
| `LicenseChecker` | Wraps the existing **RSLicenseSDK** for registration / licence verification at cold start. |

### 2.3 `storage/`

| Class | Responsibility |
|---|---|
| `EncryptedPrefs` | Wrapper around `EncryptedSharedPreferences` (`MasterKey` AES-256). User-scoped (one logged-in user per session). Today's `PreferenceDelegator` / `MemoryPreferenceDelegator` move here once typed. |
| `EncryptedDatabase` | Wrapper around **SQLCipher** (`sqlcipher-android` 4.9.0). Owns the async-init pattern (today's `AsyncGenerateCipherDatabase`) so consumers see a synchronous interface backed by a deferred handle. |
| `SecureFileStore` | For larger blobs (e.g., session payloads, captured receipt photos before upload); EncryptedFile-backed |

### 2.4 `logging/`

| Class | Responsibility |
|---|---|
| `Logger` | Timber-based wrapper. Production builds drop logs below `WARN` and never log PII (employee IDs, card numbers, amounts tied to identities). |
| `CrashlyticsTree` | Routes `WARN`+ events to Firebase Crashlytics |

### 2.5 `firebase/`

Thin wrappers around Firebase SDKs so that consumers don't need direct Firebase imports.

| Class | Responsibility |
|---|---|
| `AnalyticsClient` | `logEvent(name, params)` with PII-stripping middleware |
| `RemoteConfigClient` | Typed accessor over Firebase Remote Config |
| `MessagingService` | Push notification entry points (today's FCM-driven approval-pending notifications) |

### 2.6 `webview/`

The WebView primitive that every webview-backed screen in the project uses. See [18 — WebView Integration](18-webview-integration.md) for the full contract.

| Class | Responsibility |
|---|---|
| `BizWebView` | Compose Composable wrapping `android.webkit.WebView` with hardened settings (no file access, mixed-content blocked, javascript-disabled by default for non-allowlisted hosts), `repeatOnLifecycle(STARTED)` bridge attachment, `DisposableEffect` cleanup. Replaces today's ad-hoc `BizWebview` Java class. |
| `WebActionBridge` | Exactly **one** `@JavascriptInterface` method, accepting a versioned JSON payload. Replaces today's `BrowserBridge` and its near-duplicate `iWebAction` / `iWebActionBA` overloads. |
| `CookieSync` | Push/pull cookies between OkHttp's `CookieJar` and Android's `CookieManager` around an allowlisted URL load. Lets approval pages (today loaded from `Constant.MG.C_APPROVAL_URL`) share the native session without exposing tokens via JS. |

### 2.7 `location/`

| Class | Responsibility |
|---|---|
| `LocationProvider` | Coroutine-friendly wrapper around Fused Location and (where the consuming app pulls them in via `:features-scanner`) Naver Maps / TMap helpers. Today's `BizLocationManager` moves here, stripped of expense-domain references. |

---

## 3. What `:aos-core` Must Never Contain

- **Expense / banking / business terms:** `Receipt`, `Expense`, `Approval`, `Card`, `BizDoc`, `Beneficiary`, `Money`, `USE_INTT_ID`, `COMPANY_CD`
- **Variant or tenant identifiers:** `KR`, `KH`, `VN`, `POSCO`, `NIA`, `Lotte`, `Shinsegae`, etc.
- **Repository interfaces or implementations** — those live in `:core` (interfaces) or `:data` (impls)
- **Compose UI beyond primitives** — `:aos-core` exposes `BizWebView`, `SecureKeypadField`, and similarly tightly-scoped Composables for infrastructure concerns. It does not host expense screens.
- **Hilt modules** — DI assembly is the orchestrator's job, not infrastructure's. (Wrapper classes are exposed; how they're bound is `:app`'s decision.)

If a class is unsure, the test is: *"Could a non-expense-management project (say, an HR tools app or a healthcare app for the same engineering team) use this class verbatim?"* If the answer is no, it doesn't belong here.

---

## 4. Submodule Mechanics

### 4.1 Repository structure

```
projects/
├── aos-core/                   (separate Git repo — like today's SMART_LIB_STUDIO_CAMBODIA)
│   ├── build.gradle.kts
│   └── src/main/kotlin/...
└── bizplay/                    (this repo)
    ├── aos-core/               (submodule pointing at projects/aos-core)
    └── ...
```

### 4.2 Versioning

`:aos-core` uses **Git tags as versions** (e.g., `v2.4.1`). The Bizplay repo pins a specific commit; upgrades are explicit:

```bash
git submodule update --remote aos-core
git add aos-core
git commit -m "bump aos-core to v2.5.0"
```

This makes infrastructure upgrades reviewable artifacts, not silent dependency changes. The current `SMART_LIB_STUDIO_CAMBODIA` submodule (pinned to branch `v1.0.13` today) already follows this pattern.

### 4.3 Stability mandate

Breaking API changes in `:aos-core` are **rare** and require a major version bump. Consumers (Bizplay included) opt into the upgrade. This is the entire reason `:aos-core` is a submodule rather than a Maven dependency — the team needs source-level visibility into infrastructure when debugging production incidents on customer-specific deployments.

---

## 5. Public Surface

Only the following packages should be visible to consumers:

```
com.bizplay.aoscore.network        ← HttpClient, RetrofitFactory, BaseUrlInterceptor
com.bizplay.aoscore.security       ← SecurityProvider, BiometricAuthenticator, SecureKeypad, EdgeCrypto, EncryptionUtils, LicenseChecker
com.bizplay.aoscore.storage        ← EncryptedPrefs, EncryptedDatabase, SecureFileStore
com.bizplay.aoscore.logging        ← Logger
com.bizplay.aoscore.firebase       ← AnalyticsClient, RemoteConfigClient
com.bizplay.aoscore.webview        ← BizWebView, WebActionBridge, CookieSync
com.bizplay.aoscore.location       ← LocationProvider
```

Implementation classes are `internal`. Consumers wire these via Hilt in `:app/di/NetworkModule.kt`, `:app/di/SecurityModule.kt`, etc.

---

## 6. Cross-references

- Where the orchestrator wires `:aos-core` into the data layer: [08 — `:app`](08-app-orchestrator.md)
- How `BaseUrlInterceptor` participates in MG-driven URL switching: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The data layer that consumes `RetrofitFactory`: [05 — `:data`](05-data.md)
- The WebView contract built on top of `BizWebView`: [18 — WebView Integration](18-webview-integration.md)
