package com.mainak.callguardai

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mainak.callguardai.data.CallEventStore
import com.mainak.callguardai.data.SecureRuleStore
import com.mainak.callguardai.screening.VipRingerOverride
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class LifecycleAndRecoveryInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { input -> input.readBytes() }
        descriptor.close()
    }

    @Test
    fun revokingContactsAccessDisablesVipOverrideOnNextResume() {
        val permission = Manifest.permission.READ_CONTACTS
        val wasGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val packageName = context.packageName
        try {
            shell("pm revoke $packageName $permission")
            VipRingerOverride.setEnabled(context, true)

            ActivityScenario.launch(MainActivity::class.java).use {
                instrumentation.waitForIdleSync()
                assertFalse(
                    "VIP override must fail closed when its required Contacts permission is revoked",
                    VipRingerOverride.isEnabled(context)
                )
                onView(withText("Enable VIP ring override")).check(matches(isDisplayed()))
            }
        } finally {
            if (wasGranted) shell("pm grant $packageName $permission")
            else shell("pm revoke $packageName $permission")
            VipRingerOverride.setEnabled(context, false)
        }
    }

    @Test
    fun biometricLockRemainsEnabledAcrossActivityRecreationAndProcessRestart() {
        val security = context.getSharedPreferences("callguard_app_security", 0)
        val original = security.getBoolean("biometric_lock_enabled", false)
        security.edit().putBoolean("biometric_lock_enabled", true).commit()
        try {
            val firstLaunch = ActivityScenario.launch(MainActivity::class.java)
            instrumentation.waitForIdleSync()
            onView(withText("CALLGUARD AI\\n\\nApp locked\\nAuthenticate with your device biometric to continue."))
                .check(matches(isDisplayed()))

            firstLaunch.recreate()
            instrumentation.waitForIdleSync()
            onView(withText("CALLGUARD AI\\n\\nApp locked\\nAuthenticate with your device biometric to continue."))
                .check(matches(isDisplayed()))
            firstLaunch.close()

            // Force-stop the target app process, then launch a fresh process.
            shell("am force-stop ${context.packageName}")
            ActivityScenario.launch(MainActivity::class.java).use {
                instrumentation.waitForIdleSync()
                onView(withText("CALLGUARD AI\\n\\nApp locked\\nAuthenticate with your device biometric to continue."))
                    .check(matches(isDisplayed()))
                assertTrue(security.getBoolean("biometric_lock_enabled", false))
            }
        } finally {
            security.edit().putBoolean("biometric_lock_enabled", original).commit()
        }
    }

    @Test
    fun clearingHistoryDoesNotDeleteSavedRules() {
        val rules = SecureRuleStore(context)
        val number = "+919876543210"
        rules.removeAllow(number)
        assertTrue(rules.addAllow(number))
        context.deleteDatabase("callguard.db")
        val history = CallEventStore(context)
        try {
            history.insert(System.currentTimeMillis(), "••••3210", "ALLOW", "clear-ui-regression")
        } finally {
            history.close()
        }

        val security = context.getSharedPreferences("callguard_app_security", 0)
        val originalLock = security.getBoolean("biometric_lock_enabled", false)
        security.edit().putBoolean("biometric_lock_enabled", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText("Clear activity history")).perform(scrollTo(), click())
                onView(withText("Clear history")).perform(click())
                onView(withText("Local activity history cleared.")).check(matches(isDisplayed()))
            }
            val verifyHistory = CallEventStore(context)
            try {
                assertTrue(verifyHistory.latest(10).isEmpty())
            } finally {
                verifyHistory.close()
            }
            assertTrue("Clearing history must preserve allow/block rules", number in rules.allowed())
        } finally {
            rules.removeAllow(number)
            security.edit().putBoolean("biometric_lock_enabled", originalLock).commit()
        }
    }

    @Test
    fun dashboardRemainsAvailableAfterLandscapeRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            instrumentation.waitForIdleSync()
            onView(withText("Manage a phone number")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Recent screening activity")).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
