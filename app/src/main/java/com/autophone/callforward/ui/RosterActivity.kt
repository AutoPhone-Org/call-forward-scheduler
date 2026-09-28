package com.autophone.callforward.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridLayout
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
import java.time.YearMonth

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

    private lateinit var calendarGrid: GridLayout
    private lateinit var calendarMonthLabel: TextView
    private lateinit var calPrevButton: MaterialButton
    private lateinit var calNextButton: MaterialButton
    private lateinit var calTodayButton: MaterialButton
    private lateinit var calendarEmptyHint: TextView

    private var displayedMonth: YearMonth = YearMonth.now()
    private var rosterByDate: Map<String, List<Assignment>> = emptyMap()

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
        setupCalendar()

        saveButton.setOnClickListener { saveRoster() }

        // 默认日期为今天
        dateInput.setText(LocalDate.now().toString())
        refreshCalendar()
    }

    override fun onResume() {
        super.onResume()
        loadPeople()
        refreshCalendar()
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
        refreshCalendar()
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

    // ---------- 月历 ----------

    private fun setupCalendar() {
        calendarMonthLabel = findViewById(R.id.calendarMonthLabel)
        calendarGrid = findViewById(R.id.calendarGrid)
        calPrevButton = findViewById(R.id.calPrevButton)
        calNextButton = findViewById(R.id.calNextButton)
        calTodayButton = findViewById(R.id.calTodayButton)
        calendarEmptyHint = findViewById(R.id.calendarEmptyHint)

        calPrevButton.setOnClickListener {
            displayedMonth = displayedMonth.minusMonths(1)
            renderCalendar()
        }
        calNextButton.setOnClickListener {
            displayedMonth = displayedMonth.plusMonths(1)
            renderCalendar()
        }
        calTodayButton.setOnClickListener {
            displayedMonth = YearMonth.now()
            renderCalendar()
        }
    }

    /** 重新加载排班数据并渲染月历。 */
    private fun refreshCalendar() {
        rosterByDate = store.loadRoster().associate { it.date to it.assignments }
        renderCalendar()
    }

    /** 渲染当前月的日历网格（周一起始）。 */
    private fun renderCalendar() {
        val month = displayedMonth
        calendarMonthLabel.text = getString(R.string.cal_month_title, month.year, month.monthValue)
        calendarEmptyHint.visibility =
            if (rosterByDate.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        calendarGrid.removeAllViews()

        val weekdays = resources.getStringArray(R.array.cal_weekdays)
        for (name in weekdays) {
            val header = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }
            calendarGrid.addView(header, weekHeaderParams())
        }

        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1 // 周一 = 0
        val rows = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        val today = LocalDate.now()

        for (i in 0 until rows) {
            val dayIndex = i - offset
            if (dayIndex < 0 || dayIndex >= month.lengthOfMonth()) {
                calendarGrid.addView(android.view.View(this), cellParams())
            } else {
                val date = month.atDay(dayIndex + 1)
                calendarGrid.addView(buildCell(date, date == today), cellParams())
            }
        }
    }

    private fun buildCell(date: LocalDate, isToday: Boolean): android.view.View {
        val assignments = rosterByDate[date.toString()].orEmpty()
        val hasRoster = assignments.isNotEmpty()

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.TOP
            background = cellBackground(isToday, hasRoster)
        }

        val dayNum = TextView(this).apply {
            text = date.dayOfMonth.toString()
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(if (isToday) android.R.color.white else R.color.text_primary))
        }
        cell.addView(dayNum)

        for (a in assignments) {
            val line = TextView(this).apply {
                text = "${a.shiftName} ${a.personName}"
                textSize = 10f
                maxLines = 1
                setTextColor(getColor(if (isToday) android.R.color.white else R.color.text_secondary))
            }
            cell.addView(line)
        }
        return cell
    }

    private fun cellBackground(isToday: Boolean, hasRoster: Boolean): android.graphics.drawable.Drawable {
        val gd = GradientDrawable()
        gd.cornerRadius = dp(6).toFloat()
        gd.setStroke(dp(1), getColor(R.color.divider))
        gd.setColor(getColor(when {
            isToday -> R.color.cal_today_bg
            hasRoster -> R.color.cal_scheduled_bg
            else -> R.color.cal_cell_bg
        }))
        return gd
    }

    private fun cellParams(): GridLayout.LayoutParams {
        val lp = GridLayout.LayoutParams()
        lp.width = 0
        lp.height = dp(68)
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        return lp
    }

    private fun weekHeaderParams(): GridLayout.LayoutParams {
        val lp = GridLayout.LayoutParams()
        lp.width = 0
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        lp.setMargins(dp(2), 0, dp(2), dp(4))
        return lp
    }
}
