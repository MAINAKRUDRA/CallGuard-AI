package com.mainak.callguardai.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.mainak.callguardai.data.CallEventStore
import java.util.concurrent.Executors

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
            // Respond immediately; persistence is deliberately outside the Telecom deadline path.
            respondToCall(callDetails, response)
            val number = CallGuardPolicy.normalizeNumber(callDetails.handle?.schemeSpecificPart)
            io.execute {
                runCatching {
                    val store = CallEventStore(applicationContext)
                    try {
                        store.insert(System.currentTimeMillis(), number, decision.action.name, decision.reason)
                    } finally {
                        store.close()
                    }
                }.onFailure { Log.e(TAG, "Could not save call event", it) }
            }
            Log.i(TAG, "Screening decision=" + decision.action + "; reason=" + decision.reason)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to submit screening response", error)
        }
    }

    private companion object {
        const val TAG = "CallGuardScreening"
        val io = Executors.newSingleThreadExecutor()
    }
}
