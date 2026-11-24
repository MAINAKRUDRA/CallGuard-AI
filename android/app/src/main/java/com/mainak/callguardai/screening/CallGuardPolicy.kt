package com.mainak.callguardai.screening

import android.content.Context
import android.net.Uri

enum class ScreeningAction { ALLOW, SILENCE, REJECT }
data class ScreeningDecision(val action: ScreeningAction, val reason: String, val matchedRule: String? = null)

object CallGuardPolicy {
    fun normalizeNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return (if (trimmed.startsWith("+")) "+" else "") + digits
    }

    fun evaluate(context: Context, handle: Uri?): ScreeningDecision {
        val number = normalizeNumber(handle?.schemeSpecificPart)
            ?: return ScreeningDecision(ScreeningAction.ALLOW, "Caller ID unavailable; allowed by safe default")
        val prefs = context.getSharedPreferences("callguard_rules", Context.MODE_PRIVATE)
        val allow = prefs.getStringSet("allow_numbers", emptySet()).orEmpty()
        val block = prefs.getStringSet("block_numbers", emptySet()).orEmpty()

        // Explicit allow/VIP rules always take precedence over block rules.
        if (allow.contains(number)) return ScreeningDecision(ScreeningAction.ALLOW, "Explicit allow rule", "allow")
        if (block.contains(number)) return ScreeningDecision(ScreeningAction.REJECT, "Explicit user block rule", "block")
        return ScreeningDecision(ScreeningAction.ALLOW, "No matching rule; allowed by safe default")
    }

    fun addAllow(context: Context, rawNumber: String): Boolean = addNumber(context, "allow_numbers", rawNumber)
    fun addBlock(context: Context, rawNumber: String): Boolean = addNumber(context, "block_numbers", rawNumber)
    fun removeAllow(context: Context, rawNumber: String): Boolean = removeNumber(context, "allow_numbers", rawNumber)
    fun removeBlock(context: Context, rawNumber: String): Boolean = removeNumber(context, "block_numbers", rawNumber)

    private fun addNumber(context: Context, key: String, rawNumber: String): Boolean {
        val number = normalizeNumber(rawNumber) ?: return false
        val prefs = context.getSharedPreferences("callguard_rules", Context.MODE_PRIVATE)
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values.add(number)
        return prefs.edit().putStringSet(key, values).commit()
    }

    private fun removeNumber(context: Context, key: String, rawNumber: String): Boolean {
        val number = normalizeNumber(rawNumber) ?: return false
        val prefs = context.getSharedPreferences("callguard_rules", Context.MODE_PRIVATE)
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values.remove(number)
        return prefs.edit().putStringSet(key, values).commit()
    }
}
