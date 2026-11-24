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
 * the current DND interruption filter for a VIP call, then restores the
 * saved settings when the call ends. Android/OEM policy can still restrict it.
 */
object VipRingerOverride {
    private const val PREFS = "callguard_vip_ringer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING = "restore_pending"
    private const val KEY_ORIGINAL_MODE = "original_mode"
    private const val KEY_ORIGINAL_FILTER = "original_interruption_filter"
    private const val KEY_CHANGED_FILTER = "changed_interruption_filter"

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

    /**
     * Called only for a caller that matched an explicit VIP allow rule.
     * Returns true when a temporary phone-mode change was applied.
     */
    @Synchronized
    fun onVipIncomingCall(context: Context): Boolean {
        if (!isEnabled(context) || !hasRequiredAccess(context)) return false

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentMode = audio.ringerMode
        val currentFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter
        } else NotificationManager.INTERRUPTION_FILTER_ALL

        val needsRingerChange = currentMode == AudioManager.RINGER_MODE_SILENT ||
            currentMode == AudioManager.RINGER_MODE_VIBRATE
        val knownDndFilter = currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        val needsDndChange = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && knownDndFilter

        if (!needsRingerChange && !needsDndChange) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            val saved = prefs.edit()
                .putInt(KEY_ORIGINAL_MODE, currentMode)
                .putInt(KEY_ORIGINAL_FILTER, currentFilter)
                .putBoolean(KEY_CHANGED_FILTER, needsDndChange)
                .putBoolean(KEY_PENDING, true)
                .commit()
            if (!saved) return false
        }

        return try {
            // DND is changed only after the user explicitly enables VIP override
            // and grants Android's Notification Policy Access.
            if (needsDndChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            if (needsRingerChange) {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            val dndCleared = !needsDndChange || Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            val ringerReady = !needsRingerChange || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
            if (dndCleared && ringerReady) true else {
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

    @Synchronized
    fun restoreOriginalMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        val originalMode = prefs.getInt(KEY_ORIGINAL_MODE, AudioManager.RINGER_MODE_NORMAL)
        val originalFilter = prefs.getInt(
            KEY_ORIGINAL_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL
        )
        val changedFilter = prefs.getBoolean(KEY_CHANGED_FILTER, false)

        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Restore DND only if it still has the temporary ALL value set by us.
            // If the user changed DND during the call, respect that newer choice.
            val restorableFilter = originalFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                originalFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
            if (changedFilter && restorableFilter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            ) {
                notificationManager.setInterruptionFilter(originalFilter)
            }
            audio.ringerMode = originalMode
        } catch (_: SecurityException) {
            // Keep the pending snapshot; the app retries on its next foreground/resume.
            return
        } catch (_: RuntimeException) {
            return
        }

        prefs.edit()
            .remove(KEY_PENDING)
            .remove(KEY_ORIGINAL_MODE)
            .remove(KEY_ORIGINAL_FILTER)
            .remove(KEY_CHANGED_FILTER)
            .commit()
    }
}
