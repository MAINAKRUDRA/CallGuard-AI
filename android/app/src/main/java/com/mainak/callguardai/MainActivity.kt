package com.mainak.callguardai

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        updateRoleStatus(result.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        val title = TextView(this).apply { text = "CallGuard AI"; textSize = 28f }
        status = TextView(this).apply { textSize = 16f; setPadding(0, 24, 0, 24) }
        val enableButton = Button(this).apply {
            text = "Enable call screening"
            setOnClickListener { requestScreeningRole() }
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(enableButton)
        setContentView(layout)
        updateRoleStatus()
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
        status.text = if (enabled) {
            "Call screening is enabled. Unknown numbers are allowed unless you add an explicit rule."
        } else {
            "Call screening is not enabled. Grant the Android screening role to activate the service."
        }
    }
}
