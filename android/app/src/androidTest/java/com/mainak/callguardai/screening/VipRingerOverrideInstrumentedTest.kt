package com.mainak.callguardai.screening

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class VipRingerOverrideInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
    }

    @Test
    fun silentPhoneBecomesAudibleForVipAndOriginalSettingsAreRestored() {
        val packageName = context.packageName
        val contacts = Manifest.permission.READ_CONTACTS
        val phoneState = Manifest.permission.READ_PHONE_STATE
        val contactsWasGranted = context.checkSelfPermission(contacts) == PackageManager.PERMISSION_GRANTED
        val phoneWasGranted = context.checkSelfPermission(phoneState) == PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndWasGranted = notificationManager.isNotificationPolicyAccessGranted
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalMode = audio.ringerMode
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_RING)
        val originalVipEnabled = VipRingerOverride.isEnabled(context)

        try {
            if (!contactsWasGranted) shell("pm grant $packageName $contacts")
            if (!phoneWasGranted) shell("pm grant $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification allow_dnd $packageName")
            assumeTrue("Test device must grant VIP override permissions", VipRingerOverride.hasRequiredAccess(context))

            VipRingerOverride.setEnabled(context, true)
            audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            assertTrue(
                "A non-VIP call must not activate the override without a screening-service VIP snapshot",
                !VipRingerOverride.reassertForPendingVipCall(context)
            )
            assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)

            for (silentMode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
                audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                audio.ringerMode = silentMode

                assertTrue(
                    "VIP override should activate from Silent or Vibrate mode",
                    VipRingerOverride.onVipIncomingCall(context)
                )
                assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
                assertTrue(
                    "VIP ringtone stream must be audible",
                    audio.getStreamVolume(AudioManager.STREAM_RING) >=
                        VipRingVolumePolicy.minimumAudibleVolume(audio.getStreamMaxVolume(AudioManager.STREAM_RING))
                )

                // Simulate Telecom/OEM restoring the prior mode after the screening
                // response. A marked VIP call must be reasserted; non-VIP calls are not.
                audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                audio.ringerMode = silentMode
                assertTrue(VipRingerOverride.reassertForPendingVipCall(context))
                assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
                assertTrue(
                    audio.getStreamVolume(AudioManager.STREAM_RING) >=
                        VipRingVolumePolicy.minimumAudibleVolume(audio.getStreamMaxVolume(AudioManager.STREAM_RING))
                )

                VipRingerOverride.restoreOriginalMode(context)
                assertEquals("The original Silent/Vibrate mode must be restored", silentMode, audio.ringerMode)
                assertEquals("The original ringtone volume must be restored", 0, audio.getStreamVolume(AudioManager.STREAM_RING))
            }
        } finally {
            VipRingerOverride.restoreOriginalMode(context)
            VipRingerOverride.setEnabled(context, originalVipEnabled)
            audio.setStreamVolume(AudioManager.STREAM_RING, originalVolume, 0)
            audio.ringerMode = originalMode
            if (contactsWasGranted) shell("pm grant $packageName $contacts") else shell("pm revoke $packageName $contacts")
            if (phoneWasGranted) shell("pm grant $packageName $phoneState") else shell("pm revoke $packageName $phoneState")
            if (!dndWasGranted) shell("cmd notification disallow_dnd $packageName")
        }
    }
}
