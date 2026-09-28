package com.autophone.callforward.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autophone.callforward.executor.CallForwardExecutor
import com.autophone.callforward.notify.NotificationHelper
import com.autophone.callforward.util.AppLog

/**
 * 切换节点触发接收器：到点执行呼叫转移，并通过通知栏反馈结果。
 */
class SwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra("phone") ?: return
        val person = intent.getStringExtra("person") ?: ""

        val notifier = NotificationHelper(context.applicationContext)
        notifier.createChannels()

        // 后台线程执行，避免阻塞主线程
        Thread {
            val executor = CallForwardExecutor()
            val result = executor.setForward(phone)
            AppLog.section(
                "切换呼叫转移",
                mapOf(
                    "当班人" to person,
                    "目标号码" to phone,
                    "结果" to (if (result.success) "成功" else "失败"),
                    "详情" to result.message,
                )
            )
            notifier.notifySwitchResult(person, phone, result.success, result.message)
        }.start()
    }
}
