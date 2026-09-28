package com.autophone.callforward.update

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载器：按镜像池轮询下载 APK，支持进度回调与容灾。
 *
 * 下载目录使用应用专属外部缓存（无需存储权限），文件名 `update-{tag}.apk`。
 */
class ApkDownloader(private val context: Context) {

    /** 进度回调（已下载字节 / 总字节，totalBytes 可能为 -1 表示未知） */
    interface ProgressCallback {
        fun onProgress(downloaded: Long, total: Long)
    }

    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val message: String = "",
    )

    /**
     * 遍历 [UpdateChecker.mirrorCandidates] 镜像池下载。
     * 需在后台线程调用。
     */
    fun download(
        originUrl: String,
        tag: String,
        progress: ProgressCallback? = null,
    ): DownloadResult {
        val checker = UpdateChecker(context)
        val candidates = checker.mirrorCandidates(originUrl)

        var lastError = "所有镜像均下载失败"
        for (url in candidates) {
            try {
                val file = downloadSingle(url, tag, progress)
                if (file != null) {
                    return DownloadResult(true, file, "下载完成")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "下载异常"
            }
        }
        return DownloadResult(false, null, lastError)
    }

    /** 下载单个镜像；成功返回文件，失败返回 null 或抛异常 */
    private fun downloadSingle(
        url: String,
        tag: String,
        progress: ProgressCallback?,
    ): File? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "CallForwardScheduler/1.0")

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }

        val total = conn.contentLength.toLong()
        val dir = downloadDir()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "update-$tag.apk")
        if (file.exists()) file.delete()

        val input: InputStream = conn.inputStream
        val output = FileOutputStream(file)
        val buffer = ByteArray(8192)
        var downloaded = 0L
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                progress?.onProgress(downloaded, total)
            }
            output.flush()
        } finally {
            output.close()
            input.close()
            conn.disconnect()
        }

        // 校验文件非空
        return if (file.exists() && file.length() > 0) file else null
    }

    private fun downloadDir(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return external ?: File(context.cacheDir, "downloads")
    }
}
