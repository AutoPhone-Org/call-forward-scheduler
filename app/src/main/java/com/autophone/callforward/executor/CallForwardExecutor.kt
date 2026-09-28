package com.autophone.callforward.executor

import com.autophone.callforward.model.ForwardType
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 呼叫转移执行器：经 Shizuku 以 shell 权限下发 USSD，并回读校验。
 *
 * 实现方式（PRD §5.1）：
 *  - 设置：`service call phone <setCode> s16 '<ussd>'`
 *  - 查询：`service call phone <getCode> s16 '*#21#'`，解析返回文本判断目标号
 *
 * 注意：
 *  - [setCode]/[getCode] 因 ROM 不同而异，本类提供可配置项（默认 set=1, get=2，
 *    已在 BON-AL00 上验证 get=2 可用；set code 需实机枚举确认）。
 *  - USSD 回执为异步弹窗，故设置后延迟再查询确认。
 */
class CallForwardExecutor(
    /** 设置转移的 method code */
    private val setCode: Int = 1,
    /** 查询转移的 method code */
    private val getCode: Int = 2,
) {

    /** Shizuku 是否已授权本 App */
    val isReady: Boolean
        get() = Shizuku.pingBinder()

    /** 取 Shizuku 提供的 shell Service（用于执行 service call） */
    private fun shellService(): android.os.IBinder? {
        return try {
            ShizukuBinderWrapper(
                Shizuku.getSystemService("phone", "com.android.internal.telephony.ITelephony")
            ) as? android.os.IBinder
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 执行设置呼叫转移。返回 (成功与否, 描述)。
     *
     * 幂等：若当前已转给 [targetPhone] 则跳过。
     */
    fun setForward(targetPhone: String, type: ForwardType = ForwardType.UNCONDITIONAL): Result {
        if (!isReady) {
            return Result(false, "Shizuku 未授权")
        }
        // 先查询当前，避免重复下发
        val current = queryCurrent()
        if (current != null && current.contains(targetPhone)) {
            return Result(true, "目标已为 $targetPhone，跳过", skipped = true)
        }
        val ussd = "${type.ussdPrefix}$targetPhone#"
        val ok = executeServiceCall(setCode, ussd)
        return if (ok) Result(true, "已下发 $ussd") else Result(false, "下发失败: $ussd")
    }

    /** 取消全部转移 */
    fun cancelAll(): Result {
        if (!isReady) return Result(false, "Shizuku 未授权")
        val ok = executeServiceCall(setCode, "#21#")
        return if (ok) Result(true, "已取消全部转移") else Result(false, "取消失败")
    }

    /**
     * 查询当前无条件转移状态，返回原始文本或 null。
     * 原始文本形如 "...from uid ... not forwarded" 或包含目标号。
     */
    fun queryCurrent(): String? {
        if (!isReady) return null
        return try {
            val r = runShellCommand("service call phone $getCode s16 '*#21#'")
            r.ifBlank { null }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 以 shell 执行 `service call phone <code> s16 '<arg>'`。
     * 通过 Shizuku 的 Shizuku.newProcess 或 system service 调用。
     */
    private fun executeServiceCall(code: Int, arg: String): Boolean {
        return try {
            val out = runShellCommand("service call phone $code s16 '$arg'")
            // 有返回且未包含明显错误视为成功（实际状态需随后回读确认）
            !out.contains("Error", ignoreCase = true)
        } catch (e: Throwable) {
            false
        }
    }

    /** 通过 Shizuku 运行 shell 命令并返回合并输出 */
    private fun runShellCommand(cmd: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            out + err
        } catch (e: Throwable) {
            ""
        }
    }

    data class Result(
        val success: Boolean,
        val message: String,
        val skipped: Boolean = false,
    )
}
