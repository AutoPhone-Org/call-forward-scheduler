package com.autophone.callforward.sync

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * 云同步界面：支持 GitHub Gist 与 Gitee Gist 双平台。
 *
 * 平台切换时回填该平台已保存的令牌与 Gist ID；
 * 首次升级用户会从旧版单一 GitHub 配置键迁移。
 * 恢复成功后重建调度（RosterEngine + SwitchScheduler）。
 */
class CloudSyncActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var manager: GistSyncManager

    private lateinit var platformGroup: RadioGroup
    private lateinit var patInput: TextInputEditText
    private lateinit var gistInput: TextInputEditText
    private lateinit var uploadButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var statusText: TextView

    private fun currentPlatform(): GistSyncManager.SyncPlatform {
        return if (platformGroup.checkedRadioButtonId == R.id.platformGitee) {
            GistSyncManager.SyncPlatform.GITEE
        } else {
            GistSyncManager.SyncPlatform.GITHUB
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_sync)

        store = RosterStore(this)
        manager = GistSyncManager(this, store)
        migrateLegacyConfig()

        platformGroup = findViewById(R.id.platformGroup)
        patInput = findViewById(R.id.cloudPatInput)
        gistInput = findViewById(R.id.cloudGistInput)
        uploadButton = findViewById(R.id.cloudUploadButton)
        downloadButton = findViewById(R.id.cloudDownloadButton)
        statusText = findViewById(R.id.cloudStatus)

        platformGroup.setOnCheckedChangeListener { _, _ -> refillInputs() }
        refillInputs()

        uploadButton.setOnClickListener { runSync(isUpload = true) }
        downloadButton.setOnClickListener { runSync(isUpload = false) }
    }

    /** 旧版（仅 GitHub）配置键迁移到新的平台前缀键，仅执行一次。 */
    private fun migrateLegacyConfig() {
        val legacyToken = store.getSetting(GistSyncManager.LEGACY_KEY_GIST_TOKEN, "")
        val legacyGist = store.getSetting(GistSyncManager.LEGACY_KEY_GIST_ID, "")
        if (legacyToken.isNotBlank() || legacyGist.isNotBlank()) {
            if (manager.loadToken(GistSyncManager.SyncPlatform.GITHUB).isBlank()) {
                manager.saveConfig(
                    GistSyncManager.SyncPlatform.GITHUB, legacyToken, legacyGist
                )
            }
            // 清除旧键，避免重复迁移
            store.saveSetting(GistSyncManager.LEGACY_KEY_GIST_TOKEN, "")
            store.saveSetting(GistSyncManager.LEGACY_KEY_GIST_ID, "")
        }
    }

    /** 按当前平台回填已保存的配置。 */
    private fun refillInputs() {
        val platform = currentPlatform()
        patInput.setText(manager.loadToken(platform))
        gistInput.setText(manager.loadGistId(platform))
        statusText.visibility = View.GONE
    }

    /** 子线程执行网络请求，主线程更新状态。 */
    private fun runSync(isUpload: Boolean) {
        val platform = currentPlatform()
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
        // 输入的配置立即按平台持久化，下次进入自动回填
        manager.saveConfig(platform, token, gistId)

        setBusy(true)
        val busyRes = if (isUpload) R.string.cloud_uploading else R.string.cloud_downloading
        setStatus(getString(busyRes), R.color.text_secondary)

        Thread {
            val result = try {
                if (isUpload) {
                    val id = manager.upload(platform, token, gistId)
                    SyncResult(true, getString(R.string.cloud_upload_ok, platform.label, id))
                } else {
                    manager.download(platform, token, gistId)
                    // 恢复后立即重建调度
                    val people = store.loadPeople()
                    val roster = store.loadRoster()
                    val points = RosterEngine().expandToSwitchPoints(roster, people)
                    SwitchScheduler(this).scheduleAll(points)
                    SyncResult(
                        true,
                        getString(R.string.cloud_download_ok, people.size, roster.size)
                    )
                }
            } catch (e: Exception) {
                // 只输出异常类名与消息，绝不打印令牌 / 配置内容
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
