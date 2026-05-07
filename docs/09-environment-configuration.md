# 09 · Environment Configuration

> **Build types:**
> - **`release`** — hardcoded to production. **Zero UI** for environment switching.
> - **`debug`** — server-selection popup at first launch. Lets QA/devs toggle Staging / UAT / Sandbox / Production.
>
> **Mechanism:** an `EnvironmentInterceptor` in `:aos-core` rewrites request URLs at call time using the `BaseUrlProvider` resolved from `TenantProvider`.

---

## 1. The Two Independent Axes

A request's effective URL is a function of **two** pieces of state:

| Axis | Source of truth | Switchable at runtime? |
|---|---|---|
| **Tenant** | `TenantProvider.currentTenant` (in `:core`) | Yes — full DI swap (see [08]) |
| **Environment** | `EnvironmentRegistry.active` (in `:aos-core`) | `debug`: yes via picker · `release`: no, locked to Production |

Splitting these axes means QA can run **`tenants-kh` against Staging** while a developer concurrently runs **`tenants-vn` against UAT**. Tenant logic and environment endpoints are orthogonal concerns.

---

## 2. The Environment Model

Defined in `:aos-core/network/`:

```kotlin
enum class Environment {
    Production,
    Staging,
    Uat,
    Sandbox,
}

data class EnvironmentManifest(
    val environment: Environment,
    val tenantBaseUrls: Map<TenantId, HttpUrl>,
)

interface EnvironmentRegistry {
    val active: StateFlow<Environment>
    fun set(environment: Environment)
    fun manifestFor(environment: Environment): EnvironmentManifest
}
```

The `EnvironmentManifest` is loaded from `assets/environments/<env>.json` — a versioned, per-build-flavor file mapping each tenant to its environment-specific base URL.

> Manifests in assets (rather than `BuildConfig`) keep the build matrix small: one APK can serve all environments in `debug`, gated by the picker.

---

## 3. The `EnvironmentInterceptor`

Lives in `:aos-core/network/`. Resolves the base URL **at call time**, not at OkHttp client construction:

```kotlin
class EnvironmentInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val resolved = baseUrlProvider.current()       // synchronous read of latest
        val rewritten = original.url.newBuilder()
            .scheme(resolved.scheme)
            .host(resolved.host)
            .port(resolved.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
```

`BaseUrlProvider` is the integration point with `:core`:

```kotlin
@Singleton
class BaseUrlProvider @Inject constructor(
    private val tenantProvider: TenantProvider,
    private val environmentRegistry: EnvironmentRegistry,
) {
    fun current(): HttpUrl {
        val tenant = tenantProvider.currentTenant.value.id
        val env = environmentRegistry.active.value
        return environmentRegistry.manifestFor(env).tenantBaseUrls.getValue(tenant)
    }
}
```

Because the interceptor reads `BaseUrlProvider.current()` per request, **changing either tenant or environment takes effect on the next outbound call** — no client rebuild required.

---

## 4. The Debug Picker

Compose dialog defined in `:app/debug/EnvironmentPicker.kt`. **Source set:** `app/src/debug/kotlin/…` — not compiled into release.

Behaviour:

- Shown on first cold start of a `debug` build (one-time per install) and accessible from a long-press on the version label in Settings.
- Lists `Production`, `Staging`, `UAT`, `Sandbox` with the currently-active item checked.
- Selection writes to `EnvironmentRegistry.set(...)` and persists to `EncryptedPrefs` (under a debug-only namespace).
- A persistent `DebugOverlay` shows `${tenant.id}@${env}` in the top-right corner so QA always knows what they're hitting.

```kotlin
@Composable
internal fun EnvironmentPicker(
    current: Environment,
    onSelect: (Environment) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Environment") },
        text = {
            Column {
                Environment.entries.forEach { env ->
                    Row(Modifier.clickable { onSelect(env); onDismiss() }) {
                        RadioButton(selected = (env == current), onClick = null)
                        Text(env.name)
                    }
                }
            }
        },
        confirmButton = {}
    )
}
```

---

## 5. Why the Picker is Compiled Out of Release

The `app/src/debug/` source set is included **only** when assembling the `debug` build type. The `app/src/release/` source set provides a **stub** `EnvironmentBootstrapper` that hardcodes `Environment.Production` and never shows a dialog.

```
app/src/debug/kotlin/com/nexus/app/debug/EnvironmentBootstrapper.kt   (release-flavor stub mirrors signature)
app/src/release/kotlin/com/nexus/app/debug/EnvironmentBootstrapper.kt
```

Result: the production APK does not contain the picker code at all — not in resources, not in classes.dex, not as dead code. Reverse-engineering the binary will not reveal the picker's existence.

---

## 6. Switching Environment vs Switching Tenant

| Concern | Tenant switch | Environment switch |
|---|---|---|
| **Allowed in production?** | Yes (e.g., user belongs to multiple banks) | No (release is locked) |
| **Triggers session purge?** | **Yes — full purge** (see [08]) | Yes — same purge sequence (different env = different backend = different valid sessions) |
| **Triggers DI rebuild?** | Yes — new `TenantComponent` | **No** — same component, the interceptor just rewrites the URL on the next call |
| **Affects UI labels?** | Yes (bank logo, market currency) | No (UI is environment-agnostic) |

Because environment changes don't rebuild DI, they're cheap. But because they invalidate the session, they share the **purge** half of the tenant-switch sequence:

```kotlin
@Singleton
class EnvironmentSwitcher @Inject constructor(
    private val sessionPurger: SessionPurger,
    private val cacheCleaner: CacheCleaner,
    private val environmentRegistry: EnvironmentRegistry,
    private val navigator: GlobalNavigator,
    private val uiLock: UiLock,
) {
    suspend fun switchTo(target: Environment) {
        uiLock.lock()
        try {
            sessionPurger.purge()
            cacheCleaner.clear()
            environmentRegistry.set(target)              // next API call uses new URL
            navigator.navigate(Route.Splash, popUpToRoot = true)
        } finally {
            uiLock.unlock()
        }
    }
}
```

---

## 7. Manifest File Format

`assets/environments/staging.json`:

```json
{
  "environment": "Staging",
  "tenantBaseUrls": {
    "kh":       "https://api.staging.kh.nexus.bank/",
    "vn":       "https://api.staging.vn.nexus.bank/",
    "ppcbank":  "https://api.staging.ppc.nexus.bank/"
  }
}
```

A new tenant adds an entry per environment manifest. Missing a tenant in a manifest = build-time error (manifests are validated against the registered tenant catalogue at app cold start).

---

## 8. Certificate Pinning Across Environments

Each environment may pin different certificates. The pinning configuration is keyed by `(environment, tenantId)`:

```kotlin
class TenantPinningInterceptor @Inject constructor(
    private val pinningRegistry: PinningRegistry,
    private val baseUrlProvider: BaseUrlProvider,
) : Interceptor { … }
```

`PinningRegistry` lives in `:aos-core`. Per-environment pin sets are checked into `assets/pinning/<env>.json`. Updating a pin is a code change reviewed alongside any environment manifest change.

---

## 9. Cross-references

- The interceptor lives in: [02 — `:aos-core`](02-aos-core.md)
- The `TenantProvider` it consumes: [03 — `:core`](03-core.md)
- How tenant switching uses the same purge primitive: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- The `:app/debug/` UI surface: [06 — `:app`](06-app-orchestrator.md)
