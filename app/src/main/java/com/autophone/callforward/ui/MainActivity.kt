package com.autophone.callforward.ui

import android.os.Bundle
import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.calendar.CalendarSyncActivity
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.deeplink.DeepLinkImporter
import com.autophone.callforward.executor.CallForwardExecutor
import com.autophone.callforward.model.ForwardType
import com.autophone.callforward.notify.NotificationHelper
import com.autophone.callforward.scheduler.AlarmPermissionHelper
import com.autophone.callforward.sync.CloudSyncActivity
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
    private val alarmPermissionHelper by lazy { AlarmPermissionHelper(this) }

    private lateinit var statusText: TextView
    private lateinit var forwardTypeSpinner: Spinner
    private lateinit var targetPhoneInput: TextInputEditText
    private lateinit var grantButton: Button
    private lateinit var setButton: Button
    private lateinit var cancelButton: Button
    private lateinit var peopleEntryButton: Button
    private lateinit var rosterEntryButton: Button
    private lateinit var shiftTimesEntryButton: Button
    private lateinit var calendarSyncEntryButton: Button
    private lateinit var cloudSyncEntryButton: Button

    private val permissionListener = OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = RosterStore(this)
        notifier = NotificationHelper(applicationContext)

        statusText = findViewById(R.id.statusText)
        forwardTypeSpinner = findViewById(R.id.forwardTypeSpinner)
        targetPhoneInput = findViewById(R.id.targetPhoneInput)
        grantButton = findViewById(R.id.grantButton)
        setButton = findViewById(R.id.setButton)
        cancelButton = findViewById(R.id.cancelButton)
        peopleEntryButton = findViewById(R.id.peopleEntryButton)
        rosterEntryButton = findViewById(R.id.rosterEntryButton)
        shiftTimesEntryButton = findViewById(R.id.shiftTimesEntryButton)
        calendarSyncEntryButton = findViewById(R.id.calendarSyncEntryButton)
        cloudSyncEntryButton = findViewById(R.id.cloudSyncEntryButton)

        setupForwardTypeSpinner()
        loadSavedConfig()

        grantButton.setOnClickListener { requestShizuku() }
        setButton.setOnClickListener { applyForward() }
        cancelButton.setOnClickListener { cancelForward() }
        peopleEntryButton.setOnClickListener {
            startActivity(Intent(this, PeopleActivity::class.java))
        }
        rosterEntryButton.setOnClickListener {
            startActivity(Intent(this, RosterActivity::class.java))
        }
        shiftTimesEntryButton.setOnClickListener {
            startActivity(Intent(this, ShiftTimesActivity::class.java))
        }
        calendarSyncEntryButton.setOnClickListener {
            startActivity(Intent(this, CalendarSyncActivity::class.java))
        }
        cloudSyncEntryButton.setOnClickListener {
            startActivity(Intent(this, CloudSyncActivity::class.java))
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        updateStatus()
        handleImportIntent(intent)
        checkUpdate()
        checkExactAlarmPermission()
    }

    override fun onResume() {
        super.onResume()
        checkExactAlarmPermission()
    }

    /** 检查精确闹钟权限；Android 12+ 未授权时引导用户去设置页开启。 */
    private fun checkExactAlarmPermission() {
        if (alarmPermissionHelper.needsExactAlarmPermission()) {
            alarmPermissionHelper.showPermissionDialog(this)
        }
    }

    /** 异步检查更新；有新版时弹下载安装对话框。 */
    private fun checkUpdate() {
        Thread {
            val checker = com.autophone.callforward.update.UpdateChecker(this)
            val info = checker.check()
            runOnUiThread {
                if (info.hasUpdate && info.downloadUrl != null) {
                    showUpdateDialog(info.latestVersion ?: "", info.downloadUrl!!)
                }
            }
        }.start()
    }

    /** 弹出更新对话框，引导下载并安装。 */
    private fun showUpdateDialog(version: String, downloadUrl: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_dialog_title)
            .setMessage(getString(R.string.update_dialog_body, version))
            .setPositiveButton(R.string.update_download) { _, _ ->
                startDownload(downloadUrl, version)
            }
            .setNegativeButton(R.string.update_cancel, null)
            .show()
    }

    /** 后台下载 APK，完成后引导安装。 */
    private fun startDownload(downloadUrl: String, tag: String) {
        val downloader = com.autophone.callforward.update.ApkDownloader(this)
        val installer = com.autophone.callforward.update.ApkInstaller(this)

        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show()

        Thread {
            val progressCallback = object : com.autophone.callforward.update.ApkDownloader.ProgressCallback {
                override fun onProgress(downloaded: Long, total: Long) {
                    val percent = if (total > 0) {
                        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    notifier.notifyDownloadProgress(percent)
                }
            }
            val result = downloader.download(downloadUrl, tag, progressCallback)
            runOnUiThread {
                if (result.success && result.file != null) {
                    notifier.notifyDownloadDone(result.file)
                    installer.install(result.file)
                } else {
                    notifier.notifyDownloadFail()
                }
            }
        }.start()
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

    /** 处理 Deep Link 导入（scheme=callforward, host=import）。 */
    private fun handleImportIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "callforward" || data.host != "import") return

        val result = DeepLinkImporter(this).import(data)
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        if (result.success) {
            notifier.notifyImportSuccess(result.peopleCount, result.rosterDays)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}
