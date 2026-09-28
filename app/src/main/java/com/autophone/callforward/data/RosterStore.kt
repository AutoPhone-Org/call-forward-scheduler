package com.autophone.callforward.data

import android.content.Context
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.ForwardType
import com.autophone.callforward.model.Person
import com.autophone.callforward.model.Shift
import org.json.JSONArray
import org.json.JSONObject

/**
 * 排班与人员数据的本地 JSON 持久化（MVP 阶段用 SharedPreferences/文件即可）。
 *
 * 结构对齐 PRD §7：
 * { people: {name: phone}, roster: [ {date, assignments:[{shift, person}]} ] }
 */
class RosterStore(private val context: Context) {

    private val sp = context.getSharedPreferences("roster", Context.MODE_PRIVATE)

    /** 保存人员 + 排班表 */
    fun save(people: Map<String, String>, roster: List<DayRoster>) {
        val root = JSONObject()
        val p = JSONObject()
        people.forEach { (k, v) -> p.put(k, v) }
        root.put("people", p)

        val arr = JSONArray()
        roster.forEach { day ->
            val d = JSONObject()
            d.put("date", day.date)
            val asg = JSONArray()
            day.assignments.forEach { a ->
                val o = JSONObject().put("shift", a.shiftName).put("person", a.personName)
                asg.put(o)
            }
            d.put("assignments", asg)
            arr.put(d)
        }
        root.put("roster", arr)
        sp.edit().putString("data", root.toString()).apply()
    }

    /** 保存通用设置项（转移类型、set/get code 等） */
    fun saveSetting(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun getSetting(key: String, default: String = ""): String {
        return sp.getString(key, default) ?: default
    }

    /** 保存转移类型 */
    fun saveForwardType(type: ForwardType) {
        saveSetting("forwardType", type.name)
    }

    /** 读取转移类型，默认无条件 */
    fun loadForwardType(): ForwardType {
        return try {
            ForwardType.valueOf(getSetting("forwardType", ForwardType.UNCONDITIONAL.name))
        } catch (e: IllegalArgumentException) {
            ForwardType.UNCONDITIONAL
        }
    }

    /**
     * 保存自定义班次时间表。
     * 存储为 JSON：{ "白班": {"start":480,"end":1200}, ... }
     */
    fun saveShiftTimes(shifts: Map<String, Shift>) {
        val root = JSONObject()
        shifts.forEach { (name, s) ->
            root.put(name, JSONObject().put("start", s.startMinute).put("end", s.endMinute))
        }
        sp.edit().putString("shift_times", root.toString()).apply()
    }

    /**
     * 读取自定义班次时间表；无自定义时返回空 Map（调用方回退到默认）。
     * 时长根据起止时间自动重算（跨天自动 +24）。
     */
    fun loadShiftTimes(): Map<String, Shift> {
        val raw = sp.getString("shift_times", null) ?: return emptyMap()
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return emptyMap()
        }
        val map = mutableMapOf<String, Shift>()
        root.keys().forEach { name ->
            val obj = root.optJSONObject(name) ?: return@forEach
            val start = obj.optInt("start", -1)
            val end = obj.optInt("end", -1)
            if (start in 0..(24 * 60 - 1) && end in 0..(24 * 60 - 1)) {
                map[name] = Shift(
                    name = name,
                    startMinute = start,
                    endMinute = end,
                    durationHours = Shift.calcDuration(start, end),
                )
            }
        }
        return map
    }

    // ---------- AI 排班配置（apiKey 加密存储） ----------

    /** 保存 AI 配置：baseUrl / model 明文，apiKey 加密 */
    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        saveSetting(KEY_AI_BASE_URL, baseUrl.trim())
        saveSetting(KEY_AI_MODEL, model.trim())
        val key = apiKey.trim()
        if (key.isBlank()) {
            saveSetting(KEY_AI_API_KEY, "")
        } else {
            saveSetting(KEY_AI_API_KEY, com.autophone.callforward.crypto.TokenCryptor.encrypt(key))
        }
    }

    /** 读取 AI baseUrl */
    fun loadAiBaseUrl(): String = getSetting(KEY_AI_BASE_URL, "https://api.openai.com/v1")

    /** 读取 AI model */
    fun loadAiModel(): String = getSetting(KEY_AI_MODEL, "gpt-4o-mini")

    /** 读取 AI apiKey（解密）；未配置返回空串 */
    fun loadAiApiKey(): String {
        val stored = getSetting(KEY_AI_API_KEY, "")
        if (stored.isBlank()) return ""
        return com.autophone.callforward.crypto.TokenCryptor.decrypt(stored)
    }

    fun loadPeople(): Map<String, Person> {
        val raw = sp.getString("data", null) ?: return emptyMap()
        val people = JSONObject(raw).getJSONObject("people")
        val map = mutableMapOf<String, Person>()
        people.keys().forEach { k -> map[k] = Person(k, people.getString(k)) }
        return map
    }

    fun loadRoster(): List<DayRoster> {
        val raw = sp.getString("data", null) ?: return emptyList()
        val arr = JSONObject(raw).getJSONArray("roster")
        val list = mutableListOf<DayRoster>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            val asgArr = d.getJSONArray("assignments")
            val asgs = mutableListOf<com.autophone.callforward.model.Assignment>()
            for (j in 0 until asgArr.length()) {
                val o = asgArr.getJSONObject(j)
                asgs += com.autophone.callforward.model.Assignment(o.getString("shift"), o.getString("person"))
            }
            list += DayRoster(d.getString("date"), asgs)
        }
        return list
    }

    private companion object {
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
    }
}
