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

class CallEventStore(context: Context) : SQLiteOpenHelper(context, "callguard.db", null, 1) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit, forward-only migrations here before incrementing DB version.
    }

    fun insert(timestamp: Long, number: String?, action: String, reason: String) {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("number", number ?: "Unavailable")
            put("action", action)
            put("reason", reason)
        }
        writableDatabase.insert("call_events", null, values)
    }

    fun latest(limit: Int = 50): List<CallEvent> {
        val events = mutableListOf<CallEvent>()
        readableDatabase.query(
            "call_events",
            arrayOf("id", "timestamp", "number", "action", "reason"),
            null, null, null, null, "timestamp DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events += CallEvent(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4)
                )
            }
        }
        return events
    }

    fun clearHistory() {
        writableDatabase.delete("call_events", null, null)
    }
}
