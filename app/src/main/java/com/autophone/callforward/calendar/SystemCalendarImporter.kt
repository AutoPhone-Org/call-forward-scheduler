package com.autophone.callforward.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 系统日历排班导入器（只读）。
 *
 * 通过 [CalendarContract.Instances] 查询未来 30 天内的日历事件，
 * 按「班次 人员名」约定解析事件标题（如「白班 思源」），转换为排班数据。
 *
 * 规则：
 *  - 标题包含班次关键字（白班/夜班/早班/中班/晚班/全天）即视为排班事件，无匹配则跳过
 *  - 班次与人员名之间用空白分隔；缺省人员名时跳过该事件
 *  - 人员名不存在时自动加入 people（手机号留空）
 */
class SystemCalendarImporter(private val context: Context) {

    /** 是否已授予 READ_CALENDAR 运行时权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 查询未来 [days] 天内的日历事件并解析为排班。
     * 返回 (people增量, roster)；需已持有 READ_CALENDAR 权限，否则抛 SecurityException。
     */
    fun importUpcoming(days: Int = DEFAULT_DAYS): Pair<Map<String, String>, List<DayRoster>> {
        val assignmentsByDate = mutableMapOf<String, MutableList<Assignment>>()
        val newPeople = mutableMapOf<String, String>()

        val cursor = context.contentResolver.query(
            instancesUri(days),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
            ),
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        ) ?: return Pair(emptyMap(), emptyList())

        cursor.use { c ->
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (c.moveToNext()) {
                val title = c.getString(titleIdx)?.trim() ?: continue
                val parsed = parseTitle(title, newPeople) ?: continue
                val begin = c.getLong(beginIdx)
                val date = toIsoDate(begin)
                assignmentsByDate.getOrPut(date) { mutableListOf() }.add(parsed)
            }
        }

        val roster = assignmentsByDate
            .map { (date, list) -> DayRoster(date, list) }
            .sortedBy { it.date }
        return Pair(newPeople, roster)
    }

    /** Instances URI：按未来 [days] 天的时间范围查询（含重复事件展开）。 */
    private fun instancesUri(days: Int): android.net.Uri {
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = LocalDate.now().plusDays(days.toLong() - 1)
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        return builder.build()
    }

    /**
     * 解析事件标题为 (班次, 人员名)。
     * 命中班次关键字即返回；人员名缺失时返回 null（不修改 people）。
     */
    private fun parseTitle(
        title: String,
        newPeople: MutableMap<String, String>,
    ): Assignment? {
        val shiftName = SHIFT_KEYS.firstOrNull { title.contains(it) } ?: return null
        val personName = title.split(Regex("\\s+"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !SHIFT_KEYS.any { key -> it.contains(key) } }
            ?: return null
        newPeople.putIfAbsent(personName, "")
        return Assignment(shiftName, personName)
    }

    /** 事件开始时间戳 → ISO 日期（yyyy-MM-dd，按本地时区） */
    private fun toIsoDate(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    companion object {
        /** 默认导入未来 30 天 */
        const val DEFAULT_DAYS = 30

        /** 班次关键字 */
        private val SHIFT_KEYS = listOf("白班", "夜班", "早班", "中班", "晚班", "全天")
    }
}
