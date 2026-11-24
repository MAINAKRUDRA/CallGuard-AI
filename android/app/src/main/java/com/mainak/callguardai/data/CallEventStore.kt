package com.mainak.callguardai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.charset.StandardCharsets

data class CallEvent(
    val id: Long,
    val timestamp: Long,
    val number: String,
    val action: String,
    val reason: String
)

class CallEventStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "callguard.db", null, DATABASE_VERSION
) {
    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        createEncryptedTable(db, TABLE_NAME)
        db.execSQL("CREATE INDEX index_call_events_timestamp ON $TABLE_NAME(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 already contains call_events; version 2 adds the timestamp index.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_call_events_timestamp ON $TABLE_NAME(timestamp DESC)")
        }
        if (oldVersion < 3) {
            migratePlaintextHistoryToEncrypted(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        prune(db)
    }

    fun insert(timestamp: Long, number: String?, action: String, reason: String) {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("number_cipher", HistoryCrypto.encrypt(appContext, (number ?: "Unavailable").toByteArray(StandardCharsets.UTF_8)))
            put("action_cipher", HistoryCrypto.encrypt(appContext, action.toByteArray(StandardCharsets.UTF_8)))
            put("reason_cipher", HistoryCrypto.encrypt(appContext, reason.toByteArray(StandardCharsets.UTF_8)))
        }
        writableDatabase.insertOrThrow(TABLE_NAME, null, values)
        prune(writableDatabase)
    }

    fun latest(limit: Int = 50): List<CallEvent> {
        val safeLimit = limit.coerceIn(1, MAX_EVENTS)
        val events = mutableListOf<CallEvent>()
        readableDatabase.query(
            TABLE_NAME,
            arrayOf("id", "timestamp", "number_cipher", "action_cipher", "reason_cipher"),
            null, null, null, null, "timestamp DESC", safeLimit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events += CallEvent(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    HistoryCrypto.decrypt(appContext, cursor.getBlob(2)).toString(StandardCharsets.UTF_8),
                    HistoryCrypto.decrypt(appContext, cursor.getBlob(3)).toString(StandardCharsets.UTF_8),
                    HistoryCrypto.decrypt(appContext, cursor.getBlob(4)).toString(StandardCharsets.UTF_8)
                )
            }
        }
        return events
    }

    fun clearHistory() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }

    private fun migratePlaintextHistoryToEncrypted(db: SQLiteDatabase) {
        data class LegacyEvent(
            val id: Long,
            val timestamp: Long,
            val number: String,
            val action: String,
            val reason: String
        )

        val legacyRows = mutableListOf<LegacyEvent>()
        db.query(
            TABLE_NAME,
            arrayOf("id", "timestamp", "number", "action", "reason"),
            null, null, null, null, "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                legacyRows += LegacyEvent(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4)
                )
            }
        }

        // Keep the original table intact until every row has been encrypted and copied.
        db.execSQL("DROP INDEX IF EXISTS index_call_events_timestamp")
        createEncryptedTable(db, MIGRATION_TABLE)
        legacyRows.forEach { row ->
            val values = ContentValues().apply {
                put("id", row.id)
                put("timestamp", row.timestamp)
                put("number_cipher", HistoryCrypto.encrypt(appContext, row.number.toByteArray(StandardCharsets.UTF_8)))
                put("action_cipher", HistoryCrypto.encrypt(appContext, row.action.toByteArray(StandardCharsets.UTF_8)))
                put("reason_cipher", HistoryCrypto.encrypt(appContext, row.reason.toByteArray(StandardCharsets.UTF_8)))
            }
            db.insertOrThrow(MIGRATION_TABLE, null, values)
        }
        db.execSQL("DROP TABLE $TABLE_NAME")
        db.execSQL("ALTER TABLE $MIGRATION_TABLE RENAME TO $TABLE_NAME")
        db.execSQL("CREATE INDEX index_call_events_timestamp ON $TABLE_NAME(timestamp DESC)")
    }

    private fun createEncryptedTable(db: SQLiteDatabase, table: String) {
        db.execSQL("""
            CREATE TABLE $table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                number_cipher BLOB NOT NULL,
                action_cipher BLOB NOT NULL,
                reason_cipher BLOB NOT NULL
            )
        """.trimIndent())
    }

    private fun prune(db: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        db.delete(TABLE_NAME, "timestamp < ?", arrayOf(cutoff.toString()))
        db.execSQL("""
            DELETE FROM $TABLE_NAME
            WHERE id NOT IN (
                SELECT id FROM $TABLE_NAME ORDER BY timestamp DESC LIMIT $MAX_EVENTS
            )
        """.trimIndent())
    }

    private companion object {
        const val TABLE_NAME = "call_events"
        const val MIGRATION_TABLE = "call_events_encrypted_migration"
        const val DATABASE_VERSION = 3
        const val MAX_EVENTS = 1000
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
