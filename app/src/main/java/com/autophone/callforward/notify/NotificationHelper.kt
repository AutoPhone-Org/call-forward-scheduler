package com.autophone.callforward.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autophone.callforward.R
import com.autophone.callforward.ui.MainActivity

/**
 * 通知栏工具：创建通知渠道，发送切换成功/失败告警。
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SWITCH = "call_forward_switch"
        const val CHANNEL_ALERT = "call_forward_alert"
        private const val NOTIF_ID_BASE = 1000
    }

    /** 初始化通知渠道（Android 8.0+ 必需） */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val switchChannel = NotificationChannel(
            CHANNEL_SWITCH,
            context.getString(R.string.channel_switch),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_switch_desc)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alert_desc)
        }

        nm.createNotificationChannels(listOf(switchChannel, alertChannel))
    }

    /** 发送切换结果通知；失败走告警渠道（高优先级） */
    fun notifySwitchResult(person: String, phone: String, success: Boolean, message: String) {
        val channel = if (success) CHANNEL_SWITCH else CHANNEL_ALERT
        val title = if (success) {
            context.getString(R.string.notif_success_title)
        } else {
            context.getString(R.string.notif_fail_title)
        }
        val text = context.getString(
            if (success) R.string.notif_success_text else R.string.notif_fail_text,
            person, phone
        )

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$message"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(
                if (success) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_BASE, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时忽略
        }
    }

    /** 发送排班配置导入成功通知。 */
    fun notifyImportSuccess(peopleCount: Int, rosterDays: Int) {
        val title = context.getString(R.string.import_success_notif_title)
        val text = context.getString(R.string.import_success, peopleCount, rosterDays)

        val contentIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SWITCH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_BASE + 1, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时忽略
        }
    }
}
