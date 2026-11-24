package com.mainak.callguardai.screening

import android.content.Context
import android.net.Uri
import com.mainak.callguardai.data.SecureRuleStore

enum class ScreeningAction { ALLOW, SILENCE, REJECT }
data class ScreeningDecision(val action: ScreeningAction, val reason: String, val matchedRule: String? = null)

object CallGuardPolicy {
    fun normalizeNumber(raw: String?, region: String = PhoneNumberCanonicalizer.defaultRegion()): String? =
        PhoneNumberCanonicalizer.canonicalize(raw, region)

    fun evaluate(context: Context, handle: Uri?): ScreeningDecision {
        val number = normalizeNumber(handle?.schemeSpecificPart)
            ?: return ScreeningDecision(ScreeningAction.ALLOW, "Caller ID unavailable or invalid; allowed by safe default")
        val rules = SecureRuleStore(context)
        // Explicit allow/VIP rules always take precedence over block rules.
        if (rules.allowed().contains(number)) return ScreeningDecision(ScreeningAction.ALLOW, "Explicit allow rule", "allow")
        if (rules.blocked().contains(number)) return ScreeningDecision(ScreeningAction.REJECT, "Explicit user block rule", "block")
        return ScreeningDecision(ScreeningAction.ALLOW, "No matching rule; allowed by safe default")
    }

    fun addAllow(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber)?.let { SecureRuleStore(context).addAllow(it) } ?: false
    fun addBlock(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber)?.let { SecureRuleStore(context).addBlock(it) } ?: false
    fun removeAllow(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber)?.let { SecureRuleStore(context).removeAllow(it) } ?: false
    fun removeBlock(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber)?.let { SecureRuleStore(context).removeBlock(it) } ?: false
}
