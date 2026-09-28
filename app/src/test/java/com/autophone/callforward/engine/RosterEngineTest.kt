package com.autophone.callforward.engine

import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.DefaultTemplates
import com.autophone.callforward.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RosterEngineTest {

    private val engine = RosterEngine()
    private val people = mapOf(
        "思源" to Person("思源", "13033696831"),
        "小明" to Person("小明", "13800000111"),
        "小红" to Person("小红", "13900000222"),
        "小刚" to Person("小刚", "13700000333"),
    )

    /** 三班倒（8h）一天应生成 3 个切换节点：08:00/16:00/24:00（晚班=00:00） */
    @Test
    fun `三班倒一天3个切换节点`() {
        val roster = listOf(
            DayRoster("2026-09-28", listOf(
                Assignment("早班", "思源"),
                Assignment("中班", "小明"),
                Assignment("晚班", "小红"),
            ))
        )
        val points = engine.expandToSwitchPoints(roster, people)
        assertEquals(3, points.size)
        // 排序后：晚班(00:00) 最早，其次早班(08:00)、中班(16:00)
        assertEquals(0, points[0].atMinute)
        assertEquals("小红", points[0].personName)
        assertEquals(8 * 60, points[1].atMinute)
        assertEquals("思源", points[1].personName)
        assertEquals(16 * 60, points[2].atMinute)
        assertEquals("小明", points[2].personName)
    }

    /** 两班倒（12h）一天应生成 2 个切换节点：08:00/20:00 */
    @Test
    fun `两班倒一天2个切换节点`() {
        val roster = listOf(
            DayRoster("2026-09-28", listOf(
                Assignment("白班", "思源"),
                Assignment("夜班", "小明"),
            ))
        )
        val points = engine.expandToSwitchPoints(roster, people)
        assertEquals(2, points.size)
        assertEquals(8 * 60, points[0].atMinute)
        assertEquals("思源", points[0].personName)
        assertEquals("13033696831", points[0].targetPhone)
        assertEquals(20 * 60, points[1].atMinute)
        assertEquals("小明", points[1].personName)
        assertEquals("13800000111", points[1].targetPhone)
    }

    /** 给定时刻查当班人：14:00 应命中白班(08-20) */
    @Test
    fun `14点命中白班`() {
        val roster = listOf(
            DayRoster("2026-09-28", listOf(
                Assignment("白班", "思源"),
                Assignment("夜班", "小明"),
            ))
        )
        val asg = engine.personAt(roster, "2026-09-28", 14 * 60)
        assertNotNull(asg)
        assertEquals("思源", asg!!.personName)
    }

    /** 跨天夜班：次日 02:00 仍属于前一天的夜班 */
    @Test
    fun `次日凌晨命中前一天夜班`() {
        val roster = listOf(
            DayRoster("2026-09-28", listOf(
                Assignment("白班", "思源"),
                Assignment("夜班", "小明"),
            ))
        )
        // 9/29 凌晨 02:00 应命中 9/28 的夜班(小明)
        val asg = engine.personAt(roster, "2026-09-29", 2 * 60)
        assertNotNull(asg)
        assertEquals("小明", asg!!.personName)
    }

    /** 模板：四班两倒需要 4 人，白班/夜班各并行 2 人（MVP 先验证人员数校验） */
    @Test
    fun `四班两倒需要4人`() {
        val template = DefaultTemplates.ALL.first { it.name == "四班两倒" }
        assertEquals(4, template.peopleCount)
        assertEquals(2, template.concurrent)
        assertEquals(listOf("白班", "夜班"), template.shiftNames)
    }

    /** 模板：三班倒需要 3 人，早中晚三班 */
    @Test
    fun `三班倒模板定义`() {
        val template = DefaultTemplates.ALL.first { it.name == "三班倒" }
        assertEquals(3, template.peopleCount)
        assertEquals(listOf("早班", "中班", "晚班"), template.shiftNames)
    }

    /** Shift 时间格式化：非跨天与跨天展示 */
    @Test
    fun `Shift时间文案跨天标注`() {
        val day = com.autophone.callforward.model.Shift("白班", 8 * 60, 20 * 60, 12.0)
        assertEquals("08:00-20:00", day.timeRangeLabel())
        assert(!day.isOvernight)

        val night = com.autophone.callforward.model.Shift("夜班", 20 * 60, 8 * 60, 12.0)
        assertEquals("20:00-次日08:00", night.timeRangeLabel())
        assert(night.isOvernight)
    }

    /** 自定义班次时间覆盖默认，影响切换节点时刻 */
    @Test
    fun `自定义班次时间覆盖默认`() {
        // 自定义白班为 09:00-18:00
        val custom = mapOf(
            "白班" to com.autophone.callforward.model.Shift("白班", 9 * 60, 18 * 60, 9.0),
        )
        val engine = RosterEngine(DefaultTemplates.DEFAULT_SHIFTS + custom)
        val roster = listOf(
            DayRoster("2026-09-28", listOf(Assignment("白班", "思源")))
        )
        val points = engine.expandToSwitchPoints(roster, people)
        assertEquals(1, points.size)
        assertEquals(9 * 60, points[0].atMinute) // 自定义 09:00 而非默认 08:00
    }

    /** 时长计算：跨天自动 +24 */
    @Test
    fun `跨天时长自动计算`() {
        assertEquals(12.0, com.autophone.callforward.model.Shift.calcDuration(20 * 60, 8 * 60), 0.001)
        assertEquals(8.0, com.autophone.callforward.model.Shift.calcDuration(8 * 60, 16 * 60), 0.001)
    }
}
