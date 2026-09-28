package com.autophone.callforward.update

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 客户端自动更新检测器。
 *
 * 通过 GitHub Releases API 查询最新版本，与本地 versionName 对比；
 * 下载使用多镜像站轮询容灾（ghfast / ghproxy / moeyy / 官方直连）。
 *
 * 镜像站加速的是「GitHub 下载链接」，因此检查 API 用官方直连，
 * 下载 APK 时按镜像池逐个尝试。
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val REPO = "AutoPhone-Org/call-forward-scheduler"
        private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val FILE_NAME_TEMPLATE = "call-forward-scheduler-{tag}.apk"

        /** 下载镜像池（空串代表 GitHub 官方直连，作为最终保底） */
        private val MIRRORS = listOf(
            "https://ghfast.top/",
            "https://ghproxy.net/",
            "https://github.moeyy.xyz/",
            "",
        )
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String?,
        val downloadUrl: String?,
        val message: String,
    )

    /** 当前本地版本号（versionName） */
    fun currentVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, 0
            )
            info.versionName ?: "0.1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.1.0"
        }
    }

    /** 检查更新：返回是否有新版本，以及可用的下载地址（含镜像） */
    fun check(versionPrefix: String = "v"): UpdateInfo {
        val latest = fetchLatestTag() ?: return UpdateInfo(
            false, null, null, "无法连接版本服务"
        )
        val current = currentVersion()

        if (versionEquals(current, latest)) {
            return UpdateInfo(false, latest, null, "已是最新版本 $latest")
        }

        val origin = "https://github.com/$REPO/releases/download/$latest/" +
            FILE_NAME_TEMPLATE.replace("{tag}", latest)
        return UpdateInfo(true, latest, origin, "发现新版本 $latest")
    }

    /** 从 GitHub Releases API 获取最新 tag（官方直连，JSON 解析） */
    private fun fetchLatestTag(): String? {
        return try {
            val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) return null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            JSONObject(body).optString("tag_name").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成带镜像轮的下载候选地址列表。
     * 顺序：ghfast → ghproxy → moeyy → 官方直连（保底）。
     */
    fun mirrorCandidates(originUrl: String): List<String> {
        return MIRRORS.map { mirror -> mirror + originUrl }
    }

    /** 版本号对比（忽略 v 前缀） */
    private fun versionEquals(a: String, b: String): Boolean {
        return a.trimStart('v') == b.trimStart('v')
    }
}
