package com.autophone.callforward.engine

import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.DefaultTemplates
import com.autophone.callforward.model.Person
import com.autophone.callforward.model.Shift
import com.autophone.callforward.model.ShiftTemplate
import com.autophone.callforward.model.SwitchPoint

/**
 * 排班引擎：将排班表展开为「切换节点序列」，并支持按时刻查询当班人。
 *
 * 核心思路（PRD §6.2）：
 *  - 每个班次 start 时刻 = 一个切换节点
 *  - 跨天班次（夜班）结束节点归属次日
 *  - 同一天多班次 → 多切换节点（8h 制 3 次、12h 制 2 次、24h 制 1 次）
 */
class RosterEngine(
    private val shifts: Map<String, Shift> = DefaultTemplates.DEFAULT_SHIFTS,
) {

    /**
     * 将一批 [roster] 展开为按时间升序的 [SwitchPoint] 列表。
     * 传入的 roster 需按日期升序。
     */
    fun expandToSwitchPoints(
        roster: List<DayRoster>,
        people: Map<String, Person>,
    ): List<SwitchPoint> {
        val points = mutableListOf<SwitchPoint>()

        for (day in roster.sortedBy { it.date }) {
            for (asg in day.assignments.sortedBy { shifts[it.shiftName]?.startMinute ?: 0 }) {
                val shift = shifts[asg.shiftName]
                    ?: error("未知班次: ${asg.shiftName}")
                val person = people[asg.personName]
                    ?: error("未知人员: ${asg.personName}")

                // 每个班次的 start 就是一个切换节点
                points += SwitchPoint(
                    date = day.date,
                    atMinute = shift.startMinute,
                    personName = asg.personName,
                    targetPhone = person.phone,
                )
            }
        }
        return points.sortedWith(compareBy({ it.date }, { it.atMinute }))
    }

    /**
     * 查询指定 [date]（ISO）与 [minuteOfDay] 时刻的当班人。
     *
     * 规则：取当天 assignments 中「开始时间 <= 当前时刻」里开始时间最晚的班次。
     * 跨天班次（夜班）在 00:00-结束时刻前仍属于「前一天开始的夜班」，
     * 因此本方法会同时检查前一天是否存在覆盖到今天的跨天班次。
     */
    fun personAt(
        roster: List<DayRoster>,
        date: String,
        minuteOfDay: Int,
    ): Assignment? {
        val today = roster.firstOrNull { it.date == date }
        // 1) 当天非跨天班次中，开始时间 <= minuteOfDay 的最后者
        var candidate: Assignment? = null
        var candidateStart = -1
        today?.assignments?.forEach { asg ->
            val shift = shifts[asg.shiftName] ?: return@forEach
            if (!shift.isOvernight && shift.startMinute <= minuteOfDay) {
                if (shift.startMinute > candidateStart) {
                    candidateStart = shift.startMinute
                    candidate = asg
                }
            }
        }
        // 2) 前一天跨天班次（覆盖到今天的 00:00-结束时刻）
        val yesterday = previousDate(date)
        val y = roster.firstOrNull { it.date == yesterday }
        y?.assignments?.forEach { asg ->
            val shift = shifts[asg.shiftName] ?: return@forEach
            if (shift.isOvernight && minuteOfDay < shift.endMinute) {
                // 夜班覆盖当前凌晨时段
                candidate = asg
            }
        }
        return candidate
    }

    /** 生成某模板在 [people] 顺序下的第一天排班（轮换起点） */
    fun applyTemplateFirstDay(
        template: ShiftTemplate,
        people: List<Person>,
        date: String,
    ): DayRoster {
        require(people.size >= template.peopleCount) {
            "模板「${template.name}」需要 ${template.peopleCount} 人，实际提供 ${people.size} 人"
        }
        // 简单轮换：取前 N 人，按班次顺序依次指派
        val names = people.take(template.peopleCount).map { it.name }
        val assignments = template.shiftNames.mapIndexed { i, shiftName ->
            Assignment(shiftName, names[i % names.size])
        }
        return DayRoster(date, assignments)
    }

    /** 简单的前一天日期计算（ISO yyyy-MM-dd），用于跨天班次查找 */
    private fun previousDate(iso: String): String {
        val parts = iso.split("-")
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val date = java.time.LocalDate.of(y, m, d).minusDays(1)
        return date.toString()
    }
}
