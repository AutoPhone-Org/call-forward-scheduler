package com.autophone.callforward.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.model.DefaultTemplates
import com.autophone.callforward.model.Shift
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

/**
 * 班次管理界面：为每个班次自定义名称与起止时间（HH:mm），支持新增 / 删除班次。
 *
 * 名称可为任意字符串（如「三班倒-1组白」「四班倒-1白」「第一班组张三」）。
 * 跨天自动识别：结束时间 <= 开始时间时，自动标注「跨天」，时长按次日计算。
 * 保存后持久化到 [RosterStore.saveShiftTimes]，排班引擎与月历会使用自定义班次。
 */
class ShiftTimesActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var container: LinearLayout
    private lateinit var saveButton: MaterialButton
    private lateinit var addButton: MaterialButton

    /** 行 id → 该行的输入控件引用（名称可编辑，故用行 id 而非班次名作 key）。 */
    private data class RowRef(
        val card: MaterialCardView,
        val nameInput: TextInputEditText,
        val startInput: TextInputEditText,
        val endInput: TextInputEditText,
        val statusText: TextView,
        val summaryText: TextView,
    )

    private val rowRefs = linkedMapOf<String, RowRef>()
    private var rowCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_times)

        store = RosterStore(this)
        container = findViewById(R.id.shiftTimesContainer)
        saveButton = findViewById(R.id.shiftTimesSaveButton)
        addButton = findViewById(R.id.shiftAddButton)

        buildRows()
        saveButton.setOnClickListener { save() }
        addButton.setOnClickListener { addShiftRow() }
    }

    /** 生成班次行：优先展示自定义班次，无自定义时回退到默认班次模板。 */
    private fun buildRows() {
        container.removeAllViews()
        rowRefs.clear()
        rowCounter = 0

        val custom = store.loadShiftTimes()
        val shifts = if (custom.isNotEmpty()) custom else DefaultTemplates.DEFAULT_SHIFTS
        shifts.forEach { (name, shift) ->
            container.addView(buildShiftRow(name, shift))
        }
    }

    /** 新增一个空班次行（默认名称「新班次」+ 08:00-16:00）。 */
    private fun addShiftRow() {
        val name = uniqueNewName()
        val shift = Shift(name, 8 * 60, 16 * 60, 8.0)
        container.addView(buildShiftRow(name, shift))
    }

    /** 生成不与现有名称冲突的「新班次」名称（新班次 / 新班次2 / 新班次3 …）。 */
    private fun uniqueNewName(): String {
        val base = getString(R.string.shift_new_default)
        val existing = rowRefs.values.map { it.nameInput.text?.toString()?.trim().orEmpty() }.toSet()
        if (base !in existing) return base
        var i = 2
        while ("$base$i" in existing) i++
        return "$base$i"
    }

    /** 构建单个班次编辑行。 */
    private fun buildShiftRow(name: String, shift: Shift): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.divider)
            setCardBackgroundColor(getColor(android.R.color.white))
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            layoutParams = lp
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        card.addView(inner)

        // 名称输入 + 删除按钮
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameInput = makeTextInput(name, getString(R.string.shift_name_hint))
        header.addView(
            nameInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val deleteButton = MaterialButton(this).apply {
            icon = getDrawable(R.drawable.ic_delete)
            iconTint = ColorStateList.valueOf(getColor(R.color.status_error))
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            text = getString(R.string.shift_delete)
            setTextColor(getColor(R.color.status_error))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            layoutParams = lp
        }
        header.addView(deleteButton)
        inner.addView(header)

        // 摘要 + 跨天状态
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val summaryText = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        }
        metaRow.addView(
            summaryText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val statusText = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        }
        metaRow.addView(statusText)
        inner.addView(metaRow)

        // 时间输入行
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val startInput = makeTimeInput(shift.startLabel(), getString(R.string.shift_start_hint))
        timeRow.addView(startInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sep = TextView(this).apply {
            text = "  -  "
            setTextColor(getColor(R.color.text_secondary))
        }
        timeRow.addView(sep)

        val endInput = makeTimeInput(shift.endLabel(), getString(R.string.shift_end_hint))
        timeRow.addView(endInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        inner.addView(timeRow)

        val rowId = "row-${rowCounter++}"
        rowRefs[rowId] = RowRef(card, nameInput, startInput, endInput, statusText, summaryText)

        // 监听输入，实时更新摘要与跨天状态
        val refresh = {
            val s = Shift.labelToMinute(startInput.text?.toString()?.trim().orEmpty())
            val e = Shift.labelToMinute(endInput.text?.toString()?.trim().orEmpty())
            if (s != null && e != null) {
                val overnight = e <= s
                val dur = Shift.calcDuration(s, e)
                val range = if (overnight) {
                    "${Shift.minuteToLabel(s)}-${getString(R.string.shift_next_day)}${Shift.minuteToLabel(e)}"
                } else {
                    "${Shift.minuteToLabel(s)}-${Shift.minuteToLabel(e)}"
                }
                summaryText.text = getString(R.string.shift_current_fmt, range)
                statusText.text = getString(
                    R.string.shift_status_fmt,
                    if (overnight) getString(R.string.shift_overnight) else "",
                    formatDuration(dur),
                )
                statusText.setTextColor(
                    getColor(if (overnight) R.color.status_warn else R.color.text_secondary)
                )
            } else {
                summaryText.text = ""
                statusText.text = getString(R.string.shift_time_invalid)
                statusText.setTextColor(getColor(R.color.status_error))
            }
        }
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { refresh() }
        }
        startInput.addTextChangedListener(watcher)
        endInput.addTextChangedListener(watcher)
        refresh()

        deleteButton.setOnClickListener { deleteRow(rowId) }

        return card
    }

    /** 删除某行班次；若该班次被排班表引用则 Toast 警告（不阻断）。 */
    private fun deleteRow(rowId: String) {
        val ref = rowRefs[rowId] ?: return
        val name = ref.nameInput.text?.toString()?.trim().orEmpty()

        val referenced = if (name.isEmpty()) {
            false
        } else {
            store.loadRoster().any { day -> day.assignments.any { it.shiftName == name } }
        }
        if (referenced) {
            Toast.makeText(this, getString(R.string.shift_deleted_ref_warn), Toast.LENGTH_LONG).show()
        }

        container.removeView(ref.card)
        rowRefs.remove(rowId)
    }

    private fun makeTextInput(initial: String, hintText: String): TextInputEditText {
        return TextInputEditText(this).apply {
            setText(initial)
            hint = hintText
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
    }

    private fun makeTimeInput(initial: String, hintText: String): TextInputEditText {
        return TextInputEditText(this).apply {
            setText(initial)
            hint = hintText
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
        }
    }

    private fun save() {
        val shifts = mutableMapOf<String, Shift>()
        val seen = mutableSetOf<String>()
        var hasError = false
        for (ref in rowRefs.values) {
            val name = ref.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.shift_name_empty), Toast.LENGTH_SHORT).show()
                hasError = true
                break
            }
            if (!seen.add(name)) {
                Toast.makeText(this, getString(R.string.shift_name_duplicate, name), Toast.LENGTH_SHORT).show()
                hasError = true
                break
            }
            val s = Shift.labelToMinute(ref.startInput.text?.toString()?.trim().orEmpty())
            val e = Shift.labelToMinute(ref.endInput.text?.toString()?.trim().orEmpty())
            if (s == null || e == null) {
                Toast.makeText(this, getString(R.string.shift_save_error, name), Toast.LENGTH_SHORT).show()
                hasError = true
                break
            }
            shifts[name] = Shift(name, s, e, Shift.calcDuration(s, e))
        }
        if (hasError) return

        store.saveShiftTimes(shifts)
        Toast.makeText(this, getString(R.string.shift_save_ok, shifts.size), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun formatDuration(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return if (m == 0) "${h}h" else "${h}h${m}m"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
