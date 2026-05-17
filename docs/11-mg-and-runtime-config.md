# 11 · MG and Runtime Config

> **MG** = *Mobile Gateway*. The startup service-discovery and ops-gating endpoint.
> **RuntimeConfig** = the typed payload returned by MG, immutable for the process lifetime.

The binary contains exactly one URL (the MG URL per build environment). MG returns everything else.

---

## 1. The MG Contract

`MgClient` calls `GET {MG_URL}/v1/runtime` and expects:

```json
{
  "urls": {
    "main":       "https://api.compass.bank/",
    "auxiliary":  "https://aux.compass.bank/"
  },
  "maintenance": {
    "status":     "up",
    "message":    null,
    "etaIso8601": null
  },
  "forceUpdate": {
    "minimumVersionCode": 240,
    "storeUrl":           "market://details?id=bank.compass.app"
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
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
    val storeReviewMode: StoreReviewMode,
)

data class ApiUrls(
    val main: HttpUrl,
    val auxiliary: HttpUrl,
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

---

## 2. What MG Does and Does Not Do

| MG returns | MG does NOT return |
|---|---|
| Main API base URL(s) | Feature flags (use Firebase Remote Config or similar) |
| Maintenance state + message | Variant-specific branding tokens (logos, colors) |
| Force-update floor + store link | User-specific data (auth happens later) |
| Auxiliary URLs (analytics, push, etc.) | The user's variant ID (that comes from `AuthRepository.login`) |

Keeping MG narrow means:

- **One contract change rarely affects unrelated systems.** Adding a feature flag mechanism doesn't touch MG.
- **MG can be served from a static origin if needed** (CDN-cacheable JSON). Adding feature flags would force MG to be a real service.
- **The boot path stays predictable.** Cold-start time is roughly one HTTPS round-trip plus the security self-check.

---

## 3. Per-Environment MG URLs

The MG URL is the only thing baked into the binary. It varies per build type:

| Build type | MG URL |
|---|---|
| `release` | `https://mg.compass.bank/` |
| `debug`   | `https://mg.staging.compass.bank/` (default; overridable via debug picker) |

```kotlin
// :app/build.gradle.kts
android.buildTypes {
    release {
        buildConfigField("String", "MG_URL", "\"https://mg.compass.bank/\"")
    }
    debug {
        buildConfigField("String", "MG_URL", "\"https://mg.staging.compass.bank/\"")
    }
}
```

`MgClient` reads `BuildConfig.MG_URL`. In debug builds, the URL is **overridable** at runtime via the environment-override dialog (see §6).

### 3.1 `MgClient` implementation

```kotlin
// :app/boot/MgClient.kt
@Singleton
internal class MgClient @Inject constructor(
    @MgRetrofit private val retrofit: Retrofit,           // built against the MG URL only
    private val mgEndpointResolver: MgEndpointResolver,   // returns BuildConfig.MG_URL or debug override
) {
    private val api = retrofit.create(MgApi::class.java)

    suspend fun fetch(): RuntimeConfig =
        api.getRuntime(mgEndpointResolver.current()).toDomain()

    private interface MgApi {
        @GET suspend fun getRuntime(@Url url: String): RuntimeConfigDto
    }
}

internal data class RuntimeConfigDto(
    val urls: ApiUrlsDto,
    val maintenance: MaintenanceDto,
    val forceUpdate: ForceUpdateDto,
) {
    fun toDomain(): RuntimeConfig = RuntimeConfig(
        urls        = urls.toDomain(),
        maintenance = maintenance.toDomain(),
        forceUpdate = forceUpdate.toDomain(),
    )
}
```

`MgClient` is `:app`-scoped (not `:data`-scoped) because it bootstraps before `LoggedInComponent` exists. It uses a Retrofit instance built against the hardcoded MG URL — distinct from the `RetrofitFactory`-built instance the rest of the app uses for `RuntimeConfig.urls.main`.

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
}
```

This is why the OkHttpClient does not need to be rebuilt when MG returns. The client is built at process start with placeholder URLs; the interceptor rewrites them at call time.

---

## 5. The `MaintenanceGate`

If MG returns `maintenance.status = down` or the running app version is below `forceUpdate.minimumVersionCode`, `BootCoordinator` returns `BootResult.Maintenance(...)` or `BootResult.ForceUpdate(...)`. The `BootScreen` Composable in `:features/boot/` renders the appropriate gate:

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

The gate is a hard stop — login is not reachable until the gate is cleared (by retry, by store update, or by MG returning a different state).

---

## 6. Debug-Only Environment Override

Debug builds expose a Compose dialog that replaces the MG URL at runtime — useful for QA pointing the same APK at different test backends without rebuilding.

```kotlin
// :app/debug/EnvironmentOverride.kt (debug source set only)
enum class Environment(val mgUrl: String) {
    Production("https://mg.compass.bank/"),
    Staging   ("https://mg.staging.compass.bank/"),
    Uat       ("https://mg.uat.compass.bank/"),
    Sandbox   ("https://mg.sandbox.compass.bank/"),
}
```

Selection persists in plain `SharedPreferences` (not encrypted — this is debug-only) and is read by `MgClient` *before* `BuildConfig.MG_URL` if present. Changing the override **requires a process restart** — the override is read once at cold start.

In `release` builds, this code is not compiled in. `BuildConfig.DEBUG` gating ensures the production binary cannot expose the picker.

---

## 6.5 Store Review Mode

Some features must ship in a build that's already on the Play Console review track but stay invisible to production users until rollout completes. The `storeReviewMode` field in `RuntimeConfig` handles this without a parallel feature-flag system.

| Field | Meaning |
|---|---|
| `enabled` | Server-side bit: did MG identify this user as a Play Store reviewer? Decided by device fingerprint, registered tester email, signing-key heuristic, or whatever identifier the backend trusts. |
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
        capabilities.supportsNewFeature() && isReviewer && isReviewBuild
}
```

Two states the gate covers:

| Phase | `enabled` | `VERSION_CODE` vs `reviewVersionCode` | Result |
|---|---|---|---|
| Production users on old version | (irrelevant) | `<` | Feature absent from binary anyway |
| Production users on new version, pre-rollout | `false` | `≥` | Hidden — they don't see it yet |
| Play Store reviewers on new version | `true` | `≥` | Visible — review can proceed |
| Post-GA | `true` for all | `≥` | Visible — or just remove the gate in code |

After GA, MG flips `enabled = true` for the production segment (or the code-side gate is deleted entirely). The version-code check guarantees the gate is meaningful only on builds that actually contain the feature — a user on the old version cannot accidentally trigger code paths that don't exist.

### What `storeReviewMode` is NOT

| ❌ Not the place for | ✅ Goes elsewhere |
|---|---|
| General feature flags (toggle existing features for A/B) | Firebase Remote Config or similar |
| Per-variant capability gating | `VariantCapabilities` in `:core/policy/` |
| Tenant-specific permission gating | Server-returned permission set on `Session` |
| Hiding bugs in production | Fix the bug |

`storeReviewMode` is narrow on purpose: one bit, one version code, one mechanism, owned by MG.

---

## 7. Failure Modes

| What goes wrong | What happens |
|---|---|
| MG endpoint unreachable | `BootCoordinator` shows a "can't reach servers" screen with retry. No login allowed. |
| MG returns malformed JSON | Same as above. Logged at `ERROR` to Crashlytics. |
| MG returns a URL that doesn't exist | Login attempts fail with a generic network error; no automatic re-fetch of MG. |
| Cert pin fails on MG | Same as MG unreachable — no login. |
| App version below `minimumVersionCode` | `ForceUpdateGate`. User can only open the store. |

There is **no fallback** to a hardcoded set of URLs if MG fails. Stale URLs in production are worse than a "can't connect" screen — silent fallbacks have shipped charges to wrong endpoints in past systems. The framework deliberately blocks rather than guesses.

---

## 8. Cross-references

- Where the MG fetch sits in the boot sequence: [10 — Boot Phases](10-boot-phases.md)
- Where the MG URL constant is defined: [08 — `:app`](08-app-orchestrator.md)
- The `:aos-core` interceptor that consumes the URLs: [02 — `:aos-core`](02-aos-core.md)
- The `:data` module that builds `FintechApi` against `RuntimeConfig.urls.main`: [05 — `:data`](05-data.md)
