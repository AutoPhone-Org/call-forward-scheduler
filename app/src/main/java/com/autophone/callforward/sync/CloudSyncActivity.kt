package com.autophone.callforward.sync

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.model.Person
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * GitHub Gist 云同步界面：配置 PAT / Gist ID，上传本地排班配置或从云端恢复。
 * 恢复成功后重建调度（RosterEngine + SwitchScheduler）。
 */
class CloudSyncActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var manager: GistSyncManager

    private lateinit var patInput: TextInputEditText
    private lateinit var gistInput: TextInputEditText
    private lateinit var uploadButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_sync)

        store = RosterStore(this)
        manager = GistSyncManager(this, store)

        patInput = findViewById(R.id.cloudPatInput)
        gistInput = findViewById(R.id.cloudGistInput)
        uploadButton = findViewById(R.id.cloudUploadButton)
        downloadButton = findViewById(R.id.cloudDownloadButton)
        statusText = findViewById(R.id.cloudStatus)

        // 已保存的 Gist ID 回显；PAT 已保存时不回显明文，仅提示
        gistInput.setText(manager.loadGistId())
        if (manager.loadToken().isNotBlank()) {
            patInput.setText(manager.loadToken())
        }

        uploadButton.setOnClickListener { runSync(isUpload = true) }
        downloadButton.setOnClickListener { runSync(isUpload = false) }
    }

    /** 子线程执行网络请求，主线程更新状态。 */
    private fun runSync(isUpload: Boolean) {
        val token = patInput.text?.toString()?.trim().orEmpty()
        val gistId = gistInput.text?.toString()?.trim().orEmpty()
        if (isUpload && token.isBlank()) {
            setStatus(getString(R.string.cloud_err_no_pat), R.color.status_error)
            return
        }
        if (!isUpload && gistId.isBlank()) {
            setStatus(getString(R.string.cloud_err_no_gist), R.color.status_error)
            return
        }
        // 输入的配置立即持久化，下次进入自动回填
        manager.saveConfig(token, gistId)

        setBusy(true)
        val busyRes = if (isUpload) R.string.cloud_uploading else R.string.cloud_downloading
        setStatus(getString(busyRes), R.color.text_secondary)

        Thread {
            val result = try {
                if (isUpload) {
                    val id = manager.upload(token, gistId)
                    SyncResult(true, getString(R.string.cloud_upload_ok, id))
                } else {
                    manager.download(token, gistId)
                    // 恢复后立即重建调度
                    val people = store.loadPeople()
                    val roster = store.loadRoster()
                    val points = RosterEngine().expandToSwitchPoints(roster, people)
                    SwitchScheduler(this).scheduleAll(points)
                    SyncResult(true, getString(R.string.cloud_download_ok, people.size, roster.size))
                }
            } catch (e: Exception) {
                // 只输出异常类名与消息，绝不打印 PAT / 配置内容
                SyncResult(false, getString(R.string.cloud_err_fail, e.message ?: "?"))
            }
            runOnUiThread {
                setBusy(false)
                if (result.success) {
                    setStatus(result.message, R.color.status_ready)
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                } else {
                    setStatus(result.message, R.color.status_error)
                }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        uploadButton.isEnabled = !busy
        downloadButton.isEnabled = !busy
    }

    private fun setStatus(text: String, colorRes: Int) {
        statusText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        statusText.text = text
        statusText.setTextColor(getColor(colorRes))
    }

    private data class SyncResult(val success: Boolean, val message: String)
}
