package com.mainak.callguardai.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mainak.callguardai.screening.PhoneNumberCanonicalizer
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
}
