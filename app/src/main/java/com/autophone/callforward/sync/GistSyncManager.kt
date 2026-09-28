package com.autophone.callforward.sync

import android.content.Context
import android.util.Log
import com.autophone.callforward.crypto.TokenCryptor
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.ForwardType
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * 云同步管理器：支持 GitHub Gist 与 Gitee Gist 双平台。
 *
 * 平台差异（实测确认）：
 *  - GitHub：`https://api.github.com/gists`，认证 `Authorization: token <PAT>`
 *  - Gitee：`https://gitee.com/api/v5/gists`，认证走 query 参数 `access_token`，
 *    创建 gist 必须带 `description` 字段（GitHub 无此要求，但带上双方兼容）
 *
 * GitCode 实测 `/api/v5/gists` 返回 404（无 Gist 功能），暂不支持；
 * 如需 GitCode 可基于其仓库文件 API 另行实现（见 README 待办）。
 *
 * 安全：访问令牌经 [TokenCryptor]（Keystore AES-256-GCM）加密后存于独立
 * 的 secure prefs 文件（并排除在云备份之外），**不以明文落盘**；
 * 旧版本存于 roster prefs 的明文令牌会在首次访问时自动迁移并清除。
 * 配置按平台隔离（[keyOf] 生成平台前缀键）：
 *  - `<platform>_token`：访问令牌密文
 *  - `<platform>_gist_id`：Gist ID（非敏感，明文）
 *
 * 所有网络请求均需在子线程调用（内部不做线程切换）。
 */
class GistSyncManager(
    context: Context,
    private val store: RosterStore,
) {

    /** 同步平台定义 */
    enum class SyncPlatform(
        val label: String,
        val apiBase: String,
    ) {
        GITHUB("GitHub Gist", "https://api.github.com"),
        GITEE("Gitee Gist（码云）", "https://gitee.com/api/v5"),
    }

    private val appContext = context.applicationContext

    /** 敏感令牌专用存储：独立 prefs 文件，便于整体排除出云备份 */
    private val securePrefs = appContext.getSharedPreferences(
        SECURE_PREFS_FILE, Context.MODE_PRIVATE
    )

    // ---------- 平台配置存取 ----------

    /** 读取令牌：解密密文；检测到旧版明文令牌时自动迁移为密文存储。 */
    fun loadToken(platform: SyncPlatform): String {
        val key = keyOf(platform, KEY_SUFFIX_TOKEN)
        val stored = securePrefs.getString(key, "").orEmpty()
        if (stored.isBlank()) {
            // 尝试从 roster prefs 的旧键迁移（v0.1.4 及之前版本明文存储）
            return migrateLegacyToken(platform)
        }
        return TokenCryptor.decrypt(stored)
    }

    /** 读取 Gist ID（非敏感，明文） */
    fun loadGistId(platform: SyncPlatform): String {
        val stored = securePrefs.getString(keyOf(platform, KEY_SUFFIX_GIST), "").orEmpty()
        if (stored.isNotBlank()) return stored
        // 迁移旧版存在 roster prefs 里的 gist id
        val legacy = store.getSetting(keyOf(platform, KEY_SUFFIX_GIST), "")
        if (legacy.isNotBlank()) {
            securePrefs.edit().putString(keyOf(platform, KEY_SUFFIX_GIST), legacy).apply()
            store.saveSetting(keyOf(platform, KEY_SUFFIX_GIST), "")
        }
        return legacy
    }

    /** 保存令牌（加密落盘）与 Gist ID */
    fun saveConfig(platform: SyncPlatform, token: String, gistId: String) {
        saveToken(platform, token)
        saveGistId(platform, gistId)
    }

    /** 仅保存令牌（加密） */
    fun saveToken(platform: SyncPlatform, token: String) {
        val key = keyOf(platform, KEY_SUFFIX_TOKEN)
        if (token.isBlank()) {
            securePrefs.edit().remove(key).apply()
            return
        }
        securePrefs.edit()
            .putString(key, TokenCryptor.encrypt(token))
            .apply()
    }

    /** 仅保存 Gist ID（明文） */
    fun saveGistId(platform: SyncPlatform, gistId: String) {
        securePrefs.edit()
            .putString(keyOf(platform, KEY_SUFFIX_GIST), gistId.trim())
            .apply()
    }

    /**
     * 旧版迁移：v0.1.4 及之前把明文令牌存在 roster prefs（键 `gist_token`）。
     * 读出后加密写入 secure prefs，并清除所有旧键。
     */
    private fun migrateLegacyToken(platform: SyncPlatform): String {
        val legacyToken = store.getSetting(LEGACY_KEY_GIST_TOKEN, "")
        if (legacyToken.isBlank()) return ""
        // 仅在目标平台尚无密文时迁移，避免覆盖用户已重新配置的新令牌
        saveToken(platform, legacyToken)
        store.saveSetting(LEGACY_KEY_GIST_TOKEN, "")
        store.saveSetting(LEGACY_KEY_GIST_ID, "")
        Log.i(TAG, "已迁移旧版明文令牌为加密存储")
        return legacyToken
    }

    private fun keyOf(platform: SyncPlatform, suffix: String): String =
        platform.name.lowercase() + "_" + suffix

    // ---------- 上传 / 下载 ----------

    /**
     * 上传 people+roster+forwardType 到 gist 文件 [CONFIG_FILE]。
     * gistId 为空时自动创建新私密 gist 并持久化 ID。
     */
    @Throws(Exception::class)
    fun upload(
        platform: SyncPlatform,
        token: String,
        gistId: String,
    ): String {
        val id = gistId.ifBlank { createGist(platform, token) }
        val body = JSONObject().put(
            FILES_KEY, JSONObject().put(
                CONFIG_FILE, JSONObject().put("content", buildConfigJson())
            )
        )
        val conn = open(platform, URL("${platform.apiBase}/gists/$id"), "PATCH", token)
        writeBody(conn, body)
        val code = conn.responseCode
        val resp = readStream(conn)
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: ${abbreviate(resp)}")
        }
        if (gistId.isBlank()) {
            saveConfig(platform, token, id)
        }
        return id
    }

    /**
     * 从 gist 拉取配置并写回 [RosterStore]（people/roster/forwardType）。
     * 返回 gist ID；公开 gist 无 token 也可读取。
     */
    @Throws(Exception::class)
    fun download(
        platform: SyncPlatform,
        token: String,
        gistId: String,
    ): String {
        if (gistId.isBlank()) throw RuntimeException("请先填写 Gist ID 或先执行一次上传")
        val conn = open(platform, URL("${platform.apiBase}/gists/$gistId"), "GET", token)
        val code = conn.responseCode
        val resp = readStream(conn)
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: ${abbreviate(resp)}")
        }
        applyConfigJson(extractConfigJson(resp))
        return gistId
    }

    // ---------- JSON 组装 / 解析 ----------

    /** 打包 people+roster+forwardType（结构对齐 DeepLink 导入格式） */
    private fun buildConfigJson(): String {
        val root = JSONObject()
        val people = JSONObject()
        store.loadPeople().forEach { (name, p) -> people.put(name, p.phone) }
        root.put("people", people)

        val roster = org.json.JSONArray()
        store.loadRoster().forEach { day ->
            val d = JSONObject().put("date", day.date)
            val asgs = org.json.JSONArray()
            day.assignments.forEach { a ->
                asgs.put(JSONObject().put("shift", a.shiftName).put("person", a.personName))
            }
            d.put("assignments", asgs)
            roster.put(d)
        }
        root.put("roster", roster)
        root.put("forwardType", store.loadForwardType().name)
        return root.toString()
    }

    /** 从 gist 响应 JSON 中取出 [CONFIG_FILE] 的 content */
    private fun extractConfigJson(response: String): String {
        val root = JSONObject(response)
        val files = root.optJSONObject("files")
            ?: throw RuntimeException("gist 响应缺少 files")
        val file = files.optJSONObject(CONFIG_FILE)
            ?: throw RuntimeException("gist 中未找到 ${CONFIG_FILE}")
        val truncated = file.optBoolean("truncated", false)
        if (truncated) {
            throw RuntimeException("gist 内容过大被截断，请减小排班数据后重试")
        }
        return file.getString("content")
    }

    /** 解析配置 JSON 并写入 [RosterStore]（复用 DeepLink 的字段结构） */
    private fun applyConfigJson(json: String) {
        val root = JSONObject(json)
        val people = mutableMapOf<String, String>()
        root.optJSONObject("people")?.let { obj ->
            obj.keys().forEach { k -> people[k] = obj.optString(k, "") }
        }
        val roster = mutableListOf<DayRoster>()
        root.optJSONArray("roster")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val asgs = mutableListOf<Assignment>()
                d.optJSONArray("assignments")?.let { asgArr ->
                    for (j in 0 until asgArr.length()) {
                        val a = asgArr.getJSONObject(j)
                        asgs += Assignment(a.getString("shift"), a.getString("person"))
                    }
                }
                roster += DayRoster(d.getString("date"), asgs)
            }
        }
        store.save(people, roster)
        root.optString("forwardType").takeIf { it.isNotBlank() }?.let { raw ->
            parseForwardType(raw)?.let { store.saveForwardType(it) }
        }
    }

    /** forwardType 字符串 → 枚举，非法值返回 null（保持现有设置） */
    private fun parseForwardType(raw: String): ForwardType? {
        return try {
            ForwardType.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "未知的 forwardType: $raw")
            null
        }
    }

    // ---------- HTTP ----------

    /** gist 为空时首次创建，返回新 gist ID */
    @Throws(Exception::class)
    private fun createGist(platform: SyncPlatform, token: String): String {
        if (token.isBlank()) {
            throw RuntimeException("首次上传需要访问令牌（PAT）以创建 gist")
        }
        val body = JSONObject()
            // Gitee 创建 gist 必须带 description；GitHub 兼容该字段
            .put("description", "呼叫转移排班助手配置同步")
            .put("public", false)
            .put(
                "files", JSONObject().put(
                    CONFIG_FILE, JSONObject().put("content", buildConfigJson())
                )
            )
        val conn = open(platform, URL("${platform.apiBase}/gists"), "POST", token)
        writeBody(conn, body)
        val code = conn.responseCode
        val resp = readStream(conn)
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("创建 gist 失败 HTTP $code: ${abbreviate(resp)}")
        }
        val id = JSONObject(resp).optString("id", "")
        if (id.isBlank()) throw RuntimeException("创建 gist 响应缺少 id")
        return id
    }

    /**
     * 构造已认证的连接。
     * GitHub：`Authorization: token <PAT>`；
     * Gitee：query 参数 `access_token`（官方推荐方式，兼容性最好）。
     */
    private fun open(
        platform: SyncPlatform,
        target: URL,
        method: String,
        token: String,
    ): HttpURLConnection {
        val url = if (platform == SyncPlatform.GITEE && token.isNotBlank()) {
            val sep = if (target.query.isNullOrBlank()) "?" else "&"
            URL(target.toString() + sep + "access_token=" + URLEncoder.encode(token, "UTF-8"))
        } else {
            target
        }
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MILLIS
        conn.readTimeout = TIMEOUT_MILLIS
        conn.doOutput = method != "GET"
        if (token.isNotBlank() && platform == SyncPlatform.GITHUB) {
            // GitHub 认证头（不打印到日志）
            conn.setRequestProperty("Authorization", "token $token")
        }
        if (method != "GET") {
            conn.setRequestProperty("Content-Type", "application/json")
        }
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, body: JSONObject) {
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun readStream(conn: HttpURLConnection): String {
        val stream = try {
            conn.inputStream
        } catch (e: Exception) {
            conn.errorStream
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
            r.readText()
        }
    }

    /** 错误响应截断，避免超长 HTML 错误页刷屏 */
    private fun abbreviate(s: String): String {
        return if (s.length > MAX_ERR_LEN) s.substring(0, MAX_ERR_LEN) + "..." else s
    }

    companion object {
        private const val TAG = "GistSyncManager"
        private const val CONFIG_FILE = "call-forward-config.json"
        private const val FILES_KEY = "files"
        private const val TIMEOUT_MILLIS = 15000
        private const val MAX_ERR_LEN = 200

        /** 敏感令牌专用 prefs 文件（需排除出云备份） */
        const val SECURE_PREFS_FILE = "secure_store"

        /** 设置键后缀（实际键为 `<platform>_` + 后缀） */
        private const val KEY_SUFFIX_TOKEN = "token"
        private const val KEY_SUFFIX_GIST = "gist_id"

        /** 兼容旧版本：迁移此前仅支持 GitHub 时的明文配置键 */
        const val LEGACY_KEY_GIST_TOKEN = "gist_token"
        const val LEGACY_KEY_GIST_ID = "gist_id"
    }
}
