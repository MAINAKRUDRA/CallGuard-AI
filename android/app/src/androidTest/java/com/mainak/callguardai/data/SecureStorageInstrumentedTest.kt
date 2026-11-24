package com.mainak.callguardai.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mainak.callguardai.screening.PhoneNumberCanonicalizer
import com.mainak.callguardai.screening.CallGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedRulesRoundTripAndLegacyMigration() {
        context.getSharedPreferences("callguard_secure_rules", 0).edit().clear().commit()
        context.getSharedPreferences("callguard_rules", 0).edit().clear().commit()
        val legacy = context.getSharedPreferences("callguard_rules", 0)
        legacy.edit().putStringSet("allow_numbers", setOf("+919876543210")).commit()

        val store = SecureRuleStore(context)
        assertTrue(store.allowed().contains("+919876543210"))
        assertTrue(legacy.getStringSet("allow_numbers", emptySet()).isNullOrEmpty())
        assertTrue(store.addBlock("+442079460018"))
        assertEquals(setOf("+442079460018"), store.blocked())

        val ciphertext = context.getSharedPreferences("callguard_secure_rules", 0)
            .getString("rules_aes_gcm", "") ?: ""
        assertFalse(ciphertext.contains("+919876543210"))
        assertFalse(ciphertext.contains("+442079460018"))
        assertEquals(setOf("+919876543210"), SecureRuleStore(context).allowed())
    }

    @Test fun localAndInternationalNumbersCanonicalizeToSameE164() {
        assertEquals(
            PhoneNumberCanonicalizer.canonicalize("9876543210", "IN"),
            PhoneNumberCanonicalizer.canonicalize("+91 98765 43210", "US")
        )
    }

    @Test fun selectedCountryRegionIsValidatedAndPersists() {
        context.getSharedPreferences("callguard_settings", 0).edit().clear().commit()
        assertTrue(CallGuardPolicy.setRegion(context, "IN"))
        assertEquals("IN", CallGuardPolicy.selectedRegion(context))
        assertFalse(CallGuardPolicy.setRegion(context, "XX"))
        assertEquals("IN", CallGuardPolicy.selectedRegion(context))
    }

    @Test fun callHistoryRetentionDropsExpiredEvents() {
        context.deleteDatabase("callguard.db")
        val store = CallEventStore(context)
        try {
            store.insert(System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000, "••••3210", "ALLOW", "old")
            store.insert(System.currentTimeMillis(), "••••4321", "ALLOW", "current")
            assertEquals(1, store.latest(10).size)
            assertEquals("current", store.latest(10).first().reason)
        } finally {
            store.close()
        }
    }

    @Test fun callHistoryIsCappedAtOneThousandAndCanBeCleared() {
        context.deleteDatabase("callguard.db")
        val store = CallEventStore(context)
        try {
            repeat(1_005) { index ->
                store.insert(
                    System.currentTimeMillis() + index,
                    "••••" + (1000 + index).toString().takeLast(4),
                    "ALLOW",
                    "test-event"
                )
            }
            assertEquals(1_000, store.latest(1_000).size)
            store.clearHistory()
            assertTrue(store.latest(1_000).isEmpty())
        } finally {
            store.close()
        }
    }

    @Test fun versionTwoHistoryMigratesWithoutPlaintextRemaining() {
        context.deleteDatabase("callguard.db")
        val legacyDb = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("callguard.db"), null)
        legacyDb.execSQL("""
            CREATE TABLE call_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                number TEXT NOT NULL,
                action TEXT NOT NULL,
                reason TEXT NOT NULL
            )
        """.trimIndent())
        legacyDb.execSQL("CREATE INDEX index_call_events_timestamp ON call_events(timestamp DESC)")
        legacyDb.insertOrThrow("call_events", null, ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("number", "••••9876")
            put("action", "ALLOW")
            put("reason", "migration-check")
        })
        legacyDb.version = 2
        legacyDb.close()

        val store = CallEventStore(context)
        try {
            val migrated = store.latest(10).single()
            assertEquals("••••9876", migrated.number)
            assertEquals("ALLOW", migrated.action)
            assertEquals("migration-check", migrated.reason)
        } finally {
            store.close()
        }

        val encryptedDb = SQLiteDatabase.openDatabase(
            context.getDatabasePath("callguard.db").absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            encryptedDb.rawQuery("SELECT number_cipher, action_cipher, reason_cipher FROM call_events", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                for (index in 0..2) {
                    val value = String(cursor.getBlob(index), StandardCharsets.UTF_8)
                    assertFalse(value.contains("••••9876"))
                    assertFalse(value.contains("migration-check"))
                }
            }
        } finally {
            encryptedDb.close()
        }
    }
}
