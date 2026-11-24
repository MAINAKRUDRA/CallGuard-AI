package com.mainak.callguardai

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
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
import org.junit.Assume.assumeTrue
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
    fun revokingContactsAccessWhileBackgroundedDisablesVipOverrideOnResume() {
        val contacts = Manifest.permission.READ_CONTACTS
        val phoneState = Manifest.permission.READ_PHONE_STATE
        val packageName = context.packageName
        val contactsWasGranted = context.checkSelfPermission(contacts) == PackageManager.PERMISSION_GRANTED
        val phoneWasGranted = context.checkSelfPermission(phoneState) == PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndWasGranted = notificationManager.isNotificationPolicyAccessGranted
        try {
            if (!contactsWasGranted) shell("pm grant $packageName $contacts")
            if (!phoneWasGranted) shell("pm grant $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification allow_dnd $packageName")
            assumeTrue(
                "Emulator must grant Contacts, Phone state, and DND access for this revocation test",
                VipRingerOverride.hasRequiredAccess(context)
            )

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                VipRingerOverride.setEnabled(context, true)
                scenario.moveToState(Lifecycle.State.CREATED)
                shell("pm revoke $packageName $contacts")
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()

                assertFalse(
                    "VIP override must fail closed after Contacts permission is revoked",
                    VipRingerOverride.isEnabled(context)
                )
                onView(withText("Enable VIP ring override")).check(matches(isDisplayed()))

                // Re-granting access restores eligibility; the user can explicitly opt in again.
                shell("pm grant $packageName $contacts")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                assertTrue(VipRingerOverride.hasRequiredAccess(context))
                VipRingerOverride.setEnabled(context, true)
                assertTrue(VipRingerOverride.isEnabled(context))
            }
        } finally {
            VipRingerOverride.setEnabled(context, false)
            if (contactsWasGranted) shell("pm grant $packageName $contacts")
            else shell("pm revoke $packageName $contacts")
            if (phoneWasGranted) shell("pm grant $packageName $phoneState")
            else shell("pm revoke $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification disallow_dnd $packageName")
        }
    }

    @Test
    fun revokingPhoneStateAccessWhileBackgroundedDisablesVipOverrideOnResume() {
        val contacts = Manifest.permission.READ_CONTACTS
        val phoneState = Manifest.permission.READ_PHONE_STATE
        val packageName = context.packageName
        val contactsWasGranted = context.checkSelfPermission(contacts) == PackageManager.PERMISSION_GRANTED
        val phoneWasGranted = context.checkSelfPermission(phoneState) == PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndWasGranted = notificationManager.isNotificationPolicyAccessGranted
        try {
            if (!contactsWasGranted) shell("pm grant $packageName $contacts")
            if (!phoneWasGranted) shell("pm grant $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification allow_dnd $packageName")
            assumeTrue(
                "Emulator must grant Contacts, Phone state, and DND access for this revocation test",
                VipRingerOverride.hasRequiredAccess(context)
            )

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                VipRingerOverride.setEnabled(context, true)
                scenario.moveToState(Lifecycle.State.CREATED)
                shell("pm revoke $packageName $phoneState")
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()

                assertFalse(
                    "VIP override must fail closed after Phone state permission is revoked",
                    VipRingerOverride.isEnabled(context)
                )
                shell("pm grant $packageName $phoneState")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                assertTrue(VipRingerOverride.hasRequiredAccess(context))
                VipRingerOverride.setEnabled(context, true)
                assertTrue(VipRingerOverride.isEnabled(context))
            }
        } finally {
            VipRingerOverride.setEnabled(context, false)
            if (contactsWasGranted) shell("pm grant $packageName $contacts")
            else shell("pm revoke $packageName $contacts")
            if (phoneWasGranted) shell("pm grant $packageName $phoneState")
            else shell("pm revoke $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification disallow_dnd $packageName")
        }
    }

    @Test
    fun revokingNotificationPolicyAccessDisablesVipOverrideAndCanRecover() {
        val contacts = Manifest.permission.READ_CONTACTS
        val phoneState = Manifest.permission.READ_PHONE_STATE
        val packageName = context.packageName
        val contactsWasGranted = context.checkSelfPermission(contacts) == PackageManager.PERMISSION_GRANTED
        val phoneWasGranted = context.checkSelfPermission(phoneState) == PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndWasGranted = notificationManager.isNotificationPolicyAccessGranted
        try {
            if (!contactsWasGranted) shell("pm grant $packageName $contacts")
            if (!phoneWasGranted) shell("pm grant $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification allow_dnd $packageName")
            assumeTrue(
                "Emulator must grant Contacts, Phone state, and DND access for this revocation test",
                VipRingerOverride.hasRequiredAccess(context)
            )

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                VipRingerOverride.setEnabled(context, true)
                scenario.moveToState(Lifecycle.State.CREATED)
                shell("cmd notification disallow_dnd $packageName")
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                assertFalse(
                    "VIP override must fail closed after Notification Policy Access is revoked",
                    VipRingerOverride.isEnabled(context)
                )

                shell("cmd notification allow_dnd $packageName")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                assertTrue(VipRingerOverride.hasRequiredAccess(context))
                VipRingerOverride.setEnabled(context, true)
                assertTrue(VipRingerOverride.isEnabled(context))
            }
        } finally {
            VipRingerOverride.setEnabled(context, false)
            if (contactsWasGranted) shell("pm grant $packageName $contacts")
            else shell("pm revoke $packageName $contacts")
            if (phoneWasGranted) shell("pm grant $packageName $phoneState")
            else shell("pm revoke $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification disallow_dnd $packageName")
        }
    }

    @Test
    fun biometricLockRemainsEnabledAcrossActivityRecreation() {
        val security = context.getSharedPreferences("callguard_app_security", 0)
        val original = security.getBoolean("biometric_lock_enabled", false)
        security.edit().putBoolean("biometric_lock_enabled", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                onView(withText("Unlock with biometrics"))
                    .check(matches(isDisplayed()))

                scenario.recreate()
                instrumentation.waitForIdleSync()
                onView(withText("Unlock with biometrics"))
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
    fun dashboardControlsRemainReachableAtTwoTimesFontScale() {
        val originalScale = Settings.System.getFloat(
            context.contentResolver,
            Settings.System.FONT_SCALE,
            1.0f
        )
        try {
            shell("settings put system font_scale 2.0")
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText("Allow / VIP")).perform(scrollTo()).check(matches(isDisplayed()))
                onView(withText("Clear activity history")).perform(scrollTo()).check(matches(isDisplayed()))
            }
        } finally {
            shell("settings put system font_scale $originalScale")
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
