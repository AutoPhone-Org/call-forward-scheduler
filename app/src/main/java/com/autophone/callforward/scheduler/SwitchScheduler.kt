package com.autophone.callforward.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.autophone.callforward.model.SwitchPoint

/**
 * 调度器：为每个切换节点注册精确闹钟，到点触发 [SwitchReceiver]。
 */
class SwitchScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 根据展开后的切换节点列表重建所有闹钟。
     * 幂等：先清旧再注册新，避免重复。
     */
    fun scheduleAll(points: List<SwitchPoint>) {
        val am = alarmManager
        // 简化：清空本 App 的所有闹钟
        val cancelIntent = Intent(context, SwitchReceiver::class.java)
        val cancelPi = PendingIntent.getBroadcast(
            context, 0, cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        cancelPi?.let { am.cancel(it) }

        points.forEachIndexed { index, p ->
            val triggerAt = toEpochMillis(p)
            val intent = Intent(context, SwitchReceiver::class.java).apply {
                putExtra("date", p.date)
                putExtra("person", p.personName)
                putExtra("phone", p.targetPhone)
            }
            val pi = PendingIntent.getBroadcast(
                context, index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (_: SecurityException) {
                // Android 12+ 未授权精确闹钟时降级为 setAndAllowWhileIdle
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    /** 将切换节点转换为绝对时间戳（毫秒） */
    private fun toEpochMillis(p: SwitchPoint): Long {
        val d = java.time.LocalDate.parse(p.date)
        val dt = d.atStartOfDay(java.time.ZoneId.systemDefault())
            .plusMinutes(p.atMinute.toLong())
        return dt.toInstant().toEpochMilli()
    }
}
