package com.autophone.callforward.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autophone.callforward.executor.CallForwardExecutor
import com.autophone.callforward.notify.NotificationHelper

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
            android.util.Log.i("CallForward", "切换→$person($phone): ${result.message}")
            notifier.notifySwitchResult(person, phone, result.success, result.message)
        }.start()
    }
}
