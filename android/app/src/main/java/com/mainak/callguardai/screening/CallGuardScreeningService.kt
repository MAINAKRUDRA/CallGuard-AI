package com.mainak.callguardai.screening

import android.telecom.Call
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.telecom.CallScreeningService
import android.util.Log
import com.mainak.callguardai.data.CallEventStore
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CallGuardScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val decision = evaluateWithinDeadline(callDetails)
        val isExplicitVipIncomingCall =
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING &&
                decision.action == ScreeningAction.ALLOW && decision.matchedRule == "allow"
        if (isExplicitVipIncomingCall) {
            val changed = VipRingerOverride.onVipIncomingCall(this)
            if (changed) {
                Log.i(TAG, "VIP ring override activated; original ringer mode and volume will be restored when the call ends")
            } else {
                Log.w(TAG, "VIP ring override could not be confirmed; verify VIP toggle and required Android access")
            }
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
        } catch (_: RuntimeException) {
            Log.e(TAG, "Unable to submit screening response")
            return
        }
        if (isExplicitVipIncomingCall) {
            // OxygenOS/ColorOS and some Telecom implementations can re-apply
            // Alert Slider / prior ringer state shortly after the screening
            // callback. Retry briefly while the same VIP call is still ringing.
            // reassertForPendingVipCall checks the saved snapshot before acting,
            // so a newer user-selected mode or volume is never overwritten.
            val handler = Handler(Looper.getMainLooper())
            VIP_RING_REASSERT_DELAYS_MS.forEach { delay ->
                handler.postDelayed({
                    val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    if (telephony.callState == TelephonyManager.CALL_STATE_RINGING) {
                        VipRingerOverride.reassertForPendingVipCall(this)
                    }
                }, delay)
            }
        }
        val number = CallGuardPolicy.normalizeNumber(
            callDetails.handle?.schemeSpecificPart,
            CallGuardPolicy.selectedRegion(applicationContext)
        )
        io.execute {
            runCatching {
                val store = CallEventStore(applicationContext)
                try {
                    store.insert(System.currentTimeMillis(), maskNumber(number), decision.action.name, decision.reason)
                } finally { store.close() }
            }.onFailure { Log.e(TAG, "Could not save call event") }
        }
        Log.i(TAG, "Screening decision=" + decision.action + "; reason=" + decision.reason)
    }

    /**
     * Keep rule/Keystore work off the screening callback thread. If storage is
     * slow, unavailable, or another evaluation is still stuck, allow the call.
     * Never turn an evaluation timeout into a rejection.
     */
    private fun evaluateWithinDeadline(details: Call.Details): ScreeningDecision {
        val task = FutureTask { CallGuardPolicy.evaluate(this, details.handle) }
        return try {
            evaluator.execute(task)
            task.get(EVALUATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            evaluator.remove(task)
            Log.w(TAG, "Rule evaluation timed out; applying safe allow fallback")
            safeAllowDecision()
        } catch (_: ExecutionException) {
            Log.e(TAG, "Rule evaluation failed; applying safe allow fallback")
            safeAllowDecision()
        } catch (_: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            Log.w(TAG, "Rule evaluation interrupted; applying safe allow fallback")
            safeAllowDecision()
        } catch (_: RuntimeException) {
            task.cancel(true)
            Log.w(TAG, "Rule evaluation unavailable; applying safe allow fallback")
            safeAllowDecision()
        }
    }

    private fun safeAllowDecision() =
        ScreeningDecision(ScreeningAction.ALLOW, "Rule evaluation unavailable; safe allow fallback")

    private fun maskNumber(number: String?): String {
        if (number.isNullOrBlank()) return "Unavailable"
        val digits = number.filter(Char::isDigit)
        return if (digits.length <= 4) "••••" else "••••••" + digits.takeLast(4)
    }

    private companion object {
        const val TAG = "CallGuardScreening"
        const val EVALUATION_TIMEOUT_MS = 1_500L
        val VIP_RING_REASSERT_DELAYS_MS = longArrayOf(250L, 700L, 1_500L, 3_000L)
        val evaluator = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            ThreadFactory { runnable -> Thread(runnable, "callguard-rule-evaluator").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
        val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
