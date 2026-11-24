package com.mainak.callguardai.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizationTest {
    @Test fun formatsIndianLocalNumberAsE164() {
        assertEquals("+919876543210", PhoneNumberCanonicalizer.canonicalize("09876543210", "IN"))
    }

    @Test fun formatsIndianNumberWithoutTrunkPrefixAsE164() {
        assertEquals("+919876543210", PhoneNumberCanonicalizer.canonicalize("9876543210", "IN"))
    }

    @Test fun matchesInternationalNumberRegardlessOfDeviceRegion() {
        assertEquals("+442079460018", PhoneNumberCanonicalizer.canonicalize("+44 20 7946 0018", "IN"))
    }

    @Test fun rejectsInvalidNumbers() {
        assertNull(PhoneNumberCanonicalizer.canonicalize("12345", "IN"))
    }

    @Test fun rejectsNonPhoneText() {
        assertNull(PhoneNumberCanonicalizer.canonicalize("unknown", "IN"))
    }
}
