package com.mainak.callguardai.screening

import android.content.Context
import android.net.Uri
import com.mainak.callguardai.data.SecureRuleStore
import java.util.Locale

enum class ScreeningAction { ALLOW, SILENCE, REJECT }
data class ScreeningDecision(val action: ScreeningAction, val reason: String, val matchedRule: String? = null)

object CallGuardPolicy {
    private const val SETTINGS = "callguard_settings"
    private const val REGION_KEY = "phone_region"

    fun selectedRegion(context: Context): String =
        context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .getString(REGION_KEY, PhoneNumberCanonicalizer.defaultRegion())
            ?: PhoneNumberCanonicalizer.defaultRegion()

    fun setRegion(context: Context, rawRegion: String): Boolean {
        val region = rawRegion.trim().uppercase(Locale.ROOT)
        if (region.length != 2 || !PhoneNumberCanonicalizer.isSupportedRegion(region)) return false
        return context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .edit().putString(REGION_KEY, region).commit()
    }

    fun normalizeNumber(raw: String?, region: String = PhoneNumberCanonicalizer.defaultRegion()): String? =
        PhoneNumberCanonicalizer.canonicalize(raw, region)

    fun evaluate(context: Context, handle: Uri?): ScreeningDecision {
        val number = normalizeNumber(handle?.schemeSpecificPart, selectedRegion(context))
        if (number == null) {
            return CallRuleEvaluator.evaluate(null, emptySet(), emptySet())
        }
        val rules = SecureRuleStore(context)
        return CallRuleEvaluator.evaluate(number, rules.allowed(), rules.blocked())
    }

    fun addAllow(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber, selectedRegion(context))?.let { SecureRuleStore(context).addAllow(it) } ?: false
    fun addBlock(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber, selectedRegion(context))?.let { SecureRuleStore(context).addBlock(it) } ?: false
    fun removeAllow(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber, selectedRegion(context))?.let { SecureRuleStore(context).removeAllow(it) } ?: false
    fun removeBlock(context: Context, rawNumber: String): Boolean =
        normalizeNumber(rawNumber, selectedRegion(context))?.let { SecureRuleStore(context).removeBlock(it) } ?: false
}
