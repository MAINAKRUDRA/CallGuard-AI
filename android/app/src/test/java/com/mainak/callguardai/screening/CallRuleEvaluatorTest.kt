package com.mainak.callguardai.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRuleEvaluatorTest {
    private val vip = "+919876543210"
    private val blocked = "+919876543211"

    @Test fun explicitVipIsAllowed() {
        val result = CallRuleEvaluator.evaluate(vip, setOf(vip), emptySet())
        assertEquals(ScreeningAction.ALLOW, result.action)
        assertEquals("allow", result.matchedRule)
        assertEquals("Explicit allow rule", result.reason)
    }

    @Test fun vipAllowRuleTakesPriorityOverBlockRule() {
        val result = CallRuleEvaluator.evaluate(vip, setOf(vip), setOf(vip))
        assertEquals(ScreeningAction.ALLOW, result.action)
        assertEquals("allow", result.matchedRule)
    }

    @Test fun explicitBlockRuleRejectsCaller() {
        val result = CallRuleEvaluator.evaluate(blocked, emptySet(), setOf(blocked))
        assertEquals(ScreeningAction.REJECT, result.action)
        assertEquals("block", result.matchedRule)
        assertEquals("Explicit user block rule", result.reason)
    }

    @Test fun unknownCallerIsAllowedBySafeDefault() {
        val result = CallRuleEvaluator.evaluate("+919876543212", setOf(vip), setOf(blocked))
        assertEquals(ScreeningAction.ALLOW, result.action)
        assertNull(result.matchedRule)
        assertEquals("No matching rule; allowed by safe default", result.reason)
    }

    @Test fun missingCallerIdIsAllowedBySafeDefault() {
        val result = CallRuleEvaluator.evaluate(null, setOf(vip), setOf(blocked))
        assertEquals(ScreeningAction.ALLOW, result.action)
        assertNull(result.matchedRule)
        assertEquals("Caller ID unavailable or invalid; allowed by safe default", result.reason)
    }

    @Test fun blankCallerIdIsAllowedBySafeDefault() {
        val result = CallRuleEvaluator.evaluate("  ", setOf(vip), setOf(blocked))
        assertEquals(ScreeningAction.ALLOW, result.action)
        assertNull(result.matchedRule)
    }
}
