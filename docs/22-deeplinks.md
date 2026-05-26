# 22 · Deeplinks and App Links

> **Mechanism:** Android App Links (HTTPS, verified via `assetlinks.json`) + Compose Navigation deeplink DSL.
> **Where it lives:** `:core/deeplink/DeepLinkRoute` (sealed contract) + `:aos-sdk/deeplink/DeepLinkResolver` (URI → Intent) + per-feature deeplink registrations in `:features` nav graphs.
> **Critical PRD flows:** guarantor SMS link (item 30/31), payment deeplink (item 47), push-notification tap-through (item 64).

---

## 1. Why App Links, Not Just URI Schemes

`compass://...` custom schemes work but have three problems: any other app can claim the same scheme; SMS clients don't always render them as tappable; iOS users hitting the SMS from a desktop browser see a broken link.

**App Links** (`https://app.compass.bank/...`) solve all three:

- Verified ownership via `.well-known/assetlinks.json` on `app.compass.bank` — only the Compass APK can intercept these URLs.
- SMS clients render them as tappable hyperlinks.
- Desktop browsers / non-installed devices land on a web fallback (your marketing page or a "download the app" CTA).

The framework uses App Links as the **primary** mechanism. Custom-scheme URIs (`compass://...`) are reserved for **in-app** navigation only (push payloads, intra-app sharing) — never sent over SMS or email.

---

## 2. The Route Contract (`:core/deeplink/`)

```kotlin
// :core/deeplink/DeepLinkRoute.kt
sealed interface DeepLinkRoute {
    val path: String   // e.g. "/loan/123" or "/guarantor/verify/abc-token"

    data class LoanDetail(val loanId: LoanId) : DeepLinkRoute {
        override val path: String get() = "/loan/${loanId.value}"
    }
    data class RepaymentInstallment(val installmentId: InstallmentId) : DeepLinkRoute {
        override val path: String get() = "/repayment/${installmentId.value}"
    }
    data class GuarantorVerify(val inviteToken: String) : DeepLinkRoute {
        override val path: String get() = "/guarantor/verify/$inviteToken"
    }
    data class LoanApplicationStatus(val applicationId: LoanApplicationId) : DeepLinkRoute {
        override val path: String get() = "/application/${applicationId.value}/status"
    }
    data class ChatThread(val threadId: ChatThreadId) : DeepLinkRoute {
        override val path: String get() = "/chat/${threadId.value}"
    }
    // … one per addressable destination
}
```

The sealed shape forces every destination to be a typed value. **No raw `String` routes** outside the resolver and the nav-graph registration.

---

## 3. The Resolver (`:aos-sdk/deeplink/`)

```kotlin
// :aos-sdk/deeplink/DeepLinkResolver.kt
class DeepLinkResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeConfig: RuntimeConfigStore,
) {
    /**
     * Build an Intent that, when launched, opens the right screen in the app.
     * Used by push notifications, in-app sharing, and external links.
     */
    fun intentFor(route: DeepLinkRoute): Intent {
        val uri = Uri.parse("https://${runtimeConfig.current().urls.appLinkHost}${route.path}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(context.packageName)             // ensures our app handles it, not the browser
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun intentFor(uriString: String): Intent? = runCatching {
        Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }.getOrNull()
}
```

The host (`app.compass.bank` in production) comes from `RuntimeConfig` — never hardcoded. This lets MG point at a staging host (`app.staging.compass.bank`) without rebuilding the APK.

---

## 4. App Links Manifest Configuration

```xml
<!-- :app/src/main/AndroidManifest.xml -->
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" />
        <data android:host="app.compass.bank" />
        <!-- For staging builds, an additional host is declared via manifest placeholders -->
    </intent-filter>
</activity>
```

`android:autoVerify="true"` triggers Android to fetch `https://app.compass.bank/.well-known/assetlinks.json` at install time and verify the SHA-256 of the signing certificate. If verification succeeds, App Links open in the app silently (no disambiguation dialog).

**The `assetlinks.json` is a backend deployable**, not a mobile concern — coordinate with web infra to publish:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.compass.bank",
    "sha256_cert_fingerprints": ["XX:XX:..."]
  }
}]
```

---

## 5. Compose Navigation Registration

Each feature module declares its deeplink URIs in its nav graph:

```kotlin
// :features/loan/LoanNavigator.kt
fun NavGraphBuilder.loanNavGraph(navController: NavHostController, host: String) {
    composable(
        route = "loan/{loanId}",
        arguments = listOf(navArgument("loanId") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://$host/loan/{loanId}" },
            navDeepLink { uriPattern = "compass://loan/{loanId}" },   // internal-only scheme
        ),
    ) { backStackEntry ->
        val loanId = backStackEntry.arguments?.getString("loanId")?.let(::LoanId) ?: return@composable
        LoanDetailScreen(loanId = loanId, /* … */)
    }
    // … other routes
}
```

The `host` parameter is the App Link host string from `RuntimeConfig`, passed in from `:app/AppNavigation`. Multiple deeplinks per composable are allowed; the HTTPS one is for external links, the `compass://` one is for push payloads and in-app callers.

---

## 6. The Guarantor SMS Link Flow (Critical Path)

This flow is the most security-sensitive deeplink in the PRD (items 30–35). Required properties:

1. **The link payload must be a signed JWT**, not a database key. The guarantor flow needs to verify the invite is unexpired and bound to the correct loan application before showing any UI.
2. **The link must work for a guarantor who has the app installed AND one who doesn't.** Non-installed users get a web fallback that suggests app installation; installed users land directly in the verification flow.
3. **The link must survive a logged-in customer who taps it accidentally** — if the customer (not the guarantor) opens it, show "this link is for the guarantor" rather than processing the action.

**Implementation:**

```kotlin
// :features/loan/apply/guarantor/GuarantorVerifyNavGraph.kt
composable(
    route = "guarantor/verify/{token}",
    arguments = listOf(navArgument("token") { type = NavType.StringType }),
    deepLinks = listOf(
        navDeepLink { uriPattern = "https://$host/guarantor/verify/{token}" },
    ),
) { backStackEntry ->
    val token = backStackEntry.arguments?.getString("token") ?: return@composable
    GuarantorVerifyEntryScreen(inviteToken = token)
}

// :features/loan/apply/guarantor/GuarantorVerifyEntryViewModel.kt
@HiltViewModel
internal class GuarantorVerifyEntryViewModel @Inject constructor(
    private val guarantorRepo: GuarantorRepository,
    private val session: SessionState,     // observable: null when logged out, Session when logged in
) : MviViewModel<…>(initial = EntryState.Loading) {

    init {
        viewModelScope.launch {
            val invite = guarantorRepo.resolveInvite(token).getOrElse {
                setState { EntryState.LinkExpired }; return@launch
            }
            val currentSession = session.current()
            when {
                currentSession?.userSession?.userId == invite.borrowerUserId ->
                    setState { EntryState.SelfTapBlocked }      // customer accidentally tapped own invite
                else ->
                    emitEffect(EntryEffect.NavigateToOtp(invite))
            }
        }
    }
}
```

The JWT verification happens server-side via `guarantorRepo.resolveInvite(token)`. The client just passes the token; the server decides if it's valid and who it's for.

---

## 7. The Payment Deeplink Flow

For item 47 (Pay Now from notification or shortcut):

```kotlin
// Generated from server-side push payload or in-app share
val deepLink = deepLinkResolver.intentFor(DeepLinkRoute.RepaymentInstallment(installmentId))

// In nav graph
composable(
    route = "repayment/{installmentId}",
    deepLinks = listOf(navDeepLink { uriPattern = "https://$host/repayment/{installmentId}" }),
) { entry ->
    val installmentId = entry.arguments?.getString("installmentId")?.let(::InstallmentId)
    RepaymentDetailScreen(installmentId = installmentId, /* … */)
}
```

The screen handles the case where the user is not currently logged in (redirect to login with a `redirect_to` extra) and the case where the installment is already paid (show a "this is already paid" state).

---

## 8. Deeplinks and Login Gating

Many deeplinks require an active session. The pattern:

1. The deeplink lands on its destination Composable.
2. The Composable's ViewModel checks for an active `Session`.
3. If absent, it emits `NavigateToLogin(returnToRoute = ...)`.
4. After successful login, `BootCoordinator.onLoginSuccess` checks for a pending redirect and re-launches the original Intent.

The `returnToRoute` is held in a `@SingletonComponent`-scoped `PendingDeepLinkStore` — outside `LoggedInComponent` so it survives the not-yet-logged-in window.

Public/unauthenticated deeplinks (e.g., guarantor verify) skip step 2 entirely — they handle their own session-or-not state.

---

## 9. What Does NOT Belong Here

| ❌ Not a deeplink concern | ✅ Belongs in |
|---|---|
| Raw URL parsing in `:features` ViewModels | `DeepLinkResolver` + the nav-graph `navDeepLink { … }` registration |
| Custom URI scheme for SMS-sent links | Use HTTPS App Link; `compass://` is internal-only |
| Hardcoded App Link host | `RuntimeConfig.urls.appLinkHost` (MG-sourced) |
| App-specific dialog when a deeplink can't be resolved | Generic "this link is no longer valid" screen owned by `:features/boot` |
| Branch/Adjust SDK for deeplink attribution | Out of PRD scope; revisit if attribution is needed |

---

## 10. Cross-references

- The push integration that fires deeplinks: [21 — Push Channels](21-push-channels.md)
- The guarantor flow that consumes the SMS deeplink: [07 — `:tenants:*`](07-variants.md), [30 — Form Wizard](30-form-wizard.md)
- The `RuntimeConfig.urls.appLinkHost` that the resolver reads: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` checks for gated deeplinks: [12 — Departments and Session](12-departments-and-session.md)
- The boot redirect mechanism for pending deeplinks: [10 — Boot Phases](10-boot-phases.md)
