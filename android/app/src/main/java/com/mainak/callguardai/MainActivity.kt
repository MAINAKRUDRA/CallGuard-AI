package com.mainak.callguardai

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mainak.callguardai.contacts.ContactImporter
import com.mainak.callguardai.data.CallEventStore
import com.mainak.callguardai.screening.CallGuardPolicy
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var rulesView: TextView
    private lateinit var historyView: TextView
    private lateinit var numberInput: EditText

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateRoleStatus(it.resultCode == Activity.RESULT_OK)
    }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val count = ContactImporter.importAll(this)
            status.text = "Imported $count contact numbers into your VIP allow-list."
            refreshScreen()
        } else {
            status.text = "Contacts permission was not granted. You can still add VIP numbers manually."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 40)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(TextView(this).apply { text = "CallGuard AI"; textSize = 28f })
        content.addView(TextView(this).apply {
            text = "Private, allow-first call screening. Unknown callers are allowed unless you explicitly block them."
            textSize = 14f
            setPadding(0, 12, 0, 16)
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 12) }
        content.addView(status)
        content.addView(actionButton("Enable call screening") { requestScreeningRole() })

        content.addView(sectionTitle("Manage numbers"))
        numberInput = EditText(this).apply {
            hint = "Phone number, e.g. +919876543210"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        content.addView(numberInput, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(actionButton("Add to VIP / allow-list") {
            val value = numberInput.text.toString()
            if (CallGuardPolicy.addAllow(this, value)) {
                status.text = "Number added to VIP allow-list."
                numberInput.text.clear()
                refreshScreen()
            } else status.text = "Enter a valid phone number."
        })
        content.addView(actionButton("Block this number") {
            val value = numberInput.text.toString()
            if (CallGuardPolicy.addBlock(this, value)) {
                status.text = "Number added to block-list. VIP allow rules still take priority."
                numberInput.text.clear()
                refreshScreen()
            } else status.text = "Enter a valid phone number."
        })
        content.addView(actionButton("Import contacts as VIPs") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                val count = ContactImporter.importAll(this)
                status.text = "Imported $count contact numbers into your VIP allow-list."
                refreshScreen()
            } else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        })

        content.addView(sectionTitle("Your rules"))
        rulesView = TextView(this).apply { textSize = 14f; setPadding(0, 4, 0, 12) }
        content.addView(rulesView)
        content.addView(sectionTitle("Recent screening activity"))
        historyView = TextView(this).apply { textSize = 13f; setPadding(0, 4, 0, 12) }
        content.addView(historyView)
        content.addView(actionButton("Clear local activity history") {
            val store = CallEventStore(this)
            try { store.clearHistory() } finally { store.close() }
            refreshScreen()
        })

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Call screening role requires Android 10 or later."
            return
        }
        val manager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            status.text = "Call screening is not available on this device."
        } else if (manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            updateRoleStatus(true)
        } else {
            roleRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun updateRoleStatus(granted: Boolean? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Android 10 or later is required for the screening role."
            return
        }
        val manager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        val enabled = granted ?: manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        status.text = if (enabled) "Call screening role: enabled." else "Call screening role: not enabled."
    }

    private fun refreshScreen() {
        if (!::status.isInitialized) return
        updateRoleStatus()
        val prefs = getSharedPreferences("callguard_rules", Context.MODE_PRIVATE)
        val allow = prefs.getStringSet("allow_numbers", emptySet()).orEmpty().sorted()
        val block = prefs.getStringSet("block_numbers", emptySet()).orEmpty().sorted()
        rulesView.text = buildString {
            append("VIP / allowed numbers (" + allow.size + ")\n")
            append(if (allow.isEmpty()) "  None\n" else allow.joinToString("\n") { "  + " + it } + "\n")
            append("\nBlocked numbers (" + block.size + ")\n")
            append(if (block.isEmpty()) "  None" else block.joinToString("\n") { "  − " + it })
        }

        val store = CallEventStore(this)
        val events = try { store.latest(30) } finally { store.close() }
        historyView.text = if (events.isEmpty()) {
            "No screening events recorded yet. The log fills after the Android screening role is enabled and calls arrive."
        } else events.joinToString("\n\n") { event ->
            val whenText = DateFormat.getDateTimeInstance().format(Date(event.timestamp))
            whenText + "\n" + event.action + " · " + event.number + "\n" + event.reason
        }
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 19f
        setPadding(0, 22, 0, 8)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }
}
