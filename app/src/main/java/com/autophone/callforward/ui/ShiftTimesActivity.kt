package com.autophone.callforward.ui

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
 * 自定义班次时间界面：为每个班次设置起止时间（HH:mm）。
 *
 * 跨天自动识别：结束时间 <= 开始时间时，自动标注「跨天」，时长按次日计算。
 * 保存后持久化到 [RosterStore.saveShiftTimes]，排班引擎与月历会使用自定义时间。
 */
class ShiftTimesActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var container: LinearLayout
    private lateinit var saveButton: MaterialButton

    /** 班次名 → (开始时间输入框, 结束时间输入框, 跨天提示 TextView) */
    private data class RowRef(
        val startInput: TextInputEditText,
        val endInput: TextInputEditText,
        val statusText: TextView,
    )

    private val rowRefs = linkedMapOf<String, RowRef>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift_times)

        store = RosterStore(this)
        container = findViewById(R.id.shiftTimesContainer)
        saveButton = findViewById(R.id.shiftTimesSaveButton)

        buildRows()
        saveButton.setOnClickListener { save() }
    }

    /** 根据默认班次（或已保存的自定义时间）生成每行。 */
    private fun buildRows() {
        container.removeAllViews()
        rowRefs.clear()

        val custom = store.loadShiftTimes()
        // 以默认班次为基准，覆盖已保存的自定义时间
        DefaultTemplates.DEFAULT_SHIFTS.forEach { (name, defaultShift) ->
            val shift = custom[name] ?: defaultShift
            container.addView(buildShiftRow(name, shift))
        }
    }

    /** 构建单个班次的时间编辑行。 */
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

        // 班次名 + 跨天状态
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameView = TextView(this).apply {
            text = name
            setTextColor(getColor(R.color.text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(nameView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val statusText = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        }
        header.addView(statusText)
        inner.addView(header)

        // 时间输入行
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
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

        // 监听输入，实时更新跨天状态
        val refresh = {
            val s = Shift.labelToMinute(startInput.text?.toString()?.trim().orEmpty())
            val e = Shift.labelToMinute(endInput.text?.toString()?.trim().orEmpty())
            if (s != null && e != null) {
                val overnight = e <= s
                val dur = Shift.calcDuration(s, e)
                statusText.text = getString(
                    R.string.shift_status_fmt,
                    if (overnight) getString(R.string.shift_overnight) else "",
                    formatDuration(dur),
                )
                statusText.setTextColor(
                    getColor(if (overnight) R.color.status_warn else R.color.text_secondary)
                )
            } else {
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

        rowRefs[name] = RowRef(startInput, endInput, statusText)
        return card
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
        var hasError = false
        for ((name, ref) in rowRefs) {
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
