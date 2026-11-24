package com.mainak.callguardai.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizationTest {
    @Test fun preservesInternationalPrefixAndDigits() {
        assertEquals("+919876543210", CallGuardPolicy.normalizeNumber("+91 98765-43210"))
    }
    @Test fun removesFormattingFromLocalNumber() {
        assertEquals("9876543210", CallGuardPolicy.normalizeNumber("(98765) 43210"))
    }
    @Test fun returnsNullWhenNoDigitsExist() {
        assertNull(CallGuardPolicy.normalizeNumber("unknown"))
    }
}
