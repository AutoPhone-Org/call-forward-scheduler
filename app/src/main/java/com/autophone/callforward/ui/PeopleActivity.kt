package com.autophone.callforward.ui

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.io.RosterFileImporter
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.Person
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 人员管理界面：列表展示人员 + 手机号，支持新增/删除。
 * 数据经 [RosterStore] 持久化，删除时同步清理排班中对该人员的引用（置空指派）。
 */
class PeopleActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var fileImporter: RosterFileImporter
    private lateinit var listView: ListView
    private lateinit var emptyText: android.widget.TextView
    private lateinit var addButton: MaterialButton

    private var people = linkedMapOf<String, String>()

    // SAF 合同必须在 onStart 之前注册（属性初始化即满足）
    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) exportCsvTo(uri)
        }

    private val importCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importCsvFrom(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_people)

        store = RosterStore(this)
        fileImporter = RosterFileImporter(this)
        listView = findViewById(R.id.peopleList)
        emptyText = findViewById(R.id.peopleEmpty)
        addButton = findViewById(R.id.peopleAddButton)

        loadPeople()
        addButton.setOnClickListener { showAddDialog() }
        findViewById<MaterialButton>(R.id.peopleImportButton).setOnClickListener {
            importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
        }
        findViewById<MaterialButton>(R.id.peopleExportButton).setOnClickListener {
            exportCsvLauncher.launch(fileImporter.defaultFileName())
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            people.keys.elementAtOrNull(position)?.let { showDeleteDialog(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPeople()
    }

    private fun loadPeople() {
        people = store.loadPeople().mapValues { it.value.phone }.toMap(linkedMapOf())
        render()
    }

    private fun render() {
        emptyText.visibility = if (people.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        val rows = people.entries.map { (name, phone) -> "$name  ($phone)" }
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, rows
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<android.widget.TextView>(android.R.id.text1)
                tv.setTextColor(getColor(R.color.text_primary))
                tv.textSize = 16f
                return view
            }
        }
        listView.adapter = adapter
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        val nameLayout = TextInputLayout(this).apply {
            hint = getString(R.string.people_name_hint)
        }
        val nameInput = TextInputEditText(this).apply {
            setSingleLine(true)
        }
        nameLayout.addView(nameInput)
        container.addView(nameLayout)

        val phoneLayout = TextInputLayout(this).apply {
            hint = getString(R.string.people_phone_hint)
        }
        val phoneInput = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        phoneLayout.addView(phoneInput)
        container.addView(phoneLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.people_add)
            .setView(container)
            .setPositiveButton(R.string.people_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val phone = phoneInput.text?.toString()?.trim().orEmpty()
                addPerson(name, phone)
            }
            .setNegativeButton(R.string.people_cancel, null)
            .show()
    }

    private fun addPerson(name: String, phone: String) {
        when {
            name.isEmpty() -> toast(R.string.people_err_name_empty)
            phone.isEmpty() -> toast(R.string.people_err_phone_empty)
            people.containsKey(name) -> toast(R.string.people_err_duplicate)
            else -> {
                people[name] = phone
                persist()
                render()
            }
        }
    }

    private fun showDeleteDialog(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.people_delete_title)
            .setMessage(getString(R.string.people_delete_confirm, name))
            .setPositiveButton(R.string.people_confirm) { _, _ ->
                people.remove(name)
                // 清理排班中对被删人员的引用后一并保存
                val roster = store.loadRoster().map { day ->
                    DayRoster(
                        date = day.date,
                        assignments = day.assignments.filterNot { it.personName == name },
                    )
                }
                store.save(people, roster)
                render()
            }
            .setNegativeButton(R.string.people_cancel, null)
            .show()
    }

    private fun persist() {
        store.save(people, store.loadRoster())
    }

    // ---------- CSV 导出 / 导入（SAF） ----------

    private fun exportCsvTo(uri: Uri) {
        try {
            fileImporter.writeCsv(uri, people, store.loadRoster())
            toast(R.string.csv_export_ok)
        } catch (e: Exception) {
            toast(getString(R.string.csv_err_write, e.message ?: getString(R.string.csv_err_unknown)))
        }
    }

    private fun importCsvFrom(uri: Uri) {
        try {
            val (imported, importedRoster) = fileImporter.readCsv(uri)
            if (imported.isEmpty() && importedRoster.isEmpty()) {
                toast(R.string.csv_import_no_data)
                return
            }
            // 合并写入：文件内人员/日期覆盖本地同名条目，其余保留
            val mergedPeople = linkedMapOf<String, String>()
            mergedPeople.putAll(people)
            mergedPeople.putAll(imported)

            val mergedRoster = store.loadRoster().associateBy { it.date }.toMutableMap()
            importedRoster.forEach { day -> mergedRoster[day.date] = day }
            val roster = mergedRoster.values.sortedBy { it.date }

            store.save(mergedPeople, roster)
            rebuildSchedule(mergedPeople, roster)

            loadPeople()
            toast(getString(R.string.csv_import_ok, imported.size, importedRoster.size))
        } catch (e: Exception) {
            toast(getString(R.string.csv_err_read, e.message ?: getString(R.string.csv_err_unknown)))
        }
    }

    /** 导入后立即重建调度，让新排班马上生效 */
    private fun rebuildSchedule(mergedPeople: Map<String, String>, roster: List<DayRoster>) {
        try {
            val peopleMap = mergedPeople.mapValues { (name, phone) -> Person(name, phone) }
            val points = RosterEngine.fromStore(store).expandToSwitchPoints(roster, peopleMap)
            SwitchScheduler(this).scheduleAll(points)
        } catch (_: Exception) {
            // 排班展开失败（如班次/人员无法识别）不影响数据导入，调度保持原状
        }
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }
}
