# PRD-Fit Assessment: Compass Framework vs. Mobile Customer App (Lending)

**Date:** 2026-05-26
**Author:** Principal Android Architect / Lead Technical PM
**Status:** Critical review — input for redesign / rename decision
**Subject:** Compass Framework (this repo) measured against the Mobile Customer App PRD

---

## 1. TL;DR Verdict

The framework's **architectural pattern is sound** (Logic-Blind UI, scoped DI, MG-driven runtime config, compile-time forbidden-imports). But it is **domain-wrong and scope-incomplete for this PRD**.

- **Domain mismatch:** Every named example (`FintechTransferApi`, `TransferAmountPolicy`, `BeneficiaryValidator`, `TransferReceipt`, `AccountBalance`, `CardRepository`) targets a **transactional/payments-rail** app. The PRD is a **loan origination + servicing + collections** app. ~85% of the named domain types don't apply.
- **Scope-incomplete:** Of the 22 capability areas a modern fintech mobile app touches, the framework explicitly addresses **9**. The other **13 are silent**, including critical PRD-driving ones: realtime chat (now Sendbird buy), camera/eKYC (now in-house), push channels, deeplinks, maps, PDF, on-device persistence (Room), runtime locale switching, PIN UX, session timeout, screenshot blocking, background work, file upload.
- **Variant axis collapses into tenant hierarchy.** Round-3 decision: remove the variant DI axis entirely. Replace with a single tenant axis where regional grouping (Cambodia, Korea, …) is a **Gradle module hierarchy**, not a DI dispatch axis. Shared regional policies live in a region-base module (e.g., `:tenants:cambodia:base`) that concrete tenant modules depend on. One resolver, one map, one `@TenantKey`. ~30% less architectural surface.
- **Camera/eKYC: split.** Primitives (CameraX, ML Kit wrappers, imaging helpers) live in `:aos-sdk/{camera,ml,imaging}/` so multiple consuming apps share a battle-tested pipeline. The KYC *flow* (capture screens, MVI contract, upload orchestration) lives in `:features-kyc`. Same shape as the existing `CompassWebView` (primitive in `:aos-sdk`) vs. specific web flows (in feature modules).

**Recommendation:** Don't redesign from scratch. **Refactor in place**:
1. **Collapse variant into tenant hierarchy.** Delete `@VariantKey`, `VariantContext`, `VariantId`, `VariantCapabilities`, `VariantCatalogue`, `VariantContextResolver`, `VariantResolverModule`. Restructure `:variants-*` modules as `:tenants:{region}:{tenantId}` with Gradle dependency inheritance for shared regional policies. Ship this **before** SDK v1.0.0 — it's a breaking ABI change.
2. Rip out the transfer-domain examples; reseed with loan-domain types.
3. Add the 13 missing capability modules.
4. **Rename `:aos-core` → `:aos-sdk`** — justified by stakeholder confirmation of multi-product consumption with tagged releases. Adopt explicit public-API governance.
5. **Split camera/eKYC**: primitives in `:aos-sdk/{camera,ml,imaging}/`, flow in `:features-kyc`.
6. **Buy chat**, not build — stakeholder-confirmed direction. Sendbird is the primary pick.

---

## 1.5 Confirmed Inputs From Stakeholder (2026-05-26)

All open questions resolved across **three rounds** of input. The verdicts above are baked in here:

| Question | Answer | Impact |
|---|---|---|
| Will `:aos-core` be consumed by multiple separate Android projects (with git tags)? | **Yes.** | **Rename `:aos-core` → `:aos-sdk` is justified.** Commit to public-API governance, semver, binary-compat checking. |
| Is "KR" a UI language or a separate deployment? | **Per-project, not per-region.** App is organized by tenant, not by region. Outsource projects (e.g., a Korea engagement) may favor multi-tenant; **the current PRD is single-tenant**. Framework must stay scalable and flexible for both shapes. | **Tenant axis is the primary scaling axis.** Variant axis stays but is often degenerate in any given consuming app. Framework must handle 1-variant-1-tenant (this PRD) and N-tenant-per-variant (outsource projects) without structural change. |
| Build vs. buy chat? | **Buy.** | **Sendbird** (top pick) or **GetStream** as backup. No in-house realtime infrastructure to maintain. |
| Is the app multi-lingual? | **Yes.** | Runtime locale switching (KR/EN/KH minimum) is a **must-have**, not optional. Use `AppCompatDelegate.setApplicationLocales` + `LocaleConfig.xml`. |
| eKYC: managed SDK or in-house? | **In-house.** | CameraX + ML Kit Document Scanner + ML Kit Face Detection. Lives in `:features-kyc` sibling module. No per-user vendor fee. |
| AI Loan Health Scoring (item 77)? | **Removed from native scope.** Either deliver as WebView or drop entirely. | No on-device ML footprint. No TF Lite. Mobile is not responsible for scoring. WebView path if it ships at all. |
| MG outage policy? | **Stale-config fallback.** | Last-known-good `RuntimeConfig` cached locally; tolerated up to N hours (recommend 24h) when MG is unreachable on cold start. Telemetry alerts when fallback is active. |
| Multi-account-per-user (`DepartmentAccount`)? | **Keep, for outsource readiness.** Current PRD is single-account; outsource projects need multi-account. | Do **not** rip out `Session.activeAccountId` / `AccountIdInterceptor` / `docs/12`. Document as opt-in per tenant; the current PRD ships with `accounts.size == 1` and the active-account UI hidden. |
| Currencies on dashboard? | **KHR, USD for this PRD; KRW or others for outsource projects.** | Multi-currency display is required. Per-currency formatting per `AmountFormatter`. **No mixed-currency totals**; balances shown separately per currency. **FX conversion not required** unless an outsource project explicitly needs it (treat as future capability). |
| Camera + eKYC: in `:aos-sdk` or `:features-kyc`? | **Split.** | Primitives (CameraX wrapper, ML Kit wrappers, imaging helpers) → `:aos-sdk/{camera,ml,imaging}/` for cross-consumer reuse. KYC flow (UI screens, MVI contract, upload worker) → `:features-kyc`. Same precedent as `CompassWebView`. |
| Can variant be removed and folded into tenants? | **Yes, collapse.** | Delete `@VariantKey`, `VariantContext`, `VariantId`, `VariantCapabilities`, `VariantCatalogue`, `VariantResolverModule`. Region becomes a Gradle module hierarchy (`:tenants:cambodia:base` ← `:tenants:cambodia:nh`). One axis, one resolver, one map. Ship before SDK v1.0.0. |

---

## 2. PRD ↔ Framework Capability Matrix

Each row is a PRD feature group. **Status** is the framework's coverage today.

| PRD Feature Group | PRD Items | Framework Coverage | Status |
|---|---|---|---|
| Onboarding & splash (3-screen + animation) | 1 | Not addressed; trivially lives in `:features` | ✅ trivial |
| Sign-up: phone + OTP + PIN setup | 2, 5 | OTP policy interface exists (`OtpDeliveryPolicy`). **PIN UX absent.** | ⚠️ partial |
| Sign-in: QR, PIN, Face ID, Touch ID, session timeout | 3 | Biometric wrapper exists. **QR sign-in, PIN, session timeout — absent.** | ⚠️ partial |
| Multi-language welcome (KR/EN/KH) | 4 | **No runtime locale switching.** Variant-baked string tables only. | ❌ gap |
| Forgot PIN — recover via phone | 6 | Not addressed | ❌ gap |
| Referral code (staff ID last-5) | 7, 19, 25, 75 | Pure feature; no framework concern | ✅ trivial |
| Admin reset PIN/password | — | Not addressed; server-side concern | ⚪ n/a |
| One-account-per-device | — | **No device-binding story.** | ❌ gap |
| Customer change phone number | — | Pure feature | ✅ trivial |
| Loan calculator (real-time EMI) | 8, 14 | Pure feature; lives in `:features` | ✅ trivial |
| Request consultation | 9, 13 | Pure feature | ✅ trivial |
| Multi-currency dashboard | 10, 46 | `Money(BigDecimal, Currency)` + `AmountFormatter`. **No FX rate source.** | ✅ ok |
| List/detail loan product | 11, 12 | Pure feature | ✅ trivial |
| Apply Loan (NON-MWL) — 9-step flow | 15–20 | **Multi-step form state — no framework pattern.** | ❌ gap |
| Apply Loan (MWL) — 18-step flow with employment, agency, bank info | 21–28 | Same as above. **No form-state contract.** | ❌ gap |
| Document upload (ID, income, collateral) | 18 | **No file upload story.** Multipart/resumable absent. | ❌ gap |
| Guarantor SMS link → OTP → ID-card capture → selfie | 29–35 | **Camera, eKYC, deeplink, app-link — all absent.** | ❌ critical gap |
| BM assignment / LOS / Credit Assessment | 36–37+ | Server-side; mobile observes status timeline | ⚪ n/a |
| Loan approval → accept → disbursement notice → contract download | 38–41+ | **PDF download/preview — absent.** Push notification — partial. | ⚠️ partial |
| My Loan dashboard, timeline, status | 42, 43, 46, 49 | Pure feature; MVI pattern fits | ✅ ok |
| Loan contract view/download | 44, 53 | **PDF download/preview — absent.** | ❌ gap |
| Need-help / customer support | 45, 65, 78 | Pure feature | ✅ trivial |
| Pay Now (repayment, deeplink) | 47, 48 | **Payment deeplink — absent.** | ❌ gap |
| Repayment schedule + payment history | 50, 51 | Pure feature | ✅ trivial |
| Payoff (early settlement) | 52 | Pure feature | ✅ trivial |
| Apply restructure | 54 | Pure feature | ✅ trivial |
| Guarantor read-only view | 55, 56 | Pure feature | ✅ trivial |
| Loan reject reason + tips + try-another | 57–60, 61 | Pure feature | ✅ trivial |
| **Chat (text, voice, image, video, file, history)** | **62, 63** | **No realtime, no media pipeline, no chat infra.** | ❌ **critical gap** |
| Push notifications — 3 categories (reminder, transaction, announcement) | 64 | `MessagingService` wrapper only. **No channel design, no category routing.** | ⚠️ partial |
| Feedback / rate | 65 | Pure feature | ✅ trivial |
| Credit & insights | 66 | Pure feature; data comes from server API | ✅ ok |
| ~~AI loan health scoring~~ | ~~77~~ | **Removed from native scope per stakeholder.** Either ship as WebView or drop. No mobile contract needed. | ⚪ out-of-scope |
| User profile + KYC review | 67 | Pure feature; KYC capture is the real work | ⚠️ partial |
| Account security (biometric toggle, PIN change, active sessions) | 68 | Biometric exists. **Active session list — no contract.** | ⚠️ partial |
| App settings (theme, notifications, logout) | 69 | Logout handled (`LogoutHandler`). Theme is `:design-system`. | ✅ ok |
| App policy / T&C / privacy (admin-updated, no app release) | 70 | **WebView** integration is well-defined. Good fit. | ✅ ok |
| About company (admin-managed) | 71 | WebView or remote-content fetch | ✅ ok |
| **Branch locator (map view, directions)** | **72** | **No maps, no location.** | ❌ gap |
| Blogs / education (admin-managed) | 73 | WebView or remote content | ✅ ok |
| CBC (Credit Bureau Cambodia) check | 74 | Likely WebView or 3rd-party SDK; not addressed | ⚠️ partial |
| Salary/income assessment (bank statement analysis) | 76 | Cloud-side feature; mobile is uploader. **No file upload story.** | ❌ gap |

**Tally (after stakeholder resolutions):** Critical/large gaps = **10** (AI scoring dropped from native scope). Partial = **9**. Trivial-in-`:features` = **17**. Out-of-scope = **1**. The framework gets you the *spine* of the app but leaves the parts that *make this a banking-grade app* (eKYC, chat, push, deeplinks, maps, PDF, locale, PIN, room, workmgr, form-wizard) unaddressed.

---

## 3. Critical Gaps

The 11 critical gaps, in priority order for a lending app:

### 3.1 Camera + eKYC capture (items 33, 34, 67) — **SPLIT: primitives in `:aos-sdk`, flow in `:features-kyc`**
The guarantor flow alone requires: ID card front capture, ID card back capture, selfie-while-holding-ID liveness. The customer KYC review (67) implies the same. **[Confirmed: in-house implementation, split across SDK primitives + feature flow.]**

**Why this matters:** Loan apps are KYC-bound. Bad capture quality = bad approval rate. A bolted-on `Intent(ACTION_IMAGE_CAPTURE)` will fail QA on glare, blur, and rotation. The in-house build path saves vendor per-user cost at scale and keeps PII fully under control, at the cost of ~2–3 months of focused engineering plus ongoing maintenance.

**Why the split:** `:aos-sdk` is consumed by multiple Android products. App A (lending) needs camera for KYC; App B (merchant) might need it for product photos; App C (chat) for attachments. Centralizing the CameraX setup + ML Kit wrappers in `:aos-sdk` gives every consumer one battle-tested pipeline. The *flow* (KYC's specific 4-screen wizard) is opinionated UX and lives in `:features-kyc`. This mirrors the existing `CompassWebView`-in-`:aos-sdk` / specific-web-flows-in-features split.

**`:aos-sdk` additions (primitives — banking-agnostic, no UI flow):**

```
:aos-sdk/
  camera/           CameraXController, CompassCameraView (Compose preview primitive)
                    — like CompassWebView but for camera
  ml/               DocumentScannerWrapper, FaceDetectorWrapper
                    — wraps ML Kit Document Scanner + Face Detection; no UI
  imaging/          ImageCompressor, ExifStripper, Watermarker, BitmapRedactor
                    — pure Android Graphics, zero permissions, zero ML Kit dep
```

Dependencies:
- **CameraX** (`androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-compose`)
- **ML Kit Document Scanner** (`com.google.mlkit:document-scanner`) — ID card crop, perspective correction, glare detection. Free, on-device.
- **ML Kit Face Detection** (`com.google.mlkit:face-detection`) — liveness gate: face present, centered, eyes open, not occluded

**`:features-kyc` (flow — banking-locked, depends on `:aos-sdk` primitives and `:core/kyc/`):**

```
:features-kyc/
  ui/                 KycFlowScreen, IdCardCaptureScreen, SelfieScreen, ReviewScreen
                      — composables, use CompassCameraView + ML wrappers from :aos-sdk
  upload/             KycUploadWorker (WorkManager) — resumable, retry-with-backoff
  contract/           KycFlowState, KycFlowEvent, KycFlowEffect (MVI)
```

**Public surface in `:core/kyc/`:** `KycCaptureRequest`, `KycCaptureResult`, `KycRepository`. `:features-kyc` depends on this — not on CameraX or ML Kit directly. Keeps the contract polymorphic (e.g., a future tenant could swap to a managed eKYC vendor without touching `:features-kyc`).

**Tenant escalation:** `KycRequirementPolicy` in `:core/policy/` decides which documents are required (varies by tenant/region). Implementations in `:tenants:{region}:{tenantId}/policy/` (post-collapse — see section 6.3).

**Client-side image processing pipeline (all in `:aos-sdk/imaging/`):**
- Compress to 1280px max edge, JPEG quality 80
- Apply a tamper-resistant watermark (timestamp + user ID + nonce) to detect re-submitted captures
- Strip EXIF GPS before upload (PII)
- Hash + sign payload server-side on receipt for chain-of-custody
- `BitmapRedactor` — for logging: returns "Bitmap(1024x768, redacted)" instead of pixel data

**Total dependency cost paid by `:aos-sdk` consumers:** CameraX (~1.5MB), ML Kit Document Scanner (~3MB), ML Kit Face Detection (~5MB on-device model). R8 strips unused classes per-consumer. Apps that never instantiate `FaceDetectorWrapper` don't load the model. Acceptable; well below the 50MB Play Store APK budget.

**Optional future split:** if a consuming app (B2B admin tool, no camera ever) needs to avoid the ML weight entirely, split `:aos-sdk` into per-capability artifacts: `:aos-sdk-network`, `:aos-sdk-camera`, `:aos-sdk-ml`, etc., like `androidx.*`. Not urgent today; revisit at 4+ consumers.

### 3.2 Chat with media (items 62, 63) — **BUY, confirmed**
Text + voice + image + video + file. History. Read receipts implied. Stakeholder has confirmed the direction is to **buy**, not build.

**Primary recommendation: Sendbird**
- Compose-friendly Kotlin SDK (`com.sendbird.sdk:sendbird-chat`), proven on Android
- Built-in file/media handling (image, video, voice), thumbnails, transcoding
- Channels, threading, read receipts, typing indicators out of the box
- Push notification integration via FCM (slots into our `NotificationChannelRegistry`)
- Moderation tools (block/report) — needed for loan-officer ↔ customer compliance
- Server-side webhooks for compliance logging (every message must be archivable for KYC/AML retention)

**Backup pick: GetStream Chat** — comparable feature set, Compose UI components included. Pick if Sendbird pricing or regional availability (Korea data residency, Cambodia regulatory) doesn't fit.

**Do not pick:** Pusher Chatkit (discontinued). Twilio Conversations is viable but lacks pre-built Compose UI.

**Integration shape** (preserves Logic-Blind):
- `:core/chat/` — `ChatThread`, `ChatMessage`, `ChatRepository` interfaces (provider-agnostic)
- `:data/chat/` — `SendbirdChatRepo : ChatRepository` (the only place Sendbird types appear)
- `:features-support-chat/` — sibling UI module (heavy SDK weight, same justification as `:features-chatbot`)
- Sendbird app ID + per-environment endpoint come from **MG `RuntimeConfig`** — not BuildConfig (invariant #8)
- Message archival webhook server-side; mobile never directly exports for compliance

The framework's `:features-chatbot` module remains separate for the LLM/NLP chatbot. Two distinct UI modules, both Logic-Blind, both consuming `ChatRepository` from `:core` but talking to different backends.

### 3.3 Push notifications with categories (item 64)
Three categories: reminder, transaction, announcement. The framework only wraps `MessagingService`.

**Why this matters:** Without `NotificationChannel`s per category, users cannot mute "announcement" while keeping "reminder" — and Android 13+ requires runtime permission per-channel UX.

**Recommendation:** Add to `:aos-core` (or `:aos-sdk`):
- `NotificationChannelRegistry` with channels `reminder`, `transaction`, `announcement`
- Per-channel importance / sound / vibration / badge config from `RuntimeConfig`
- Topic subscription on login per active account
- Deeplink payload extraction → Compose Nav route
- POST_NOTIFICATIONS permission flow (Android 13+)

### 3.4 Deeplinks & Universal/App Links (items 30, 31, 47)
Guarantor SMS link must open the app (or web fallback). Pay-now deeplink for repayment. Without a deeplink contract, both flows ship broken.

**Recommendation:**
- **App Links** (HTTPS, verified via `assetlinks.json`) — required for SMS-link reliability
- Centralize URI scheme in `:core/deeplink/`: `DeepLinkRoute` sealed class, `DeepLinkResolver` interface, per-route handler in `:features`
- Compose Navigation supports `deeplink { uriPattern = ... }` natively — use it
- Guarantor link payload should be a signed JWT, not a database key; verify before camera flow

### 3.5 Branch locator with map (item 72)
Map view, search, directions, contact.

**Recommendation:**
- **Google Maps Compose** (`com.google.maps.android:maps-compose`)
- **Places API** for branch search
- Branch list comes from `:data` (cacheable, low-churn) — store in **Room** (see 3.10) for offline
- Lives in `:features/branchlocator/` package

### 3.6 PDF download + preview (items 41, 44, 53)
Loan contract download, view in-app, share.

**Recommendation:**
- **DownloadManager** (system) for resumable download with notification, OR OkHttp + `FileProvider` for in-app download with progress UI
- Preview via **`androidx.pdf:pdf-viewer`** (Jetpack PDF library, Android 14+) or **PdfRenderer** (older) or **AndroidPdfViewer** (Barteksc)
- Store in app-private external dir; **never** in shared storage (contract has PII)
- Share via `FileProvider` + `ACTION_SEND`

### 3.7 Multi-step form state for loan applications (items 15–28)
Apply MWL is 18 steps. Apply NON-MWL is 9 steps. Each step has validation, save-draft-on-back, resume.

**Why this matters:** Today the framework's MVI contract is per-screen. A 18-step flow with cross-step dependencies needs a flow-level state holder (a "wizard" or "saga" pattern), not 18 unrelated ViewModels.

**Recommendation:**
- Define a `:core/wizard/` contract: `WizardState`, `WizardStep`, `WizardEvent`, `WizardEffect`
- Persist draft to local DB (Room) at every step; resume from last index on app return
- Use Hilt's `@ViewModelScoped` not `@LoggedInScoped` for the flow ViewModel (lives per-flow, not per-session)
- Alternative: scoped Navigation graph + shared `hiltViewModel(parentNavBackStackEntry)` — works without a custom contract, but discipline-only

### 3.8 Runtime locale switching (KR/EN/KH) — **CONFIRMED multi-lingual** (item 4)
**[Confirmed by stakeholder: app is multi-lingual.]** KR (Korean), EN (English), KH (Khmer) at minimum. User-selectable, persisted, applied immediately without app restart. This is a **first-class requirement**, not optional polish.

**Why this matters:** Khmer is LTR but uses Khmer script — font fallback matters. Korean has Hangul. Both have non-Latin numerals to consider. And once a user switches to Khmer, **every screen** in the 200-screen surface must render correctly — including pluralization, date formats, number grouping, currency placement.

**Recommendation:**
- **`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("km"))`** (Android 13+) is the modern API; pre-13 uses an in-app wrapper (`androidx.appcompat:appcompat` 1.6+ backports it via the AndroidManifest service hook)
- `res/values/strings.xml` (en default), `res/values-ko/`, `res/values-km/`
- **`LocaleConfig.xml`** declared in manifest (`<application android:localeConfig="@xml/locales_config">`) — required Android 13+ for system-level per-app language picker
- Bundle **Noto Sans Khmer** + **Noto Sans KR** as app fonts; do not rely on device fallback (older Khmer devices render boxes)
- Number/date/currency formatting: use **`ICU NumberFormatter`** and **`DateTimeFormatter`** with the active `Locale` — not hand-rolled formatters in `:tenants:*`
- **The framework's existing pattern of baking user-facing strings into tenant policy classes is wrong** and must be corrected. Tenant policy classes own *business rules* (rates, thresholds, calendars), not *display text*. All user-facing text belongs in `strings.xml` resources, localized per language. The example `ReceiptRenderer` with hardcoded Khmer+English string concatenation in `:variants-kh/format/` (the legacy layout) is a counter-pattern; rewrite as `strings.xml` lookups inside a `:design-system` component
- Add a `LocaleSelector` UI component to `:design-system` (settings screen reusable)
- Lives in `:aos-sdk/i18n/` (new sub-package): `LocaleManager` interface in `:core`, `AppCompatLocaleManager` impl in `:aos-sdk`

### 3.9 PIN UX (items 2, 5, 6, 68)
4-digit PIN at sign-up, sign-in, change-PIN, forgot-PIN.

**Recommendation:**
- Custom Compose component in `:design-system` — `CompassPinInput`, `CompassPinDots`
- PIN hash via **`EncryptedSharedPreferences`** with an **HMAC** (not store plaintext, not reversible)
- **Brute-force lockout:** 3-5 fails → exponential lockout (10s, 1m, 10m, 1h)
- Bind PIN to **biometric-protected Keystore key** (`setUserAuthenticationRequired(true)`) for the strongest tier
- Forgot-PIN flow uses OTP-to-phone (already in framework as `OtpDeliveryPolicy`)
- Wipe PIN on logout

### 3.10 Local persistence (Room) — implicit gap
Chat history, draft applications, branch list cache, repayment schedule offline view, notification inbox.

**Why this matters:** The framework names `EncryptedSharedPreferences` and `EncryptedFile` — neither is a database. SharedPreferences-for-lists is a known antipattern.

**Recommendation:**
- **Room** with **SQLCipher** (`net.zetetic:android-database-sqlcipher`) for encrypted DB
- Or **Room** + **`net.sqlcipher:sqlcipher-android`** (newer maintained fork)
- Key the DB with a Keystore-derived passphrase
- Lives in `:aos-core/storage/` — add `EncryptedDatabase` alongside `EncryptedPrefs`

### 3.11 Background work — implicit gap
Scheduled repayment reminders, retry-on-failure for document uploads, FCM token refresh, draft sync.

**Recommendation:** **WorkManager**. Add a `:aos-core/work/` layer with:
- `BackgroundWorkScheduler` interface in `:core`
- Hilt-integrated `WorkerFactory` registered at boot
- Worker types: `UploadDocumentWorker`, `RefreshFcmTokenWorker`, `SyncDraftWorker`

---

## 4. Scalability Risks

### 4.1 Variant axis collapsed into tenant hierarchy — single axis, Gradle-module-based regional grouping
**[Updated per round-3 stakeholder decision.]** The variant DI axis is removed. The tenant axis becomes the only axis. Regional grouping (Cambodia, Korea, …) is preserved as a **Gradle module hierarchy**, not a DI dispatch axis.

**The new module shape:**

```
:tenants/
  cambodia/
    base/             ← shared KH policies: KHR/USD, KH compliance thresholds,
                         OTP-via-SMS, KH business calendar, KH KYC requirements
                         (was :variants-kh)
    nh/               ← NH-KH (this PRD's tenant) — depends on cambodia/base
    partner-a/        ← future Partner-A-KH tenant — depends on cambodia/base
  korea/
    base/             ← shared KR policies (was :variants-kr)
    nh/               ← NH-KR
    partner-b/        ← Partner-B-KR
```

**How bindings resolve:** Each tenant module is a Hilt `@Module` with `@Binds @IntoMap @TenantKey("nh-kh") @LoggedInScoped` for every policy it provides. Concrete tenants (e.g., `:tenants:cambodia:nh`) depend on their region base (`:tenants:cambodia:base`) as a standard Gradle module dependency. Hilt sees the *union* of bindings; the concrete tenant wins on conflict via standard `@Binds` precedence — this is how Hilt already works, no custom resolver needed.

**Single dispatch point:** `:app/di/TenantResolverModule.kt` reads `TenantContext.id` from the post-login `Session` and provides the appropriate keyed bindings. No `VariantResolverModule` exists.

**This PRD ships as:** 1 tenant (`:tenants:cambodia:nh`). The base policies (KH compliance, KHR/USD, OTP-SMS) come from `:tenants:cambodia:base` via Gradle dependency. Single value in the tenant map at runtime, but the pattern is identical to multi-tenant deployments.

**Outsource Korea project ships as:** N tenants under `:tenants:korea:*`, all depending on `:tenants:korea:base` for shared KR policies. Each concrete tenant overrides only the deltas.

**Validation moment:** the **second tenant** under a shared base is the pattern's validation. The Korea outsource project (multi-tenant) is the natural validation moment.

**What is deleted:**
- `@VariantKey` annotation in `:core/scope/`
- `VariantContext`, `VariantId`, `VariantCapabilities` types in `:core/variant/`
- `VariantCatalogue`, `VariantContextResolver`, `VariantResolverModule` in `:app/di/`
- `docs/07-variants.md` (merged into the tenant doc)
- `docs/13-onboarding-a-variant.md` (rewritten as tenant onboarding)
- The "VariantKey map" mechanism described in `docs/10`, `docs/11`
- The `:variants-*` module path convention

**What is preserved unchanged:**
- All `:core/policy/` interfaces and their per-tenant implementations
- The `@LoggedInScoped` lifecycle
- `Session`, `DepartmentAccount`, `AccountIdInterceptor`
- The "bind once at login, drop at logout" semantics
- The "no `if (tenant.id == ...)`" lint rule (now half the work — only one axis to police)
- Compile-time forbidden-imports DAG (with `:variants-*` → `:tenants:*:*` rename)

**What is reshaped:**
- "Tenants are an axis inside variants" → "Tenants form a hierarchy via Gradle module dependency"
- "Every variant ships a default tenant" → "Every region base ships a `:tenants:{region}:default`"
- Hard invariant #1: "no conditional logic on variant ID *or* tenant ID" → "no conditional logic on tenant ID" (variant ID no longer exists)
- Hard invariant #11 (variant onboarding additive) → restated as tenant onboarding additive
- Hard invariant #12 (tenants are axis inside variants) → restated as tenant hierarchy

**Net effect:** ~30% reduction in architectural surface. One axis, one resolver, one map, one type, one set of docs. Easier to explain, easier to onboard engineers, no loss of expressiveness (the regional grouping is structural rather than DI-axial).

**Risk and mitigation:** the only thing lost is the *DI-enforced* property that "all tenants in a country share their country policies." That is now enforced by **Gradle module dependency** instead. If an engineer creates a new tenant module and forgets to depend on the region base, Hilt resolution will fail at app start (missing bindings for the policies declared as required by `LoggedInComponent`'s `@EntryPoint`). Fail-fast at boot, not at runtime — acceptable.

**Migration path:** since the framework is doc-only today, this is a **doc rewrite + a few renames in the dependency DAG**, not a code change. Estimated 3–4 days of doc work. Must land **before** the first SDK v1.0.0 tag so the breaking ABI change is in the initial release, not a v2.0.0.

### 4.2 Multi-account readiness must be preserved even though this PRD is single-account
**[Updated per round-2 stakeholder input.]** `DepartmentAccount`, `Session.activeAccountId: StateFlow<AccountId>`, `AccountIdInterceptor`, and `docs/12-departments-and-session.md` **stay in the SDK**. The current PRD's lending product is single-account (one customer = one loan account context); outsource projects need multi-account.

**Implementation pattern for the current PRD:**
- `LoginResponse` returns a single `DepartmentAccount`; `Session.accounts.size == 1` always.
- The active-account switcher UI in `:features` is hidden when `accounts.size == 1` (declarative `if (accounts.size > 1)` in the dashboard top-bar composable, not gated by a feature flag).
- `AccountIdInterceptor` still stamps `X-Account-Id` from `Session.activeAccountId.value` — server just gets the same value for every request from this PRD's users. No code branch needed.

**For outsource projects:**
- Multi-account UI is the same composable, just unhidden when `accounts.size > 1`.
- No SDK change required; the pattern is **`if (count > 1) showSwitcher`**, not **`if (tenant == "outsource-kr") showSwitcher`**.

### 4.2 Multi-account readiness must be preserved even though this PRD is single-account
**[Updated per round-2 stakeholder input.]** `DepartmentAccount`, `Session.activeAccountId: StateFlow<AccountId>`, `AccountIdInterceptor`, and `docs/12-departments-and-session.md` **stay in the SDK**. The current PRD's lending product is single-account (one customer = one loan account context); outsource projects need multi-account.

**Implementation pattern for the current PRD:**
- `LoginResponse` returns a single `DepartmentAccount`; `Session.accounts.size == 1` always.
- The active-account switcher UI in `:features` is hidden when `accounts.size == 1` (declarative `if (accounts.size > 1)` in the dashboard top-bar composable, not gated by a feature flag).
- `AccountIdInterceptor` still stamps `X-Account-Id` from `Session.activeAccountId.value` — server just gets the same value for every request from this PRD's users. No code branch needed.

**For outsource projects:**
- Multi-account UI is the same composable, just unhidden when `accounts.size > 1`.
- No SDK change required; the pattern is **`if (count > 1) showSwitcher`**, not **`if (variant == "outsource-kr") showSwitcher`**.

### 4.3 `:features` Hybrid-Monolith — does it survive 60+ screens?
The PRD has ~78 numbered features, each likely 2-5 screens. Call it 200 Compose screens. Hybrid-Monolith ("packages, not modules") is a defensible choice — but at this scale:
- Build time: still acceptable on modern hardware, but Compose recompiles get slow past ~100k lines in one module.
- Code review: PR diff radius grows. Without module boundaries, ownership becomes informal.
- Test parallelization: one Gradle module = one test task; hard to fan out CI.

**Recommendation:** Keep Hybrid-Monolith **as default**, but pre-commit to the **escape valves**:
- `:features-loan-origination` (the 27 apply-loan steps deserve their own module — they're tenant-locked behavior anyway since MWL/NON-MWL differ)
- `:features-support-chat` (officer chat, heavy SDK weight)
- `:features-chatbot` (LLM — already documented)
- `:features-kyc` (camera + ML Kit dependency weight)
- `:features-branch-locator` (Google Maps dependency weight)

These all match the framework's existing "heavy SDK or tenant-locked" criterion. Document them in `docs/06-features.md` so the line is drawn before someone draws it badly.

### 4.4 The data layer's name implies one backend
The framework names `FintechAuthApi`, `FintechTransferApi`, etc., with the invariant "**one** fintech backend, no per-variant duplication." For a lending app integrating with **CBC** (Credit Bureau Cambodia), **bank-statement-analyzer** (item 76), and possibly **MWL agency systems**, there are clearly multiple backends.

**Recommendation:** The "one backend" invariant is too tight. Restate as: "One **primary** fintech backend per logical capability (auth, loan, repayment); third-party integrations (CBC, agency, scoring) get their own typed clients in `:data`, but **never branch by variant**." Add a `:data/external/` package convention.

### 4.5 Session timeout is undefined
The PRD says "session managed with configurable timeout" (item 3). The framework defines `Session` and `LoggedInComponent` lifecycle but **does not document timeout**. Without this, a tab-and-forget user could leave the app authenticated indefinitely. Compliance auditors will flag this.

**Recommendation:** Add to `:aos-sdk/security/`:
- `InactivityDetector` — ticks on user input via `Activity.dispatchTouchEvent`
- `SessionTimeoutPolicy` in `:core` — variant/tenant-configurable timeout duration
- On timeout: trigger `LogoutHandler` via the existing path

### 4.6 Multi-currency display without FX conversion
**[Updated per round-2 stakeholder input.]** Currencies in scope: **KHR, USD** (this PRD); **KRW** or others (outsource projects). Balances are displayed **per currency, not converted**. FX rate conversion is **not required** for this PRD and is treated as a future capability if any outsource project explicitly needs it.

**Recommendation:**
- Keep `Money(BigDecimal, Currency)` in `:core` as-is.
- Add per-currency `AmountFormatter` implementations to the relevant region-base module (e.g., `KhrAmountFormatter`, `UsdAmountFormatter` in `:tenants:cambodia:base`; `KrwAmountFormatter` in `:tenants:korea:base`).
- **No mixed-currency totals.** The dashboard shows each currency as its own line/card. Mixing KHR + USD into a single "total" is a regulator violation in most jurisdictions and a UX disaster.
- If an outsource project needs FX later, add `FxRateProvider` to `:core` with MG-configured endpoint. Until then, do not build it.

### 4.7 MG stale-config fallback (NEW — replaces hard-fail-on-MG-down)
**[Updated per round-2 stakeholder input.]** The framework's current boot model hard-fails on MG outage at cold start. Stakeholder direction: **stale-config fallback** — tolerate MG being unreachable when a recent cached config exists.

**Recommendation:** Amend the boot model (`docs/10`, `docs/11`):
- After every successful MG fetch, persist the typed `RuntimeConfig` to `EncryptedPrefs` with a timestamp.
- On cold start, `BootCoordinator` attempts MG fetch with a short timeout (3–5s). On failure:
  - If a cached `RuntimeConfig` exists and `now - cachedAt < staleConfigTtl` (default **24h**, configurable), boot proceeds with the cached config.
  - The app surfaces a non-blocking banner: "Using last known configuration. Some features may be out of date." (localized per locale).
  - Background WorkManager job retries MG every 15 minutes until successful, then refreshes config and dismisses banner.
- If no cached config OR cached config is older than `staleConfigTtl`, hard-fail to the existing maintenance/offline screen (the original boot behavior, now the worst case).
- Telemetry: **emit a non-fatal Crashlytics event** every time the fallback activates. The team needs to know how often MG is degraded; the user should not.
- `staleConfigTtl` value comes from the **cached config itself** (chicken-and-egg: bootstrapping value is a `BuildConfig` constant, **only** for this single value, with a sane default like 24h). This is the one allowed exception to invariant #8.

**Why this matters:** A loan customer logging in to check their repayment schedule should not see a "service unavailable" screen because of an unrelated MG outage. The fallback turns a hard-fail into a soft-degrade, which is what compliance auditors and product owners both want.

### 4.7 Screenshot blocking is undocumented
A loan app shows PII, amounts, contracts. `FLAG_SECURE` on sensitive screens is table-stakes.

**Recommendation:** Add to `:design-system` a `Modifier.secureScreen()` or a `CompassSecureScreen` wrapper that sets `FLAG_SECURE` on the host activity scoped to the composable's lifecycle. Default-on for PIN, contract, KYC, profile screens.

---

## 5. Library / Tech Stack Recommendations

Concrete additions to `docs/15-tech-stack.md`:

| Area | Current Doc | Recommendation |
|---|---|---|
| Realtime chat | (silent) | **Sendbird** (confirmed buy). GetStream as backup. |
| Camera capture | (silent) | **CameraX** + **ML Kit Document Scanner** + **ML Kit Face Detection** |
| eKYC capture | (silent) | **In-house** (confirmed): CameraX + ML Kit Document Scanner + ML Kit Face Detection. No managed vendor. |
| Push channels | FCM only | **FCM + `NotificationChannel`** registry (in `:aos-core`) |
| Local DB | (silent) | **Room** + **SQLCipher** (encrypted) |
| Background work | (silent) | **WorkManager** with Hilt `WorkerFactory` |
| Deep links | (silent) | **App Links** (HTTPS verified) + **Compose Navigation** `deeplink` DSL |
| Maps | (silent) | **`maps-compose`** (Google Maps Compose) + **Places API** |
| PDF preview | (silent) | **`androidx.pdf`** (preferred) or **PdfRenderer** |
| File picker / upload | (silent) | **`ActivityResultContracts.GetContent`** + OkHttp multipart + WorkManager retry |
| Locale switching | (silent) | **`AppCompatDelegate.setApplicationLocales`** + `LocaleConfig.xml` |
| Form state | (silent) | Custom `:core/wizard/` contract (preferred over a 3rd-party form lib) |
| Image loading | (silent) | **Coil** (Compose-native, lighter than Glide) |
| Animation | (silent) | **Lottie Compose** for onboarding (item 1) |
| Permissions UX | (silent) | **Accompanist Permissions** (or roll into `:aos-core/permissions/`) |
| Crash reporting | Firebase Crashlytics | Keep; add **non-fatal logging** convention |
| Performance | (silent) | **Firebase Performance Monitoring** + **Baseline Profiles** for cold start |
| App Startup | (silent) | **`androidx.startup`** for deterministic init order with `SecurityProvider.runColdStartChecks()` |
| Compose stability | (mention only) | **Compose Compiler Metrics** in CI to catch unstable params |
| Snapshot tests | (silent) | **Paparazzi** or **Roborazzi** for `:design-system` and `:features` screens |
| Static analysis | (silent) | **Detekt** + **ktlint** + Gradle's dependency-graph validation for forbidden-imports |
| Dependency injection | Hilt | Keep; revisit **Anvil** or **kotlin-inject** only if Hilt build-time becomes blocking (it won't at this scale) |
| HTTP retry/refresh | (silent) | Implement **Authenticator** for OkHttp (handles 401 + refresh-token rotation) + **fail-fast with exponential backoff** for 5xx |
| Certificate pinning | mentioned, not detailed | Document **rotation strategy**: include 2 active pins + 1 backup; rotate via MG, not buildconfig |
| Observability | Crashlytics + Analytics | Add **OpenTelemetry-Android** if SLOs are formalized |

---

## 6. Architecture Refinements (Domain-Fit)

If the framework is **kept** (recommended over redesign), these changes adapt it to lending without violating its invariants:

### 6.1 Reseed `:core` and `:data` with loan-domain types

**Remove from examples:**
- `TransferRepository`, `TransferAmountPolicy`, `BeneficiaryValidator`, `TransferReceipt`, `FintechTransferApi`, `FintechAuthApi` (rename — see below), `CardRepository`, `FintechCardApi`

**Add:**

```
:core/model/
  Loan, LoanProduct, LoanApplication, LoanStatus
  RepaymentSchedule, Installment, PaymentMethod
  Guarantor, GuarantorInvite, KycCapture
  Borrower, EmploymentInfo, BankAccount
  ConsultationRequest, Branch
  ChatThread, ChatMessage, ChatAttachment
  Referral, ReferralReward
  CbcReport, AiLoanHealthScore

:core/repo/
  LoanProductRepository
  LoanApplicationRepository
  RepaymentRepository
  GuarantorRepository
  KycRepository
  ChatRepository
  ReferralRepository
  ConsultationRepository
  BranchRepository
  AiScoringRepository
  CbcRepository

:core/policy/  (variant/tenant escalation points)
  LoanEligibilityPolicy        // age, income, bureau-score thresholds per region
  EmiCalculator                // rounding rules, day-count convention
  RepaymentPenaltyCalculator   // overdue penalty per regulator
  KycRequirementPolicy         // what documents per variant
  OtpDeliveryPolicy            // (keep — already exists)
  AmountFormatter              // (keep — already exists)
  BusinessCalendar             // (keep — already exists)
  StaffIdPolicy                // referral validation per tenant
  SessionTimeoutPolicy         // (new)
```

### 6.2 Rename `Fintech*Api` → `*Api`
The "Fintech" prefix is redundant inside a fintech codebase and clashes with the "one backend" invariant when third-parties (CBC, agency) are added. Use:

```
:data/api/
  AuthApi              (was FintechAuthApi — also the "Auth" hides PIN/biometric/QR which are not the auth method but the credential type)
  LoanApi              (origination, products, status)
  RepaymentApi
  GuarantorApi
  KycApi
  ChatApi              (or via Sendbird if bought)
  ReferralApi
  ConsultationApi
  BranchApi
  AiScoringApi
:data/external/        (third-party, not the primary backend)
  CbcApi
  BankStatementAnalyzerApi
  MwlAgencyApi
```

### 6.3 Collapse variant into tenant hierarchy — single axis, region as Gradle dependency
**[Updated per round-3 stakeholder decision.]** Variant is removed as a DI axis. Regional grouping becomes a Gradle module hierarchy. The framework now has one runtime axis (tenant) and one structural axis (region, expressed as module dependency).

**Module layout:**

```
:core/tenant/
  TenantContext, TenantId, TenantFlags, TenantParams, TenantCapabilities
  (no more :core/variant/)

:tenants/
  cambodia/
    base/                ← shared KH policies (was :variants-kh)
                            currency: KHR/USD AmountFormatter
                            compliance: ComplianceThresholds
                            otp: OtpDeliveryPolicy (SMS, codeLength=6)
                            calendar: BusinessCalendar
                            kyc: KycRequirementPolicy
                            capabilities: TenantCapabilities (KH-baseline)
    nh/                  ← NH-KH (this PRD)
                            depends on :tenants:cambodia:base
                            overrides: TenantFlags, TenantParams, staff-ID regex,
                                       receipt footer, support contacts
                            adds: NH-specific approval-line renderer
    partner-a/           ← future Partner-A-KH tenant (illustrative)
                            depends on :tenants:cambodia:base
    default/             ← empty/sentinel tenant for KH
                            depends on :tenants:cambodia:base only
                            used in tests and as the "no-overrides" baseline
  korea/
    base/                ← shared KR policies (was :variants-kr, if it ships)
    nh/                  ← NH-KR
    default/

:app/di/
  TenantResolverModule.kt    ← single point of dispatch
                                reads TenantContext from Session
                                provides keyed bindings via Hilt @IntoMap
  (no more VariantResolverModule)
```

**Hilt pattern:**

```kotlin
// In :tenants:cambodia:base
@Module @InstallIn(LoggedInComponent::class)
internal object CambodiaBaseModule {
    @Binds @IntoMap @TenantKey("cambodia:base") @LoggedInScoped
    fun bindAmountFormatter(impl: KhrAmountFormatter): AmountFormatter
    // ... other KH-shared policies
}

// In :tenants:cambodia:nh (which depends on :tenants:cambodia:base as a Gradle module)
@Module @InstallIn(LoggedInComponent::class)
internal object NhKhModule {
    @Binds @IntoMap @TenantKey("nh-kh") @LoggedInScoped
    fun bindApprovalLineRenderer(impl: NhKhApprovalLineRenderer): ApprovalLineRenderer
    // ... NH-KH-specific overrides
}
```

The base module's bindings are keyed by the **base tenant id** (`"cambodia:base"`) and the concrete tenant's bindings are keyed by the **concrete tenant id** (`"nh-kh"`). The resolver walks the chain: when `TenantContext.id == "nh-kh"`, it merges bindings from `"nh-kh"` over `"cambodia:base"`. The chain is encoded in a per-tenant `parentId` constant.

**Alternative (simpler, recommended for v1):** skip the chain-walking. Each concrete tenant module re-binds *every* policy it needs. The "base" module just provides default *implementation classes* (not Hilt bindings) that concrete tenants can use:

```kotlin
// In :tenants:cambodia:base — provides classes but not Hilt bindings
class KhDefaultOtpDeliveryPolicy : OtpDeliveryPolicy { ... }

// In :tenants:cambodia:nh — provides Hilt bindings, can reuse base classes
@Module @InstallIn(LoggedInComponent::class)
internal object NhKhModule {
    @Binds @IntoMap @TenantKey("nh-kh") @LoggedInScoped
    fun bindOtp(impl: KhDefaultOtpDeliveryPolicy): OtpDeliveryPolicy  // reuses base
    @Binds @IntoMap @TenantKey("nh-kh") @LoggedInScoped
    fun bindApprovalLine(impl: NhKhApprovalLineRenderer): ApprovalLineRenderer  // overrides
}
```

This avoids the chain-walking complexity. The tradeoff is more boilerplate in concrete tenants. Pick this for v1; revisit if tenant count grows past ~10.

**Boot behavior:** `BootCoordinator` fails fast if `TenantContext.id` has no binding in the multibinding map. No silent fallback. The active tenant is set once from `LoginResponse.tenantId`, never swapped in-session.

**Forbidden-imports update:**
- `:tenants:{region}:{tenantA}` → `:tenants:{region}:{tenantB}` — forbidden (no cross-tenant coupling, same as previous cross-variant rule)
- `:tenants:*:*` → `:data`, `:design-system` — forbidden (tenants are pure policy + DI)
- `:tenants:{regionA}:*` → `:tenants:{regionB}:*` — forbidden (no cross-region coupling at the tenant level; if shared policy is truly needed across regions, promote to `:core`)
- Regional-base module depends only on `:core` (and optionally `:aos-sdk` for infrastructure types if needed)
- Concrete tenant module depends only on `:core` + its region-base sibling

### 6.4 Promote `:features-loan-origination` to its own module
The 27 apply-loan items are the riskiest, most-changed, most-tested surface in the app. They deserve a module boundary so:
- A bug in branch-locator can't break the application form's state machine.
- The form module can pin its own version of any form-state library without dragging the rest of `:features` along.
- Onboarding a new MWL destination (Korea → Japan → Singapore) is localized.

Same applies to `:features-kyc` and `:features-support-chat`. Document the criterion in `docs/06`.

### 6.5 Add `:aos-sdk` submodules (renamed from `:aos-core`)
**[Updated per round-2 + round-3 stakeholder decisions.]** As capabilities are added (chat, KYC, maps, PDF, work), `:aos-sdk` will become a kitchen sink. Pre-empt by structuring as sub-packages with clear surfaces and a versioned public API:

```
:aos-sdk (renamed from :aos-core, git-tag-released)
  network/        HttpClient, interceptors, Authenticator
  security/       SecurityProvider, BiometricAuthenticator, KeystoreManager, ScreenSecurity
  storage/        EncryptedPrefs, SecureFileStore, EncryptedDatabase (NEW)
  logging/        Logger, CrashlyticsTree
  analytics/      AnalyticsClient
  firebase/       MessagingService, RemoteConfigClient, NotificationChannelRegistry (NEW)
  webview/        CompassWebView, WebActionBridge, CookieSync
  work/           BackgroundWorkScheduler (NEW)
  permissions/    PermissionRequester (NEW)
  deeplink/       DeepLinkResolver (NEW)
  i18n/           LocaleManager, FontFallback (NEW — for KR/EN/KH)
  camera/         CameraXController, CompassCameraView (NEW — round-3 split)
  ml/             DocumentScannerWrapper, FaceDetectorWrapper (NEW — ML Kit wrappers, no UI)
  imaging/        ImageCompressor, ExifStripper, Watermarker, BitmapRedactor (NEW)
  pdf/            PdfDownloader, PdfViewer (NEW)
```

**Chat is intentionally absent from `:aos-sdk`** — Sendbird is bought, lives in `:data/chat/` of the consuming app. The SDK does not need to ship a chat capability; that's per-app integration.

**Camera/ML/imaging in `:aos-sdk` (not in `:features-kyc`)** because they're commodity primitives reusable across consuming apps and across feature modules within one app (profile photo, chat attachments, KYC, future merchant flows). The specific KYC *flow* lives in `:features-kyc` per the split in section 3.1.

The invariant "`:aos-sdk` is banking-agnostic" still holds — none of these names contain banking terms. Each sub-package gets one `public` API file (`api.kt` or per-feature `*Api.kt`) and the rest are `internal`.

**Future split option:** if `:aos-sdk` consumer count grows past 4 and dependency-weight complaints emerge (e.g., a B2B admin app wants no camera), split into per-capability artifacts (`:aos-sdk-network`, `:aos-sdk-camera`, `:aos-sdk-ml`, …) like Jetpack's `androidx.*`. R8 already strips unused classes per consumer; this split is only needed if AAR size itself becomes an issue.

---

## 7. The `:aos-core` → `:aos-sdk` Rename — **APPROVED**

**[Resolved per stakeholder input.]** `:aos-sdk` will be consumed by multiple separate Android projects with git-tagged releases. The rename is **justified and recommended**. "SDK" is honest naming here — it signals to consumers that the module has a versioned public contract and that breaking changes follow semver.

**Adopting the SDK name brings four obligations** that should be set up before the first consuming project locks against a tag:

1. **Explicit public API surface**
   - Adopt **`kotlin-binary-compatibility-validator`** (Kotlin's `binary-compatibility-validator` Gradle plugin) — generates `api/` dumps in CI, fails the build on accidental ABI changes
   - Default visibility for new code is `internal`; only the per-sub-package `api.kt` (or explicit `public` types) is part of the contract
   - **`@PublishedApi`** for any inline'd internals that must leak

2. **Semantic versioning + git tag discipline**
   - `vMAJOR.MINOR.PATCH` git tags on `:aos-sdk` only — never on consuming-app commits
   - **MAJOR** = breaking API change or invariant restatement
   - **MINOR** = new public API, new sub-package, new capability
   - **PATCH** = bug fix, internal-only change, doc-only change
   - Tag once per release; consuming apps pin via submodule sha → git tag mapping or via Maven coordinates
   - Maintain a `CHANGELOG.md` at the root of `:aos-sdk` with one entry per tag

3. **Multi-project distribution mechanism**
   - **Option A (recommended):** Publish to an internal Maven repository (Nexus, Artifactory, or GitHub Packages). Consumers declare `implementation("com.nhfinance:aos-sdk:1.4.2")`. Cleanest dependency story; no submodule juggling.
   - **Option B:** Keep git submodule but pin to **tags only**, never to floating branches. Each consumer's `git submodule status` shows a tag, not a hash.
   - Option A is preferred for >2 consumers. Option B is fine while only 2 consumers exist.

4. **Consumer-facing documentation**
   - Move `docs/02-aos-core.md` content to **`:aos-sdk/README.md`** (lives inside the SDK module itself, ships with the artifact)
   - Each sub-package gets a `README.md` with the public API examples
   - Migration guide between MAJOR versions
   - Compatibility matrix: which `:aos-sdk` versions support which `compileSdk` / `minSdk` / Kotlin / Compose / Hilt versions

**Naming convention going forward:**
- Module path: `:aos-sdk`
- Maven coordinate (if Option A): `com.nhfinance:aos-sdk` or `com.nh.kosign:aos-sdk` — confirm with infra team
- Sub-package public types are not re-prefixed (no `SdkHttpClient` — just `HttpClient` in `com.nh.aos.sdk.network`)
- Existing types keep their names (`CompassWebView`, `MgClient`, etc.) — the **module** is renamed, the **types** are not

**Breaking changes implied by this rename** (do them in one MAJOR bump):
- Update all docs (`docs/00–19`) to refer to `:aos-sdk` instead of `:aos-core`
- Update the forbidden-imports table in `docs/01`
- Update the dependency DAG diagrams
- Update `CLAUDE.md`
- Update `settings.gradle.kts` (in consuming apps)
- Git-tag as `v1.0.0` — the first stable SDK release

**Cost estimate:** ~3 days of doc + rename work, plus 1 week to stand up Maven publishing pipeline if Option A is chosen. Do this **before** the second consuming app starts integrating.

---

## 8. Should We Redesign From Scratch?

**No.** The architectural pattern is well-reasoned and the doc set is unusually thorough. A from-scratch redesign would lose:

- The **compile-time forbidden-imports** enforcement (most teams never get this right; this team has).
- The **MG-first runtime config** (avoids the `production.json` antipattern).
- The **`@LoggedInScoped` component** with `Session` rebuild-once semantics (avoids the "purge sequence" antipattern).
- The **MVI contract** (`UiState/UiEvent/UiEffect` with one-shot effects via `SharedFlow`).
- The **WebView hardening** (JS bridge with versioned payload, URL allowlist, cookie sync).
- The **tenant model** (the right abstraction once promoted).

These are **rare and valuable**. Don't throw them out.

**What to throw out:**
- The **transfer/payments domain examples** throughout `docs/02–08`, `docs/15`. The "Compass" name is fine; the examples are wrong domain.
- The **variant axis as a DI dispatch dimension.** Replaced with single tenant axis + Gradle module hierarchy for regional grouping (section 6.3 round-3). Deletes ~30% of architectural surface.
- The pattern of baking **user-facing strings into tenant/variant policy classes** (e.g., `ReceiptRenderer` with hardcoded Khmer+English). Strings belong in `strings.xml` for runtime locale switching (confirmed multi-lingual requirement).
- The implicit "one backend" wording in invariant #4 — restate as "one **primary** fintech backend; third-party integrations get typed clients in `:data` but never branch by tenant."

**What to keep (do not touch):**
- The **tenant axis** as the single DI dispatch dimension — stakeholder confirmed multi-tenant deployments (NH-KH for this PRD; future Korea outsource projects with multiple tenants).
- The "one APK contains all tenants" property — earns its keep at 2+ tenants per consuming app.
- The MVI contract, `@LoggedInScoped` lifecycle, MG-driven config, WebView hardening, forbidden-imports enforcement.
- The "bind once at login, drop at logout" lifecycle, `Session`, `DepartmentAccount` (for outsource readiness), `AccountIdInterceptor`.

**What to add:**
- The 11 critical-gap capabilities (sections 3.1–3.11).
- The 7 scalability mitigations (section 4).
- The library picks (section 5).

**Estimated effort to refactor (not redesign):**
- Reseed `:core` and `:data` for loan domain: ~2 weeks (mostly doc edits + Kotlin signatures, since impl doesn't exist yet)
- Promote tenant / demote variant: ~1 week (doc edits + rename)
- Add capability layers (chat, kyc, push channels, deeplinks, maps, PDF, room, workmgr, locale, pin, session-timeout): ~6–10 weeks of staffed engineering
- Add `:features-loan-origination`, `:features-kyc`, `:features-support-chat` boundaries: built incrementally as feature work lands

**Estimated effort to redesign from scratch:** 3–6 months and the loss of the patterns above. Not worth it.

---

## 9. Recommended Next Steps

In priority order, with all three rounds of stakeholder decisions baked in. **Steps 1–3 must land before SDK v1.0.0 tag** to avoid shipping breaking ABI changes in a v2.0.0:

1. **Collapse variant into tenant hierarchy** (round-3 decision).
   - Delete `@VariantKey`, `VariantContext`, `VariantId`, `VariantCapabilities`, `VariantCatalogue`, `VariantContextResolver`, `VariantResolverModule`.
   - Rewrite the `:variants-*` → `:tenants:{region}:{tenantId}` module convention.
   - Restructure `docs/07-variants.md` (delete) + `docs/13-onboarding-a-variant.md` (rewrite as tenant onboarding) + `docs/19-tenants-and-variants.md` (rewrite as single-axis).
   - Update the dependency DAG in `docs/01-module-topology.md` and forbidden-imports table.
   - Update `CLAUDE.md` invariants #1, #11, #12.
   - (3–4 days of doc work — framework is doc-only today.)
2. **Execute the `:aos-core` → `:aos-sdk` rename.** Stand up Maven publishing or git-tag discipline, kotlin-binary-compatibility-validator, CHANGELOG. Tag `v1.0.0` after steps 1 + 3 are complete. (1 week — including infra.)
3. **Reseed the domain examples.** Replace every `Transfer*` / `Beneficiary*` / `Card*` reference in `docs/02–08, 15` with loan-domain equivalents (`Loan*`, `LoanApplication*`, `Repayment*`, `Guarantor*`, `Kyc*`). Rename `Fintech*Api` → `*Api` and add the `:data/external/` convention for CBC / MWL agency. (1–2 weeks.)
4. **Write the 11 missing capability docs.** One doc each:
   - `20-chat.md` (Sendbird integration, provider-agnostic `ChatRepository`)
   - `21-push-channels.md` (reminder / transaction / announcement)
   - `22-deeplinks.md` (App Links + Compose Nav DSL)
   - `23-kyc-capture.md` (`:aos-sdk` primitives + `:features-kyc` flow per the split)
   - `24-pdf.md` (download + preview)
   - `25-locale.md` (KR/EN/KH runtime switching, font fallback)
   - `26-pin-and-session.md` (PIN UX, lockout, session timeout)
   - `27-maps-and-location.md` (branch locator)
   - `28-background-work.md` (WorkManager)
   - `29-local-database.md` (Room + SQLCipher)
   - `30-form-wizard.md` (multi-step apply-loan)
   - Plus amend `docs/10-boot-phases.md` and `docs/11-mg-and-runtime-config.md` for **stale-config fallback**. (2–3 weeks.)
5. **Update `docs/17-project-structure.md`** with the renamed `:aos-sdk` (incl. new `camera/ml/imaging/i18n/work/pdf` sub-packages), the `:tenants:{region}:{tenantId}` hierarchy, and the planned sibling `:features-*` modules (`features-loan-origination`, `features-kyc`, `features-support-chat`, `features-branch-locator`). (1 day.)
6. **Start Sendbird vendor engagement.** Confirm pricing, Korea & Cambodia data residency, compliance archival webhook support. Get a sandbox account so `:data/chat/SendbirdChatRepo` can be prototyped early. (1 week elapsed; minimal engineering time.)
7. **Stand up the missing libraries in a `:aos-sdk` integration spike**: Room + SQLCipher, WorkManager, Coil, CameraX + ML Kit Document Scanner + Face Detection, Maps Compose, AppCompatDelegate locale switching, `androidx.pdf`, FCM `NotificationChannel`s. The framework being "doc-only" is fine for an architecture spec, but it stops being credible at SDK-tag time if the infrastructure libraries aren't proven to integrate cleanly with each other and with Hilt. (2 weeks.)
8. **Add CI lint rule** that fails on `tenant.id == ...` / `when (tenant.id)` outside `:tenants:*` and `:app/di/TenantResolverModule.kt`. (Note: `variant.id` checks no longer apply — the type doesn't exist post-collapse.) Doc patterns are not self-enforcing — this is the single most important guardrail. (2 days.)

---

## 10. Open Questions — All Resolved

All open questions resolved across the two rounds of stakeholder input on 2026-05-26. Summary:

| # | Question | Resolution |
|---|---|---|
| 1 | "KR" — separate country deployment or UI language? | **Per-project, not per-region.** App is organized by tenant. Current PRD = single-tenant. Outsource projects may be multi-tenant. Framework must flex to both shapes. |
| 2 | `:aos-core` consumed by multiple Android projects? | **Yes**, with git tags. **`:aos-sdk` rename approved.** |
| 3 | Build vs. buy chat? | **Buy.** Sendbird primary; GetStream backup. |
| 4 | Multi-lingual app? | **Yes.** Runtime locale switching for KR/EN/KH minimum. |
| 5 | eKYC: managed or in-house? | **In-house.** CameraX + ML Kit Document Scanner + Face Detection. `:features-kyc` sibling module. |
| 6 | AI Loan Health Scoring (item 77)? | **Removed from native scope.** Either WebView or drop. No on-device ML. |
| 7 | MG outage policy? | **Stale-config fallback.** 24h TTL. Telemetry on fallback activation. |
| 8 | Multi-account-per-user readiness? | **Keep for outsource readiness.** This PRD ships single-account; framework retains `DepartmentAccount` / `AccountIdInterceptor` / `docs/12` unchanged. Active-account UI hidden when `accounts.size == 1`. |
| 9 | Dashboard currencies? | **KHR + USD** this PRD; **KRW** or others for outsource projects. Per-currency display, no mixed totals. **FX conversion not required**; deferred until an outsource project asks. |

The assessment is now fully calibrated to stakeholder direction. No remaining decision blockers for the refactor or the SDK v1.0.0 tag.

---

## 11. Final Recommendation

**Refactor in place, collapse variant into tenant hierarchy, rename `:aos-core` → `:aos-sdk`, split camera/eKYC across SDK primitives and feature module, ship v1.0.0 with the changes in section 6 and the docs additions in section 9.**

The framework's spine is sound and worth keeping. The work ahead, in dependency order:

1. **Collapse variant into tenant hierarchy** — single axis, region as Gradle module dependency. Deletes ~30% of architectural surface. Must land before SDK v1.0.0 (breaking ABI change).
2. **Domain reseed** (transfer → loan/lending examples throughout `docs/02–08, 15`)
3. **SDK rename + governance** (Maven publishing or tag-pinned submodule, kotlin-binary-compatibility-validator, CHANGELOG, public-API discipline)
4. **Camera/eKYC split** — primitives (`camera`, `ml`, `imaging`) added to `:aos-sdk`; KYC flow lives in `:features-kyc`
5. **11 missing capability docs** (`docs/20–30`)
6. **MG stale-config fallback** amendment to `docs/10, 11`
7. **Lint rule** enforcing "no `tenant.id ==` outside `:tenants:*` and the resolver module"
8. **Integration spike** standing up Room + WorkManager + CameraX + ML Kit + Maps Compose + AppCompat locale switching in the renamed `:aos-sdk` to prove they integrate cleanly with Hilt and each other

No structural redesign required. The "Compass" name can stay, or be reconsidered as a marketing decision riding the v1.0.0 SDK tag as the natural inflection point. The architectural simplifications (variant collapse, SDK rename, camera split) are large wins that genuinely shrink the framework's surface while preserving its load-bearing properties: Logic-Blind UI, `@LoggedInScoped` lifecycle, MG-driven config, MVI contract, WebView hardening, compile-time forbidden-imports.

---

## Appendix A — One-Line Summary Per PRD Item

For traceability during refactor planning. Coverage codes: ✅ trivial / ⚠️ partial / ❌ gap / ⚪ n/a.

| # | Feature | Coverage |
|---|---|---|
| 1 | Splash/Onboarding 3-screen + animation | ✅ |
| 2 | Sign-up phone + OTP + PIN | ⚠️ PIN absent |
| 3 | Sign-in QR/PIN/Face/Touch + session timeout | ⚠️ QR, PIN, timeout absent |
| 4 | Welcome multi-language KR/EN/KH | ❌ runtime locale switching absent |
| 5 | PIN 4-digit | ❌ PIN UX & secure store contract absent |
| 6 | Forgot PIN via phone | ❌ |
| 7 | Referral (staff ID last-5) | ✅ |
| 8 | Loan calculator | ✅ |
| 9 | Request consultation | ✅ |
| 10 | Multi-currency dashboard | ⚠️ FX rate source missing |
| 11 | List loan product | ✅ |
| 12 | Detail loan product | ✅ |
| 13 | Request consultation (product-specific) | ✅ |
| 14 | Loan calculator (product-specific) | ✅ |
| 15–20 | Apply Loan NON-MWL (6 substeps) | ❌ form-wizard + file-upload absent |
| 21–28 | Apply Loan MWL (8 substeps) | ❌ form-wizard + file-upload absent |
| 29 | Add guarantor info | ❌ |
| 30 | Guarantor SMS/link | ❌ deeplink + app-link absent |
| 31 | Guarantor receives link | ❌ |
| 32 | Guarantor OTP | ⚠️ OTP policy exists, SMS link handling absent |
| 33 | Guarantor face + ID capture | ❌ camera + ML Kit absent |
| 34 | Guarantor confirm application | ✅ |
| 35 | Borrower confirm guarantor & submit | ✅ |
| 36–37 | BM/LOS server-side | ⚪ |
| 38–41 | Approval → accept → disbursement → contract | ⚠️ push + PDF absent |
| 42 | Loan detail (progressing) | ✅ |
| 43 | Timeline | ✅ |
| 44 | Loan contract view/download | ❌ PDF absent |
| 45 | Need help | ✅ |
| 46 | Dashboard (approved) + deeplink payment | ⚠️ deeplink absent |
| 47 | Pay Now | ⚠️ payment deeplink absent |
| 48 | Pay Now overdue | ✅ |
| 49 | Loan info | ✅ |
| 50 | Repayment schedule + history | ✅ |
| 51 | Payment history | ✅ |
| 52 | Payoff | ✅ |
| 53 | Loan contract | ❌ PDF |
| 54 | Apply restructure | ✅ |
| 55 | Guarantor loan overview | ✅ |
| 56 | Preview repayment schedule | ✅ |
| 57 | Loan reject reason | ✅ |
| 58 | Tip suggestion | ✅ |
| 59 | Loan application overview | ✅ |
| 60 | Try another loan | ✅ |
| 61 | Limit try another | ✅ |
| 62 | Chat list | ❌ chat infra absent |
| 63 | Chat room (text/voice/image/video/file) | ❌ chat infra + media pipeline absent |
| 64 | Push 3 categories | ⚠️ channel registry absent |
| 65 | Feedback/rate | ✅ |
| 66 | Credit & insights | ✅ |
| 67 | User profile + KYC review | ⚠️ in-house camera + ML Kit (3.1) |
| 68 | Account security (biometric, PIN, sessions) | ⚠️ PIN + active sessions absent |
| 69 | App settings | ✅ |
| 70 | App policy (admin-updated) | ✅ |
| 71 | About company | ✅ |
| 72 | Branch locator | ❌ maps absent |
| 73 | Blogs education | ✅ |
| 74 | CBC check | ⚠️ vendor SDK or API choice |
| 75 | Referral history | ✅ |
| 76 | Salary/income assessment | ❌ file upload + 3rd-party API absent |
| 77 | ~~AI loan health scoring~~ | ⚪ out-of-scope per stakeholder (WebView or drop) |
| 78 | FAQ Center | ✅ |

---

## Appendix B — Invariants the PRD Stresses (Will They Hold?)

Of the 12 hard invariants in `CLAUDE.md`, how does the PRD strain each — and how do the round-3 decisions reshape them?

| # | Invariant (current text) | PRD Stress | Round-3 Reshape | Verdict |
|---|---|---|---|---|
| 1 | No conditional logic on variant *or* tenant ID | None directly; KR/EN/KH is locale not dispatch | **Restated:** "No conditional logic on tenant ID." Variant ID no longer exists post-collapse. | ✅ holds, simpler |
| 2 | `:features` is Logic-Blind | MWL vs. NON-MWL different flows could tempt `if (productType == MWL)` — must be polymorphism via `LoanProduct.type` capability | Unchanged | ⚠️ at risk during apply-loan implementation |
| 3 | `:aos-core` banking-agnostic | Adding camera/PDF/maps to SDK doesn't violate; adding `LoanDocumentUploader` would | **Renamed:** `:aos-sdk` banking-agnostic. New `camera/ml/imaging` sub-packages all banking-agnostic. | ✅ holds if discipline maintained |
| 4 | One backend, no per-variant duplication | Multiple **third-party** backends (CBC, MWL agency, bank-statement) — invariant needs restatement (see 4.4) | **Restated:** "One primary fintech backend per logical capability; third-party integrations get typed clients in `:data` but never branch by tenant." | ✅ holds with restatement |
| 5 | Variant modules = policies + DI only | (was: watch for `:variants-*/api/` creep) | **Restated:** "Tenant modules (`:tenants:{region}:{tenantId}`) = policies + DI only. Region-base modules same constraint." | ✅ holds, renamed |
| 6 | `:design-system` variant/domain-agnostic | KR/KH font fallback might tempt locale-specific tokens — must stay in resources, not in design-system code | **Restated:** "`:design-system` tenant/domain-agnostic." | ⚠️ at risk |
| 7 | Variant binds once at login | Customer change phone number / admin reset — these don't change tenant, just credentials | **Restated:** "Tenant binds once at login. There is no in-session swap. Tenant change in production means logout-then-login." (Variant clause deleted.) | ✅ holds, simpler |
| 8 | Only MG URL is hardcoded | CBC SDK endpoints, Sendbird app-id, Google Maps API key — these will tempt BuildConfig. Must come via MG `RuntimeConfig` | **Amended:** one **additional** allowed BuildConfig constant — `staleConfigTtl` for the MG fallback bootstrap (section 4.7). All other third-party config still via MG. | ⚠️ at risk during 3rd-party integration |
| 9 | Departments are accounts, not sub-variants | This PRD is single-account; outsource projects multi-account | **Restated:** "Departments are accounts, not sub-tenants. Multiple `DepartmentAccount`s under one logged-in user share the same `LoggedInComponent`." (Variant→tenant rename.) | ✅ trivially holds for this PRD; preserved for outsource readiness |
| 10 | `:features` is Hybrid-Monolith on purpose | 200 screens push the limit; pre-commit to sibling modules (see 4.3) | Unchanged. New siblings pre-committed: `:features-loan-origination`, `:features-kyc`, `:features-support-chat`, `:features-branch-locator`. | ⚠️ needs upfront planning |
| 11 | Variant onboarding is strictly additive | (was: vacuous until 2nd variant ships) | **Deleted.** Replaced with new invariant 11': **Tenant onboarding is strictly additive.** New tenant module + one `include()` line + one `TenantCatalogue` entry. No edits to `:aos-sdk`, `:core`, `:data`, `:design-system`, `:features`, sibling tenants, or other regions. | ✅ holds, renamed |
| 12 | Tenants are an axis inside variants | (was: tenants nested in variant DI scope) | **Replaced:** "Tenants form a hierarchy via Gradle module dependency. Region bases (`:tenants:{region}:base`) provide shared regional policies; concrete tenants depend on a region base. There is no variant axis." | ✅ replaced |

**Net after round-3 collapse:**
- **3 invariants simplified** (#1, #7, #9) — variant clauses deleted, leaving cleaner tenant-only rules
- **3 invariants renamed** (#3, #5, #11) — terminology updates with no semantic change
- **2 invariants restated** (#4, #12) — meaningful semantic refinements
- **1 invariant amended** (#8) — single new BuildConfig exception for stale-config TTL
- **3 implementation-time risks** (#2, #6, #8) — unchanged from earlier rounds, still flagged for code review checklists

The framework's load-bearing properties — Logic-Blind UI, scoped DI with single-axis dispatch, MG-driven config, MVI contract, WebView hardening, compile-time forbidden-imports — are all preserved through the simplification.

---

*End of assessment.*
