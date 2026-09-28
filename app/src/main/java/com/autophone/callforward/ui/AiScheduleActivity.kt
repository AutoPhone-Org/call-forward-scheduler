package com.autophone.callforward.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.ai.AiScheduleClient
import com.autophone.callforward.ai.SchedulePrompt
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.scheduler.SwitchScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

/**
 * AI 排班界面：配置 OpenAI 兼容大模型，自动生成排班并注入到应用。
 *
 * 流程：读取已配置的人员/班次/规则 → 拼装 prompt → 调大模型 →
 * 解析返回 JSON → 保存排班 → 重建调度。
 */
class AiScheduleActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var rulesInput: TextInputEditText
    private lateinit var generateButton: MaterialButton
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_schedule)

        store = RosterStore(this)
        baseUrlInput = findViewById(R.id.aiBaseUrlInput)
        apiKeyInput = findViewById(R.id.aiApiKeyInput)
        modelInput = findViewById(R.id.aiModelInput)
        rulesInput = findViewById(R.id.aiRulesInput)
        generateButton = findViewById(R.id.aiGenerateButton)
        statusText = findViewById(R.id.aiStatusText)

        loadSavedConfig()
        generateButton.setOnClickListener { generate() }
    }

    private fun loadSavedConfig() {
        baseUrlInput.setText(store.loadAiBaseUrl())
        modelInput.setText(store.loadAiModel())
        apiKeyInput.setText(store.loadAiApiKey())
    }

    /** 生成排班（子线程调用大模型）。 */
    private fun generate() {
        val baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty()
        val apiKey = apiKeyInput.text?.toString()?.trim().orEmpty()
        val model = modelInput.text?.toString()?.trim().orEmpty()
        val rules = rulesInput.text?.toString()?.trim().orEmpty()

        if (baseUrl.isBlank() || model.isBlank()) {
            statusText.text = getString(R.string.ai_err_config)
            return
        }

        val people = store.loadPeople()
        if (people.isEmpty()) {
            statusText.text = getString(R.string.ai_err_no_people)
            return
        }

        // 保存配置（apiKey 加密落盘）
        store.saveAiConfig(baseUrl, apiKey, model)

        // 收集班次（自定义时间优先，回退默认）
        val engine = RosterEngine.fromStore(store)
        val shiftNames = com.autophone.callforward.model.DefaultTemplates.DEFAULT_SHIFTS.keys.toList()

        // 生成未来 30 天排班
        val start = LocalDate.now()
        val end = start.plusDays(29)
        val dateRange = "${start} 至 $end"

        val systemPrompt = SchedulePrompt.systemPrompt(
            people = people.keys.toList(),
            shifts = shiftNames,
            rules = rules.ifBlank { getString(R.string.ai_default_rules) },
            dateRange = dateRange,
        )
        val userPrompt = SchedulePrompt.userPrompt(null)

        setBusy(true)
        statusText.text = getString(R.string.ai_generating)

        Thread {
            val client = AiScheduleClient(baseUrl, apiKey, model)
            val result = client.generate(systemPrompt, userPrompt)

            runOnUiThread {
                setBusy(false)
                if (result.success && result.roster.isNotEmpty()) {
                    // 保存排班并重建调度
                    store.save(people.mapValues { it.value.phone }, result.roster)
                    val points = engine.expandToSwitchPoints(result.roster, people)
                    SwitchScheduler(this).scheduleAll(points)

                    statusText.text = getString(R.string.ai_generate_ok, result.roster.size)
                    Toast.makeText(
                        this, getString(R.string.ai_generate_ok, result.roster.size),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    statusText.text = getString(R.string.ai_generate_fail, result.message)
                }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        generateButton.isEnabled = !busy
    }
}
