package com.autophone.callforward.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.DefaultTemplates
import com.autophone.callforward.model.Person
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate

/**
 * 排班管理界面：选择倒班模板 + 日期，为每个班次指派人员并保存。
 * 保存后调用 [RosterEngine.expandToSwitchPoints] + [SwitchScheduler.scheduleAll] 重建调度。
 */
class RosterActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var templateSpinner: Spinner
    private lateinit var dateInput: TextInputEditText
    private lateinit var assignContainer: LinearLayout
    private lateinit var saveButton: MaterialButton
    private lateinit var hintText: TextView

    private var people = emptyMap<String, Person>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roster)

        store = RosterStore(this)
        templateSpinner = findViewById(R.id.rosterTemplateSpinner)
        dateInput = findViewById(R.id.rosterDateInput)
        assignContainer = findViewById(R.id.rosterAssignContainer)
        saveButton = findViewById(R.id.rosterSaveButton)
        hintText = findViewById(R.id.rosterHint)

        setupTemplateSpinner()
        loadPeople()

        saveButton.setOnClickListener { saveRoster() }

        // 默认日期为今天
        dateInput.setText(LocalDate.now().toString())
    }

    override fun onResume() {
        super.onResume()
        loadPeople()
    }

    private fun setupTemplateSpinner() {
        val labels = DefaultTemplates.ALL.map { it.name }
        templateSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        templateSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                renderAssignments()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun loadPeople() {
        people = store.loadPeople()
        hintText.visibility = if (people.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        renderAssignments()
    }

    /** 根据当前模板动态生成每个班次的人员下拉框。 */
    private fun renderAssignments() {
        assignContainer.removeAllViews()
        val template = currentTemplate()
        if (people.isEmpty()) return

        template.shiftNames.forEach { shiftName ->
            val label = TextView(this).apply {
                text = shiftName
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                layoutParams = lp
            }
            assignContainer.addView(label)

            val names = people.keys.toList()
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@RosterActivity, android.R.layout.simple_spinner_item, names
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                tag = shiftName
            }
            assignContainer.addView(spinner)
        }
    }

    private fun currentTemplate() = DefaultTemplates.ALL[templateSpinner.selectedItemPosition]

    private fun saveRoster() {
        val template = currentTemplate()
        if (people.isEmpty()) {
            toast(R.string.roster_err_no_people)
            return
        }
        if (people.size < template.peopleCount) {
            toast(getString(R.string.roster_err_people_not_enough, template.name, template.peopleCount, people.size))
            return
        }

        val date = dateInput.text?.toString()?.trim().orEmpty()
        if (!isValidDate(date)) {
            toast(R.string.roster_err_date_invalid)
            return
        }

        // 收集每个班次的选择
        val assignments = mutableListOf<Assignment>()
        var incomplete = false
        for (i in 0 until assignContainer.childCount) {
            val child = assignContainer.getChildAt(i)
            if (child is Spinner) {
                val shiftName = child.tag as String
                val personName = child.selectedItem as? String ?: ""
                if (personName.isEmpty()) {
                    incomplete = true
                    break
                }
                assignments += Assignment(shiftName, personName)
            }
        }
        if (incomplete) {
            toast(R.string.roster_err_assign_incomplete)
            return
        }

        // 更新该日排班（若已存在则替换）
        val roster = store.loadRoster().toMutableList()
        val existing = roster.indexOfFirst { it.date == date }
        val day = DayRoster(date, assignments)
        if (existing >= 0) roster[existing] = day else roster += day

        // 持久化 + 重建调度
        store.save(people.mapValues { it.value.phone }, roster)

        val points = RosterEngine().expandToSwitchPoints(roster, people)
        SwitchScheduler(this).scheduleAll(points)

        toast(getString(R.string.roster_saved, date))
    }

    private fun isValidDate(iso: String): Boolean {
        return try {
            LocalDate.parse(iso)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
