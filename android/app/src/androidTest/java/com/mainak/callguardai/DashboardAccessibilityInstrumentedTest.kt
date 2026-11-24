package com.mainak.callguardai

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardAccessibilityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun dashboardShowsSummaryAndPrimaryRuleActions() {
        onView(withText("VIP / ALLOWED")).check(matches(isDisplayed()))
        onView(withText("BLOCKED")).check(matches(isDisplayed()))
        onView(withText("ACTIVITY")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Allow / VIP")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Block number")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun preferenceSwitchesExposeDescriptiveAccessibilityLabels() {
        onView(withContentDescription("Enable or disable the optional VIP ringer and Do Not Disturb override"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))

        onView(withContentDescription("Require a supported strong device biometric before opening CallGuard"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }
}
