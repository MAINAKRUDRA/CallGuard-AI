package com.mainak.callguardai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CallEvent(
    val id: Long,
    val timestamp: Long,
    val number: String,
    val action: String,
    val reason: String
)

/**
 * App-private call activity history.
 *
 * Timestamps remain queryable for sorting/retention. Caller labels, actions and
 * reasons are AES-256-GCM encrypted with a non-exportable Android Keystore key.
 * Schema v3 migrates existing plaintext v2 rows inside SQLiteOpenHelper's
 * upgrade transaction; a failed migration is not silently discarded.
 */
class CallEventStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "callguard.db", null, DATABASE_VERSION) {
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
        // Version 1 already contains call_events; version 2 added the timestamp index.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_call_events_timestamp ON call_events(timestamp DESC)")
        }
        if (oldVersion < 3) {
            encryptExistingRows(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        prune(db)
    }

    @Synchronized
    fun insert(timestamp: Long, number: String?, action: String, reason: String) {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("number", encrypt(number ?: "Unavailable"))
            put("action", encrypt(action))
            put("reason", encrypt(reason))
        }
        writableDatabase.insertOrThrow("call_events", null, values)
        prune(writableDatabase)
    }

    @Synchronized
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
                    cursor.getLong(0),
                    cursor.getLong(1),
                    decrypt(cursor.getString(2)),
                    decrypt(cursor.getString(3)),
                    decrypt(cursor.getString(4))
                )
            }
        }
        return events
    }

    @Synchronized
    fun clearHistory() {
        writableDatabase.delete("call_events", null, null)
    }

    private fun encryptExistingRows(db: SQLiteDatabase) {
        db.query(
            "call_events",
            arrayOf("id", "number", "action", "reason"),
            null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val values = ContentValues().apply {
                    put("number", encryptIfNeeded(cursor.getString(1)))
                    put("action", encryptIfNeeded(cursor.getString(2)))
                    put("reason", encryptIfNeeded(cursor.getString(3)))
                }
                db.update("call_events", values, "id = ?", arrayOf(id.toString()))
            }
        }
    }

    private fun encryptIfNeeded(value: String): String =
        if (value.startsWith(ENCRYPTED_PREFIX)) value else encrypt(value)

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv +
            cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        require(stored.startsWith(ENCRYPTED_PREFIX)) { "Call history contains an unencrypted value" }
        val payload = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Empty encrypted call-history value" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) { "Invalid encrypted call-history value" }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
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
        const val DATABASE_VERSION = 3
        const val MAX_EVENTS = 1000
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        const val KEY_ALIAS = "callguard.history.aes256.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFIX = "enc:"
    }
}
