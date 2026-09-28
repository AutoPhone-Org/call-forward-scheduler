package com.autophone.callforward.scheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 精确闹钟权限引导：Android 12+（API 31+）需用户在系统设置中授予「闹钟与提醒」权限，
 * 否则 [AlarmManager.setExactAndAllowWhileIdle] 会抛出 SecurityException 并降级为普通闹钟。
 */
class AlarmPermissionHelper(private val context: Context) {

    /** 是否需要引导授权精确闹钟权限（Android 12+ 且未授权时返回 true） */
    fun needsExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return !am.canScheduleExactAlarms()
    }

    /** 弹出引导对话框，点击「去设置」跳转系统设置页开启精确闹钟权限 */
    fun showPermissionDialog(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.alarm_perm_title)
            .setMessage(R.string.alarm_perm_body)
            .setPositiveButton(R.string.alarm_perm_go) { _, _ ->
                activity.startActivity(intent)
            }
            .setNegativeButton(R.string.alarm_perm_cancel, null)
            .show()
    }
}
