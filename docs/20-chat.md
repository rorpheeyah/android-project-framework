# 20 · Chat (Customer ↔ Loan Officer)

> **Direction:** Buy, not build. Provider: **Sendbird** (primary), GetStream (backup vendor).
> **UI module:** `:features-support-chat` (sibling to `:features`, heavy-SDK pattern).
> **Contract:** `ChatRepository` in `:core` — provider-agnostic. Mobile never names Sendbird types outside `:data/chat/`.

> **PRD scope:** items 62 (chat list) and 63 (chat room with text/voice/image/video/file). Customer-to-loan-officer support chat. **Distinct from** `:features-chatbot` (NLP/LLM bot) — different products, different modules.

---

## 1. Why Buy

A loan-officer support chat needs: text + voice + image + video + file, threads with history, read receipts, typing indicators, push integration, server-side message archival for KYC/AML compliance, moderation tools. A build-from-scratch path is OkHttp WebSocket + Room cache + WorkManager retry + S3-presigned media uploads — that's a multi-quarter engineering investment for table-stakes functionality. Sendbird delivers it in days.

The build option is reserved for the case where deep CRM/ticketing/supervisor-handoff integration is required and no vendor can accommodate. The PRD does not surface that requirement.

---

## 2. The Three-Layer Split

```
:features-support-chat/         ← UI flow (Compose screens, MVI contract)
        │
        ▼
:core/repository/ChatRepository ← provider-agnostic interface + domain types
        ▲
        │  (Hilt binding from DataModule)
        │
:data/chat/SendbirdChatRepo     ← the ONLY place Sendbird types appear
        │
        ▼
   Sendbird SDK
```

This shape preserves three properties:

1. **`:features-support-chat` is provider-blind.** Swapping Sendbird for GetStream is a `:data/chat/` rewrite, not a UI rewrite.
2. **`:core` knows nothing about real-time mechanics.** No `ChatChannel`, no `BaseMessage`, no Sendbird-typed callbacks leak past the repository boundary.
3. **Tenant-agnostic.** Every tenant uses the same Sendbird app-id (the server scopes channels per user). No `:tenants:*:*` involvement.

---

## 3. The `:core` Contract

```kotlin
// :core/repository/ChatRepository.kt
interface ChatRepository {
    fun threads(): Flow<List<ChatThread>>
    fun messages(threadId: ChatThreadId): Flow<List<ChatMessage>>
    suspend fun send(threadId: ChatThreadId, content: ChatMessageContent): Result<ChatMessageId>
    suspend fun markRead(threadId: ChatThreadId, upTo: ChatMessageId): Result<Unit>
    fun unreadCount(): Flow<Int>
    suspend fun openThread(officerId: OfficerId): Result<ChatThreadId>   // creates or returns existing
}

// :core/model/ChatThread.kt
data class ChatThread(
    val id: ChatThreadId,
    val officerName: String,
    val officerAvatarUrl: String?,
    val lastMessagePreview: String,
    val lastMessageAt: Instant,
    val unreadCount: Int,
)

// :core/model/ChatMessage.kt
data class ChatMessage(
    val id: ChatMessageId,
    val sender: ChatSender,                   // Customer | Officer
    val sentAt: Instant,
    val content: ChatMessageContent,
    val readAt: Instant?,
)

// :core/model/ChatMessageContent.kt — sealed; one shape per supported media type
sealed interface ChatMessageContent {
    data class Text(val text: String) : ChatMessageContent
    data class Image(val url: Url, val thumbnailUrl: Url?, val widthPx: Int, val heightPx: Int) : ChatMessageContent
    data class Video(val url: Url, val thumbnailUrl: Url?, val durationMs: Long) : ChatMessageContent
    data class Voice(val url: Url, val durationMs: Long, val waveform: List<Int>) : ChatMessageContent
    data class File(val url: Url, val fileName: String, val sizeBytes: Long, val mimeType: String) : ChatMessageContent
}

@JvmInline value class ChatThreadId(val value: String)
@JvmInline value class ChatMessageId(val value: String)
@JvmInline value class OfficerId(val value: String)
```

The `Url` type is a `:core` value class wrapping a String — concrete URL parsing happens in `:aos-sdk` or `:data/chat/`.

---

## 4. The `:data/chat/` Implementation

`SendbirdChatRepo` is the *only* class allowed to import Sendbird types. It translates between `:core` domain types and the SDK's models.

```kotlin
// :data/chat/SendbirdChatRepo.kt
internal class SendbirdChatRepo @Inject constructor(
    private val sendbird: SendbirdSdk,                           // wrapper around SendbirdChat
    private val mediaUploader: ChatMediaUploader,                // wraps :aos-sdk/imaging + Sendbird file upload
    private val runtimeConfig: RuntimeConfig,                    // for sendbird app-id
    private val session: Session,
) : ChatRepository {

    init { sendbird.connect(session.userSession.userId, session.userSession.tokens.bearer) }

    override fun threads(): Flow<List<ChatThread>> = callbackFlow {
        val listener = sendbird.observeChannels(
            onChange = { trySend(it.map { ch -> ch.toDomain() }) },
        )
        awaitClose { sendbird.removeListener(listener) }
    }

    override suspend fun send(threadId: ChatThreadId, content: ChatMessageContent): Result<ChatMessageId> =
        runCatching {
            when (content) {
                is ChatMessageContent.Text  -> sendbird.sendText(threadId.value, content.text).id
                is ChatMessageContent.Image -> mediaUploader.uploadImage(threadId, content).id
                is ChatMessageContent.Video -> mediaUploader.uploadVideo(threadId, content).id
                is ChatMessageContent.Voice -> mediaUploader.uploadVoice(threadId, content).id
                is ChatMessageContent.File  -> mediaUploader.uploadFile(threadId, content).id
            }.let(::ChatMessageId)
        }

    // … markRead, unreadCount, openThread
}
```

The Sendbird app-id and per-environment endpoint come from `RuntimeConfig` (MG-sourced), **never** from `BuildConfig`. This is invariant #8: only MG URL is hardcoded.

```kotlin
// :core/runtime/RuntimeConfig.kt — extension
data class ThirdPartyAppIds(
    val sendbird: String,        // Sendbird application id
    val googleMaps: String,
)

data class RuntimeConfig(
    val urls: ApiUrls,
    val maintenance: MaintenanceState,
    val forceUpdate: ForceUpdate,
    val thirdPartyAppIds: ThirdPartyAppIds,
    // …
)
```

---

## 5. The `:features-support-chat` UI Module

Heavy-SDK module, sibling to `:features`. Same module shape as `:features-chatbot` (the precedent).

```
:features-support-chat/
└── src/main/kotlin/com/<org>/features/supportchat/
    ├── (Sendbird Compose components imported here; this is the only feature module that pulls Sendbird UI)
    ├── ui/
    │   ├── ThreadListScreen.kt
    │   ├── ChatRoomScreen.kt
    │   ├── VoiceRecorderSheet.kt
    │   └── AttachmentPicker.kt
    ├── viewmodel/
    │   ├── ThreadListViewModel.kt
    │   └── ChatRoomViewModel.kt
    ├── contract/
    │   ├── ThreadListContract.kt
    │   └── ChatRoomContract.kt
    └── nav/
        └── SupportChatNavigator.kt          # NavGraphBuilder.supportChatNavGraph(...)
```

ViewModels depend on `ChatRepository` from `:core` only — no direct Sendbird import.

**Why a separate module rather than a package inside `:features`?** Same rationale as `:features-chatbot`: Sendbird transitively pulls non-trivial dependency weight. Co-locating it with the main UI engine would slow incremental builds of unrelated features.

---

## 6. Push, Notification, and Foreground Behavior

| Concern | Mechanism |
|---|---|
| New-message push when app is in background | FCM topic subscription on chat thread; routed through `NotificationChannelRegistry` (see [21 — Push Channels](21-push-channels.md)) into the `reminder` channel |
| In-app notification badge (unread count) | `ChatRepository.unreadCount(): Flow<Int>` collected by the bottom-bar or dashboard ViewModel |
| Voice-message recording | Compose `VoiceRecorderSheet` records via `MediaRecorder`; `:aos-sdk/imaging/` is not involved (audio, not images); waveform pre-rendered before upload |
| Image / video attachments | `AttachmentPicker` → `ActivityResultContracts.GetContent` → `:aos-sdk/imaging/` compresses → `mediaUploader` uploads via Sendbird's file API |
| Foreground service while user is in the chat room | Not needed — Sendbird's SDK maintains its own websocket. Foreground service only required for active VoIP calls (out of PRD scope) |

---

## 7. Compliance: Message Archival

Loan-officer ↔ customer messages must be archivable for KYC/AML retention windows. **Mobile does not export.** Server-side webhook (Sendbird configurable) sends every message to the bank's archival store. Mobile's only responsibility is sending; deletion/retention is server-side.

**Implication for the contract:** `ChatRepository` does not expose `deleteMessage()` to the UI. If a user "deletes" a message, it's a soft-hide in the local cache; the server keeps the original.

---

## 8. Vendor Substitution Plan (Backup: GetStream)

If Sendbird becomes unsuitable (cost, regional availability, regulatory pushback), the migration path is:

1. Add `:data/chat/GetStreamChatRepo` implementing the same `ChatRepository` interface.
2. Swap the Hilt binding in `DataModule`: `@Binds chatRepo(impl: GetStreamChatRepo): ChatRepository`.
3. Update `RuntimeConfig.thirdPartyAppIds.sendbird` → `.getStream` (or add both during rollout).
4. `:features-support-chat` and `:core` see no change.

This is the architectural payoff of the three-layer split. The migration is a `:data/chat/` rewrite plus a one-line DI swap.

---

## 9. What Does NOT Belong Here

| ❌ Not in this module | ✅ Belongs in |
|---|---|
| Sendbird types in `:features-support-chat` or any ViewModel | `:data/chat/SendbirdChatRepo` only |
| Sendbird app-id in `BuildConfig` | `RuntimeConfig.thirdPartyAppIds` (MG-sourced) |
| Per-tenant message routing in `ChatRepository` | The server scopes per user; the client is tenant-blind |
| The chatbot's NLP/LLM logic | `:features-chatbot` (separate product, separate module) |
| Persistent local message storage of officer-side messages | The server is source of truth; local Room cache is read-through, not authoritative |

---

## 10. Cross-references

- The DI binding pattern: [05 — `:data`](05-data.md) (`DataModule` includes the chat binding)
- The push channel routing: [21 — Push Channels](21-push-channels.md)
- The chatbot module (different product): [07 — `:tenants:*` § 11](07-variants.md)
- The MG-sourced app-ids contract: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The local cache used for offline message read: [29 — Local Database](29-local-database.md)
