package com.mainak.callguardai.screening

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Opt-in VIP override. Temporarily changes Silent/Vibrate to Ring and pauses
 * the current DND interruption filter for a VIP call, then restores settings
 * only when they still appear to be in the temporary state set by CallGuard.
 * Android/OEM policy can still restrict it.
 */
object VipRingerOverride {
    private const val PREFS = "callguard_vip_ringer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING = "restore_pending"
    private const val KEY_ORIGINAL_MODE = "original_mode"
    private const val KEY_ORIGINAL_FILTER = "original_interruption_filter"
    private const val KEY_CHANGED_FILTER = "changed_interruption_filter"
    private const val KEY_ORIGINAL_RING_VOLUME = "original_ring_volume"
    private const val KEY_TEMP_RING_VOLUME = "temporary_ring_volume"
    private const val KEY_CHANGED_RING_VOLUME = "changed_ring_volume"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun hasRequiredAccess(context: Context): Boolean {
        val contactsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val phonePermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val policyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.isNotificationPolicyAccessGranted
        } else true
        return contactsPermission && phonePermission && policyAccess
    }

    @Synchronized
    fun onVipIncomingCall(context: Context): Boolean {
        if (!isEnabled(context) || !hasRequiredAccess(context)) return false

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentMode = audio.ringerMode
        val currentRingVolume = audio.getStreamVolume(AudioManager.STREAM_RING)
        val maxRingVolume = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        val audibleRingVolume = VipRingVolumePolicy.minimumAudibleVolume(maxRingVolume)
        val targetRingVolume = maxOf(currentRingVolume, audibleRingVolume)
        val needsVolumeChange = currentRingVolume < targetRingVolume
        val currentFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter
        } else NotificationManager.INTERRUPTION_FILTER_ALL

        val needsRingerChange = currentMode == AudioManager.RINGER_MODE_SILENT ||
            currentMode == AudioManager.RINGER_MODE_VIBRATE
        val knownDndFilter = currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        val needsDndChange = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && knownDndFilter

        // Keep a call-scoped snapshot even if the phone is already in Ring mode.
        // Telecom or an OEM may re-apply Silent/Vibrate after the screening response.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasPending = prefs.getBoolean(KEY_PENDING, false)
        val editor = prefs.edit()
        if (!wasPending) {
            editor.putInt(KEY_ORIGINAL_MODE, currentMode)
                .putInt(KEY_ORIGINAL_FILTER, currentFilter)
                .putInt(KEY_ORIGINAL_RING_VOLUME, currentRingVolume)
                .putBoolean(KEY_CHANGED_FILTER, needsDndChange)
                .putBoolean(KEY_CHANGED_RING_VOLUME, false)
                .putBoolean(KEY_PENDING, true)
        }
        if (needsDndChange) editor.putBoolean(KEY_CHANGED_FILTER, true)
        if (needsVolumeChange) {
            editor.putInt(KEY_TEMP_RING_VOLUME, targetRingVolume)
                .putBoolean(KEY_CHANGED_RING_VOLUME, true)
        }
        if (!editor.commit()) return false

        return try {
            // Raise a zero/very-low ringtone stream as well as leaving Silent/Vibrate.
            // The original stream volume is restored after the call if the user has
            // not changed it again in the meantime.
            if (needsDndChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            if (needsRingerChange) {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            if (needsVolumeChange) {
                audio.setStreamVolume(AudioManager.STREAM_RING, targetRingVolume, 0)
            }
            val dndCleared = !needsDndChange || Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            val ringerReady = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
            val volumeReady = audio.getStreamVolume(AudioManager.STREAM_RING) >= audibleRingVolume
            if (dndCleared && ringerReady && volumeReady) true else {
                restoreOriginalMode(context)
                false
            }
        } catch (_: SecurityException) {
            restoreOriginalMode(context)
            false
        } catch (_: RuntimeException) {
            restoreOriginalMode(context)
            false
        }
    }

    /** Re-apply only when the screening service has already marked this call as VIP. */
    @Synchronized
    fun reassertForPendingVipCall(context: Context): Boolean {
        val pending = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING, false)
        return pending && onVipIncomingCall(context)
    }

    @Synchronized
    fun restoreOriginalMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        val originalMode = prefs.getInt(KEY_ORIGINAL_MODE, AudioManager.RINGER_MODE_NORMAL)
        val originalFilter = prefs.getInt(
            KEY_ORIGINAL_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL
        )
        val changedFilter = prefs.getBoolean(KEY_CHANGED_FILTER, false)
        val originalRingVolume = prefs.getInt(KEY_ORIGINAL_RING_VOLUME, -1)
        val temporaryRingVolume = prefs.getInt(KEY_TEMP_RING_VOLUME, -1)
        val changedRingVolume = prefs.getBoolean(KEY_CHANGED_RING_VOLUME, false)

        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val restorableFilter = originalFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
            if (changedFilter && restorableFilter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            ) {
                notificationManager.setInterruptionFilter(originalFilter)
            }

            // Restore volume only if it is still the temporary value set by CallGuard.
            // This preserves a volume adjustment the user makes during the call.
            if (changedRingVolume && originalRingVolume >= 0 && temporaryRingVolume >= 0 &&
                audio.getStreamVolume(AudioManager.STREAM_RING) == temporaryRingVolume
            ) {
                audio.setStreamVolume(AudioManager.STREAM_RING, originalRingVolume, 0)
            }

            // Do not overwrite a newer ringer choice made by the user during the call.
            if (RingerRestorePolicy.shouldRestoreOriginalMode(audio.ringerMode)) {
                audio.ringerMode = originalMode
            }
        } catch (_: SecurityException) {
            return
        } catch (_: RuntimeException) {
            return
        }

        prefs.edit()
            .remove(KEY_PENDING)
            .remove(KEY_ORIGINAL_MODE)
            .remove(KEY_ORIGINAL_FILTER)
            .remove(KEY_CHANGED_FILTER)
            .remove(KEY_ORIGINAL_RING_VOLUME)
            .remove(KEY_TEMP_RING_VOLUME)
            .remove(KEY_CHANGED_RING_VOLUME)
            .commit()
    }
}

/** Pure policy so user changes made during a call are not overwritten at call end. */
internal object RingerRestorePolicy {
    fun shouldRestoreOriginalMode(currentMode: Int): Boolean =
        currentMode == AudioManager.RINGER_MODE_NORMAL
}

/** Pure volume policy shared by implementation and JVM tests. */
internal object VipRingVolumePolicy {
    fun minimumAudibleVolume(maxVolume: Int): Int =
        if (maxVolume <= 0) 0 else (maxVolume / 2.0).toInt().coerceAtLeast(1)

    fun shouldRestoreOriginalVolume(currentVolume: Int, temporaryVolume: Int): Boolean =
        currentVolume == temporaryVolume
}
