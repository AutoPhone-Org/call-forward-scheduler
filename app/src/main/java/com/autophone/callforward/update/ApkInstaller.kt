package com.autophone.callforward.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装器：通过 FileProvider 生成 content URI，调起系统安装器。
 */
class ApkInstaller(private val context: Context) {

    /**
     * 引导安装指定 APK 文件。
     * 需处理 Android 7.0+ FileProvider 与 8.0+ 未知来源权限。
     */
    fun install(apkFile: File): Boolean {
        if (!apkFile.exists()) return false
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
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
