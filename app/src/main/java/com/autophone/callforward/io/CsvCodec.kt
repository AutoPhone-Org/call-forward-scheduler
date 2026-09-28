package com.autophone.callforward.io

import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster

/**
 * 人员 + 排班数据的 CSV 编解码。
 *
 * 文件格式（6 列，首行为表头，UTF-8 带 BOM 以便 Excel 识别中文）：
 * ```
 * type,name,phone,date,shift,person
 * person,思源,13033696831,,,
 * roster,,,,2026-09-28,白班,思源
 * ```
 * 注意：roster 行实际有 7 个字段（type,name,phone 空三列 + date,shift,person），
 * 本类按上例的字段位置编码；解析时兼容「date 在第 3 列（6 列写法）或第 4 列（7 列写法）」。
 */
object CsvCodec {

    /** UTF-8 BOM，写文件时置于内容开头，避免 Excel 打开中文乱码 */
    const val BOM = "\uFEFF"

    private const val HEADER = "type,name,phone,date,shift,person"

    private const val TYPE_PERSON = "person"
    private const val TYPE_ROSTER = "roster"

    /** 编码为 CSV 文本（开头含 BOM） */
    fun encode(people: Map<String, String>, roster: List<DayRoster>): String {
        val sb = StringBuilder()
        sb.append(BOM).append(HEADER).append("\r\n")

        people.forEach { (name, phone) ->
            sb.append(
                listOf(TYPE_PERSON, name, phone, "", "", "").joinToString(",") { escape(it) }
            ).append("\r\n")
        }

        roster.forEach { day ->
            if (day.assignments.isEmpty()) {
                // 保留「有日期但无指派」的排班日
                sb.append(
                    listOf(TYPE_ROSTER, "", "", day.date, "", "").joinToString(",") { escape(it) }
                ).append("\r\n")
            } else {
                day.assignments.forEach { asg ->
                    sb.append(
                        listOf(TYPE_ROSTER, "", "", day.date, asg.shiftName, asg.personName)
                            .joinToString(",") { escape(it) }
                    ).append("\r\n")
                }
            }
        }
        return sb.toString()
    }

    /**
     * 解析 CSV 文本，返回 (people, roster)。
     * 容错：跳过空行、表头行、未知 type、字段数不足的行。
     * roster 行按日期聚合为 [DayRoster]；人员以姓名为键去重（后者覆盖）。
     */
    fun decode(text: String): Pair<Map<String, String>, List<DayRoster>> {
        val people = linkedMapOf<String, String>()
        // date -> 有序班次指派（同一日的多行聚合）
        val rosterByDate = linkedMapOf<String, MutableList<Assignment>>()

        stripBom(text)
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val fields = parseLine(line)
                val type = fields.getOrNull(0)?.trim()?.lowercase() ?: return@forEach
                when (type) {
                    TYPE_PERSON -> {
                        val name = fields.getOrNull(1)?.trim().orEmpty()
                        if (name.isEmpty()) return@forEach
                        people[name] = fields.getOrNull(2)?.trim().orEmpty()
                    }
                    TYPE_ROSTER -> {
                        // 兼容两种列布局：date 在第 3 列（6 列写法）或第 4 列（7 列写法）
                        val dateCol =
                            if (fields.size >= 6 && !fields[3].contains('-') && fields[4].contains('-')) 4 else 3
                        val date = fields.getOrNull(dateCol)?.trim().orEmpty()
                        val shift = fields.getOrNull(dateCol + 1)?.trim().orEmpty()
                        val person = fields.getOrNull(dateCol + 2)?.trim().orEmpty()
                        if (!isValidDate(date)) return@forEach
                        val list = rosterByDate.getOrPut(date) { mutableListOf() }
                        if (shift.isNotEmpty() || person.isNotEmpty()) {
                            list += Assignment(shift, person)
                        }
                    }
                    // 未知 type：跳过
                }
            }

        val roster = rosterByDate.map { (date, asgs) -> DayRoster(date, asgs.toList()) }
            .sortedBy { it.date }
        return people to roster
    }

    /** 字段含逗号/引号/换行时用双引号包裹并转义内部引号 */
    private fun escape(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    /** 解析一行 CSV（支持双引号包裹、转义引号 ""；不支持跨行引号字段） */
    private fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == '"' && sb.isEmpty() -> inQuotes = true
                c == ',' && !inQuotes -> {
                    fields += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        fields += sb.toString()
        return fields
    }

    private fun stripBom(text: String): String {
        return if (text.isNotEmpty() && text[0] == BOM[0]) text.substring(1) else text
    }

    private fun isValidDate(iso: String): Boolean {
        if (iso.length != 10 || iso[4] != '-' || iso[7] != '-') return false
        return try {
            java.time.LocalDate.parse(iso)
            true
        } catch (e: Exception) {
            false
        }
    }
}
