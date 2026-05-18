# 18 · WebView Integration

> **Why this doc exists:** The existing Bizplay app is a *heavy* WebView consumer — approval UI, terms / privacy pages, member signup / forgot-password, KakaoPay link callbacks, online-mall partner pages, BizDoc approval flows, Biple Pay portal. All load through a custom `BizWebview` Java class wired to a `BrowserBridge` JS interface with near-duplicate methods (`iWebAction`, `iWebActionBA`, `iwebaction`). The framework accepts WebView's role but disciplines it: one primitive, one bridge contract, one URL allowlist, one cookie-sync mechanism.

---

## 1. Purpose

Three rules govern every WebView in the project:

1. **WebView code is infrastructure, not UI logic.** Primitives live in `:aos-core/webview/`. The actual webview-backed screens live in `:features-{name}` sibling modules (mirroring `:features-scanner`), **never** in `:features` itself.
2. **The JS bridge is a typed contract** — one method name, one versioned payload shape. Not the existing grab-bag of `iWebAction` / `iWebActionBA` / `iwebaction` near-duplicates that the legacy `BrowserBridge` exposes.
3. **Every URL the WebView may load comes from `RuntimeConfig.webRoutes`** (or a `:core` policy when per-variant). No hardcoded `loadUrl("https://…")` in any screen.

---

## 2. When to use WebView vs native

| Use WebView when | Use native when |
|---|---|
| The page is owned by a partner or regulator (KakaoPay link callback, Biple Pay portal, online-mall pages) | The flow is core expense UX (receipt list, OCR capture, approval inbox) |
| Content updates faster than the app ship cycle (terms, privacy, FAQs, notices, BizDoc form schemas) | The flow handles money, OTP, or session-sensitive state |
| The page already exists for another channel and rebuilds aren't justified (approval form for legacy companies) | The flow needs Compose features (gestures, animations) |
| The vendor / partner page is web-only | Performance matters (cold-start critical paths, long lists) |

A WebView screen is **legitimate**. A WebView screen that posts results back into native state via a JS bridge is where the discipline below matters.

---

## 3. Module Placement

```
                       :aos-core/webview/                  product-agnostic primitives
                       ├── BizWebView.kt                    Composable: cookie sync,
                       ├── WebActionBridge.kt               default chrome client,
                       └── CookieSync.kt                    one-method JS bridge
                                      ↑
                ┌─────────────────────┴─────────────────────┐
                │                                           │
        :core/policy/                              :design-system/
        WebActionPolicy + WebAction types          BizWebViewFrame
        (variant action-code map)                  (themed loading/error overlay)
                ↑                                           ↑
        :variants-{id}/policy/                     :features-{name}/
        KrWebActionPolicy impl                     :features-terms,
                                                   :features-online-mall,
                                                   :features-kakaopay-link,
                                                   :features-approval-webview, …
                                                   (webview-backed sibling modules)
```

A WebView-heavy feature follows the existing variant-locked feature pattern (see [07 — `:variants-*` § 9](07-variants.md)). It does **not** live inside `:features`: a webview screen with its own JS bridge has its own dependencies and SDK weight; isolating it is the same call as isolating the scanner.

> **Today vs framework:** the existing Bizplay code has WebView usage spread across `ApprovalFragment`, `ApprovalActivity`, `BizDocActivity`, `WebViewActivity`, `NewPopupWebviewActivity`, `LoginCompanyActivity`, `TermsAndConditionsActivity`, `BiplePayActivity`, `OnlineMallActivity`, `ReceiptTransportationDetailFragment`, and several popup helpers — each maintaining its own loadUrl / bridge / cleanup. The framework consolidates the primitive in `:aos-core/webview/` and lets each webview-backed feature own only its bridge handler + URL key.

---

## 4. The JS Bridge — one method, versioned payload

The single most common WebView antipattern is exposing several `@JavascriptInterface` methods that all do the same thing under slightly different names. Today's `BrowserBridge` is exactly that. The framework forbids it.

```kotlin
// :aos-core/webview/WebActionBridge.kt
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
| Exactly one `@JavascriptInterface` method per bridge | Web teams can't pick the wrong one if there's only one. Drift like today's `iWebAction` / `iWebActionBA` / `iwebaction` is impossible. |
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

    BizWebView(
        url    = vm.state.collectAsState().value.url,
        bridge = bridge,
        bridgeName = "Bizplay",      // window.Bizplay.postAction(...) from JS
    )
}
```

`BizWebView` is the `:aos-core` Composable that wraps `android.webkit.WebView` with: `addJavascriptInterface` lifecycle, `WebSettings` hardening (no file access, no JS-to-loadURL bridge, mixed-content blocked), cookie sync (§6), URL allowlist (§5), and a `DisposableEffect`-bound `destroy()`.

---

## 5. URL Allowlist via `RuntimeConfig`

A webview screen never receives an arbitrary URL string. It receives a **named route key** that resolves to a URL through `RuntimeConfig.webRoutes`:

```kotlin
// :core/runtime/RuntimeConfig.kt — extends the contract in 11
data class RuntimeConfig(
    val urls: ApiUrls,
    val webRoutes: Map<String, HttpUrl>,        // e.g. "terms.user_agreement" → HttpUrl
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
    val storeReviewMode: StoreReviewMode,
)
```

The map's *keys* are constants known to the client; the *values* come from MgGate. Today's existing key set (`Constant.MG.C_APPROVAL_URL`, `C_MEMBER_URL`, `C_FORGET_ID_URL`, `C_FORGET_PW_URL`, `C_LOGO_URL`, `C_APP_STORE_URL`, `C_PWD_INIT_URL`, …) becomes the typed string keys in `webRoutes`.

Resolved at the VM, validated by the framework, never by a string literal in the screen:

```kotlin
class TermsViewModel @Inject constructor(
    private val runtimeConfig: RuntimeConfigStore,
) : MviViewModel<…>() {
    private val url = runtimeConfig.webRoute("terms.user_agreement")    // throws if missing
    override val initialState = TermsState(url = url)
}
```

`BizWebView` **rejects** any in-app navigation whose host is not in `runtimeConfig.webRoutes.values.map { it.host }`. Out-of-allowlist URLs open in a system `CustomTabsIntent` (the external browser) instead — so a compromised partner redirect cannot impersonate the in-app session.

Per-variant URL sets are also legal — register a `WebRoutePolicy` in `:core/policy/` if KR needs different partner URLs than KH (which it almost certainly does — KakaoPay link is Korea-only).

---

## 6. Cookie / Session Sync

Native and WebView agree on the user's session **without** exposing raw tokens through JS.

1. After `AuthRepository.login` succeeds, `:data` stores the session cookie in OkHttp's `CookieJar` — same as native requests use.
2. Before loading an allowlisted URL, `CookieSync.pushTo(webview, url)` copies matching cookies from the jar into Android's `CookieManager`.
3. When the page is dismissed, `CookieSync.pullFrom(webview, url)` reads any cookies the page wrote back and stages them in the jar — keeping native requests in sync if the page rotated a token.

```kotlin
// :aos-core/webview/CookieSync.kt
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

The framework **never** exposes tokens to JS. If a partner page genuinely needs auth context beyond the cookie, generate a short-lived signed URL parameter native-side and pass it as `?sig=` — the cookie carries the session; the URL carries scoped intent. **`evaluateJavascript("window.token='$accessToken'")` is forbidden** (today's code is suspected of doing this in some KakaoPay callback paths — those need to migrate).

---

## 7. Per-variant Action Codes — `WebActionPolicy`

When the set of action codes a web page may emit differs per variant (KR supports `OPEN_KAKAOPAY_LINK`, KH/VN don't), the policy seam handles it — there are no `when (variantId)` branches in the bridge or the VM.

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
// :variants-kr/policy/KrWebActionPolicy.kt
internal class KrWebActionPolicy : WebActionPolicy {
    private val allowed = setOf(
        "CLOSE", "OPEN_OTP", "OPEN_KAKAOPAY_LINK", "RECEIPT_SHARED",
        "APPROVAL_SUBMITTED", "APPROVAL_REJECTED", "BIZDOC_COMPLETED",
    )

    override fun isAllowed(code: String) = code in allowed

    override fun handle(action: WebAction): WebActionResult = when (action.code) {
        "CLOSE"               -> WebActionResult.Navigate(NavRoute.PopBack)
        "OPEN_OTP"            -> WebActionResult.Navigate(NavRoute.NativeOtp)
        "OPEN_KAKAOPAY_LINK"  -> WebActionResult.Navigate(NavRoute.KakaoPayLink)
        "RECEIPT_SHARED"      -> WebActionResult.Handled
        "APPROVAL_SUBMITTED"  -> WebActionResult.Navigate(NavRoute.ApprovalInbox)
        "APPROVAL_REJECTED"   -> WebActionResult.Navigate(NavRoute.ApprovalInbox)
        "BIZDOC_COMPLETED"    -> WebActionResult.Navigate(NavRoute.ReceiptList)
        else                  -> WebActionResult.Reject("Unknown action ${action.code}")
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

## 8. Worked Flow: user submits an approval through the legacy approval-form web page

```
User taps an approval task in the inbox
         │
         ▼
:features-approval-webview (sibling module)
    ApprovalFormViewModel.initialState.url ← RuntimeConfig.webRoute("approval.form")
         │
         ▼
BizWebView (in :aos-core; framed by :design-system BizWebViewFrame)
    addJavascriptInterface(WebActionBridge, "Bizplay")
    CookieSync.pushTo(webView, url)
    loadUrl(state.url + "?approvalId=" + approvalId)
         │
         ▼  ── (web page runs) ──
         │
Page calls:  window.Bizplay.postAction(JSON.stringify({v:1, code:"APPROVAL_SUBMITTED"}))
         │
         ▼
WebActionBridge.postAction(payload)
    parse → WebAction(v=1, code="APPROVAL_SUBMITTED")
    scope.launch(Main.immediate) { onAction(action) }
         │
         ▼
ApprovalFormViewModel.onWebAction(action)
    policy.handle(action)          ← :core WebActionPolicy (KrWebActionPolicy is active)
         │
         ▼
WebActionResult.Navigate(NavRoute.ApprovalInbox) → UiEffect emitted, screen pops
         │
         ▼
CookieSync.pullFrom(webView, url)  (any new cookies staged into OkHttp's jar)
WebView.destroy()                   (via DisposableEffect)
         │
         ▼
ApprovalInboxScreen refreshes (its VM observes session.activeAccountId and re-fetches)
```

The native UI never trusts the page enough to mutate session state directly. The page emits an *intent* (action code); the policy validates it; the VM emits a navigation effect. Same shape as any other MVI event.

---

## 9. What does NOT go in a webview module

| ❌ Antipattern (seen in legacy code) | ✅ Framework rule |
|---|---|
| `mBizWebView.loadUrl("https://hardcoded.example.com/path")` | Resolve via `RuntimeConfig.webRoutes` keyed by route name |
| `mBizWebView.loadUrl("javascript:window.token='$accessToken'")` | Sign URLs / cookie-sync; never inject tokens via JS |
| `@JavascriptInterface fun iWebAction(s)` + `iWebActionBA(s)` + `iwebaction(s)` (today's `BrowserBridge`) | Exactly one method (`postAction`), versioned payload |
| `mHandler = new Handler()` (no Looper) | `CoroutineScope` + `Dispatchers.Main.immediate` |
| `if (mActivity.isFinishing()) return; … dlg.show()` | `repeatOnLifecycle(STARTED)`-bound bridge; `DisposableEffect` for cleanup |
| `if (DetailConfig.isPOSCO_ICT()) allow("OPEN_POSCO_BIZDOC")` | `WebActionPolicy` impl in `:variants-kr` (or tenant param if it's a customer-level toggle) |
| `static WebView sShared = …` | One per screen; lifecycle-bound; never reused |
| Bridge holds `Activity` reference | Bridge holds a callback closure over the VM only |
| Re-entrant URL config via `Conf.ISRELEASE ? prod : test` | `BuildConfig.MG_URL` per buildType (see [11](11-mg-and-runtime-config.md) §3) |
| `loadUrl(Constant.MG.C_APPROVAL_URL)` with the constant being a `MemoryPreferenceDelegator` key | `runtimeConfig.webRoute("approval.form")` returning a typed `HttpUrl` |

---

## 10. Cross-references

- The `RuntimeConfig` contract extended here (`webRoutes`, `storeReviewMode`): [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- Where webview-backed feature modules sit in the DAG: [07 — `:variants-*` § 9](07-variants.md)
- The `:aos-core` infrastructure layer hosting the WebView primitives: [02 — `:aos-core`](02-aos-core.md)
- The policy seam used for per-variant action codes: [03 — `:core` § 2.5](03-core.md)
- Cookie-jar configuration in OkHttp: [05 — `:data`](05-data.md)
