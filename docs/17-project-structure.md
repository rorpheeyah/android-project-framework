# 17 · Project Structure

> Single-page reference: every module's directory layout, in one place. Read top-down; the order matches the dependency DAG.

---

## Top-Level

```
compass/
├── aos-sdk/                                (Git submodule, pinned to a git-tag release)
├── core/
├── design-system/
├── data/
├── features/
├── features-chatbot/
├── features-kyc/                           (heavy-SDK sibling: CameraX + ML Kit)
├── features-support-chat/                  (heavy-SDK sibling: Sendbird)
├── features-branch-locator/                (heavy-SDK sibling: Google Maps Compose)
├── features-{tenant-feature}/              (zero or more, e.g. features-bakong-disputes)
├── tenants/
│   ├── cambodia/
│   │   ├── base/                           (region baseline)
│   │   ├── default/                        (sentinel tenant: tests + no-overrides baseline)
│   │   └── nh/                             (concrete tenant)
│   └── korea/                              (illustrative: if/when KR ships)
│       ├── base/
│       ├── default/
│       └── nh/
├── app/
├── docs/
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

---

## `:aos-sdk`

```
aos-sdk/
├── build.gradle.kts
├── CHANGELOG.md                            (semver release history, one entry per git tag)
└── src/main/kotlin/com/aos/sdk/
    ├── network/
    │   ├── HttpClient.kt
    │   ├── BaseApiResponse.kt
    │   ├── BaseUrlInterceptor.kt
    │   ├── BaseUrlProvider.kt
    │   ├── AuthHeaderInterceptor.kt
    │   ├── Authenticator.kt                (OkHttp refresh-token rotation on 401)
    │   └── RetrofitFactory.kt
    ├── security/
    │   ├── SecurityProvider.kt
    │   ├── BiometricAuthenticator.kt
    │   ├── EncryptionUtils.kt
    │   ├── KeystoreManager.kt
    │   └── ScreenSecurity.kt               (FLAG_SECURE helper)
    ├── storage/
    │   ├── EncryptedPrefs.kt
    │   ├── SecureFileStore.kt
    │   └── EncryptedDatabase.kt            (Room + SQLCipher wrapper)
    ├── logging/
    │   ├── Logger.kt
    │   └── CrashlyticsTree.kt
    ├── analytics/
    │   └── AnalyticsClient.kt
    ├── firebase/
    │   ├── RemoteConfigClient.kt
    │   ├── MessagingService.kt
    │   └── NotificationChannelRegistry.kt  (reminder / transaction / announcement)
    ├── webview/
    │   ├── CompassWebView.kt
    │   ├── WebActionBridge.kt
    │   └── CookieSync.kt
    ├── work/
    │   └── BackgroundWorkScheduler.kt      (WorkManager helpers)
    ├── permissions/
    │   └── PermissionRequester.kt
    ├── deeplink/
    │   └── DeepLinkResolver.kt
    ├── i18n/
    │   ├── LocaleManager.kt                (KR/EN/KH runtime switching)
    │   └── FontFallback.kt                 (Noto Sans Khmer + Noto Sans KR)
    ├── camera/
    │   ├── CameraXController.kt
    │   └── CompassCameraView.kt            (Compose preview primitive)
    ├── ml/
    │   ├── DocumentScannerWrapper.kt       (ML Kit Document Scanner)
    │   └── FaceDetectorWrapper.kt          (ML Kit Face Detection)
    ├── imaging/
    │   ├── ImageCompressor.kt
    │   ├── ExifStripper.kt
    │   ├── Watermarker.kt
    │   └── BitmapRedactor.kt
    └── pdf/
        ├── PdfDownloader.kt
        └── PdfViewer.kt
```

Detail: [02 — `:aos-sdk`](02-aos-core.md)

---

## `:core`

```
core/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/core/
    ├── tenant/
    │   ├── TenantContext.kt
    │   ├── TenantId.kt                     (composite "<region>:<tenantSlug>")
    │   ├── TenantFlags.kt
    │   ├── TenantParams.kt
    │   └── TenantCapabilities.kt
    ├── runtime/
    │   ├── RuntimeConfig.kt
    │   ├── ApiUrls.kt
    │   ├── MaintenanceState.kt
    │   └── ForceUpdate.kt
    ├── session/
    │   ├── Session.kt
    │   ├── DepartmentAccount.kt
    │   └── AccountId.kt
    ├── repository/
    │   ├── LoanRepository.kt
    │   ├── LoanApplicationRepository.kt
    │   ├── RepaymentRepository.kt
    │   ├── GuarantorRepository.kt
    │   ├── KycRepository.kt
    │   ├── AuthRepository.kt
    │   ├── ChatRepository.kt               (provider-agnostic; Sendbird impl in :data)
    │   ├── ReferralRepository.kt
    │   ├── ConsultationRepository.kt
    │   └── BranchRepository.kt
    ├── policy/
    │   ├── LoanEligibilityPolicy.kt
    │   ├── EmiCalculator.kt
    │   ├── RepaymentPenaltyCalculator.kt
    │   ├── AmountFormatter.kt
    │   ├── OtpDeliveryPolicy.kt
    │   ├── SupportContacts.kt
    │   ├── ComplianceThresholds.kt
    │   ├── BusinessCalendar.kt
    │   ├── KycRequirementPolicy.kt
    │   ├── StaffIdValidator.kt
    │   └── SessionTimeoutPolicy.kt
    ├── kyc/
    │   ├── KycCaptureRequest.kt
    │   └── KycCaptureResult.kt
    ├── wizard/
    │   ├── WizardState.kt
    │   ├── WizardEvent.kt
    │   └── WizardEffect.kt
    ├── deeplink/
    │   └── DeepLinkRoute.kt
    ├── model/
    │   ├── Money.kt
    │   ├── Currency.kt
    │   ├── UserSession.kt
    │   ├── LoginResponse.kt
    │   ├── Loan.kt
    │   ├── LoanProduct.kt
    │   ├── LoanApplication.kt
    │   ├── RepaymentSchedule.kt
    │   ├── Installment.kt
    │   ├── Guarantor.kt
    │   └── AccountBalance.kt
    ├── mvi/
    │   ├── UiState.kt
    │   ├── UiEvent.kt
    │   ├── UiEffect.kt
    │   └── MviViewModel.kt
    └── scope/
        ├── LoggedInScoped.kt
        └── TenantKey.kt                    (@MapKey for tenant multibindings)
```

Detail: [03 — `:core`](03-core.md)

---

## `:design-system`

```
design-system/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/design/
    ├── theme/
    │   ├── CompassTheme.kt
    │   ├── CompassColors.kt
    │   ├── CompassTypography.kt
    │   ├── CompassSpacing.kt
    │   └── CompassShapes.kt
    ├── components/
    │   ├── button/
    │   │   ├── CompassButton.kt
    │   │   ├── CompassPrimaryButton.kt
    │   │   └── CompassSecondaryButton.kt
    │   ├── input/
    │   │   ├── CompassTextField.kt
    │   │   ├── CompassPasswordField.kt
    │   │   ├── CompassPinInput.kt
    │   │   └── CompassPinDots.kt
    │   ├── feedback/
    │   │   ├── CompassSnackbar.kt
    │   │   └── CompassDialog.kt
    │   ├── layout/
    │   │   ├── CompassCard.kt
    │   │   └── CompassBottomSheet.kt
    │   ├── i18n/
    │   │   └── LocaleSelector.kt
    │   └── icons/
    │       └── CompassIcons.kt
    └── modifiers/
        ├── DebouncedClickable.kt
        ├── HapticTouchable.kt
        └── SecureScreen.kt                  (FLAG_SECURE for PII screens)
```

User-facing text in this module reads from `strings.xml` localized resources (`values/`, `values-ko/`, `values-km/`); no hardcoded language strings.

Detail: [04 — `:design-system`](04-design-system.md)

---

## `:data`

```
data/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/data/
    ├── api/
    │   ├── FintechAuthApi.kt               # /v1/auth/...
    │   ├── FintechLoanApi.kt               # /v1/loans/...
    │   ├── FintechRepaymentApi.kt          # /v1/repayments/...
    │   ├── FintechGuarantorApi.kt          # /v1/guarantors/...
    │   ├── FintechKycApi.kt                # /v1/kyc/...
    │   ├── FintechReferralApi.kt           # /v1/referrals/...
    │   ├── FintechConsultationApi.kt       # /v1/consultations/...
    │   ├── FintechBranchApi.kt             # /v1/branches/...
    │   └── dto/
    │       ├── auth/
    │       ├── loan/
    │       ├── repayment/
    │       ├── guarantor/
    │       ├── kyc/
    │       └── shared/
    ├── chat/
    │   └── SendbirdChatRepo.kt             # implements ChatRepository (provider-bound)
    ├── external/
    │   ├── CbcApi.kt                       # Credit Bureau Cambodia (third-party)
    │   ├── BankStatementAnalyzerApi.kt
    │   └── MwlAgencyApi.kt
    ├── repo/
    │   ├── LoanRepo.kt                     # implements LoanRepository
    │   ├── LoanApplicationRepo.kt
    │   ├── RepaymentRepo.kt
    │   ├── GuarantorRepo.kt
    │   ├── KycRepo.kt
    │   ├── AuthRepo.kt
    │   ├── ReferralRepo.kt
    │   ├── ConsultationRepo.kt
    │   ├── BranchRepo.kt
    │   └── mapping/
    │       └── (DTO ↔ domain mappers)
    └── di/
        └── DataModule.kt
```

Detail: [05 — `:data`](05-data.md)

---

## `:features`

```
features/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/
    ├── boot/
    │   ├── BootScreen.kt
    │   ├── BootViewModel.kt
    │   ├── BootContract.kt
    │   ├── MaintenanceGate.kt
    │   └── ForceUpdateGate.kt
    ├── auth/
    │   ├── login/
    │   ├── pin/
    │   ├── otp/
    │   ├── biometric/
    │   └── AuthNavigator.kt
    ├── dashboard/
    │   ├── DashboardScreen.kt
    │   ├── DashboardViewModel.kt
    │   └── DashboardContract.kt
    ├── loan/
    │   ├── product-list/
    │   ├── product-detail/
    │   ├── apply-non-mwl/                   (multi-step wizard package)
    │   ├── apply-mwl/                       (multi-step wizard package)
    │   ├── my-loan/
    │   ├── repayment/
    │   ├── payoff/
    │   ├── calculator/
    │   └── LoanNavigator.kt
    ├── consultation/
    ├── referral/
    ├── notification/
    ├── profile/
    ├── settings/
    ├── faq/
    └── about/
```

Heavy-SDK flows (KYC capture, support chat, branch locator with Maps) live in sibling `:features-{name}` modules, not in `:features`. See below.

Detail: [06 — `:features`](06-features.md)

---

## `:features-chatbot`

```
features-chatbot/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/chatbot/
    ├── (heavy SDKs imported here)
    ├── ChatScreen.kt
    ├── ChatViewModel.kt
    ├── ChatContract.kt
    └── ChatbotNavigator.kt
```

---

## `:features-kyc` (sibling — CameraX + ML Kit)

```
features-kyc/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/kyc/
    ├── ui/
    │   ├── KycFlowScreen.kt
    │   ├── IdCardCaptureScreen.kt
    │   ├── SelfieScreen.kt
    │   └── ReviewScreen.kt
    ├── upload/
    │   └── KycUploadWorker.kt              (WorkManager)
    └── contract/
        ├── KycFlowState.kt
        ├── KycFlowEvent.kt
        └── KycFlowEffect.kt
```

Uses `:aos-sdk/camera/`, `:aos-sdk/ml/`, `:aos-sdk/imaging/`. Calls `KycRepository` from `:core`.

---

## `:features-support-chat` (sibling — Sendbird)

```
features-support-chat/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/supportchat/
    ├── (Sendbird SDK imported here)
    ├── ThreadListScreen.kt
    ├── ChatRoomScreen.kt
    ├── ChatViewModel.kt
    └── ChatContract.kt
```

Sendbird app id comes from `RuntimeConfig` (MG-sourced), not BuildConfig.

---

## `:features-branch-locator` (sibling — Google Maps)

```
features-branch-locator/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/branchlocator/
    ├── (maps-compose imported here)
    ├── BranchMapScreen.kt
    ├── BranchListScreen.kt
    ├── BranchViewModel.kt
    └── BranchContract.kt
```

---

## `:features-{tenant-feature}` (e.g. `:features-bakong-disputes`)

```
features-bakong-disputes/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/bakongdisputes/
    ├── api/
    │   ├── BakongDisputeApi.kt
    │   └── dto/
    ├── repo/
    │   └── BakongDisputeRepo.kt
    ├── screen/
    │   ├── DisputeListScreen.kt
    │   ├── DisputeDetailScreen.kt
    │   └── DisputeContract.kt
    └── di/
        └── BakongDisputesModule.kt
```

Detail: [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md)

---

## `:tenants:{region}:base` (e.g. `:tenants:cambodia:base`)

```
tenants/cambodia/base/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/tenants/cambodia/base/
    ├── policy/
    │   ├── KhDefaultLoanEligibilityPolicy.kt
    │   ├── KhDefaultEmiCalculator.kt
    │   ├── KhDefaultRepaymentPenaltyCalculator.kt
    │   ├── KhOtpDeliveryPolicy.kt
    │   ├── KhComplianceThresholds.kt
    │   ├── KhBusinessCalendar.kt
    │   └── KhDefaultKycRequirementPolicy.kt
    ├── format/
    │   ├── KhrAmountFormatter.kt
    │   └── UsdAmountFormatter.kt           (dual-currency for KH)
    └── capability/
        └── KhBaseCapabilities.kt
```

No Hilt module here — provides implementation classes only. Concrete tenants bind them via `@TenantKey`.

---

## `:tenants:{region}:default` (e.g. `:tenants:cambodia:default`)

```
tenants/cambodia/default/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/tenants/cambodia/default/
    └── di/
        └── KhDefaultTenantModule.kt        (@TenantKey("cambodia:default") bindings, all reusing :base classes)
```

Sentinel tenant. Used in tests and as the no-overrides baseline. Never resolves in production.

---

## `:tenants:{region}:{tenantSlug}` (e.g. `:tenants:cambodia:nh`)

```
tenants/cambodia/nh/
├── build.gradle.kts                        (depends on :tenants:cambodia:base — MANDATORY)
└── src/main/kotlin/com/<org>/tenants/cambodia/nh/
    ├── flags/
    │   └── NhKhTenantProfile.kt            (TenantContext factory)
    ├── policy/
    │   └── NhKhStaffIdValidator.kt         (tenant-specific override)
    ├── support/
    │   └── NhKhSupportContacts.kt
    ├── capability/
    │   └── NhKhCapabilities.kt
    └── di/
        └── NhKhTenantModule.kt             (@TenantKey("cambodia:nh") bindings — concrete-rebinds-everything)
```

Detail: [07 — `:tenants:*`](07-variants.md)

---

## `:app`

```
app/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
├── src/main/res/xml/locales_config.xml     (KR / EN / KH per-app locale registry)
├── src/main/res/values/strings.xml         (EN — default)
├── src/main/res/values-ko/strings.xml
├── src/main/res/values-km/strings.xml
├── src/main/kotlin/com/<org>/app/
│   ├── CompassApplication.kt
│   ├── MainActivity.kt
│   ├── AppNavigation.kt
│   ├── boot/
│   │   ├── BootCoordinator.kt
│   │   ├── MgClient.kt
│   │   ├── BootResult.kt
│   │   └── StaleConfigFallback.kt          (24h last-known-good cache)
│   ├── di/
│   │   ├── NetworkModule.kt
│   │   ├── LoggedInComponent.kt
│   │   ├── LoggedInEntryPoint.kt
│   │   ├── LoggedInBindingsModule.kt
│   │   ├── TenantResolverModule.kt         # picks active tenant's policy from the multibindings map
│   │   ├── RuntimeConfigModule.kt
│   │   └── FirebaseModule.kt
│   ├── session/
│   │   ├── SessionFactory.kt
│   │   ├── AccountIdInterceptor.kt
│   │   ├── InactivityDetector.kt           (session timeout)
│   │   ├── LoggedInComponentManager.kt
│   │   └── LogoutHandler.kt
│   └── tenant/
│       ├── TenantCatalogue.kt              (TenantId → TenantProfile factory)
│       └── TenantContextResolver.kt        (resolves TenantContext from LoginResponse)
└── src/debug/kotlin/com/<org>/app/debug/
    ├── EnvironmentOverride.kt
    └── DebugOverlay.kt
```

Detail: [08 — `:app`](08-app-orchestrator.md)

---

## Build Wiring (`settings.gradle.kts`)

```kotlin
rootProject.name = "compass"

include(":aos-sdk")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":features-chatbot")
include(":features-kyc")
include(":features-support-chat")
include(":features-branch-locator")
// :features-{tenant-feature} modules — added as tenant-locked features ship

include(":tenants:cambodia:base")
include(":tenants:cambodia:default")
include(":tenants:cambodia:nh")
// Additional concrete tenants under :tenants:cambodia:* — added per organization

// :tenants:korea:* modules — added if/when Korea ships
// include(":tenants:korea:base")
// include(":tenants:korea:default")
// include(":tenants:korea:nh")

include(":app")
```

---

## Cross-references

- Why this shape: [01 — Module Topology](01-module-topology.md)
- Onboarding a new tenant or region uses this layout: [13 — Onboarding a Tenant](13-onboarding-a-variant.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
- Tenant behavioral model: [19 — Tenants and Regions](19-tenants-and-variants.md)
