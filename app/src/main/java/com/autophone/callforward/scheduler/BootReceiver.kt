package com.autophone.callforward.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.engine.RosterEngine

/**
 * 开机广播：重启后从本地持久化数据恢复调度（重建闹钟）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val store = RosterStore(context)
        val people = store.loadPeople()
        val roster = store.loadRoster()
        if (people.isEmpty() || roster.isEmpty()) {
            android.util.Log.i("CallForward", "BOOT_COMPLETED: 无排班数据，跳过恢复")
            return
        }
        val points = RosterEngine().expandToSwitchPoints(roster, people)
        SwitchScheduler(context).scheduleAll(points)
        android.util.Log.i("CallForward", "BOOT_COMPLETED: 已恢复 ${points.size} 个切换节点")
    }
}
