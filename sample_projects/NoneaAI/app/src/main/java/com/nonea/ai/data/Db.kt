package com.nonea.ai.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" | "assistant"
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val convId: Long = 1,
    /** true when the user spoke this message (shown with a mic badge in history). */
    @ColumnInfo(defaultValue = "0") val voice: Boolean = false
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val ts: Long = System.currentTimeMillis()
)

/** A fact/preference the user explicitly approved Nonea to remember. */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE convId = :c ORDER BY ts ASC, id ASC") fun forConv(c: Long): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE convId = :c ORDER BY ts DESC, id DESC LIMIT :n") suspend fun recentInConv(c: Long, n: Int): List<MessageEntity>
    /** The user's own earlier messages from other chats (used for cross-conversation suggestions). */
    @Query("SELECT * FROM messages WHERE role = 'user' AND convId != :c ORDER BY ts DESC, id DESC LIMIT :n") suspend fun recentUserElsewhere(c: Long, n: Int): List<MessageEntity>
    @Insert suspend fun insert(m: MessageEntity)
    @Query("DELETE FROM messages WHERE convId = :c") suspend fun deleteConv(c: Long)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY ts DESC, id DESC") fun all(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations ORDER BY ts DESC, id DESC LIMIT 1") suspend fun latest(): ConversationEntity?
    @Insert suspend fun insert(c: ConversationEntity): Long
    @Query("UPDATE conversations SET ts = :ts WHERE id = :id") suspend fun touch(id: Long, ts: Long)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM conversations") suspend fun clear()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY ts DESC, id DESC") fun all(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY ts DESC, id DESC") suspend fun list(): List<MemoryEntity>
    @Insert suspend fun insert(m: MemoryEntity)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM memories") suspend fun clear()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memories` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `ts` INTEGER NOT NULL)"
        )
    }
}

/** Adds conversations; all pre-existing messages become one "Earlier chat" (id 1). */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversations` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `ts` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `convId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "INSERT INTO `conversations` (`id`, `title`, `ts`) " +
                "SELECT 1, 'Earlier chat', ${System.currentTimeMillis()} WHERE EXISTS (SELECT 1 FROM `messages`)"
        )
    }
}

/** Voice assistant: remembers which user messages were spoken. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `voice` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [MessageEntity::class, ConversationEntity::class, MemoryEntity::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun memories(): MemoryDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "nonea.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { inst = it }
        }
    }
}
