package com.mainak.callguardai

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mainak.callguardai.data.SecureRuleStore
import com.mainak.callguardai.screening.CallGuardPolicy
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleManagementInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testNumber = "+919876543210"

    @Test
    fun allowAndBlockActionsPersistNormalizedRules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureRuleStore(context)
        store.removeAllow(testNumber)
        store.removeBlock(testNumber)

        onView(withHint("+91 98765 43210"))
            .perform(scrollTo(), replaceText("9876543210"))
        onView(withText("Allow / VIP")).perform(scrollTo(), click())
        onView(withText("Number added to your VIP allow-list.")).check(matches(isDisplayed()))
        assertTrue(testNumber in store.allowed())

        onView(withHint("+91 98765 43210"))
            .perform(scrollTo(), replaceText("9876543210"))
        onView(withText("Block number")).perform(scrollTo(), click())
        onView(withText("Number blocked. VIP allow rules still take priority.")).check(matches(isDisplayed()))
        assertTrue(testNumber in store.blocked())

        store.removeAllow(testNumber)
        store.removeBlock(testNumber)
    }

    @Test
    fun savedAllowRuleCanBeRemovedFromManagementDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureRuleStore(context)
        store.removeAllow(testNumber)
        assertTrue(CallGuardPolicy.addAllow(context, testNumber))

        onView(withContentDescription("Saved allow and block rules. Activate to manage rules."))
            .perform(scrollTo(), click())
        onView(withText(startsWith("Remove VIP:"))).perform(click())

        assertFalse(testNumber in store.allowed())
    }

    @Test
    fun savedBlockRuleCanBeRemovedFromManagementDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureRuleStore(context)
        store.removeBlock(testNumber)
        assertTrue(CallGuardPolicy.addBlock(context, testNumber))

        onView(withContentDescription("Saved allow and block rules. Activate to manage rules."))
            .perform(scrollTo(), click())
        onView(withText(startsWith("Unblock:"))).perform(click())

        assertFalse(testNumber in store.blocked())
    }
    @Test
    fun supportedPhoneRegionCanBeSavedAndInvalidRegionIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalRegion = CallGuardPolicy.selectedRegion(context)
        try {
            onView(withHint("IN"))
                .perform(scrollTo(), replaceText("US"))
            onView(withText("Save phone region")).perform(scrollTo(), click())
            onView(withText("Phone region saved as US.")).check(matches(isDisplayed()))
            assertTrue(CallGuardPolicy.selectedRegion(context) == "US")

            onView(withHint("IN"))
                .perform(scrollTo(), replaceText("ZZ"))
            onView(withText("Save phone region")).perform(scrollTo(), click())
            onView(withText("Enter a supported two-letter country code, such as IN or US."))
                .check(matches(isDisplayed()))
            assertTrue(CallGuardPolicy.selectedRegion(context) == "US")
        } finally {
            CallGuardPolicy.setRegion(context, originalRegion)
        }
    }

    @Test
    fun invalidNumberDoesNotCreateAnAllowRule() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureRuleStore(context)
        store.removeAllow(testNumber)

        onView(withHint("+91 98765 43210"))
            .perform(scrollTo(), replaceText("123"))
        onView(withText("Allow / VIP")).perform(scrollTo(), click())
        onView(withText("Enter a valid phone number for the selected region."))
            .check(matches(isDisplayed()))
        assertFalse(testNumber in store.allowed())
    }
}
