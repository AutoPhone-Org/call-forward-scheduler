package com.autophone.callforward.deeplink

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine
import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.ForwardType
import com.autophone.callforward.model.Person
import com.autophone.callforward.scheduler.SwitchScheduler
import org.json.JSONObject

/**
 * Deep Link 配置导入解析器。
 *
 * 协议（与静态页面约定一致）：
 *   callforward://import?config=<URL安全Base64(JSON)>
 *
 * 解析链路：取 `config` 参数 → URL decode（由 [Uri.getQueryParameter] 完成，
 * UTF-8 安全）→ Base64 解码 → UTF-8 JSON 字符串 → 反序列化为 people/roster/forwardType。
 *
 * JSON 结构示例：
 *   {"people":{"思源":"13033696831"},
 *    "forwardType":"unconditional",
 *    "roster":[{"date":"2026-09-28","assignments":[{"shift":"白班","person":"思源"}]}]}
 */
class DeepLinkImporter(private val context: Context) {

    /** 解析并导入；成功后持久化并重建调度。 */
    fun import(uri: Uri): ImportResult {
        return try {
            val rawConfig = uri.getQueryParameter("config")
            if (rawConfig.isNullOrBlank()) {
                return ImportResult(false, context.getString(com.autophone.callforward.R.string.import_err_no_config), 0, 0)
            }
            val json = decodeBase64(rawConfig)
            applyJson(json)
        } catch (e: Exception) {
            ImportResult(false, context.getString(com.autophone.callforward.R.string.import_err_parse, e.message ?: "?"), 0, 0)
        }
    }

    /**
     * Base64 解码（标准 / URL-safe 自适应），得到 UTF-8 JSON 字符串。
     *
     * 页面端经 `btoa(unescape(encodeURIComponent(json)))` 产出标准 Base64
     * （含 `+` `/` `=`），再由 [Uri.getQueryParameter] 还原；同时兼容 base64url
     * （`-` `_`，可能无 padding）。Android 的 `Base64.decode` 会静默跳过非法字符，
     * 盲目按多套 flag 依次尝试可能得到错误字节，因此这里统一化字符集后补 padding 解码。
     */
    private fun decodeBase64(input: String): String {
        var s = input.trim().replace(Regex("\\s"), "")
        if (s.contains('-') || s.contains('_')) {
            s = s.replace('-', '+').replace('_', '/')
        }
        // 补足 padding 到 4 的倍数
        val rem = s.length % 4
        if (rem != 0) s += "=".repeat(4 - rem)
        return String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    }

    /** 反序列化 JSON 并写入存储 + 重建调度。 */
    private fun applyJson(json: String): ImportResult {
        val root = JSONObject(json)

        // people: {姓名: 手机号}
        val people = mutableMapOf<String, String>()
        root.optJSONObject("people")?.let { obj ->
            obj.keys().forEach { k -> people[k] = obj.getString(k) }
        }

        // roster: [{date, assignments:[{shift, person}]}]
        val roster = mutableListOf<DayRoster>()
        root.optJSONArray("roster")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val assignments = mutableListOf<Assignment>()
                d.optJSONArray("assignments")?.let { asgArr ->
                    for (j in 0 until asgArr.length()) {
                        val a = asgArr.getJSONObject(j)
                        assignments += Assignment(
                            a.getString("shift"),
                            a.getString("person"),
                        )
                    }
                }
                roster += DayRoster(d.getString("date"), assignments)
            }
        }

        // forwardType
        val type = parseForwardType(root.optString("forwardType"))

        val store = RosterStore(context)
        store.save(people, roster)
        store.saveForwardType(type)

        // 重建调度
        val peopleMap = people.mapValues { (k, v) -> Person(k, v) }
        val points = RosterEngine().expandToSwitchPoints(roster, peopleMap)
        SwitchScheduler(context).scheduleAll(points)

        return ImportResult(
            success = true,
            message = context.getString(
                com.autophone.callforward.R.string.import_success,
                people.size, roster.size,
            ),
            peopleCount = people.size,
            rosterDays = roster.size,
        )
    }

    /** forwardType 字符串 → 枚举（兼容 lowercase/noanswer 等变体）。 */
    private fun parseForwardType(raw: String?): ForwardType {
        return when (raw?.lowercase()?.trim()) {
            "busy" -> ForwardType.BUSY
            "noanswer", "no_answer" -> ForwardType.NO_ANSWER
            "unreachable" -> ForwardType.UNREACHABLE
            else -> ForwardType.UNCONDITIONAL
        }
    }

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val peopleCount: Int,
        val rosterDays: Int,
    )
}
