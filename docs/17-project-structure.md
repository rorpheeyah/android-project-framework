# 17 · Project Structure

> Single-page reference: every module's directory layout, in one place. Read top-down; the order matches the dependency DAG.

---

## Top-Level

```
compass/
├── aos-core/                       (Git submodule)
├── core/
├── design-system/
├── data/
├── features/
├── features-chatbot/
├── features-{variant-feature}/     (zero or more, e.g. features-bakong-disputes)
├── variants-{id}/                  (one per region/company: variants-kh, variants-vn, ...)
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
└── src/main/kotlin/com/aos/core/
    ├── network/
    │   ├── HttpClient.kt
    │   ├── BaseApiResponse.kt
    │   ├── BaseUrlInterceptor.kt
    │   ├── BaseUrlProvider.kt
    │   ├── AuthHeaderInterceptor.kt
    │   └── RetrofitFactory.kt
    ├── security/
    │   ├── SecurityProvider.kt
    │   ├── BiometricAuthenticator.kt
    │   ├── EncryptionUtils.kt
    │   └── KeystoreManager.kt
    ├── storage/
    │   ├── EncryptedPrefs.kt
    │   └── SecureFileStore.kt
    ├── logging/
    │   ├── Logger.kt
    │   └── CrashlyticsTree.kt
    └── firebase/
        ├── AnalyticsClient.kt
        ├── RemoteConfigClient.kt
        └── MessagingService.kt
```

Detail: [02 — `:aos-core`](02-aos-core.md)

---

## `:core`

```
core/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/core/
    ├── variant/
    │   ├── VariantContext.kt
    │   └── VariantId.kt
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
    │   ├── TransferRepository.kt
    │   ├── AuthRepository.kt
    │   └── AccountRepository.kt
    ├── policy/
    │   ├── TransferAmountPolicy.kt
    │   ├── FeeCalculator.kt
    │   ├── AmountFormatter.kt
    │   ├── VariantCapabilities.kt
    │   ├── BeneficiaryValidator.kt
    │   ├── OtpDeliveryPolicy.kt
    │   ├── SupportContacts.kt
    │   ├── ComplianceThresholds.kt
    │   ├── BusinessCalendar.kt
    │   └── ReceiptRenderer.kt
    ├── model/
    │   ├── Money.kt
    │   ├── Currency.kt
    │   ├── UserSession.kt
    │   ├── LoginResponse.kt
    │   ├── Beneficiary.kt
    │   ├── TransferIntent.kt
    │   ├── TransferReceipt.kt
    │   └── AccountBalance.kt
    ├── mvi/
    │   ├── UiState.kt
    │   ├── UiEvent.kt
    │   ├── UiEffect.kt
    │   └── MviViewModel.kt
    └── scope/
        ├── LoggedInScoped.kt
        └── VariantKey.kt           # @MapKey for variant multibindings
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
    │   │   └── CompassPasswordField.kt
    │   ├── feedback/
    │   │   ├── CompassSnackbar.kt
    │   │   └── CompassDialog.kt
    │   ├── layout/
    │   │   ├── CompassCard.kt
    │   │   └── CompassBottomSheet.kt
    │   └── icons/
    │       └── CompassIcons.kt
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
└── src/main/kotlin/com/<org>/data/
    ├── api/
    │   ├── FintechAuthApi.kt          # /v1/auth/...
    │   ├── FintechTransferApi.kt      # /v1/transfer/...
    │   ├── FintechAccountApi.kt       # /v1/accounts/...
    │   ├── FintechCardApi.kt          # /v1/cards/...
    │   └── dto/
    │       ├── auth/
    │       │   ├── LoginRequest.kt
    │       │   ├── LoginResponse.kt
    │       │   └── OtpHandleDto.kt
    │       ├── transfer/
    │       │   ├── TransferRequest.kt
    │       │   ├── TransferResponse.kt
    │       │   └── FeeQuoteDto.kt
    │       ├── account/
    │       │   ├── AccountBalanceDto.kt
    │       │   └── TransactionPageDto.kt
    │       └── shared/
    │           └── EmptyResponse.kt
    ├── repo/
    │   ├── FintechAuthRepo.kt         # implements AuthRepository
    │   ├── FintechTransferRepo.kt     # implements TransferRepository
    │   ├── FintechAccountRepo.kt      # implements AccountRepository
    │   ├── FintechCardRepo.kt         # implements CardRepository
    │   └── mapping/
    │       ├── AuthMapping.kt
    │       ├── TransferMapping.kt
    │       └── AccountMapping.kt
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
    │   │   ├── LoginScreen.kt
    │   │   ├── LoginViewModel.kt
    │   │   └── LoginContract.kt
    │   ├── otp/
    │   │   ├── OtpScreen.kt
    │   │   ├── OtpViewModel.kt
    │   │   └── OtpContract.kt
    │   └── AuthNavigator.kt
    ├── transfer/
    │   ├── input/
    │   ├── review/
    │   ├── result/
    │   ├── TransferFlowState.kt
    │   └── TransferNavigator.kt
    └── account/
        ├── balance/
        ├── history/
        ├── switcher/
        └── AccountNavigator.kt
```

Theme and components live in `:design-system`, not in a `common/` package here.

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

## `:features-{variant-feature}` (e.g. `:features-bakong-disputes`)

```
features-bakong-disputes/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/features/bakongdisputes/
    ├── api/
    │   ├── BakongDisputeApi.kt
    │   └── dto/
    │       ├── DisputeRequest.kt
    │       └── DisputeResponse.kt
    ├── repo/
    │   └── BakongDisputeRepo.kt
    ├── screen/
    │   ├── DisputeListScreen.kt
    │   ├── DisputeDetailScreen.kt
    │   └── DisputeContract.kt
    └── di/
        └── BakongDisputesModule.kt
```

Detail: [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md)

---

## `:variants-{id}` (e.g. `:variants-kh`)

```
variants-kh/
├── build.gradle.kts
└── src/main/kotlin/com/<org>/variants/kh/
    ├── policy/
    │   ├── KhTransferAmountPolicy.kt
    │   ├── KhFeeCalculator.kt
    │   ├── KhBeneficiaryValidator.kt
    │   ├── KhOtpDeliveryPolicy.kt
    │   ├── KhComplianceThresholds.kt
    │   ├── KhBusinessCalendar.kt
    │   └── KhReceiptRenderer.kt
    ├── format/
    │   └── KhrAmountFormatter.kt
    ├── capability/
    │   └── KhCapabilities.kt
    ├── support/
    │   └── KhSupportContacts.kt
    └── di/
        └── KhVariantModule.kt
```

Detail: [07 — `:variants-*`](07-variants.md)

---

## `:app`

```
app/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
├── src/main/kotlin/com/<org>/app/
│   ├── CompassApplication.kt
│   ├── MainActivity.kt
│   ├── AppNavigation.kt
│   ├── boot/
│   │   ├── BootCoordinator.kt
│   │   ├── MgClient.kt
│   │   └── BootResult.kt
│   ├── di/
│   │   ├── NetworkModule.kt
│   │   ├── LoggedInComponent.kt
│   │   ├── LoggedInEntryPoint.kt
│   │   ├── LoggedInBindingsModule.kt
│   │   ├── VariantResolverModule.kt   # picks active variant's policy from the multibindings map
│   │   ├── RuntimeConfigModule.kt
│   │   └── FirebaseModule.kt
│   ├── session/
│   │   ├── SessionFactory.kt
│   │   ├── AccountIdInterceptor.kt
│   │   ├── LoggedInComponentManager.kt
│   │   └── LogoutHandler.kt
│   └── variant/
│       ├── VariantCatalogue.kt
│       └── VariantContextResolver.kt
└── src/debug/kotlin/com/<org>/app/debug/
    ├── EnvironmentOverride.kt
    └── DebugOverlay.kt
```

Detail: [08 — `:app`](08-app-orchestrator.md)

---

## Build Wiring (`settings.gradle.kts`)

```kotlin
rootProject.name = "compass"

include(":aos-core")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":features-chatbot")
include(":variants-kh")
include(":variants-vn")
include(":variants-ppcbank")
include(":app")
// Add additional :variants-{id} and :features-{variant-feature} modules here as the project grows.
```

---

## Cross-references

- Why this shape: [01 — Module Topology](01-module-topology.md)
- Onboarding a new variant uses this layout: [13 — Onboarding a Variant](13-onboarding-a-variant.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
