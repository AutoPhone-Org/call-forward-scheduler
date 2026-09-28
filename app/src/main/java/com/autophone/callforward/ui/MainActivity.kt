package com.autophone.callforward.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.executor.CallForwardExecutor
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

/**
 * 主界面。
 *
 * 白色背景主题，操作区使用带图标按钮（Material 3）。
 */
class MainActivity : AppCompatActivity() {

    private val executor = CallForwardExecutor()
    private val TEST_PHONE = "13033696831" // 思源（测试号）

    private lateinit var statusText: TextView
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

        statusText = findViewById(R.id.statusText)
        grantButton = findViewById(R.id.grantButton)
        setButton = findViewById(R.id.setButton)
        cancelButton = findViewById(R.id.cancelButton)

        grantButton.setOnClickListener { requestShizuku() }
        setButton.setOnClickListener { setTestForward() }
        cancelButton.setOnClickListener { cancelForward() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        updateStatus()
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

    private fun setTestForward() {
        Thread {
            val r = executor.setForward(TEST_PHONE)
            runOnUiThread { statusText.text = r.message }
        }.start()
    }

    private fun cancelForward() {
        Thread {
            val r = executor.cancelAll()
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
