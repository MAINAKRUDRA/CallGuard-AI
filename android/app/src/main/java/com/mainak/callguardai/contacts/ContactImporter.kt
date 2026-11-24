package com.mainak.callguardai.contacts

import android.content.Context
import android.provider.ContactsContract
import com.mainak.callguardai.screening.CallGuardPolicy

/**
 * Imports VIP numbers and their display names directly from the device Contacts provider.
 * No Android call-log query is used. The app stores only the normalized number/name mapping
 * needed to label the local VIP list; the original address book remains on the device.
 */
object ContactImporter {
    private const val PREFS = "callguard_imported_contact_labels"

    fun importAll(context: Context): Int {
        var imported = 0
        val labels = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = labels.edit()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberColumn < 0) return 0
            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberColumn) ?: continue
                val canonical = CallGuardPolicy.normalizeNumber(rawNumber, CallGuardPolicy.selectedRegion(context)) ?: continue
                val name = if (nameColumn >= 0) cursor.getString(nameColumn)?.trim().orEmpty() else ""
                if (CallGuardPolicy.addAllow(context, canonical)) imported++
                if (name.isNotBlank()) editor.putString(canonical, name)
            }
        }
        editor.apply()
        return imported
    }

    /** Adds only the phone number explicitly selected by the user to the VIP allow-list. */
    fun addSelected(context: Context, name: String, rawNumber: String): Boolean {
        val canonical = CallGuardPolicy.normalizeNumber(rawNumber, CallGuardPolicy.selectedRegion(context))
            ?: return false
        if (!CallGuardPolicy.addAllow(context, canonical)) return false
        if (name.isNotBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(canonical, name.trim()).apply()
        }
        return true
    }

    fun displayName(context: Context, canonicalNumber: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(canonicalNumber, null)
            ?.takeIf { it.isNotBlank() }
}
