package com.autophone.callforward.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.executor.CallForwardExecutor
import com.autophone.callforward.model.ForwardType
import com.autophone.callforward.notify.NotificationHelper
import com.google.android.material.textfield.TextInputEditText
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

/**
 * 主界面：白色背景主题，支持配置转移类型、目标号码，并持久化。
 */
class MainActivity : AppCompatActivity() {

    private val executor = CallForwardExecutor()
    private lateinit var store: RosterStore
    private lateinit var notifier: NotificationHelper

    private lateinit var statusText: TextView
    private lateinit var forwardTypeSpinner: Spinner
    private lateinit var targetPhoneInput: TextInputEditText
    private lateinit var grantButton: Button
    private lateinit var setButton: Button
    private lateinit var cancelButton: Button

    private val permissionListener = OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = RosterStore(this)
        notifier = NotificationHelper(this)

        statusText = findViewById(R.id.statusText)
        forwardTypeSpinner = findViewById(R.id.forwardTypeSpinner)
        targetPhoneInput = findViewById(R.id.targetPhoneInput)
        grantButton = findViewById(R.id.grantButton)
        setButton = findViewById(R.id.setButton)
        cancelButton = findViewById(R.id.cancelButton)

        setupForwardTypeSpinner()
        loadSavedConfig()

        grantButton.setOnClickListener { requestShizuku() }
        setButton.setOnClickListener { applyForward() }
        cancelButton.setOnClickListener { cancelForward() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        updateStatus()
    }

    /** 转移类型下拉框 */
    private fun setupForwardTypeSpinner() {
        val labels = ForwardType.values().map { typeLabel(it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        forwardTypeSpinner.adapter = adapter
    }

    /** 读取已保存配置 */
    private fun loadSavedConfig() {
        val savedType = store.loadForwardType()
        forwardTypeSpinner.setSelection(ForwardType.values().indexOf(savedType))
        val savedPhone = store.getSetting("targetPhone", getString(R.string.default_target_phone))
        targetPhoneInput.setText(savedPhone)
    }

    /** 保存当前配置 */
    private fun saveConfig() {
        val type = ForwardType.values()[forwardTypeSpinner.selectedItemPosition]
        store.saveForwardType(type)
        store.saveSetting("targetPhone", targetPhoneInput.text?.toString()?.trim() ?: "")
    }

    private fun typeLabel(type: ForwardType): String {
        return when (type) {
            ForwardType.UNCONDITIONAL -> getString(R.string.type_unconditional)
            ForwardType.BUSY -> getString(R.string.type_busy)
            ForwardType.NO_ANSWER -> getString(R.string.type_no_answer)
            ForwardType.UNREACHABLE -> getString(R.string.type_unreachable)
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            statusText.text = getString(R.string.status_grant_missing)
            return
        }
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateStatus()
        } else {
            Shizuku.requestPermission(100)
        }
    }

    private fun applyForward() {
        val phone = targetPhoneInput.text?.toString()?.trim().orEmpty()
        if (phone.isEmpty()) {
            statusText.text = getString(R.string.error_empty_phone)
            return
        }
        val type = ForwardType.values()[forwardTypeSpinner.selectedItemPosition]
        saveConfig()

        Thread {
            val r = executor.setForward(phone, type)
            notifier.notifySwitchResult("", phone, r.success, r.message)
            runOnUiThread { statusText.text = r.message }
        }.start()
    }

    private fun cancelForward() {
        Thread {
            val r = executor.cancelAll()
            notifier.notifySwitchResult("", "", r.success, r.message)
            runOnUiThread { statusText.text = r.message }
        }.start()
    }

    private fun updateStatus() {
        val ready = executor.isReady
        val perm = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        statusText.text = when {
            !ready -> getString(R.string.status_not_running)
            !perm -> getString(R.string.status_no_permission)
            else -> getString(R.string.status_ready)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}
