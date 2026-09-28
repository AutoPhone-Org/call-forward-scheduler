package com.autophone.callforward.io

import android.content.Context
import android.net.Uri
import com.autophone.callforward.model.DayRoster
import java.io.IOException

/**
 * 基于 Storage Access Framework 的排班数据 CSV 导入导出。
 *
 * 导出：由 Activity 通过 [androidx.activity.result.contract.ActivityResultContracts.CreateDocument]
 * 拿到 Uri 后调用 [writeCsv]；导入：通过 OpenDocument 拿到 Uri 后调用 [readCsv]。
 * 均无需存储权限。
 */
class RosterFileImporter(private val context: Context) {

    /** 导出文件名（带日期，便于多次导出存档） */
    fun defaultFileName(): String {
        val date = java.time.LocalDate.now().toString()
        return "callforward-backup-$date.csv"
    }

    /** 把 people + roster 写入 [uri]（UTF-8 带 BOM） */
    @Throws(IOException::class)
    fun writeCsv(uri: Uri, people: Map<String, String>, roster: List<DayRoster>) {
        val csv = CsvCodec.encode(people, roster)
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            os.write(csv.toByteArray(Charsets.UTF_8))
        } ?: throw IOException(context.getString(com.autophone.callforward.R.string.csv_err_no_stream_out))
    }

    /** 从 [uri] 读取 CSV，返回 (people, roster) */
    @Throws(IOException::class)
    fun readCsv(uri: Uri): Pair<Map<String, String>, List<DayRoster>> {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException(context.getString(com.autophone.callforward.R.string.csv_err_no_stream_in))
        return CsvCodec.decode(text)
    }
}
