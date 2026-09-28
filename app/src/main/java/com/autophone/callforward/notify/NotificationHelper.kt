package com.autophone.callforward.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.autophone.callforward.R
import com.autophone.callforward.ui.MainActivity
import java.io.File

/**
 * 通知栏工具：创建通知渠道，发送切换成功/失败告警。
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SWITCH = "call_forward_switch"
        const val CHANNEL_ALERT = "call_forward_alert"
        const val CHANNEL_DOWNLOAD = "call_forward_download"
        private const val NOTIF_ID_BASE = 1000
        private const val NOTIF_ID_DOWNLOAD = 2000
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

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            context.getString(R.string.channel_download),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_download_desc)
        }

        nm.createNotificationChannels(listOf(switchChannel, alertChannel, downloadChannel))
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

    /** 更新下载进度通知（在后台线程调用，进度 0-100）。 */
    fun notifyDownloadProgress(percent: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notif_downloading))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时忽略
        }
    }

    /** 下载完成：发出可点击安装的通知，同时移除进度条。 */
    fun notifyDownloadDone(apkFile: File) {
        val contentIntent = installIntent(apkFile)
            ?: PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notif_download_done))
            .setContentText(context.getString(R.string.notif_download_done_click))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时忽略
        }
    }

    /** 下载失败：发送告警通知并取消进度条。 */
    fun notifyDownloadFail() {
        val contentIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_download_fail))
            .setContentText(context.getString(R.string.update_download_fail))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时忽略
        }
    }

    /** 构造 APK 安装的 contentIntent，失败返回 null。 */
    private fun installIntent(apkFile: File): PendingIntent? {
        if (!apkFile.exists()) return null
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context, 4, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            null
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
