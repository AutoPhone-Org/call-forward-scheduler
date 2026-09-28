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
 * 主界面（MVP 阶段）。
 *
 * TODO：完整 UI 见 PRD §9，此处先实现最小可运行闭环：
 *  - 显示 Shizuku 授权状态
 *  - 一键取消转移
 *  - 用示例号（思源 13033696831）设置转移
 */
class MainActivity : AppCompatActivity() {

    private val executor = CallForwardExecutor()
    private val TEST_PHONE = "13033696831" // 思源（测试号）

    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var setButton: Button
    private lateinit var cancelButton: Button

    private val permissionListener = OnRequestPermissionResultListener { requestCode, grantResult ->
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
            statusText.text = "未检测到 Shizuku，请先安装并启动 Shizuku"
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
            runOnUiThread { statusText.text = "设置结果：${r.message}" }
        }.start()
    }

    private fun cancelForward() {
        Thread {
            val r = executor.cancelAll()
            runOnUiThread { statusText.text = "取消结果：${r.message}" }
        }.start()
    }

    private fun updateStatus() {
        val ready = executor.isReady
        val perm = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        statusText.text = when {
            !ready -> "Shizuku 未运行"
            !perm -> "Shizuku 已运行，但未授权本 App"
            else -> "Shizuku 就绪，可执行呼叫转移"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}
