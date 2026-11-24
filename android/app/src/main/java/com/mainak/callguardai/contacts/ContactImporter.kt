package com.mainak.callguardai.contacts

import android.content.Context
import android.provider.ContactsContract
import com.mainak.callguardai.screening.CallGuardPolicy

object ContactImporter {
    fun importAll(context: Context): Int {
        var imported = 0
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberColumn < 0) return 0
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberColumn) ?: continue
                if (CallGuardPolicy.addAllow(context, number)) imported++
            }
        }
        return imported
    }
}
