# 11 · MG and Runtime Config

> **MG** = *Mobile Gateway*. The startup service-discovery and ops-gating endpoint. In the existing Bizplay project this is the `MgGate` endpoint at `https://mg.bizplay.co.kr/MgGate` (production) — the framework keeps the name verbatim.
> **RuntimeConfig** = the typed payload returned by MG, immutable for the process lifetime.

The binary contains exactly one URL (the MgGate URL per build environment). MG returns everything else.

---

## 1. The MG Contract

`MgClient` calls `GET {MG_URL}?master_id=A_BIZ_IPPP_<COMPANY_CD>_G_1&COMPANY_CD=<companyCode>` (today's shape — keep it) and expects, decoded into a typed JSON envelope:

```json
{
  "urls": {
    "main":         "https://www.bizplay.co.kr/mobl_ppp_infm_r001.jct",
    "auxiliary":    "https://aux.bizplay.co.kr/"
  },
  "webRoutes": {
    "terms.user_agreement":    "https://www.bizplay.co.kr/terms/user",
    "terms.privacy":           "https://www.bizplay.co.kr/terms/privacy",
    "approval.form":           "https://approval.bizplay.co.kr/form",
    "member.signup":           "https://www.bizplay.co.kr/member/signup",
    "member.findpw":           "https://www.bizplay.co.kr/member/findpw",
    "kakaopay.link":           "https://kakaopay.bizplay.co.kr/link",
    "online_mall.entry":       "https://mall.bizplay.co.kr/"
  },
  "maintenance": {
    "status":     "up",
    "message":    null,
    "etaIso8601": null
  },
  "forceUpdate": {
    "minimumVersionCode": 240,
    "storeUrl":           "market://details?id=com.bizcard.bizplayPPPEnt"
  },
  "storeReviewMode": {
    "enabled":           false,
    "reviewVersionCode": 245
  }
}
```

Decoded into `:core/runtime/RuntimeConfig`:

```kotlin
data class RuntimeConfig(
    val urls: ApiUrls,
    val webRoutes: Map<String, HttpUrl>,
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
    val storeReviewMode: StoreReviewMode,
)

data class ApiUrls(
    val main: HttpUrl,        // today's Conf.IPPP_SITE_URL
    val auxiliary: HttpUrl,   // partner callbacks, future endpoints
)

sealed interface MaintenanceState {
    object Up : MaintenanceState
    data class Down(val message: String, val eta: Instant?) : MaintenanceState
}

data class ForceUpdate(
    val minimumVersionCode: Int,
    val storeUrl: String,
)

data class StoreReviewMode(
    val enabled: Boolean,         // server-side decision: is this user a Play Store reviewer?
    val reviewVersionCode: Int,   // version code currently in store review / phased rollout
)
```

> **Why the typed `webRoutes` map?** The existing Bizplay code today reads `MemoryPreferenceDelegator.get(Constant.MG.C_APPROVAL_URL)` etc., with the keys as untyped string constants. The framework replaces that with a typed `webRoutes` map plus typed accessor methods, so a missing key fails loudly at the call site rather than producing a `null` that propagates to `webView.loadUrl(null)`. See [18 — WebView Integration](18-webview-integration.md).

---

## 2. What MG Does and Does Not Do

| MG returns | MG does NOT return |
|---|---|
| Main IPPP API base URL | Feature flags (use Firebase Remote Config or similar) |
| Maintenance state + message | Per-tenant branding tokens (logos, colors) |
| Force-update floor + store link | User-specific data (auth happens later) |
| Auxiliary URLs (partner callbacks, push, etc.) | The user's variant ID or tenant ID (those come from `AuthRepository.login`) |
| WebView URL allowlist (`webRoutes`) | Per-tenant `TenantFlags` values (those come on the login response) |

Keeping MG narrow means:

- **One contract change rarely affects unrelated systems.** Adding a feature flag mechanism doesn't touch MG.
- **MG can be served from a static origin if needed** (CDN-cacheable JSON). Adding feature flags would force MG to be a real service.
- **The boot path stays predictable.** Cold-start time is roughly one HTTPS round-trip plus the security self-check (mVaccine + root/jailbreak + RSLicense).

---

## 3. Per-Environment MG URLs

The MG URL is the only thing baked into the binary. It varies per build type:

| Build type | MG URL |
|---|---|
| `release` | `https://mg.bizplay.co.kr/MgGate` |
| `debug`   | `https://mg-dev.bizplay.co.kr/MgGate` (default; overridable via debug picker) |

```kotlin
// :app/build.gradle.kts
android.buildTypes {
    release {
        buildConfigField("String", "MG_URL", "\"https://mg.bizplay.co.kr/MgGate\"")
    }
    debug {
        buildConfigField("String", "MG_URL", "\"https://mg-dev.bizplay.co.kr/MgGate\"")
    }
}
```

`MgClient` reads `BuildConfig.MG_URL`. In debug builds, the URL is **overridable** at runtime via the environment-override dialog (see §6).

> **Note:** today's Bizplay project uses `Conf.SITE_MG_URL` per build type (see `Conf.java` lines 37–80). The framework keeps the exact shape — only the read path changes (from a static Java field to a typed `BuildConfig` constant funneled through `MgClient`).

### 3.1 `MgClient` implementation

```kotlin
// :app/boot/MgClient.kt
@Singleton
internal class MgClient @Inject constructor(
    @MgRetrofit private val retrofit: Retrofit,           // built against the MG URL only
    private val mgEndpointResolver: MgEndpointResolver,   // returns BuildConfig.MG_URL or debug override
) {
    private val api = retrofit.create(MgApi::class.java)

    suspend fun fetch(masterId: String, companyCode: String): RuntimeConfig =
        api.getRuntime(mgEndpointResolver.current(), masterId, companyCode).toDomain()

    private interface MgApi {
        @GET suspend fun getRuntime(
            @Url url: String,
            @Query("master_id") masterId: String,
            @Query("COMPANY_CD") companyCode: String,
        ): RuntimeConfigDto
    }
}

internal data class RuntimeConfigDto(
    val urls: ApiUrlsDto,
    val webRoutes: Map<String, String>,
    val maintenance: MaintenanceDto,
    val forceUpdate: ForceUpdateDto,
    val storeReviewMode: StoreReviewModeDto,
) {
    fun toDomain(): RuntimeConfig = RuntimeConfig(
        urls            = urls.toDomain(),
        webRoutes       = webRoutes.mapValues { it.value.toHttpUrl() },
        maintenance     = maintenance.toDomain(),
        forceUpdate     = forceUpdate.toDomain(),
        storeReviewMode = storeReviewMode.toDomain(),
    )
}
```

`MgClient` is `:app`-scoped (not `:data`-scoped) because it bootstraps before `LoggedInComponent` exists. It uses a Retrofit instance built against the hardcoded MgGate URL — distinct from the `RetrofitFactory`-built instance the rest of the app uses for `RuntimeConfig.urls.main`.

---

## 4. The `BaseUrlInterceptor` Bridge

Once `RuntimeConfig` is committed, every Retrofit call routed through `:aos-core`'s OkHttp client gets its base URL rewritten:

```kotlin
// :aos-core/network/BaseUrlInterceptor.kt
class BaseUrlInterceptor(private val provider: BaseUrlProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newUrl = request.url.newBuilder()
            .scheme(provider.current().scheme)
            .host(provider.current().host)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
```

`BaseUrlProvider` is backed by the `RuntimeConfigStore` in `:app`:

```kotlin
@Singleton
class RuntimeConfigStore @Inject constructor() : BaseUrlProvider {
    private var config: RuntimeConfig? = null
    fun commit(c: RuntimeConfig) { config = c }
    override fun current(): HttpUrl = checkNotNull(config).urls.main
    fun webRoute(key: String): HttpUrl = checkNotNull(config?.webRoutes?.get(key)) {
        "MgGate did not return webRoutes['$key']"
    }
}
```

This is why the OkHttpClient does not need to be rebuilt when MgGate returns. The client is built at process start with placeholder URLs; the interceptor rewrites them at call time.

---

## 5. The `MaintenanceGate`

If MgGate returns `maintenance.status = down` or the running app version is below `forceUpdate.minimumVersionCode`, `BootCoordinator` returns `BootResult.Maintenance(...)` or `BootResult.ForceUpdate(...)`. The `BootScreen` Composable in `:features/boot/` renders the appropriate gate:

```kotlin
@Composable
internal fun MaintenanceGate(state: MaintenanceState.Down, onRetry: () -> Unit) {
    // Lock screen with message + ETA, retry button
}

@Composable
internal fun ForceUpdateGate(forceUpdate: ForceUpdate, onOpenStore: () -> Unit) {
    // Lock screen with "Update required" + open-store button
}
```

The gate is a hard stop — login is not reachable until the gate is cleared (by retry, by store update, or by MgGate returning a different state).

---

## 6. Debug-Only Environment Override

Debug builds expose a Compose dialog that replaces the MG URL at runtime — useful for QA pointing the same APK at different test backends without rebuilding.

```kotlin
// :app/debug/EnvironmentOverride.kt (debug source set only)
enum class Environment(val mgUrl: String) {
    Production("https://mg.bizplay.co.kr/MgGate"),
    Staging   ("https://mg-stg.bizplay.co.kr/MgGate"),
    Uat       ("https://mg-uat.bizplay.co.kr/MgGate"),
    Sandbox   ("https://mg-dev.bizplay.co.kr/MgGate"),
}
```

Selection persists in plain `SharedPreferences` (not encrypted — this is debug-only) and is read by `MgClient` *before* `BuildConfig.MG_URL` if present. Changing the override **requires a process restart** — the override is read once at cold start.

In `release` builds, this code is not compiled in. `BuildConfig.DEBUG` gating ensures the production binary cannot expose the picker.

---

## 6.5 Store Review Mode

Some features must ship in a build that's already on the Play Console review track but stay invisible to production users until rollout completes. The `storeReviewMode` field in `RuntimeConfig` handles this without a parallel feature-flag system.

| Field | Meaning |
|---|---|
| `enabled` | Server-side bit: did MgGate identify this user as a Play Store reviewer? Decided by device fingerprint, registered tester email, signing-key heuristic, or whatever identifier the backend trusts. |
| `reviewVersionCode` | The version code currently going through store review. Features tagged with `>= reviewVersionCode` are visible to reviewers; everyone else stays on the prior surface. |

### Gating pattern

```kotlin
// :features/<feature>/SomeViewModel.kt
class SomeViewModel @Inject constructor(
    private val runtimeConfig: RuntimeConfigStore,
    private val capabilities: VariantCapabilities,
) : MviViewModel<…>() {

    private val srm           = runtimeConfig.current().storeReviewMode
    private val isReviewer    = srm.enabled
    private val isReviewBuild = BuildConfig.VERSION_CODE >= srm.reviewVersionCode

    // Feature ships in this binary but is review-only until GA:
    private val showReviewOnlyFeature: Boolean =
        capabilities.supportsMyDataIntegration() && isReviewer && isReviewBuild
}
```

Two states the gate covers:

| Phase | `enabled` | `VERSION_CODE` vs `reviewVersionCode` | Result |
|---|---|---|---|
| Production users on old version | (irrelevant) | `<` | Feature absent from binary anyway |
| Production users on new version, pre-rollout | `false` | `≥` | Hidden — they don't see it yet |
| Play Store reviewers on new version | `true` | `≥` | Visible — review can proceed |
| Post-GA | `true` for all | `≥` | Visible — or just remove the gate in code |

After GA, MgGate flips `enabled = true` for the production segment (or the code-side gate is deleted entirely). The version-code check guarantees the gate is meaningful only on builds that actually contain the feature — a user on the old version cannot accidentally trigger code paths that don't exist.

### What `storeReviewMode` is NOT

| ❌ Not the place for | ✅ Goes elsewhere |
|---|---|
| General feature flags (toggle existing features for A/B) | Firebase Remote Config or similar |
| Per-variant capability gating | `VariantCapabilities` in `:core/policy/` |
| Per-tenant permission gating | `TenantFlags` / `TenantParams` on `Session.tenantContext`, sourced from the login response |
| Hiding bugs in production | Fix the bug |

`storeReviewMode` is narrow on purpose: one bit, one version code, one mechanism, owned by MgGate.

---

## 7. Failure Modes

| What goes wrong | What happens |
|---|---|
| MgGate endpoint unreachable | `BootCoordinator` shows a "can't reach servers" screen with retry. No login allowed. |
| MgGate returns malformed JSON | Same as above. Logged at `ERROR` to Crashlytics. |
| MgGate returns a URL that doesn't exist | Login attempts fail with a generic network error; no automatic re-fetch of MgGate. |
| Cert pin fails on MgGate | Same as MgGate unreachable — no login. |
| App version below `minimumVersionCode` | `ForceUpdateGate`. User can only open the store. |
| `RuntimeConfig.webRoutes["approval.form"]` missing when an approval-form screen tries to read it | Hard fail at the call site (`error(...)`). Crashlytics-logged. The framework refuses to load an unallowlisted URL — see [18](18-webview-integration.md). |

There is **no fallback** to a hardcoded set of URLs if MgGate fails. Stale URLs in production are worse than a "can't connect" screen — silent fallbacks have shipped customers to wrong approval forms in past systems. The framework deliberately blocks rather than guesses.

---

## 8. Cross-references

- Where the MgGate fetch sits in the boot sequence: [10 — Boot Phases](10-boot-phases.md)
- Where the MG URL constant is defined: [08 — `:app`](08-app-orchestrator.md)
- The `:aos-core` interceptor that consumes the URLs: [02 — `:aos-core`](02-aos-core.md)
- The `:data` module that builds the `Ippp*Api` family against `RuntimeConfig.urls.main`: [05 — `:data`](05-data.md)
- The WebView module that consumes `RuntimeConfig.webRoutes`: [18 — WebView Integration](18-webview-integration.md)
