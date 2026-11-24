package com.mainak.callguardai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mainak.callguardai.contacts.ContactImporter
import com.mainak.callguardai.data.CallEventStore
import com.mainak.callguardai.data.SecureRuleStore
import com.mainak.callguardai.screening.CallGuardPolicy
import com.mainak.callguardai.screening.VipRingerOverride
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var rulesView: TextView
    private lateinit var historyView: TextView
    private lateinit var numberInput: EditText
    private lateinit var regionInput: EditText
    private lateinit var allowCount: TextView
    private lateinit var blockCount: TextView
    private lateinit var activityCount: TextView
    private lateinit var roleBadge: TextView
    private lateinit var importSummary: TextView
    private lateinit var vipRingSwitch: Switch

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateRoleStatus(it.resultCode == Activity.RESULT_OK)
    }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) importContactsNow()
        else showStatus("Contacts permission was not granted. You can still add VIP numbers manually.")
    }

    private val phoneStatePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestNotificationPolicyAccess()
        else {
            VipRingerOverride.setEnabled(this, false)
            syncVipRingSwitch()
            showStatus("Phone-state permission is required to restore your previous ringer mode after a VIP call.")
        }
    }
    private val policyAccessRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        completeVipRingSetup()
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
            setBackgroundColor(Color.rgb(244, 247, 251))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(20))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#14243A", 24)
            setPadding(dp(22), dp(24), dp(22), dp(22))
        }
        hero.addView(label("CALLGUARD AI  ·  PERSONAL CALL CONTROL", "#A9C5E8", 11, true))
        hero.addView(label("Your calls.\nYour rules.", "#FFFFFF", 30, true).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        hero.addView(label("A private, allow-first screening dashboard. Unknown callers are allowed unless you explicitly block them.", "#D6E1EF", 14, false))
        roleBadge = label("CHECKING SCREENING STATUS…", "#FFFFFF", 12, true).apply {
            background = rounded("#29415E", 30)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        hero.addView(roleBadge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })
        hero.addView(actionButton("Enable call screening", true) { requestScreeningRole() }.apply {
            setTextColor(Color.rgb(20, 36, 58))
        }, matchWrap().apply { topMargin = dp(14) })
        content.addView(hero, matchWrap())

        status = label("Checking call screening status…", "#334155", 13, true).apply {
            background = rounded("#FFFFFF", 14, "#E1E8F0")
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        content.addView(status, matchWrap().apply { topMargin = dp(12) })

        content.addView(sectionTitle("Overview"), matchWrap().apply { topMargin = dp(24) })
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        allowCount = statCard(stats, "VIP / ALLOWED", "0", "#E8F5EE", "#147A48")
        blockCount = statCard(stats, "BLOCKED", "0", "#FDECEC", "#B42318")
        activityCount = statCard(stats, "ACTIVITY", "0", "#EAF1FC", "#245A9B")
        content.addView(stats, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Manage a phone number"), matchWrap().apply { topMargin = dp(24) })
        content.addView(label("Add a number once, then choose what CallGuard should do with it.", "#64748B", 13, false), matchWrap())
        numberInput = EditText(this).apply {
            hint = "+91 98765 43210"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.rgb(25, 38, 55))
            setHintTextColor(Color.rgb(145, 157, 172))
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded("#FFFFFF", 14, "#DCE4EE")
        }
        content.addView(numberInput, matchWrap().apply { topMargin = dp(12) })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(actionButton("Allow / VIP", true) {
            val value = numberInput.text.toString()
            if (CallGuardPolicy.addAllow(this@MainActivity, value)) {
                numberInput.text.clear()
                showStatus("Number added to your VIP allow-list.")
                refreshScreen()
            } else showStatus("Enter a valid phone number for the selected region.")
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        actions.addView(actionButton("Block number", false) {
            val value = numberInput.text.toString()
            if (CallGuardPolicy.addBlock(this@MainActivity, value)) {
                numberInput.text.clear()
                showStatus("Number blocked. VIP allow rules still take priority.")
                refreshScreen()
            } else showStatus("Enter a valid phone number for the selected region.")
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        content.addView(actions, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Contacts"), matchWrap().apply { topMargin = dp(24) })
        val contactsCard = card()
        contactsCard.addView(label("Import trusted contacts", "#172B45", 16, true))
        contactsCard.addView(label("Valid phone numbers from your address book will be added to the VIP allow-list. Review the permission before continuing.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(6) })
        importSummary = label("No contacts imported in this session.", "#147A48", 12, true)
        contactsCard.addView(importSummary, matchWrap().apply { topMargin = dp(10) })
        contactsCard.addView(actionButton("Review and import contacts", false) {
            AlertDialog.Builder(this)
                .setTitle("Import contacts as VIPs?")
                .setMessage("Every valid phone number in your contacts will be added to the VIP allow-list and allowed through screening. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ ->
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        importContactsNow()
                    } else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
                .show()
        }, matchWrap().apply { topMargin = dp(12) })
        content.addView(contactsCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("VIP ring override"), matchWrap().apply { topMargin = dp(24) })
        val vipCard = card()
        vipCard.addView(label("Let VIP callers ring through Silent / Vibrate", "#172B45", 16, true))
        vipCard.addView(label("When an explicitly allowed VIP calls, CallGuard temporarily switches the ringer to Normal and restores your previous Silent or Vibrate mode when the call ends.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(6) })
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        vipRingSwitch = Switch(this).apply {
            text = "Enable VIP ring override"
            textSize = 14f
            setTextColor(Color.rgb(34, 57, 83))
            setOnCheckedChangeListener { _, checked ->
                if (!checked) {
                    VipRingerOverride.setEnabled(this@MainActivity, false)
                    showStatus("VIP ring override is off.")
                } else startVipRingSetup()
            }
        }
        switchRow.addView(vipRingSwitch, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        vipCard.addView(switchRow, matchWrap().apply { topMargin = dp(10) })
        vipCard.addView(label("Requires Phone state permission and Android Notification Policy Access. Do Not Disturb is not bypassed. SMS permission is not needed for call screening.", "#8A5A18", 12, false), matchWrap().apply { topMargin = dp(8) })
        content.addView(vipCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Your rules"), matchWrap().apply { topMargin = dp(24) })
        val rulesCard = card()
        rulesView = label("Loading your encrypted rules…", "#334155", 13, false)
        rulesView.setTextIsSelectable(true)
        rulesCard.addView(rulesView, matchWrap())
        content.addView(rulesCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Phone settings"), matchWrap().apply { topMargin = dp(24) })
        val settingsCard = card()
        settingsCard.addView(label("Default country / region", "#172B45", 16, true))
        settingsCard.addView(label("Used to normalize local phone numbers. Enter a two-letter country code.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(5) })
        regionInput = EditText(this).apply {
            hint = "IN"
            setSingleLine(true)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(CallGuardPolicy.selectedRegion(this@MainActivity))
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded("#F7F9FC", 12, "#DCE4EE")
        }
        settingsCard.addView(regionInput, matchWrap().apply { topMargin = dp(10) })
        settingsCard.addView(actionButton("Save phone region", false) {
            val region = regionInput.text.toString()
            if (CallGuardPolicy.setRegion(this@MainActivity, region)) {
                showStatus("Phone region saved as ${region.trim().uppercase()}.")
            } else showStatus("Enter a supported two-letter country code, such as IN or US.")
        }, matchWrap().apply { topMargin = dp(10) })
        content.addView(settingsCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Recent screening activity"), matchWrap().apply { topMargin = dp(24) })
        val historyCard = card()
        historyView = label("No screening events recorded yet.", "#334155", 13, false)
        historyCard.addView(historyView, matchWrap())
        content.addView(historyCard, matchWrap().apply { topMargin = dp(10) })
        content.addView(actionButton("Clear activity history", false) {
            AlertDialog.Builder(this)
                .setTitle("Clear local history?")
                .setMessage("This permanently deletes the call screening activity stored on this device. Your allow and block rules will not be changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear history") { _, _ ->
                    val store = CallEventStore(this)
                    try { store.clearHistory() } finally { store.close() }
                    refreshScreen()
                    showStatus("Local activity history cleared.")
                }
                .show()
        }, matchWrap().apply { topMargin = dp(10) })

        content.addView(label("CallGuard AI  ·  Version ${BuildConfig.VERSION_NAME}", "#64748B", 11, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(2))
        }, matchWrap())
        content.addView(label("PRIVACY FIRST  ·  DATA STORED ON THIS DEVICE", "#64748B", 11, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(26), 0, dp(4))
        }, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun statCard(parent: LinearLayout, title: String, value: String, backgroundColor: String, valueColor: String): TextView {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(backgroundColor, 16)
            setPadding(dp(12), dp(14), dp(8), dp(14))
        }
        item.addView(label(title, "#526174", 10, true))
        val number = label(value, valueColor, 25, true)
        item.addView(number, matchWrap().apply { topMargin = dp(7) })
        parent.addView(item, LinearLayout.LayoutParams(0, dp(88), 1f).apply {
            if (parent.childCount > 0) leftMargin = dp(7)
            rightMargin = dp(3)
        })
        return number
    }

    private fun refreshScreen() {
        if (!::status.isInitialized) return
        updateRoleStatus()
        if (::vipRingSwitch.isInitialized) syncVipRingSwitch()
        try {
            val rules = SecureRuleStore(this)
            val allow = rules.allowed().sorted()
            val block = rules.blocked().sorted()
            allowCount.text = allow.size.toString()
            blockCount.text = block.size.toString()
            rulesView.text = buildString {
                append("VIP / ALLOWED  ·  ${allow.size}\n")
                append(if (allow.isEmpty()) "No VIP numbers yet." else allow.joinToString("\n") { "✓  ${it}" })
                append("\n\nBLOCKED  ·  ${block.size}\n")
                append(if (block.isEmpty()) "No blocked numbers." else block.joinToString("\n") { "✕  ${it}" })
                append("\n\nSelect a number above to remove or unblock it.")
            }
        } catch (error: RuntimeException) {
            rulesView.text = "Encrypted rules could not be opened. Do not clear app data; investigate Android Keystore state."
            showStatus("Secure storage error: " + (error.message ?: "unknown error"))
        }

        val store = CallEventStore(this)
        val events = try { store.latest(30) } finally { store.close() }
        activityCount.text = events.size.toString()
        historyView.text = if (events.isEmpty()) {
            "No screening events recorded yet. Activity appears here after the screening role is enabled and calls arrive."
        } else events.joinToString("\n\n────────────────────\n\n") { event ->
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.timestamp))
            "${event.action.uppercase()}  ·  ${event.number}\n${whenText}\n${event.reason}"
        }
    }

    private fun importContactsNow() {
        try {
            val count = ContactImporter.importAll(this)
            importSummary.text = "Imported ${count} valid phone number(s) into your VIP list."
            showStatus("Contact import completed.")
            refreshScreen()
        } catch (error: RuntimeException) {
            showStatus("Contact import failed: " + (error.message ?: "unknown error"))
        }
    }

    private fun startVipRingSetup() {
        // Keep the switch off until both user-controlled permissions are granted.
        VipRingerOverride.setEnabled(this, false)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            phoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }
        requestNotificationPolicyAccess()
    }

    private fun requestNotificationPolicyAccess() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isNotificationPolicyAccessGranted) {
            policyAccessRequest.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } else {
            completeVipRingSetup()
        }
    }

    private fun completeVipRingSetup() {
        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val policyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || manager.isNotificationPolicyAccessGranted
        val enabled = phoneGranted && policyGranted
        VipRingerOverride.setEnabled(this, enabled)
        syncVipRingSwitch()
        showStatus(if (enabled) {
            "VIP ring override enabled. VIP calls can temporarily ring through Silent/Vibrate when Do Not Disturb is off."
        } else {
            "VIP ring override was not enabled. Grant both requested permissions in Android Settings."
        })
    }

    private fun syncVipRingSwitch() {
        if (!::vipRingSwitch.isInitialized) return
        vipRingSwitch.setOnCheckedChangeListener(null)
        vipRingSwitch.isChecked = VipRingerOverride.isEnabled(this)
        vipRingSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                VipRingerOverride.setEnabled(this, false)
                showStatus("VIP ring override is off.")
            } else startVipRingSetup()
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showStatus("Call screening role requires Android 10 or later.")
            return
        }
        val manager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            showStatus("Call screening is not available on this device.")
        } else if (manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            updateRoleStatus(true)
        } else {
            roleRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun updateRoleStatus(granted: Boolean? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Android 10 or later is required for the screening role."
            roleBadge.text = "ANDROID 10+ REQUIRED"
            return
        }
        val manager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        val enabled = granted ?: manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val message = if (enabled) "Call screening is enabled on this device." else "Call screening is not enabled yet. Enable it to start screening calls."
        status.text = message
        roleBadge.text = if (enabled) "●  SCREENING ACTIVE" else "○  SCREENING NOT ENABLED"
        roleBadge.background = rounded(if (enabled) "#174D42" else "#29415E", 30)
    }

    private fun showStatus(message: String) {
        status.text = message
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 18, "#E5EAF1")
        setPadding(dp(16), dp(16), dp(16), dp(16))
        elevation = dp(1).toFloat()
    }

    private fun sectionTitle(value: String) = label(value, "#172B45", 19, true).apply {
        setPadding(0, 0, 0, dp(2))
    }

    private fun label(value: String, color: String, size: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 14f
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setTextColor(Color.WHITE)
        background = rounded(if (primary) "#C6E7D5" else "#EAF0F7", 13)
        if (!primary) setTextColor(Color.rgb(34, 57, 83))
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun rounded(fill: String, radius: Int, stroke: String? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
