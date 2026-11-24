package com.mainak.callguardai.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallGuardScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val decision = CallGuardPolicy.evaluate(this, callDetails.handle)
        val response = CallResponse.Builder()
            .setDisallowCall(decision.action == ScreeningAction.REJECT)
            .setRejectCall(decision.action == ScreeningAction.REJECT)
            .setSilenceCall(decision.action == ScreeningAction.SILENCE)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        try {
            respondToCall(callDetails, response)
            Log.i(TAG, "Screening decision=" + decision.action + "; reason=" + decision.reason)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to submit screening response", error)
        }
    }

    private companion object { const val TAG = "CallGuardScreening" }
}
