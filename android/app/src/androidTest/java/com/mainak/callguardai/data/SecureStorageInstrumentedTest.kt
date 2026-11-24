package com.mainak.callguardai.data

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
            store.readableDatabase.rawQuery(
                "SELECT number, action, reason FROM call_events", null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("enc:"))
                assertTrue(cursor.getString(1).startsWith("enc:"))
                assertTrue(cursor.getString(2).startsWith("enc:"))
                assertFalse(cursor.getString(2).contains("current"))
            }
        } finally {
            store.close()
        }
    }
    @Test fun plaintextVersionTwoHistoryMigratesToEncryptedRows() {
        context.deleteDatabase("callguard.db")
        val databaseFile = context.getDatabasePath("callguard.db")
        databaseFile.parentFile?.mkdirs()
        val legacyDb = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacyDb.execSQL("""
            CREATE TABLE call_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                number TEXT NOT NULL,
                action TEXT NOT NULL,
                reason TEXT NOT NULL
            )
        """.trimIndent())
        legacyDb.execSQL(
            "INSERT INTO call_events(timestamp, number, action, reason) VALUES(?, ?, ?, ?)",
            arrayOf(System.currentTimeMillis(), "••••3210", "ALLOW", "legacy VIP allow")
        )
        legacyDb.version = 2
        legacyDb.close()

        val store = CallEventStore(context)
        try {
            val migrated = store.latest(10).single()
            assertEquals("••••3210", migrated.number)
            assertEquals("ALLOW", migrated.action)
            assertEquals("legacy VIP allow", migrated.reason)
            store.readableDatabase.rawQuery(
                "SELECT number, action, reason FROM call_events", null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("enc:"))
                assertTrue(cursor.getString(1).startsWith("enc:"))
                assertTrue(cursor.getString(2).startsWith("enc:"))
                assertFalse(cursor.getString(2).contains("legacy VIP allow"))
            }
        } finally {
            store.close()
        }
    }

}
