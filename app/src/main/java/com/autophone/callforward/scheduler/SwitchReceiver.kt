package com.autophone.callforward.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autophone.callforward.executor.CallForwardExecutor

/**
 * 切换节点触发接收器：到点执行呼叫转移。
 */
class SwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra("phone") ?: return
        val person = intent.getStringExtra("person") ?: ""

        // 在后台线程执行，避免阻塞主线程
        Thread {
            val executor = CallForwardExecutor()
            val result = executor.setForward(phone)
            // TODO: 通知栏展示结果（含失败告警）
            android.util.Log.i("CallForward", "切换→$person($phone): ${result.message}")
        }.start()
    }
}
