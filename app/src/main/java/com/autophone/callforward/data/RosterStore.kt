package com.autophone.callforward.data

import android.content.Context
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.Person
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
}
