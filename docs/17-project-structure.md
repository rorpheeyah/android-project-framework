# 17 · Project Structure

> Single-page reference: every module's directory layout, in one place. Read top-down; the order matches the dependency DAG.

---

## Top-Level

```
bizplay/
├── aos-core/                       (Git submodule)
├── core/
├── design-system/
├── data/
├── features/
├── features-scanner/
├── features-hipass/                (variant-locked: Korea-only highway-toll capture)
├── features-{variant-feature}/     (zero or more future modules, e.g. features-mydata)
├── variants-kr/                    (Korea — multiple tenant profiles inside)
├── variants-kh/                    (Cambodia)
├── variants-vn/                    (Vietnam)
├── app/
├── docs/
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

---

## `:aos-core`

```
aos-core/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/aoscore/
    ├── network/
    │   ├── HttpClient.kt
    │   ├── BaseApiResponse.kt
    │   ├── BaseUrlInterceptor.kt
    │   ├── BaseUrlProvider.kt
    │   ├── AuthHeaderInterceptor.kt
    │   └── RetrofitFactory.kt
    ├── security/
    │   ├── SecurityProvider.kt           # wraps mVaccine + root/jailbreak checks
    │   ├── BiometricAuthenticator.kt
    │   ├── SecureKeypad.kt               # wraps TransKey SDK
    │   ├── EdgeCrypto.kt                 # wraps Secucen libEdgeCrypto.so
    │   ├── EncryptionUtils.kt
    │   ├── KeystoreManager.kt
    │   └── LicenseChecker.kt             # wraps RSLicenseSDK
    ├── storage/
    │   ├── EncryptedPrefs.kt             # wraps EncryptedSharedPreferences
    │   ├── EncryptedDatabase.kt          # wraps SQLCipher (async init)
    │   └── SecureFileStore.kt
    ├── logging/
    │   ├── Logger.kt
    │   └── CrashlyticsTree.kt
    ├── firebase/
    │   ├── AnalyticsClient.kt
    │   ├── RemoteConfigClient.kt
    │   └── MessagingService.kt
    ├── webview/
    │   ├── BizWebView.kt                 # Compose Composable; replaces today's BizWebview Java class
    │   ├── WebActionBridge.kt            # one @JavascriptInterface method, versioned payload
    │   └── CookieSync.kt
    └── location/
        └── LocationProvider.kt           # replaces today's BizLocationManager
```

Detail: [02 — `:aos-core`](02-aos-core.md)

---

## `:core`

```
core/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/core/
    ├── variant/
    │   ├── VariantContext.kt
    │   ├── VariantId.kt
    │   ├── TenantContext.kt
    │   ├── TenantId.kt
    │   ├── TenantFlags.kt
    │   └── TenantParams.kt
    ├── runtime/
    │   ├── RuntimeConfig.kt
    │   ├── ApiUrls.kt
    │   ├── MaintenanceState.kt
    │   ├── ForceUpdate.kt
    │   └── StoreReviewMode.kt
    ├── session/
    │   ├── Session.kt
    │   ├── DepartmentAccount.kt
    │   └── AccountId.kt
    ├── repository/
    │   ├── AuthRepository.kt
    │   ├── ReceiptRepository.kt
    │   ├── ApprovalRepository.kt
    │   ├── CardRepository.kt
    │   ├── ExpenseRepository.kt
    │   ├── OcrRepository.kt
    │   └── NoticeRepository.kt
    ├── policy/
    │   ├── ExpenseAmountPolicy.kt
    │   ├── FeeCalculator.kt
    │   ├── AmountFormatter.kt
    │   ├── VariantCapabilities.kt
    │   ├── EmployeeIdValidator.kt
    │   ├── OtpDeliveryPolicy.kt
    │   ├── SupportContacts.kt
    │   ├── ApprovalThresholds.kt
    │   ├── BusinessCalendar.kt
    │   ├── ReceiptRenderer.kt
    │   ├── ApprovalLineRenderer.kt
    │   ├── WebActionPolicy.kt
    │   └── TenantProfile.kt
    ├── model/
    │   ├── Money.kt
    │   ├── Currency.kt
    │   ├── UserSession.kt
    │   ├── LoginResponse.kt
    │   ├── Receipt.kt
    │   ├── ReceiptDraft.kt
    │   ├── ApprovalRequest.kt
    │   ├── ApprovalLine.kt
    │   ├── Card.kt
    │   ├── CardStatement.kt
    │   ├── ExpenseCategory.kt
    │   ├── WebAction.kt
    │   └── RenderedReceipt.kt
    ├── mvi/
    │   ├── UiState.kt
    │   ├── UiEvent.kt
    │   ├── UiEffect.kt
    │   └── MviViewModel.kt
    └── scope/
        ├── LoggedInScoped.kt
        ├── VariantKey.kt              # @MapKey for variant multibindings
        └── TenantKey.kt               # @MapKey for tenant multibindings
```

Detail: [03 — `:core`](03-core.md)

---

## `:design-system`

```
design-system/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/design/
    ├── theme/
    │   ├── BizTheme.kt
    │   ├── BizColors.kt
    │   ├── BizTypography.kt
    │   ├── BizSpacing.kt
    │   └── BizShapes.kt
    ├── components/
    │   ├── button/
    │   │   ├── BizButton.kt
    │   │   ├── BizPrimaryButton.kt
    │   │   └── BizSecondaryButton.kt
    │   ├── input/
    │   │   ├── BizTextField.kt
    │   │   ├── BizPasswordField.kt        # wraps :aos-core SecureKeypadField (TransKey)
    │   │   └── BizAmountField.kt
    │   ├── feedback/
    │   │   ├── BizSnackbar.kt
    │   │   ├── BizDialog.kt
    │   │   └── BizToast.kt
    │   ├── layout/
    │   │   ├── BizCard.kt
    │   │   ├── BizBottomSheet.kt
    │   │   ├── BizToolbar.kt              # Compose successor to today's FlexibleToolBar
    │   │   └── BizScaffold.kt
    │   ├── receipt/
    │   │   ├── BizReceiptHeader.kt
    │   │   ├── BizReceiptRow.kt
    │   │   └── BizReceiptFooter.kt
    │   ├── webview/
    │   │   └── BizWebViewFrame.kt         # themed loading/error overlay around BizWebView
    │   └── icons/
    │       └── BizIcons.kt
    └── modifiers/
        ├── DebouncedClickable.kt
        └── HapticTouchable.kt
```

Detail: [04 — `:design-system`](04-design-system.md)

---

## `:data`

```
data/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/data/
    ├── api/
    │   ├── IpppAuthApi.kt
    │   ├── IpppReceiptApi.kt
    │   ├── IpppApprovalApi.kt
    │   ├── IpppCardApi.kt
    │   ├── IpppExpenseApi.kt
    │   ├── IpppOcrApi.kt
    │   ├── IpppNoticeApi.kt
    │   └── dto/
    │       ├── auth/
    │       │   ├── LoginRequest.kt
    │       │   ├── LoginResponse.kt        # carries variantId + tenantId + tenantFlags + tenantParams + accounts
    │       │   ├── OtpHandleDto.kt
    │       │   └── InstitutionDto.kt       # USE_INTT_ID + COMPANY_CD + DVSN_CD/DVSN_NM + business info
    │       ├── receipt/
    │       │   ├── ReceiptDraftRequest.kt
    │       │   ├── ReceiptResponse.kt
    │       │   ├── ReceiptListResponse.kt
    │       │   └── ReceiptFilter.kt
    │       ├── approval/
    │       │   ├── ApprovalListResponse.kt
    │       │   ├── ApprovalActionRequest.kt
    │       │   └── ApprovalLineDto.kt
    │       ├── card/
    │       │   ├── CardRegistrationRequest.kt
    │       │   ├── CardListResponse.kt
    │       │   └── StatementResponse.kt
    │       ├── expense/
    │       │   ├── ExpenseReportRequest.kt
    │       │   └── BizTripBundleRequest.kt
    │       ├── ocr/
    │       │   ├── OcrSubmissionRequest.kt
    │       │   └── OcrResultDto.kt
    │       └── shared/
    │           └── EmptyResponse.kt
    ├── repo/
    │   ├── IpppAuthRepo.kt
    │   ├── IpppReceiptRepo.kt
    │   ├── IpppApprovalRepo.kt
    │   ├── IpppCardRepo.kt
    │   ├── IpppExpenseRepo.kt
    │   ├── IpppOcrRepo.kt
    │   ├── IpppNoticeRepo.kt
    │   └── mapping/
    │       ├── AuthMapping.kt
    │       ├── ReceiptMapping.kt
    │       ├── ApprovalMapping.kt
    │       └── CardMapping.kt
    └── di/
        └── DataModule.kt
```

Detail: [05 — `:data`](05-data.md)

---

## `:features`

```
features/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/features/
    ├── boot/
    │   ├── BootScreen.kt
    │   ├── BootViewModel.kt
    │   ├── BootContract.kt
    │   ├── MaintenanceGate.kt
    │   └── ForceUpdateGate.kt
    ├── auth/
    │   ├── login/
    │   │   ├── LoginScreen.kt
    │   │   ├── LoginViewModel.kt
    │   │   └── LoginContract.kt
    │   ├── otp/
    │   │   ├── OtpScreen.kt
    │   │   ├── OtpViewModel.kt
    │   │   └── OtpContract.kt
    │   ├── institutionpicker/                  # successor to today's SelectUserInttIdActivity
    │   │   ├── InstitutionPickerScreen.kt
    │   │   ├── InstitutionPickerViewModel.kt
    │   │   └── InstitutionPickerContract.kt
    │   └── AuthNavigator.kt
    ├── receipt/
    │   ├── list/
    │   ├── detail/
    │   ├── edit/
    │   ├── create/
    │   ├── transport/
    │   ├── biztrip/
    │   ├── gasoline/
    │   └── ReceiptNavigator.kt
    ├── expense/
    │   ├── report/
    │   ├── category/
    │   └── ExpenseNavigator.kt
    ├── approval/
    │   ├── inbox/
    │   ├── action/
    │   ├── line/
    │   └── ApprovalNavigator.kt
    ├── card/
    │   ├── register/
    │   ├── list/
    │   ├── statement/
    │   └── CardNavigator.kt
    ├── notice/
    │   ├── list/
    │   ├── detail/
    │   └── NoticeNavigator.kt
    └── account/
        ├── switcher/                            # in-session active-institution flip
        ├── profile/
        ├── language/
        └── AccountNavigator.kt
```

Theme and components live in `:design-system`, not in a `common/` package here.

Detail: [06 — `:features`](06-features.md)

---

## `:features-scanner`

```
features-scanner/
├── build.gradle.kts                     # io.card, cameraviewplus, sasapi, OCR partner SDKs
└── src/main/kotlin/com/bizplay/features/scanner/
    ├── camera/
    │   ├── CameraCaptureScreen.kt
    │   ├── CameraCaptureViewModel.kt
    │   └── CameraCaptureContract.kt
    ├── ocr/
    │   ├── ReceiptOcrScreen.kt
    │   ├── ReceiptOcrViewModel.kt
    │   ├── TicketOcrScreen.kt
    │   └── TicketOcrViewModel.kt
    ├── cardscan/
    │   ├── CardScanScreen.kt              # wraps io.card SDK
    │   └── CardScanViewModel.kt
    ├── scraping/
    │   └── ScrapingEnrichmentService.kt   # wraps sasapi
    ├── di/
    │   └── ScannerModule.kt
    └── ScannerNavigator.kt
```

---

## `:features-hipass` (canonical variant-locked example)

```
features-hipass/                          (Korea-only highway-toll capture)
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/features/hipass/
    ├── api/
    │   ├── HipassApi.kt                 # Retrofit; KR-only endpoints
    │   └── dto/
    │       ├── HipassUsageRequest.kt
    │       └── HipassUsageResponse.kt
    ├── repo/
    │   └── HipassRepo.kt                # internal
    ├── screen/
    │   ├── HipassListScreen.kt
    │   ├── HipassDetailScreen.kt
    │   └── HipassContract.kt
    └── di/
        └── HipassModule.kt              # @InstallIn(LoggedInComponent::class)
```

Detail: [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md)

---

## `:variants-kr` (with tenant subtree)

```
variants-kr/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/variants/kr/
    ├── policy/
    │   ├── KrExpenseAmountPolicy.kt
    │   ├── KrFeeCalculator.kt
    │   ├── KrEmployeeIdValidator.kt
    │   ├── KrOtpDeliveryPolicy.kt
    │   ├── KrApprovalThresholds.kt
    │   ├── KrBusinessCalendar.kt
    │   └── KrReceiptRenderer.kt
    ├── format/
    │   └── KrwAmountFormatter.kt
    ├── capability/
    │   └── KrCapabilities.kt
    ├── support/
    │   └── KrSupportContacts.kt
    ├── tenants/
    │   ├── default/
    │   │   ├── DefaultKrTenantProfile.kt
    │   │   └── DefaultApprovalLineRenderer.kt
    │   ├── posco_ict/
    │   │   └── PoscoIctTenantProfile.kt
    │   ├── lotte/
    │   │   └── LotteTenantProfile.kt
    │   ├── nia/
    │   │   └── NiaTenantProfile.kt
    │   ├── shinsegae/
    │   │   ├── ShinsegaeTenantProfile.kt
    │   │   └── ShinsegaeApprovalLineRenderer.kt    # structural
    │   ├── itcen/
    │   │   └── ItcenTenantProfile.kt
    │   ├── wips/
    │   │   └── WipsTenantProfile.kt
    │   ├── hana/
    │   │   └── HanaTenantProfile.kt
    │   ├── ibs/
    │   │   └── IbsTenantProfile.kt
    │   └── spc/
    │       └── SpcTenantProfile.kt
    └── di/
        ├── KrVariantModule.kt           # variant-level @VariantKey bindings
        └── KrTenantModule.kt            # tenant-level @TenantKey bindings (structural impls)
```

Detail: [07 — `:variants-*`](07-variants.md), [19 — Tenants and Variants](19-tenants-and-variants.md)

---

## `:variants-kh` (single-tenant variant)

```
variants-kh/
├── build.gradle.kts
└── src/main/kotlin/com/bizplay/variants/kh/
    ├── policy/
    │   ├── KhExpenseAmountPolicy.kt
    │   ├── KhFeeCalculator.kt
    │   └── KhReceiptRenderer.kt
    ├── format/
    │   └── KhrAmountFormatter.kt
    ├── capability/
    │   └── KhCapabilities.kt
    ├── support/
    │   └── KhSupportContacts.kt
    ├── tenants/
    │   └── default/
    │       └── DefaultKhTenantProfile.kt
    └── di/
        └── KhVariantModule.kt
```

---

## `:app`

```
app/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
├── src/main/kotlin/com/bizplay/app/
│   ├── BizplayApplication.kt
│   ├── MainActivity.kt
│   ├── AppNavigation.kt
│   ├── boot/
│   │   ├── BootCoordinator.kt
│   │   ├── MgClient.kt
│   │   └── BootResult.kt
│   ├── di/
│   │   ├── NetworkModule.kt
│   │   ├── SecurityModule.kt
│   │   ├── LoggedInComponent.kt
│   │   ├── LoggedInEntryPoint.kt
│   │   ├── LoggedInBindingsModule.kt
│   │   ├── VariantResolverModule.kt          # picks active variant's policy from the multibindings map
│   │   ├── TenantResolverModule.kt           # picks active tenant's structural policies (with default fallback)
│   │   ├── RuntimeConfigModule.kt
│   │   └── FirebaseModule.kt
│   ├── session/
│   │   ├── SessionFactory.kt
│   │   ├── AccountIdInterceptor.kt           # stamps X-Use-Intt-Id + X-Company-Cd
│   │   ├── LoggedInComponentManager.kt
│   │   └── LogoutHandler.kt
│   └── variant/
│       ├── VariantCatalogue.kt
│       ├── VariantContextResolver.kt
│       ├── TenantCatalogue.kt
│       └── TenantContextResolver.kt
└── src/debug/kotlin/com/bizplay/app/debug/
    ├── EnvironmentOverride.kt
    └── DebugOverlay.kt
```

Detail: [08 — `:app`](08-app-orchestrator.md)

---

## Build Wiring (`settings.gradle.kts`)

```kotlin
rootProject.name = "bizplay"

include(":aos-core")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":features-scanner")
include(":features-hipass")
include(":variants-kr")
include(":variants-kh")
include(":variants-vn")
include(":app")
// Add additional :variants-{id} and :features-{feature-name} modules here as the project grows.
```

---

## Cross-references

- Why this shape: [01 — Module Topology](01-module-topology.md)
- Onboarding a new variant uses this layout: [13 — Onboarding a Variant](13-onboarding-a-variant.md)
- Onboarding a tenant inside an existing variant: [19 — Tenants and Variants § 10](19-tenants-and-variants.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
