# 29 · Local Database (Room + SQLCipher)

> **Storage:** Encrypted SQLite via **Room** + **SQLCipher**. Passphrase derived from a Keystore-resident key.
> **Where it lives:** `:aos-sdk/storage/EncryptedDatabase` (wrapper) + per-feature DAOs in the consuming modules.
> **Used for:** chat history cache, draft applications, branch list cache, notification inbox, repayment schedule offline view.
> **NOT used for:** auth tokens (those stay in `EncryptedPrefs`), PDFs (those go to `Context.filesDir`).

---

## 1. Why Room + SQLCipher

`EncryptedSharedPreferences` is the framework's existing key-value secure store. It's right for a handful of keyed values (tokens, PIN hash, FCM token). It's wrong for lists, queryable data, and anything that needs schema migration. Storing a 200-row chat history in SharedPreferences is the textbook antipattern.

**Room** is the standard structured-data answer; **SQLCipher** encrypts the underlying SQLite file with AES-256. The combination is what every regulated mobile app needs.

The `:aos-sdk/storage/EncryptedDatabase` wrapper handles the Keystore-derived passphrase, Room database building, and integration with the rest of the SDK — so per-feature DAOs can be declared trivially.

---

## 2. The Wrapper

```kotlin
// :aos-sdk/storage/EncryptedDatabase.kt
class EncryptedDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) {
    /**
     * Lazily build a Room database with SQLCipher encryption.
     * The passphrase is derived from a Keystore-bound AES key — never persisted as a string.
     */
    inline fun <reified DB : RoomDatabase> build(
        name: String,
        migrations: Array<Migration> = emptyArray(),
        crossinline configure: RoomDatabase.Builder<DB>.() -> Unit = {},
    ): DB {
        val passphrase = derivePassphrase(name)
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, DB::class.java, name)
            .openHelperFactory(factory)
            .addMigrations(*migrations)
            .apply(configure)
            .build()
    }

    private fun derivePassphrase(databaseName: String): ByteArray {
        // Use Keystore-resident HMAC to deterministically derive a 32-byte passphrase
        // from a per-database key alias. The passphrase is never stored; it is re-derived per process.
        val keyAlias = "compass.db.$databaseName"
        val key = keystoreManager.getOrCreateHmacKey(keyAlias)
        return hmacSha256(key, databaseName.toByteArray()).copyOf(32)
    }
}
```

`SupportFactory` is from `net.zetetic:android-database-sqlcipher`. (The newer `net.sqlcipher:sqlcipher-android` is also acceptable; pick the actively maintained variant.)

**Why derive the passphrase rather than store it?** Storing it requires another secure-store layer (turtles all the way down). Deriving it from a Keystore-resident HMAC key means the passphrase exists only as a process-lifetime `ByteArray`; even a forensic disk dump cannot recover it without root + Keystore extraction.

---

## 3. The Feature DAO Pattern

Each feature owns its DAO + entity classes in its own module. The wrapper is shared.

```kotlin
// :data/chat/db/ChatDatabase.kt
@Database(entities = [ChatMessageEntity::class, ChatThreadEntity::class], version = 1)
internal abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): ChatMessageDao
    abstract fun threadDao(): ChatThreadDao
}

// :data/chat/db/ChatMessageEntity.kt
@Entity(
    tableName = "chat_messages",
    indices = [Index("threadId"), Index("sentAt")],
)
internal data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val senderType: String,         // "Customer" | "Officer"
    val sentAtEpochMs: Long,
    val contentType: String,        // "Text" | "Image" | …
    val contentJson: String,        // serialized ChatMessageContent
    val readAtEpochMs: Long?,
)

@Dao
internal interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY sentAtEpochMs ASC")
    fun observe(threadId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)
}

// :data/chat/di/ChatDatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal object ChatDatabaseModule {
    @Provides @Singleton
    fun chatDatabase(encryptedDatabase: EncryptedDatabase): ChatDatabase =
        encryptedDatabase.build(name = "chat.db")

    @Provides fun messageDao(db: ChatDatabase): ChatMessageDao = db.messageDao()
    @Provides fun threadDao(db: ChatDatabase): ChatThreadDao = db.threadDao()
}
```

Each Room database is a separate file on disk — `chat.db`, `branches.db`, `drafts.db`. They share the same encryption key derivation but are independent for migration purposes.

---

## 4. What Gets Cached

| Surface | DB | Cache strategy | TTL |
|---|---|---|---|
| Chat messages | `chat.db` | Read-through (Sendbird is authoritative; cache is for offline display) | Last 30 days; rolling window |
| Branch list (locator) | `branches.db` | Stale-while-revalidate (always serve cache; refresh in background) | Refresh on app cold start + manual pull-to-refresh |
| Draft loan applications | `drafts.db` | Write-through (cache is authoritative until server submission) | Keep until submission or 90 days idle |
| Notification inbox | `notifications.db` | Write-through (FCM payloads stored locally for the inbox view) | Last 100 entries |
| Repayment schedule (current loan) | `loans.db` | Stale-while-revalidate | Refresh on every loan-screen entry |
| Loan products list | `loans.db` | Stale-while-revalidate (mostly static) | 1 day stale-OK; refresh on demand |

---

## 5. What Does NOT Get Cached

| Surface | Why not |
|---|---|
| Auth tokens (bearer, refresh) | Stay in `EncryptedPrefs` — single-key access is the right shape; cross-process reading via SharedPreferences contract is well-defined |
| PIN hash | `EncryptedPrefs` — same rationale |
| FCM device token | `EncryptedPrefs` — single key |
| Loan contracts (PDF) | App-private file storage (`Context.filesDir/pdf/`) — see [24 — PDF](24-pdf.md) |
| KYC captures (pre-upload) | App-private file storage — bitmaps are too large for SQLite |
| Branch map tiles | Google's TOS prohibits caching map tiles |
| User personal data fetched from server (name, photo) | Refresh on every login; do not persist across sessions |

The rule: **session-scoped data does not survive logout.** Logout drops the `LoggedInComponent` (in-memory), clears `EncryptedPrefs.clearSessionScope()`, and **also** clears session-scoped DBs:

```kotlin
// :app/session/LogoutHandler.kt — extended
suspend fun logout() {
    encryptedPrefs.clearSessionScope()
    httpClient.cache?.evictAll()
    chatDatabase.clearAllTables()              // wipes chat history
    draftsDatabase.clearAllTables()            // wipes uncommitted drafts
    notificationsDatabase.clearAllTables()
    // branches.db and loans.db (current-user loan products) are also wiped
    componentManager.drop()
    navigator.navigate(Route.Login, popUpToRoot = true)
}
```

Branch list survives across logouts in some apps (it's public-ish information). The PRD lending product errs on the conservative side — wipe everything on logout. Re-fetch on next login.

---

## 6. Schema Migrations

Room migrations are versioned per database. The rule: **no destructive migration in production**.

```kotlin
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN forwardedFrom TEXT")
    }
}

@Module
internal object ChatDatabaseModule {
    @Provides @Singleton
    fun chatDatabase(encryptedDatabase: EncryptedDatabase): ChatDatabase =
        encryptedDatabase.build<ChatDatabase>(
            name = "chat.db",
            migrations = arrayOf(MIGRATION_1_2),
        )
}
```

Every PR that bumps a Room `@Database(version = N)` MUST include the `Migration(N-1, N)`. CI enforces this via Room's `exportSchema = true` + the `room.schemaLocation` annotation processor option — the JSON schemas are committed; a diff in the schema without a corresponding migration fails the build.

If a migration is genuinely impossible (e.g., a column data-type change SQLite can't perform), the fallback is `fallbackToDestructiveMigration()`. **Never** use this in production for chat or drafts; users would lose their drafts. Use it only for purely-server-backed caches (branches) that can be re-fetched losslessly.

---

## 7. Cross-Process Considerations

The framework runs as a single process. **No multi-process database access is supported.** WorkManager's workers run in the same process as the rest of the app, so they can use the same DAOs via Hilt injection.

If a future feature needs a separate process (e.g., a tile-server for offline maps), that would require a new database with cross-process WAL handling — out of scope today.

---

## 8. Performance Notes

| Concern | Note |
|---|---|
| Encryption overhead | SQLCipher adds ~5-15% overhead on read/write. Imperceptible for typical app workloads. |
| First-open latency | The Keystore HMAC derivation is ~5ms. Acceptable. |
| Large query result sets | Use `Flow<List<T>>` (Room emits on every change); avoid `LiveData<PagingData>` when scope is local-only |
| Background DB access | Always via `Dispatchers.IO`; Room enforces this for `suspend` DAO methods |

---

## 9. What Does NOT Belong Here

| ❌ Wrong | ✅ Right |
|---|---|
| Plain `Room.databaseBuilder` without SQLCipher | Always via `EncryptedDatabase.build()` |
| Storing the SQLCipher passphrase in SharedPreferences (even encrypted) | Derive per-process from Keystore HMAC |
| Cross-feature shared `EncryptedDatabase` | Each feature owns its own `*.db` for migration independence |
| Caching auth tokens in Room | `EncryptedPrefs` (single-key access) |
| `fallbackToDestructiveMigration()` for user-authored data (chat, drafts) | Write the migration |
| DAO methods returning raw `Cursor` | Always typed entities or Flows |

---

## 10. Cross-references

- The `EncryptedPrefs` for single-key secure storage: [02 — `:aos-sdk`](02-aos-core.md) (`storage/`)
- The PDF storage (filesDir, not Room): [24 — PDF](24-pdf.md)
- The KYC bitmap storage (filesDir): [23 — KYC Capture](23-kyc-capture.md)
- The logout flow that wipes session-scoped DBs: [10 — Boot Phases § 6](10-boot-phases.md)
- The Keystore wrapper that derives the passphrase: [02 — `:aos-sdk`](02-aos-core.md) (`security/`)
- The branch-locator that consumes the offline cache: [27 — Maps and Location](27-maps-and-location.md)
- The chat module that consumes `chat.db`: [20 — Chat](20-chat.md)
- The form-wizard that consumes `drafts.db`: [30 — Form Wizard](30-form-wizard.md)
