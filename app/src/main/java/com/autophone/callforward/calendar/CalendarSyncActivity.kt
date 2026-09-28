package com.autophone.callforward.calendar

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.model.Person
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 系统日历排班导入界面：申请 READ_CALENDAR 权限后只读导入未来 30 天排班，
 * 预览确认后写入 [RosterStore] 并重建调度。
 */
class CalendarSyncActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var importer: SystemCalendarImporter

    private lateinit var importButton: MaterialButton
    private lateinit var applyButton: MaterialButton
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewText: TextView
    private lateinit var statusText: TextView

    private lateinit var requestCalendarPermission: androidx.activity.result.ActivityResultLauncher<String>

    /** 预览暂存：导入到的人（含已有人员，手机号以既有为准） */
    private var previewPeople: Map<String, Person> = emptyMap()
    private var previewRoster: List<com.autophone.callforward.model.DayRoster> = emptyList()

    init {
        // registerForActivityResult 必须在 onStart 之前注册（init 期注册最稳妥）
        requestCalendarPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                runImport()
            } else {
                statusText.text = getString(R.string.cal_sync_permission_denied)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar_sync)

        store = RosterStore(this)
        importer = SystemCalendarImporter(this)

        importButton = findViewById(R.id.calSyncImportButton)
        applyButton = findViewById(R.id.calSyncApplyButton)
        previewCard = findViewById(R.id.calSyncPreviewCard)
        previewText = findViewById(R.id.calSyncPreviewText)
        statusText = findViewById(R.id.calSyncStatus)

        importButton.setOnClickListener { requestPermissionOrImport() }
        applyButton.setOnClickListener { applyImport() }
    }

    /** 已授权直接导入；否则发起运行时权限申请。 */
    private fun requestPermissionOrImport() {
        if (importer.hasPermission()) {
            runImport()
        } else {
            requestCalendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    /** 子线程查询日历，主线程展示预览。 */
    private fun runImport() {
        statusText.text = getString(R.string.cal_sync_importing)
        previewCard.visibility = View.GONE
        Thread {
            val result = try {
                val (incPeople, roster) = importer.importUpcoming()
                buildPreview(incPeople, roster)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (result == null) {
                    statusText.text = getString(R.string.cal_sync_err_import)
                    return@runOnUiThread
                }
                if (result.rosterDays == 0) {
                    statusText.text = getString(R.string.cal_sync_empty)
                    return@runOnUiThread
                }
                previewPeople = result.people
                previewRoster = result.roster
                previewText.text = getString(
                    R.string.cal_sync_preview,
                    result.rosterDays,
                    result.totalAssignments,
                )
                previewCard.visibility = View.VISIBLE
                statusText.text = ""
            }
        }.start()
    }

    /** 合并 people 增量与既有人员，统计预览数据。 */
    private fun buildPreview(
        incPeople: Map<String, String>,
        roster: List<com.autophone.callforward.model.DayRoster>,
    ): PreviewData? {
        if (roster.isEmpty()) return PreviewData(emptyMap(), roster, 0, 0)
        val existing = store.loadPeople()
        val merged = existing.toMutableMap()
        incPeople.forEach { (name, phone) -> merged.putIfAbsent(name, Person(name, phone)) }

        val totalAssignments = roster.sumOf { it.assignments.size }
        return PreviewData(merged, roster, roster.size, totalAssignments)
    }

    /** 确认导入：持久化 + 重建调度。 */
    private fun applyImport() {
        if (previewRoster.isEmpty()) return
        val peopleMap = previewPeople
        val roster = previewRoster

        Thread {
            // 先展开切换点，成功后再落盘，避免出现「已保存但闹钟未重建」的中间态
            val points = try {
                RosterEngine.fromStore(store).expandToSwitchPoints(roster, peopleMap)
            } catch (e: Exception) {
                null
            }
            if (points == null) {
                runOnUiThread {
                    statusText.text = getString(R.string.cal_sync_err_expand)
                }
                return@Thread
            }
            store.save(peopleMap.mapValues { it.value.phone }, roster)
            SwitchScheduler(this).scheduleAll(points)
            runOnUiThread {
                statusText.text = getString(
                    R.string.cal_sync_applied,
                    roster.size,
                    points.size,
                )
                previewCard.visibility = View.GONE
                Toast.makeText(this, R.string.cal_sync_done, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    /** 预览数据 */
    private data class PreviewData(
        val people: Map<String, Person>,
        val roster: List<com.autophone.callforward.model.DayRoster>,
        val rosterDays: Int,
        val totalAssignments: Int,
    )
}
