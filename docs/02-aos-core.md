# 02 · `:aos-core` — Infrastructure Layer

> **Type:** Git submodule · Android library
> **Stability:** High · independent of banking business rules
> **Reuse target:** Multiple Anthropic-of-Cambodia projects share this module verbatim

---

## 1. Purpose

`:aos-core` is the project-agnostic plumbing layer. Anything a serious Android app needs that is **not specific to banking** lives here. It is a Git submodule so that bug fixes and security patches propagate to all dependent projects without per-project rework.

If you find yourself writing something that mentions `Account`, `Money`, `KHQR`, `Tenant`, or any banking term — **it does not belong in `:aos-core`**.

---

## 2. Contents

### 2.1 `network/`

| Class | Responsibility |
|---|---|
| `NexusHttpClient` | Configures OkHttp with certificate pinning, connection timeouts, retry policy, TLS settings |
| `BaseApiResponse<T>` | Generic JSON envelope: `{ status, code, message, data }` |
| `EnvironmentInterceptor` | Rewrites the request URL using a `BaseUrlProvider` resolved at call time (not at client construction). Enables runtime environment switching. See [09 — Environment Configuration](09-environment-configuration.md). |
| `AuthHeaderInterceptor` | Attaches bearer/session tokens; reads from `EncryptedPrefs` |
| `RetrofitFactory` | Builds `Retrofit` instances with Moshi converters; consumed by `:tenants:*` to define their per-tenant APIs |

### 2.2 `security/`

| Class | Responsibility |
|---|---|
| `SecurityProvider` | Root/jailbreak detection, debugger detection, integrity checks. Failure = abort cold start. |
| `BiometricAuthenticator` | Wraps `androidx.biometric` for fingerprint / face authentication prompts |
| `EncryptionUtils` | AES-GCM and RSA helpers for at-rest encryption (PIN, session tokens) |
| `KeystoreManager` | Provisions and rotates Android Keystore-backed keys |

### 2.3 `storage/`

| Class | Responsibility |
|---|---|
| `EncryptedPrefs` | Wrapper around `EncryptedSharedPreferences` (`MasterKey` AES-256). Tenant-scoped prefix support — see [08](08-runtime-tenant-switching.md). |
| `SecureFileStore` | For larger blobs (e.g., session payloads); EncryptedFile-backed |

### 2.4 `logging/`

| Class | Responsibility |
|---|---|
| `NexusLogger` | Timber-based wrapper. Production builds drop logs below `WARN` and never log PII. |
| `CrashlyticsTree` | Routes `WARN`+ events to Firebase Crashlytics |

### 2.5 `firebase/`

Thin wrappers around Firebase SDKs so that consumers don't need direct Firebase imports.

| Class | Responsibility |
|---|---|
| `AnalyticsClient` | `logEvent(name, params)` with PII-stripping middleware |
| `RemoteConfigClient` | Typed accessor over Firebase Remote Config |
| `MessagingService` | Push notification entry points |

---

## 3. What `:aos-core` Must Never Contain

- **Banking terms:** `Account`, `Money`, `Transfer`, `Beneficiary`, `KHQR`
- **Tenant identifiers:** `KH`, `VN`, `PPCBank`, etc.
- **Repository interfaces or implementations** — those live in `:core` (interfaces) or `:tenants:*` (implementations)
- **Compose UI** — `:aos-core` is a non-UI library
- **Hilt modules** — DI assembly is the orchestrator's job, not infrastructure's

If a class is unsure, the test is: *"Could a non-banking project use this class verbatim?"* If the answer is no, it doesn't belong here.

---

## 4. Submodule Mechanics

### 4.1 Repository structure

```
projects/
├── aos-core/                    (separate Git repo)
│   ├── build.gradle.kts
│   └── src/main/kotlin/...
└── nexus/                       (this repo)
    ├── aos-core/                (submodule pointing at projects/aos-core)
    └── ...
```

### 4.2 Versioning

`:aos-core` uses **Git tags as versions** (e.g., `v2.4.1`). The Nexus repo pins a specific commit; upgrades are explicit:

```bash
git submodule update --remote aos-core
git add aos-core
git commit -m "bump aos-core to v2.5.0"
```

This makes infrastructure upgrades reviewable artifacts, not silent dependency changes.

### 4.3 Stability mandate

Breaking API changes in `:aos-core` are **rare** and require a major version bump. Consumers (Nexus included) opt into the upgrade. This is the entire reason `:aos-core` is a submodule rather than a Maven dependency — the team needs source-level visibility into infrastructure when debugging production incidents.

---

## 5. Public Surface

Only the following packages should be visible to consumers:

```
com.aos.core.network        ← NexusHttpClient, RetrofitFactory, EnvironmentInterceptor
com.aos.core.security       ← SecurityProvider, BiometricAuthenticator, EncryptionUtils
com.aos.core.storage        ← EncryptedPrefs, SecureFileStore
com.aos.core.logging        ← NexusLogger
com.aos.core.firebase       ← AnalyticsClient, RemoteConfigClient
```

Implementation classes are `internal`. Consumers wire these via Hilt in `:app/di/NetworkModule.kt`.

---

## 6. Cross-references

- Where the orchestrator wires `:aos-core` into tenant repos: [06 — `:app`](06-app-orchestrator.md)
- How `EnvironmentInterceptor` participates in runtime env switching: [09 — Environment Configuration](09-environment-configuration.md)
