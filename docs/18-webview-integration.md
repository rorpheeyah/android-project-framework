# 18 · WebView Integration

> **Why this doc exists:** Real fintech apps embed substantial web content — regulator-mandated forms, partner-owned booking flows, points malls, KYC, support content. The framework accepts that, but on its own terms: WebView is treated like any other external system the app integrates with, not as an escape hatch from the module boundaries.

---

## 1. Purpose

Three rules govern every WebView in the project:

1. **WebView code is infrastructure, not UI logic.** Primitives live in `:aos-sdk/webview/`. The actual webview-backed screens live in `:features-{name}` sibling modules (mirroring `:features-chatbot`), **never** in `:features` itself.
2. **The JS bridge is a typed contract** — one method name, one versioned payload shape. Not a grab-bag of `iWebAction` / `iWebActionBA` / `iwebaction` near-duplicates.
3. **Every URL the WebView may load comes from `RuntimeConfig`** (or a `:core` policy when per-variant). No hardcoded `loadUrl("https://…")` in any screen.

---

## 2. When to use WebView vs native

| Use WebView when | Use native when |
|---|---|
| The page is owned by a partner or regulator | The flow is core product UX |
| Content updates faster than the app ship cycle (legal copy, FAQs) | The flow handles money, OTP, or session-sensitive state |
| The page already exists for another channel and rebuilds aren't justified | The flow needs Compose features (gestures, animations) |
| The vendor SDK is web-only | Performance matters (cold-start critical paths, long lists) |

A WebView screen is **legitimate**. A WebView screen that posts results back into native state via a JS bridge is where the discipline below matters.

---

## 3. Module Placement

```
                       :aos-sdk/webview/                  banking-agnostic primitives
                       ├── CompassWebView.kt                Composable: cookie sync,
                       ├── WebActionBridge.kt               default chrome client,
                       └── CookieSync.kt                    one-method JS bridge
                                      ↑
                ┌─────────────────────┴─────────────────────┐
                │                                           │
        :core/policy/                              :design-system/
        WebActionPolicy + WebAction types          CompassWebViewFrame
        (tenant action-code map)                   (themed loading/error overlay)
                ↑                                           ↑
        :tenants:{region}:{tenantSlug}/policy/                     :features-{name}/
        KhWebActionPolicy impl                     :features-terms,
                                                   :features-online-mall,
                                                   :features-points-mall, …
                                                   (webview-backed sibling modules)
```

A WebView-heavy feature follows the existing tenant-locked feature pattern (see [07 — `:tenants:*` § 9](07-variants.md)). It does **not** live inside `:features`: a webview screen with its own JS bridge has its own dependencies and SDK weight; isolating it is the same call as isolating the chatbot.

---

## 4. The JS Bridge — one method, versioned payload

The single most common WebView antipattern is exposing several `@JavascriptInterface` methods that all do the same thing under slightly different names. The framework forbids it.

```kotlin
// :aos-sdk/webview/WebActionBridge.kt
class WebActionBridge(
    private val scope: CoroutineScope,
    private val onAction: (WebAction) -> Unit,
) {
    @JavascriptInterface
    fun postAction(payload: String) {                     // ← the ONE method
        scope.launch(Dispatchers.Main.immediate) {
            val action = WebAction.parse(payload) ?: return@launch
            onAction(action)
        }
    }
}

// :core/model/WebAction.kt
@Serializable
data class WebAction(
    val v: Int,                       // wire-format version, starts at 1
    val code: String,                 // action code, see :core constants
    val data: JsonElement? = null,    // shape per action; resolved by WebActionPolicy
)
```

| Rule | Why |
|---|---|
| Exactly one `@JavascriptInterface` method per bridge | Web teams can't pick the wrong one if there's only one. Drift is impossible. |
| Payload carries explicit `v` | Schema evolution is forced into the open; old clients reject `v > supported`. |
| Action codes are constants in `:core` | One vocabulary shared by web and native — no "what does `BA` mean?" |
| Dispatch via explicit `CoroutineScope` + `Dispatchers.Main` | No `Handler()` with implicit Looper. Bridge knows where it runs. |
| Bridge bound via `repeatOnLifecycle(STARTED)` | No `isFinishing()` TOCTOU. Detached automatically when the screen leaves the foreground. |
| Bridge holds **no** strong reference to the Activity | Pass a callback closing over the VM only. |

### Wiring the bridge in a screen

```kotlin
@Composable
internal fun TermsAgreementScreen(vm: TermsViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val bridge = remember { WebActionBridge(scope, onAction = vm::onWebAction) }

    CompassWebView(
        url    = vm.state.collectAsState().value.url,
        bridge = bridge,
        bridgeName = "Compass",      // window.Compass.postAction(...) from JS
    )
}
```

`CompassWebView` is the `:aos-sdk` Composable that wraps `android.webkit.WebView` with: `addJavascriptInterface` lifecycle, `WebSettings` hardening (no file access, no JS-to-loadURL bridge, mixed-content blocked), cookie sync (§6), URL allowlist (§5), and a `DisposableEffect`-bound `destroy()`.

---

## 5. URL Allowlist via `RuntimeConfig`

A webview screen never receives an arbitrary URL string. It receives a **named route key** that resolves to a URL through `RuntimeConfig.webRoutes`:

```kotlin
// :core/runtime/RuntimeConfig.kt — extends the contract in 11
data class RuntimeConfig(
    val urls: ApiUrls,
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
    val storeReviewMode: StoreReviewMode,
    val webRoutes: Map<String, HttpUrl>,        // e.g. "terms.user_agreement" → HttpUrl
)
```

Resolved at the VM, validated by the framework, never by a string literal in the screen:

```kotlin
class TermsViewModel @Inject constructor(
    private val runtimeConfig: RuntimeConfigStore,
) : MviViewModel<…>() {
    private val url = runtimeConfig.current().webRoutes["terms.user_agreement"]
        ?: error("MG must define webRoutes['terms.user_agreement']")
    override val initialState = TermsState(url = url)
}
```

`CompassWebView` **rejects** any in-app navigation whose host is not in `runtimeConfig.webRoutes.values.map { it.host }`. Out-of-allowlist URLs open in a system `CustomTabsIntent` (the external browser) instead — so a compromised partner redirect cannot impersonate the in-app session.

Per-variant URL sets are also legal — register a `WebRoutePolicy` in `:core/policy/` if KH needs different partner URLs than VN.

---

## 6. Cookie / Session Sync

Native and WebView agree on the user's session **without** exposing raw tokens through JS.

1. After `AuthRepository.login` succeeds, `:data` stores the session cookie in OkHttp's `CookieJar` — same as native requests use.
2. Before loading an allowlisted URL, `CookieSync.pushTo(webview, url)` copies matching cookies from the jar into Android's `CookieManager`.
3. When the page is dismissed, `CookieSync.pullFrom(webview, url)` reads any cookies the page wrote back and stages them in the jar — keeping native requests in sync if the page rotated a token.

```kotlin
// :aos-sdk/webview/CookieSync.kt
class CookieSync(private val jar: CookieJar) {
    fun pushTo(webView: WebView, url: HttpUrl) {
        jar.loadForRequest(url).forEach { c ->
            CookieManager.getInstance().setCookie(url.toString(), c.toString())
        }
        CookieManager.getInstance().flush()
    }
    fun pullFrom(webView: WebView, url: HttpUrl) {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return
        val parsed = Cookie.parseAll(url, Headers.headersOf("Set-Cookie", raw))
        jar.saveFromResponse(url, parsed)
    }
}
```

The framework **never** exposes tokens to JS. If a partner page genuinely needs auth context beyond the cookie, generate a short-lived signed URL parameter native-side and pass it as `?sig=` — the cookie carries the session; the URL carries scoped intent. **`evaluateJavascript("window.token='$accessToken'")` is forbidden.**

---

## 7. Per-variant Action Codes — `WebActionPolicy`

When the set of action codes a web page may emit differs per variant (KH supports `OPEN_KHQR_SCAN`, VN supports `OPEN_VIETQR_SCAN`), the policy seam handles it — there are no `when (variantId)` branches in the bridge or the VM.

```kotlin
// :core/policy/WebActionPolicy.kt
interface WebActionPolicy {
    fun isAllowed(actionCode: String): Boolean
    fun handle(action: WebAction): WebActionResult
}

sealed interface WebActionResult {
    object Handled : WebActionResult
    data class Navigate(val route: NavRoute) : WebActionResult
    data class Reject(val reason: String) : WebActionResult
}
```

Variant impl:

```kotlin
// :tenants:cambodia:nh/policy/KhWebActionPolicy.kt
internal class KhWebActionPolicy : WebActionPolicy {
    private val allowed = setOf("CLOSE", "OPEN_OTP", "OPEN_KHQR_SCAN", "RECEIPT_SHARED")

    override fun isAllowed(code: String) = code in allowed

    override fun handle(action: WebAction): WebActionResult = when (action.code) {
        "CLOSE"          -> WebActionResult.Navigate(NavRoute.PopBack)
        "OPEN_OTP"       -> WebActionResult.Navigate(NavRoute.NativeOtp)
        "OPEN_KHQR_SCAN" -> WebActionResult.Navigate(NavRoute.KhqrScan)
        "RECEIPT_SHARED" -> WebActionResult.Handled
        else             -> WebActionResult.Reject("Unknown action ${action.code}")
    }
}
```

The VM consumes `WebActionPolicy` by interface — same shape as any other policy seam.

```kotlin
class TermsViewModel @Inject constructor(
    private val policy: WebActionPolicy,
    ...
) : MviViewModel<…>() {
    fun onWebAction(action: WebAction) = when (val r = policy.handle(action)) {
        is WebActionResult.Navigate -> viewModelScope.launch { emitEffect(r.toEffect()) }
        WebActionResult.Handled     -> { /* no-op */ }
        is WebActionResult.Reject   -> logRejection(r.reason)
    }
}
```

---

## 8. Worked Flow: user opens a partner agreement page

```
User taps "Agree" on native screen
         │
         ▼
:features-terms (sibling module)
    TermsViewModel.initialState.url ← RuntimeConfig.webRoutes["terms.user_agreement"]
         │
         ▼
CompassWebView (in :aos-sdk; framed by :design-system)
    addJavascriptInterface(WebActionBridge, "Compass")
    CookieSync.pushTo(webView, url)
    loadUrl(state.url)
         │
         ▼  ── (web page runs) ──
         │
Page calls:  window.Compass.postAction(JSON.stringify({v:1, code:"AGREED"}))
         │
         ▼
WebActionBridge.postAction(payload)
    parse → WebAction(v=1, code="AGREED")
    scope.launch(Main.immediate) { onAction(action) }
         │
         ▼
TermsViewModel.onWebAction(action)
    policy.handle(action)          ← :core WebActionPolicy
         │
         ▼
WebActionResult.Navigate(NavRoute.MainScaffold) → UiEffect emitted, screen pops
         │
         ▼
CookieSync.pullFrom(webView, url)  (any new cookies staged into OkHttp's jar)
WebView.destroy()                   (via DisposableEffect)
```

The native UI never trusts the page enough to mutate session state directly. The page emits an *intent* (action code); the policy validates it; the VM emits a navigation effect. Same shape as any other MVI event.

---

## 9. What does NOT go in a webview module

| ❌ Antipattern (seen in legacy code) | ✅ Framework rule |
|---|---|
| `webView.loadUrl("https://hardcoded.example.com/path")` | Resolve via `RuntimeConfig.webRoutes` keyed by route name |
| `webView.evaluateJavascript("window.token='$accessToken'")` | Sign URLs / cookie-sync; never inject tokens via JS |
| `@JavascriptInterface fun iWebAction(s)` + `iWebActionBA(s)` + `iwebaction(s)` | Exactly one method (`postAction`), versioned payload |
| `mHandler = new Handler()` (no Looper) | `CoroutineScope` + `Dispatchers.Main.immediate` |
| `if (mActivity.isFinishing()) return; … dlg.show()` | `repeatOnLifecycle(STARTED)`-bound bridge; `DisposableEffect` for cleanup |
| `if (tenant.id == TenantId("cambodia:nh")) allow("OPEN_KHQR_SCAN")` | `WebActionPolicy` impl in `:tenants:cambodia:nh` |
| `static WebView sShared = …` | One per screen; lifecycle-bound; never reused |
| Bridge holds `Activity` reference | Bridge holds a callback closure over the VM only |
| Re-entrant cookies via `Conf.ISRELEASE ? prod : test` | `BuildConfig.MG_URL` per buildType (see [11](11-mg-and-runtime-config.md) §3) |

---

## 10. Cross-references

- The `RuntimeConfig` contract extended here (`webRoutes`, `storeReviewMode`): [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- Where webview-backed feature modules sit in the DAG: [07 — `:tenants:*:*` § 9](07-variants.md)
- The `:aos-sdk` infrastructure layer hosting the WebView primitives: [02 — `:aos-sdk`](02-aos-core.md)
- The policy seam used for per-variant action codes: [03 — `:core` § 2.5](03-core.md)
- Cookie-jar configuration in OkHttp: [05 — `:data`](05-data.md)
