package com.mainak.callguardai.screening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Temporarily changes Silent/Vibrate to Normal for an explicitly allowed VIP call.
 * The original ringer mode is restored when Android reports that the call ended.
 * Do Not Disturb is deliberately not bypassed.
 */
object VipRingerOverride {
    private const val PREFS = "callguard_vip_ringer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING = "restore_pending"
    private const val KEY_ORIGINAL_MODE = "original_mode"

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

    fun onVipIncomingCall(context: Context): Boolean {
        if (!isEnabled(context) || !hasRequiredAccess(context)) return false

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Do not override a separately enabled Do Not Disturb mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        ) return false

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentMode = audio.ringerMode
        if (currentMode == AudioManager.RINGER_MODE_NORMAL) return false
        if (currentMode != AudioManager.RINGER_MODE_SILENT &&
            currentMode != AudioManager.RINGER_MODE_VIBRATE
        ) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            prefs.edit()
                .putInt(KEY_ORIGINAL_MODE, currentMode)
                .putBoolean(KEY_PENDING, true)
                .commit()
        }

        return try {
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun restoreOriginalMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        val originalMode = prefs.getInt(KEY_ORIGINAL_MODE, AudioManager.RINGER_MODE_NORMAL)
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode = originalMode
        } catch (_: SecurityException) {
            // Keep the pending state so the app can retry after access is restored.
            return
        } catch (_: RuntimeException) {
            return
        }
        prefs.edit().remove(KEY_PENDING).remove(KEY_ORIGINAL_MODE).commit()
    }
}
