package com.mainak.callguardai.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.mainak.callguardai.data.CallEventStore
import java.util.concurrent.Executors

class CallGuardScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val decision = try {
            CallGuardPolicy.evaluate(this, callDetails.handle)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Rule evaluation failed; applying allow-first fallback", error)
            ScreeningDecision(ScreeningAction.ALLOW, "Rule storage unavailable; safe allow fallback")
        }
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING &&
            decision.action == ScreeningAction.ALLOW && decision.matchedRule == "allow"
        ) {
            val changed = VipRingerOverride.onVipIncomingCall(this)
            if (changed) Log.i(TAG, "VIP ring override activated; original ringer mode will be restored when the call ends")
        }
        val response = CallResponse.Builder()
            .setDisallowCall(decision.action == ScreeningAction.REJECT)
            .setRejectCall(decision.action == ScreeningAction.REJECT)
            .setSilenceCall(decision.action == ScreeningAction.SILENCE)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        try {
            respondToCall(callDetails, response)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to submit screening response", error)
            return
        }
        val number = CallGuardPolicy.normalizeNumber(callDetails.handle?.schemeSpecificPart, CallGuardPolicy.selectedRegion(applicationContext))
        io.execute {
            runCatching {
                val store = CallEventStore(applicationContext)
                try {
                    store.insert(System.currentTimeMillis(), maskNumber(number), decision.action.name, decision.reason)
                } finally { store.close() }
            }.onFailure { Log.e(TAG, "Could not save call event", it) }
        }
        Log.i(TAG, "Screening decision=" + decision.action + "; reason=" + decision.reason)
    }

    private fun maskNumber(number: String?): String {
        if (number.isNullOrBlank()) return "Unavailable"
        val digits = number.filter(Char::isDigit)
        return if (digits.length <= 4) "••••" else "••••••" + digits.takeLast(4)
    }

    private companion object {
        const val TAG = "CallGuardScreening"
        val io = Executors.newSingleThreadExecutor()
    }
}
