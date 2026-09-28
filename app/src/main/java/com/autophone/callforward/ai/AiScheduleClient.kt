package com.autophone.callforward.ai

import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容接口客户端：调用 `/chat/completions` 生成排班。
 *
 * 支持任意 OpenAI 格式的第三方大模型服务（如 DeepSeek、通义、Moonshot、
 * 本地 vLLM/Ollama 等），只需配置 baseUrl / apiKey / model。
 *
 * 认证：`Authorization: Bearer <apiKey>`（OpenAI 标准格式）。
 * 所有网络请求需在子线程调用。
 */
class AiScheduleClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    companion object {
        private const val TIMEOUT_MILLIS = 60000
        private const val MAX_TOKENS = 8192
        private const val TEMPERATURE = 0.2
    }

    data class ScheduleResult(
        val success: Boolean,
        val roster: List<DayRoster> = emptyList(),
        val message: String = "",
        val raw: String = "",
    )

    /** 调用大模型生成排班，解析返回 JSON 为 [DayRoster] 列表。 */
    fun generate(systemPrompt: String, userPrompt: String): ScheduleResult {
        return try {
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
                )
                .put("max_tokens", MAX_TOKENS)
                .put("temperature", TEMPERATURE)
                .put("stream", false)

            val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MILLIS
            conn.readTimeout = TIMEOUT_MILLIS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val resp = readStream(conn)
            conn.disconnect()

            if (code !in 200..299) {
                return ScheduleResult(false, message = "HTTP $code: ${abbreviate(resp)}")
            }

            val content = JSONObject(resp)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: ""

            if (content.isBlank()) {
                return ScheduleResult(false, message = "模型返回内容为空")
            }

            val roster = parseRoster(content)
            ScheduleResult(true, roster, "生成成功", content)
        } catch (e: Exception) {
            ScheduleResult(false, message = "请求失败: ${e.message}")
        }
    }

    /** 解析模型返回的 JSON → [DayRoster]，解析失败返回空列表。 */
    private fun parseRoster(rawContent: String): List<DayRoster> {
        return try {
            val json = SchedulePrompt.extractJson(rawContent)
            val root = JSONObject(json)
            val arr = root.optJSONArray("roster") ?: return emptyList()
            val roster = mutableListOf<DayRoster>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val date = d.optString("date")
                if (date.isBlank()) continue
                val assignments = mutableListOf<Assignment>()
                d.optJSONArray("assignments")?.let { asgArr ->
                    for (j in 0 until asgArr.length()) {
                        val a = asgArr.getJSONObject(j)
                        val shift = a.optString("shift")
                        val person = a.optString("person")
                        if (shift.isNotBlank() && person.isNotBlank()) {
                            assignments += Assignment(shift, person)
                        }
                    }
                }
                roster += DayRoster(date, assignments)
            }
            roster.sortedBy { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readStream(conn: HttpURLConnection): String {
        val stream = try {
            conn.inputStream
        } catch (e: Exception) {
            conn.errorStream
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun abbreviate(s: String): String =
        if (s.length > 300) s.substring(0, 300) + "..." else s
}
