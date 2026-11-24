package com.mainak.callguardai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CallEvent(
    val id: Long,
    val timestamp: Long,
    val number: String,
    val action: String,
    val reason: String
)

class CallEventStore(context: Context) : SQLiteOpenHelper(context, "callguard.db", null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE call_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                number TEXT NOT NULL,
                action TEXT NOT NULL,
                reason TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_call_events_timestamp ON call_events(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Forward-only migrations. Version 1 already contains call_events.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_call_events_timestamp ON call_events(timestamp DESC)")
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        prune(db)
    }

    fun insert(timestamp: Long, number: String?, action: String, reason: String) {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("number", number ?: "Unavailable")
            put("action", action)
            put("reason", reason)
        }
        writableDatabase.insertOrThrow("call_events", null, values)
        prune(writableDatabase)
    }

    fun latest(limit: Int = 50): List<CallEvent> {
        val safeLimit = limit.coerceIn(1, MAX_EVENTS)
        val events = mutableListOf<CallEvent>()
        readableDatabase.query(
            "call_events",
            arrayOf("id", "timestamp", "number", "action", "reason"),
            null, null, null, null, "timestamp DESC", safeLimit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events += CallEvent(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4)
                )
            }
        }
        return events
    }

    fun clearHistory() {
        writableDatabase.delete("call_events", null, null)
    }

    private fun prune(db: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        db.delete("call_events", "timestamp < ?", arrayOf(cutoff.toString()))
        db.execSQL("""
            DELETE FROM call_events
            WHERE id NOT IN (
                SELECT id FROM call_events ORDER BY timestamp DESC LIMIT $MAX_EVENTS
            )
        """.trimIndent())
    }

    private companion object {
        const val DATABASE_VERSION = 2
        const val MAX_EVENTS = 1000
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
