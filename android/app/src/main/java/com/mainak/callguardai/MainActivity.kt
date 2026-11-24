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
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mainak.callguardai.contacts.ContactImporter
import com.mainak.callguardai.data.CallEventStore
import com.mainak.callguardai.data.SecureRuleStore
import com.mainak.callguardai.screening.CallGuardPolicy
import com.mainak.callguardai.screening.VipRingerOverride
import java.text.DateFormat
import java.util.Date

class MainActivity : FragmentActivity() {
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
    private lateinit var ringerStatus: TextView
    private lateinit var dndAccessStatus: TextView
    private lateinit var biometricLockSwitch: Switch
    private var biometricPromptActive = false
    private var appUnlocked = false
    private var returnLockPending = false

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateRoleStatus()
    }
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data
            if (contactUri == null) {
                showStatus("No contact was selected.")
            } else {
                try {
                    val projection = arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                            val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                            if (number.isNotBlank() && ContactImporter.addSelected(this, name, number)) {
                                showStatus("${name.ifBlank { "Contact" }} added to VIP. Only the contact you selected was saved.")
                                refreshScreen()
                            } else {
                                showStatus("This contact has no valid phone number for region ${CallGuardPolicy.selectedRegion(this)}.")
                            }
                        } else showStatus("Could not read the selected contact.")
                    } ?: showStatus("Could not open the selected contact.")
                } catch (error: RuntimeException) {
                    showStatus("Could not add this contact: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private val callScreeningContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestScreeningRoleNow()
        else {
            showStatus("Contacts access was declined. CallGuard can still screen eligible unknown callers, but Android may not send saved-contact calls to this app.")
            requestScreeningRoleNow()
        }
    }

    private val screeningContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestPhoneStatePermission()
        else {
            VipRingerOverride.setEnabled(this, false)
            syncVipRingSwitch()
            showStatus("Contacts access is needed so Android sends saved-contact calls to CallGuard for VIP matching.")
        }
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
        if (isBiometricLockEnabled()) {
            showBiometricGate()
        } else {
            buildScreen()
            refreshScreen()
        }
    }

    override fun onStart() {
        super.onStart()
        if (returnLockPending && isBiometricLockEnabled()) {
            returnLockPending = false
            showBiometricGate()
        }
    }

    override fun onStop() {
        if (isBiometricLockEnabled() && appUnlocked && !biometricPromptActive) {
            appUnlocked = false
            returnLockPending = true
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (telephony.callState == TelephonyManager.CALL_STATE_IDLE) {
                VipRingerOverride.restoreOriginalMode(this)
            }
        }
        if (::status.isInitialized) refreshScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(241, 246, 250))
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
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(10, 28, 48), Color.rgb(17, 78, 91))).apply { cornerRadius = dp(26).toFloat() }
            setPadding(dp(22), dp(24), dp(22), dp(22))
        }
        hero.addView(label("✦  CALLGUARD AI  /  SMART CALL PROTECTION", "#9DE9DF", 11, true))
        hero.addView(label("Your calls.\nYour rules.", "#FFFFFF", 32, true).apply {
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
        hero.addView(actionButton("Set as caller ID & spam app", true) { requestScreeningRole() }.apply {
            setTextColor(Color.rgb(20, 36, 58))
        }, matchWrap().apply { topMargin = dp(14) })
        hero.addView(label("CallGuard controls and preferences stay inside this app. Android itself displays a system-owned confirmation when you grant the Call Screening role; this is required by Android and cannot be replaced with an in-app switch.", "#D6E1EF", 12, false), matchWrap().apply { topMargin = dp(8) })
        hero.addView(label("CallGuard does not take over your default Phone/Dialer app or draw over other apps. Android permits only one app to hold the Call Screening role at a time. Truecaller can remain your dialer, but it cannot share that same screening role with CallGuard.", "#D6E1EF", 12, false), matchWrap().apply { topMargin = dp(8) })
        content.addView(hero, matchWrap())

        status = label("Checking call screening status…", "#334155", 13, true).apply {
            background = rounded("#FFFFFF", 16, "#DCE7EF")
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
        contactsCard.addView(label("Add one contact as VIP", "#172B45", 16, true))
        contactsCard.addView(label("Choose a single person from your phone contacts. Android returns you to CallGuard, and that selected phone number is normalized and saved to your VIP list. No bulk import.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(6) })
        importSummary = label("No contact selected yet.", "#147A48", 12, true)
        contactsCard.addView(importSummary, matchWrap().apply { topMargin = dp(10) })
        contactsCard.addView(actionButton("Select contact and add to VIP", true) {
            // ACTION_PICK grants temporary access to the one selected phone row.
            // Broad READ_CONTACTS access is requested separately only for call screening.
            launchContactPicker()
        }, matchWrap().apply { topMargin = dp(12) })
        content.addView(contactsCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("VIP ring override"), matchWrap().apply { topMargin = dp(24) })
        val vipCard = card()
        vipCard.addView(label("Let VIP callers ring through Silent / Vibrate / DND", "#172B45", 16, true))
        vipCard.addView(label("When an explicitly allowed VIP calls, CallGuard temporarily switches Silent/Vibrate to Ring and pauses the active Do Not Disturb filter. It restores the saved phone modes after the call ends. This requires Android permissions and may be restricted by some devices.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(6) })
        ringerStatus = label("Current phone mode: checking…", "#245A9B", 12, true).apply {
            background = rounded("#EAF1FC", 10)
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }
        vipCard.addView(ringerStatus, matchWrap().apply { topMargin = dp(10) })
        dndAccessStatus = label("Do Not Disturb access: checking…", "#8A5A18", 13, true).apply {
            background = rounded("#FFF5E5", 10)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        vipCard.addView(dndAccessStatus, matchWrap().apply { topMargin = dp(10) })
        vipCard.addView(actionButton("Grant / manage DND access", true) {
            openNotificationPolicySettings()
        }, matchWrap().apply { topMargin = dp(8) })
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
        vipCard.addView(label("Grant CallGuard AI access on the Android screen that opens, then return here. Requires Contacts, Phone state and Notification Policy Access. When this feature is enabled, DND may be paused temporarily for a VIP call and then restored. SMS and overlay permissions are not needed.", "#8A5A18", 12, false), matchWrap().apply { topMargin = dp(8) })
        content.addView(vipCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("Your rules"), matchWrap().apply { topMargin = dp(24) })
        val rulesCard = card()
        rulesView = label("Loading your encrypted rules…", "#334155", 13, false)
        rulesView.setTextIsSelectable(true)
        rulesView.setOnClickListener { showRuleManagementDialog() }
        rulesCard.addView(rulesView, matchWrap())
        content.addView(rulesCard, matchWrap().apply { topMargin = dp(10) })

        content.addView(sectionTitle("App preferences"), matchWrap().apply { topMargin = dp(24) })
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

        settingsCard.addView(label("Fingerprint app lock", "#172B45", 16, true), matchWrap().apply { topMargin = dp(18) })
        settingsCard.addView(label("Require Android biometric authentication before opening your CallGuard rules and activity. Fingerprint data stays with Android and is never collected by CallGuard.", "#64748B", 13, false), matchWrap().apply { topMargin = dp(5) })
        biometricLockSwitch = Switch(this).apply {
            text = "Require fingerprint to open app"
            textSize = 14f
            setTextColor(Color.rgb(34, 57, 83))
            isChecked = isBiometricLockEnabled()
            setOnCheckedChangeListener { _, enabled ->
                isEnabled = false
                authenticateBiometric(
                    if (enabled) "Enable CallGuard fingerprint lock" else "Disable CallGuard fingerprint lock",
                    if (enabled) "Confirm your fingerprint to protect this app." else "Confirm your fingerprint to turn off app lock.",
                    onSuccess = {
                        appUnlocked = true
                        setBiometricLockEnabled(enabled)
                        biometricLockSwitch.isChecked = enabled
                        biometricLockSwitch.isEnabled = true
                        showStatus(if (enabled) "Fingerprint app lock enabled." else "Fingerprint app lock disabled.")
                    },
                    onFailure = { message ->
                        biometricLockSwitch.setOnCheckedChangeListener(null)
                        biometricLockSwitch.isChecked = isBiometricLockEnabled()
                        biometricLockSwitch.isEnabled = true
                        biometricLockSwitch.setOnCheckedChangeListener { _, checked -> toggleBiometricLock(checked) }
                        showStatus(message)
                    }
                )
            }
        }
        settingsCard.addView(biometricLockSwitch, matchWrap().apply { topMargin = dp(8) })
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

    private fun isBiometricLockEnabled(): Boolean =
        getSharedPreferences("callguard_app_security", Context.MODE_PRIVATE)
            .getBoolean("biometric_lock_enabled", false)

    private fun setBiometricLockEnabled(enabled: Boolean) {
        getSharedPreferences("callguard_app_security", Context.MODE_PRIVATE)
            .edit().putBoolean("biometric_lock_enabled", enabled).commit()
    }

    private fun toggleBiometricLock(enabled: Boolean) {
        biometricLockSwitch.isEnabled = false
        authenticateBiometric(
            if (enabled) "Enable CallGuard fingerprint lock" else "Disable CallGuard fingerprint lock",
            "Confirm your fingerprint to update app security.",
            onSuccess = {
                appUnlocked = true
                setBiometricLockEnabled(enabled)
                biometricLockSwitch.isChecked = enabled
                biometricLockSwitch.isEnabled = true
                showStatus(if (enabled) "Fingerprint app lock enabled." else "Fingerprint app lock disabled.")
            },
            onFailure = { message ->
                biometricLockSwitch.setOnCheckedChangeListener(null)
                biometricLockSwitch.isChecked = isBiometricLockEnabled()
                biometricLockSwitch.isEnabled = true
                biometricLockSwitch.setOnCheckedChangeListener { _, checked -> toggleBiometricLock(checked) }
                showStatus(message)
            }
        )
    }

    private fun showBiometricGate() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(10, 28, 48))
        }
        val message = label("CALLGUARD AI\n\nApp locked\nAuthenticate with your fingerprint to continue.", "#FFFFFF", 20, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        root.addView(message, matchWrap())
        root.addView(actionButton("Unlock with fingerprint", true) {
            authenticateBiometric(
                "Unlock CallGuard AI",
                "Authenticate with your enrolled fingerprint.",
                onSuccess = { appUnlocked = true; buildScreen(); refreshScreen() },
                onFailure = { message.text = "Authentication required.\n$message" }
            )
        }, matchWrap().apply { topMargin = dp(22) })
        setContentView(root)
        root.post {
            authenticateBiometric(
                "Unlock CallGuard AI",
                "Authenticate with your enrolled fingerprint.",
                onSuccess = { buildScreen(); refreshScreen() },
                onFailure = { message.text = "Authentication required. Tap below to try again." }
            )
        }
    }

    private fun authenticateBiometric(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val availability = BiometricManager.from(this).canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            val reason = when (availability) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no supported biometric sensor."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable."
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a fingerprint in Android device security before enabling app lock."
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Update Android security before using biometric authentication."
                else -> "Strong biometric authentication is not currently available on this device."
            }
            onFailure(reason)
            return
        }
        biometricPromptActive = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptActive = false
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPromptActive = false
                    onFailure("Authentication was not completed: $errString")
                }
                override fun onAuthenticationFailed() {
                    // Keep the system prompt open so the user can retry.
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun statCard(parent: LinearLayout, title: String, value: String, backgroundColor: String, valueColor: String): TextView {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(backgroundColor, 18)
            setPadding(dp(13), dp(15), dp(10), dp(15))
        }
        item.addView(label(title, "#526174", 10, true))
        val number = label(value, valueColor, 25, true)
        item.addView(number, matchWrap().apply { topMargin = dp(7) })
        parent.addView(item, LinearLayout.LayoutParams(0, dp(94), 1f).apply {
            if (parent.childCount > 0) leftMargin = dp(7)
            rightMargin = dp(3)
        })
        return number
    }

    private fun refreshScreen() {
        if (!::status.isInitialized) return
        updateRoleStatus()
        if (::vipRingSwitch.isInitialized) syncVipRingSwitch()
        if (::ringerStatus.isInitialized) updateRingerStatus()
        try {
            val rules = SecureRuleStore(this)
            val allow = rules.allowed().sorted()
            val block = rules.blocked().sorted()
            allowCount.text = allow.size.toString()
            blockCount.text = block.size.toString()
            rulesView.text = buildString {
                append("VIP / ALLOWED  ·  ${allow.size}\n")
                append(if (allow.isEmpty()) "No VIP numbers yet." else allow.joinToString("\n") {
                    val contactName = ContactImporter.displayName(this@MainActivity, it)
                    if (contactName.isNullOrBlank()) "✓  $it" else "✓  $contactName  ·  $it"
                })
                append("\n\nBLOCKED  ·  ${block.size}\n")
                append(if (block.isEmpty()) "No blocked numbers." else block.joinToString("\n") { "✕  ${it}" })
                append("\n\nTap here to manage or remove a rule.")
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

    private fun showRuleManagementDialog() {
        val rules = SecureRuleStore(this)
        val allow = rules.allowed().sorted()
        val block = rules.blocked().sorted()
        val entries = mutableListOf<Pair<String, () -> Unit>>()
        allow.forEach { number ->
            val name = ContactImporter.displayName(this, number)
            entries += ("Remove VIP: " + (name?.let { "$it · " } ?: "") + number) to {
                CallGuardPolicy.removeAllow(this, number)
                getSharedPreferences("callguard_imported_contact_labels", Context.MODE_PRIVATE)
                    .edit().remove(number).apply()
                refreshScreen()
                showStatus("VIP number removed.")
            }
        }
        block.forEach { number ->
            entries += "Unblock: $number" to {
                CallGuardPolicy.removeBlock(this, number)
                refreshScreen()
                showStatus("Blocked number removed from your block-list.")
            }
        }
        if (entries.isEmpty()) {
            showStatus("There are no saved rules to manage.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Manage saved rules")
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            setDataAndType(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            )
        }
        contactPicker.launch(intent)
    }

    private fun updateRingerStatus() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mode = when (audio.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT -> "Silent"
            android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal / Ring"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dnd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val policyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || manager.isNotificationPolicyAccessGranted
        if (::dndAccessStatus.isInitialized) {
            dndAccessStatus.text = if (policyGranted) {
                "✓ Do Not Disturb access: GRANTED\nCallGuard is authorized to temporarily change DND for an explicitly saved VIP call."
            } else {
                "⚠ Do Not Disturb access: NOT GRANTED\\nTap “Grant / manage DND access” below, enable CallGuard AI in Android settings, then return to this app."
            }
            dndAccessStatus.setTextColor(Color.rgb(if (policyGranted) 20 else 138, if (policyGranted) 122 else 90, if (policyGranted) 72 else 24))
            dndAccessStatus.background = rounded(if (policyGranted) "#E8F5EE" else "#FFF5E5", 10)
        }
        val access = if (contactsGranted && phoneGranted && policyGranted) "Required access: granted" else {
            "Access missing: " + listOfNotNull(
                if (!contactsGranted) "Contacts" else null,
                if (!phoneGranted) "Phone state" else null,
                if (!policyGranted) "Do Not Disturb access" else null
            ).joinToString(", ")
        }
        ringerStatus.text = buildString {
            append("Phone mode: $mode · $access")
            if (dnd) append(" · Do Not Disturb is active; VIP override will temporarily pause it for a matched VIP call if Android allows it")
        }
    }

    private fun startVipRingSetup() {
        // Contacts access lets Android deliver saved-contact calls to CallScreeningService.
        VipRingerOverride.setEnabled(this, false)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            screeningContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        requestPhoneStatePermission()
    }

    private fun requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            phoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }
        requestNotificationPolicyAccess()
    }

    private fun requestNotificationPolicyAccess() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isNotificationPolicyAccessGranted) {
            openNotificationPolicySettings()
        } else {
            completeVipRingSetup()
        }
    }

    private fun openNotificationPolicySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            completeVipRingSetup()
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.isNotificationPolicyAccessGranted) {
            completeVipRingSetup()
            return
        }
        try {
            policyAccessRequest.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Exception) {
            showStatus("Android must show its system Do Not Disturb access page for your approval. Return to CallGuard AI after granting access.")
        }
    }

    private fun completeVipRingSetup() {
        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val policyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || manager.isNotificationPolicyAccessGranted
        val enabled = contactsGranted && phoneGranted && policyGranted
        VipRingerOverride.setEnabled(this, enabled)
        syncVipRingSwitch()
        showStatus(if (enabled) {
            "VIP ring override enabled. For matched VIP calls, CallGuard will try to enable Ring mode and temporarily pause DND, then restore your previous settings after the call."
        } else {
            "VIP ring override was not enabled. Grant Contacts, Phone state, and Notification Policy Access."
        })
    }

    private fun syncVipRingSwitch() {
        if (!::vipRingSwitch.isInitialized) return
        if (VipRingerOverride.isEnabled(this) && !VipRingerOverride.hasRequiredAccess(this)) {
            VipRingerOverride.setEnabled(this, false)
        }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            callScreeningContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        requestScreeningRoleNow()
    }

    private fun requestScreeningRoleNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
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
        val message = if (enabled) "CallGuard is the active Android call-screening / caller ID app. It can work alongside Truecaller as the dialer, but only one app can hold this screening role." else "Select CallGuard AI as the Android Caller ID & spam app. If another caller ID app (such as Truecaller) owns this role, Android must transfer the role to CallGuard."
        status.text = message
        roleBadge.text = if (enabled) "●  CALLER ID & SPAM ROLE ACTIVE" else "○  CALLGUARD NOT SELECTED AS CALLER ID APP"
        roleBadge.background = rounded(if (enabled) "#174D42" else "#29415E", 30)
    }

    private fun showStatus(message: String) {
        status.text = message
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 20, "#E1EAF1")
        setPadding(dp(16), dp(16), dp(16), dp(16))
        elevation = dp(3).toFloat()
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
        background = rounded(if (primary) "#126B67" else "#E8F0F6", 14)
        if (!primary) setTextColor(Color.rgb(30, 55, 76))
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
