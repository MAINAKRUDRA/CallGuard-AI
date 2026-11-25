package com.mainak.callguardai.screening

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Only reassert when CallScreeningService has already marked this
                // exact incoming call as an explicit VIP match.
                VipRingerOverride.reassertForPendingVipCall(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> VipRingerOverride.restoreOriginalMode(context)
        }
    }
}
