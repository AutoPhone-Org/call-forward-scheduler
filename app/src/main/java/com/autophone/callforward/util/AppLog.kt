package com.autophone.callforward.util

import android.util.Log

/**
 * 统一日志工具：结构化、带统一前缀与分隔排版，便于在 logcat 中筛选。
 *
 * 排版格式（每条日志自带清晰分隔）：
 *   ══ [CallForward] 标题 ══
 *     字段1: 值
 *     字段2: 值
 *
 * 通过 `adb logcat -s CallForward` 可只查看本应用的日志。
 */
object AppLog {

    private const val TAG = "CallForward"

    /** 分隔线宽度 */
    private const val LINE = "══════════════════════════════════"

    /** 普通信息日志 */
    fun i(message: String) {
        Log.i(TAG, message)
    }

    /** 带标题的结构化日志：标题 + 若干「键: 值」行，自动加分隔线便于定位 */
    fun section(title: String, fields: Map<String, String>) {
        Log.i(TAG, "$LINE")
        Log.i(TAG, "  $title")
        Log.i(TAG, "$LINE")
        for ((k, v) in fields) {
            Log.i(TAG, "  $k: $v")
        }
        Log.i(TAG, "$LINE")
    }

    /** 错误日志（含异常类名与消息） */
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /** 警告日志 */
    fun w(message: String) {
        Log.w(TAG, message)
    }
}
