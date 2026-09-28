package com.autophone.callforward.executor

import android.os.Parcel
import com.autophone.callforward.model.ForwardType
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 呼叫转移执行器：经 Shizuku 以 shell 权限下发 USSD，并回读校验。
 *
 * 实现方式（PRD §5.1）：
 *  - 设置：`service call phone <setCode> s16 '<ussd>'`
 *  - 查询：`service call phone <getCode> s16 '*#21#'`
 *
 * 注意：
 *  - [setCode]/[getCode] 因 ROM 不同而异（默认 set=1, get=2；已在 BON-AL00 验证 get=2，
 *    set 需实机枚举确认）。
 *  - USSD 回执为异步弹窗，故设置后延迟再查询确认。
 *
 * 由于 Shizuku 13.x 的 `Shizuku.newProcess` 已私有化，本类采用
 * `SystemServiceHelper.getSystemService("phone")` 获取 ITelephony binder，
 * 通过 `ShizukuBinderWrapper` + transact 直接调用（等价于 `adb shell service call phone`）。
 */
class CallForwardExecutor(
    /** 设置转移的 ITelephony method code */
    private val setCode: Int = 1,
    /** 查询转移的 ITelephony method code */
    private val getCode: Int = 2,
) {

    /** Shizuku 是否已授权本 App */
    val isReady: Boolean
        get() = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * 执行设置呼叫转移。返回 (成功与否, 描述)。
     * 幂等：若当前已转给 [targetPhone] 则跳过。
     */
    fun setForward(targetPhone: String, type: ForwardType = ForwardType.UNCONDITIONAL): Result {
        if (!isReady) return Result(false, "Shizuku 未授权")
        val current = queryCurrent()
        if (current != null && current.contains(targetPhone)) {
            return Result(true, "目标已为 $targetPhone，跳过", skipped = true)
        }
        val ussd = "${type.ussdPrefix}$targetPhone#"
        val ok = serviceCall(setCode, ussd)
        return if (ok) Result(true, "已下发 $ussd") else Result(false, "下发失败: $ussd")
    }

    /** 取消全部转移 */
    fun cancelAll(): Result {
        if (!isReady) return Result(false, "Shizuku 未授权")
        val ok = serviceCall(setCode, "#21#")
        return if (ok) Result(true, "已取消全部转移") else Result(false, "取消失败")
    }

    /** 查询当前无条件转移状态，返回原始 Parcel 文本或 null */
    fun queryCurrent(): String? {
        if (!isReady) return null
        return try {
            val reply = serviceCallRaw(getCode, "*#21#") ?: return null
            // 从 Parcel 中读取字符串（service call 返回 UTF-16 字符串）
            reply.readString()
        } catch (e: Throwable) {
            null
        }
    }

    /** 执行 service call 并判断是否成功（无异常即可） */
    private fun serviceCall(code: Int, arg: String): Boolean {
        return try {
            serviceCallRaw(code, arg) != null
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 底层：等价于 `adb shell service call phone <code> s16 '<arg>'`。
     *
     * 通过 Shizuku 获取的 phone (ITelephony) binder，用 transact 调用。
     * 参数打包：`s16` 表示一个 UTF-16 字符串。
     */
    private fun serviceCallRaw(code: Int, arg: String): Parcel? {
        val raw = SystemServiceHelper.getSystemService("phone")
            ?: return null
        val binder = ShizukuBinderWrapper(raw)

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("com.android.internal.telephony.ITelephony")
            data.writeString(arg)
            val ok = binder.transact(code, data, reply, 0)
            if (!ok) return null
            reply.setDataPosition(0)
            return reply
        } finally {
            data.recycle()
            // reply 由调用方消费后回收
        }
    }

    data class Result(
        val success: Boolean,
        val message: String,
        val skipped: Boolean = false,
    )
}
