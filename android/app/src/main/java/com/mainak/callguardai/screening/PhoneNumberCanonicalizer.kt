package com.mainak.callguardai.screening

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object PhoneNumberCanonicalizer {
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    fun canonicalize(raw: String?, region: String = defaultRegion()): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val parsed = util.parse(raw.trim(), region.uppercase(Locale.ROOT))
            if (!util.isValidNumber(parsed)) null
            else util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            null
        }
    }

    fun isSupportedRegion(region: String): Boolean =
        region in util.supportedRegions

    fun defaultRegion(): String =
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
}
