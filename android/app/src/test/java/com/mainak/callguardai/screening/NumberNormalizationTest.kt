package com.mainak.callguardai.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizationTest {
    @Test fun defaultsUnprefixedNumbersToIndiaAndAddsCountryCode() {
        assertEquals("IN", PhoneNumberCanonicalizer.defaultRegion())
        assertEquals("+919876543210", PhoneNumberCanonicalizer.canonicalize("9876543210"))
    }

    @Test fun formatsIndianLocalNumberAsE164() {
        assertEquals("+919876543210", PhoneNumberCanonicalizer.canonicalize("09876543210", "IN"))
    }

    @Test fun formatsIndianNumberWithoutTrunkPrefixAsE164() {
        assertEquals("+919876543210", PhoneNumberCanonicalizer.canonicalize("9876543210", "IN"))
    }

    @Test fun matchesInternationalNumberRegardlessOfDeviceRegion() {
        assertEquals("+442079460018", PhoneNumberCanonicalizer.canonicalize("+44 20 7946 0018", "IN"))
    }

    @Test fun equivalentCallerPresentationsNormalizeToSameNumber() {
        val compact = PhoneNumberCanonicalizer.canonicalize("+919876543210", "IN")
        val spaced = PhoneNumberCanonicalizer.canonicalize("+91 98765 43210", "IN")
        assertEquals(compact, spaced)
    }

    @Test fun rejectsInvalidNumbers() {
        assertNull(PhoneNumberCanonicalizer.canonicalize("12345", "IN"))
    }

    @Test fun rejectsNonPhoneText() {
        assertNull(PhoneNumberCanonicalizer.canonicalize("unknown", "IN"))
    }

    @Test fun restrictedPrivateAndUnavailableCallerPresentationsAreNotNumbers() {
        listOf("", " ", "-1", "P", "anonymous", "private", "restricted", "unavailable", "unknown").forEach {
            assertNull("Expected no canonical number for presentation: $it",
                PhoneNumberCanonicalizer.canonicalize(it, "IN"))
        }
    }

    @Test fun malformedCallerPresentationIsAllowedBySafeDefault() {
        val number = PhoneNumberCanonicalizer.canonicalize("not-a-phone-number", "IN")
        val decision = CallRuleEvaluator.evaluate(number, emptySet(), setOf("+919876543210"))
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertNull(decision.matchedRule)
    }

    @Test fun restrictedCallerPresentationIsAllowedBySafeDefault() {
        val number = PhoneNumberCanonicalizer.canonicalize("restricted", "IN")
        val decision = CallRuleEvaluator.evaluate(number, emptySet(), emptySet())
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertNull(decision.matchedRule)
    }
}
