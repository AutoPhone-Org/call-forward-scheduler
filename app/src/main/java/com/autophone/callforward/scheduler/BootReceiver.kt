package com.autophone.callforward.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播：重启后恢复调度（从本地排班数据重建闹钟）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: 从本地存储读取排班并重建闹钟
            // val engine = RosterEngine()
            // val points = engine.expandToSwitchPoints(loadRoster(), loadPeople())
            // SwitchScheduler(context).scheduleAll(points)
            android.util.Log.i("CallForward", "BOOT_COMPLETED: 需重建调度（待接入存储）")
        }
    }
}
