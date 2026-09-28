package com.autophone.callforward.model

/**
 * 数据模型定义。
 * 与 PRD §7 数据模型一致，采用 Kotlin data class 表达。
 */

/** 人员 ↔ 手机号映射 */
data class Person(
    val name: String,
    val phone: String,
)

/** 班次定义：起止时间（分钟，可能跨天）+ 时长（小时） */
data class Shift(
    val name: String,
    /** 开始时间，一天内的分钟数，如 08:00 = 480 */
    val startMinute: Int,
    /** 结束时间，一天内的分钟数；若 <= startMinute 表示跨天（如夜班 20:00-08:00） */
    val endMinute: Int,
    /** 单班时长（小时），用于展示与校验 */
    val durationHours: Double,
) {
    /** 是否为跨天班次（如夜班 20:00 - 次日 08:00） */
    val isOvernight: Boolean get() = endMinute <= startMinute

    /** 开始时间的 HH:mm 文案，如 "08:00" */
    fun startLabel(): String = minuteToLabel(startMinute)

    /** 结束时间的 HH:mm 文案，如 "20:00" */
    fun endLabel(): String = minuteToLabel(endMinute)

    /**
     * 起止时间的完整文案，跨天时自动标注「次日」。
     * 例：白班 → "08:00-20:00"；夜班 → "20:00-次日08:00"
     */
    fun timeRangeLabel(): String {
        return if (isOvernight) {
            "${startLabel()}-次日${endLabel()}"
        } else {
            "${startLabel()}-${endLabel()}"
        }
    }

    companion object {
        /** 分钟数 → HH:mm */
        fun minuteToLabel(minute: Int): String {
            val m = ((minute % 1440) + 1440) % 1440
            val h = m / 60
            val mm = m % 60
            return "%02d:%02d".format(h, mm)
        }

        /** HH:mm → 分钟数；解析失败返回 null */
        fun labelToMinute(label: String): Int? {
            val parts = label.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }

        /** 根据起止分钟自动计算时长（小时），跨天自动 +24 */
        fun calcDuration(startMinute: Int, endMinute: Int): Double {
            val diff = if (endMinute <= startMinute) {
                endMinute + 24 * 60 - startMinute
            } else {
                endMinute - startMinute
            }
            return diff / 60.0
        }
    }
}

/** 某一天内的一个排班指派：某个班次由某人值守 */
data class Assignment(
    val shiftName: String,
    val personName: String,
)

/** 某一天的排班：可包含多个班次（多切换节点） */
data class DayRoster(
    /** ISO 日期，如 "2026-09-28" */
    val date: String,
    val assignments: List<Assignment>,
)

/** 转移类型（对应运营商 USSD 前缀） */
enum class ForwardType(val ussdPrefix: String) {
    /** 无条件转移 *21* */
    UNCONDITIONAL("*21*"),
    /** 遇忙转移 *67* */
    BUSY("*67*"),
    /** 无应答转移 *61* */
    NO_ANSWER("*61*"),
    /** 不可及转移 *62* */
    UNREACHABLE("*62*"),
}

/** 一个展开后的切换节点：在 [atMinute] 时刻把转移目标切到 [targetPhone] */
data class SwitchPoint(
    /** 日期（ISO） */
    val date: String,
    /** 触发时刻（该日内的分钟数） */
    val atMinute: Int,
    /** 当班人姓名 */
    val personName: String,
    /** 目标手机号 */
    val targetPhone: String,
)

/** 内置倒班模板定义 */
data class ShiftTemplate(
    val name: String,
    /** 参与人数 */
    val peopleCount: Int,
    /** 循环天数（cycle_days） */
    val cycleDays: Int,
    /** 班次名列表（复用 shifts 表定义） */
    val shiftNames: List<String>,
    /** 同班并行上岗人数（如四班两倒=2） */
    val concurrent: Int,
    /** 轮休人数（如四班三倒=1） */
    val rest: Int,
)

/** 内置模板库（与 PRD §3.2.1 一致） */
object DefaultTemplates {
    val ALL = listOf(
        ShiftTemplate("两班倒", 2, 1, listOf("白班", "夜班"), concurrent = 1, rest = 0),
        ShiftTemplate("三班倒", 3, 1, listOf("早班", "中班", "晚班"), concurrent = 1, rest = 0),
        ShiftTemplate("四班两倒", 4, 2, listOf("白班", "夜班"), concurrent = 2, rest = 2),
        ShiftTemplate("四班三倒", 4, 1, listOf("早班", "中班", "晚班"), concurrent = 3, rest = 1),
    )

    /** 默认班次时间表（供模板引用） */
    val DEFAULT_SHIFTS = mapOf(
        "白班" to Shift("白班", 8 * 60, 20 * 60, 12.0),           // 08:00-20:00
        "夜班" to Shift("夜班", 20 * 60, 8 * 60, 12.0),           // 20:00-08:00（跨天）
        "早班" to Shift("早班", 8 * 60, 16 * 60, 8.0),            // 08:00-16:00
        "中班" to Shift("中班", 16 * 60, 24 * 60, 8.0),           // 16:00-24:00
        "晚班" to Shift("晚班", 0, 8 * 60, 8.0),                  // 00:00-08:00
        "全天" to Shift("全天", 0, 24 * 60, 24.0),                // 00:00-24:00
    )
}
