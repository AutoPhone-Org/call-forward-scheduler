package com.autophone.callforward.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 排班生成 Prompt 构建器。
 *
 * 通过把「人员/班次/约束」注入到 system prompt，让任意 OpenAI 兼容
 * 大模型按既定 JSON schema 输出一整月/多天的排班表。
 */
object SchedulePrompt {

    /** 输出格式说明（必须让模型严格输出该 JSON 结构） */
    private const val OUTPUT_SCHEMA = """
{
  "roster": [
    { "date": "2026-10-01", "assignments": [ { "shift": "白班", "person": "思源" } ] }
  ]
}
""".trimIndent()

    /**
     * 构建 system prompt。
     *
     * @param people 人员列表（姓名）
     * @param shifts 班次列表（名称 + 起止时间文案）
     * @param rules 排班规则/约束（如「每天白班夜班各一人，轮休」）
     * @param dateRange 日期范围描述（如「2026-10-01 至 2026-10-31」）
     * @param cycleDays 轮换周期（可选）
     */
    fun systemPrompt(
        people: List<String>,
        shifts: List<String>,
        rules: String,
        dateRange: String,
        cycleDays: Int? = null,
    ): String {
        val peopleLine = people.joinToString("、") { it }
        val shiftLine = shifts.joinToString("、") { it }
        val cycleLine = if (cycleDays != null) "\n- 轮换周期：每 $cycleDays 天循环一次" else ""

        return """
你是一个排班助手，负责根据给定的值班人员、班次和规则，生成一份排班表。

## 人员
$peopleLine

## 班次
$shiftLine

## 排班日期范围
$dateRange

## 排班规则
$rules
$cycleLine

## 输出要求
1. 只输出一个 JSON 对象，不要输出任何解释、注释或 Markdown 代码块。
2. JSON 结构必须严格如下：
$OUTPUT_SCHEMA
3. date 使用 YYYY-MM-DD 格式；shift 必须是上述班次之一；person 必须是上述人员之一。
4. 每一天的 assignments 必须覆盖当天所有需要值守的班次，不得遗漏、不得重复同一班次。
5. 按日期升序排列。
""".trimIndent()
    }

    /** 构建 user prompt（用户额外要求或留空） */
    fun userPrompt(extra: String?): String {
        return if (extra.isNullOrBlank()) {
            "请生成上述排班表。"
        } else {
            "请生成上述排班表。补充要求：$extra"
        }
    }

    /** 从模型返回文本中提取 JSON（容错：剥离可能的 ```json 代码块围栏）。 */
    fun extractJson(raw: String): String {
        var text = raw.trim()
        // 去除 Markdown 代码块围栏
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline >= 0) text = text.substring(firstNewline + 1)
            val lastFence = text.lastIndexOf("```")
            if (lastFence >= 0) text = text.substring(0, lastFence)
            text = text.trim()
        }
        // 若模型输出了说明文字 + JSON，截取第一个 { 到最后一个 }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start in 0 until end) {
            text = text.substring(start, end + 1)
        }
        return text
    }
}
