package com.autophone.callforward.sync

import android.content.Context
import android.util.Log
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.model.Assignment
import com.autophone.callforward.model.DayRoster
import com.autophone.callforward.model.ForwardType
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Gist 云同步管理器。
 *
 * 配置（PAT / Gist ID）通过 [RosterStore.saveSetting] 持久化：
 *  - KEY_GIST_TOKEN：GitHub PAT（仅需 gist scope，敏感信息不落日志）
 *  - KEY_GIST_ID：Gist ID，为空时首次上传会自动创建公开 gist 并回存
 *
 * 上传：PATCH /gists/{id}，写入文件 call-forward-config.json
 * 下载：GET /gists/{id}，解析同结构 JSON 后写回 [RosterStore]
 *
 * 所有网络请求均需在子线程调用（内部不做线程切换）。
 */
class GistSyncManager(
    context: Context,
    private val store: RosterStore,
) {

    private val appContext = context.applicationContext

    /** 读取已配置的 PAT */
    fun loadToken(): String = store.getSetting(KEY_GIST_TOKEN, "")

    /** 读取已配置的 Gist ID */
    fun loadGistId(): String = store.getSetting(KEY_GIST_ID, "")

    /** 持久化 PAT 与 Gist ID */
    fun saveConfig(token: String, gistId: String) {
        store.saveSetting(KEY_GIST_TOKEN, token.trim())
        store.saveSetting(KEY_GIST_ID, gistId.trim())
    }

    /**
     * 上传 people+roster+forwardType 到 gist 文件 call-forward-config.json。
     * gistId 为空时自动创建新 gist 并持久化 ID。
     */
    @Throws(Exception::class)
    fun upload(token: String, gistId: String): String {
        val id = gistId.ifBlank { createGist(token) }
        val body = JSONObject().put(
            FILES_KEY, JSONObject().put(
                CONFIG_FILE, JSONObject().put("content", buildConfigJson())
            )
        )
        val conn = open(URL("$API_BASE/gists/$id"), "PATCH", token)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = readStream(conn)
        conn.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: ${abbreviate(resp)}")
        }
        if (gistId.isBlank()) {
            saveConfig(token, id)
        }
        return id
    }

    /**
     * 从 gist 拉取配置并写回 [RosterStore]（people/roster/forwardType）。
     * 返回 gist ID；公开 gist 无 PAT 也可读取。
     */
    @Throws(Exception::class)
    fun download(token: String, gistId: String): String {
        val conn = open(URL("$API_BASE/gists/$gistId"), "GET", token)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
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

    /** 从 gist 响应 JSON 中取出 call-forward-config.json 的 content */
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
    private fun createGist(token: String): String {
        if (token.isBlank()) {
            throw RuntimeException("首次上传需要 PAT 以创建 gist")
        }
        val body = JSONObject()
            .put("public", false)
            .put(
                "files", JSONObject().put(
                    CONFIG_FILE, JSONObject().put("content", buildConfigJson())
                )
            )
        val conn = open(URL(API_BASE + "/gists"), "POST", token)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
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

    private fun open(url: URL, method: String, token: String): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MILLIS
        conn.readTimeout = TIMEOUT_MILLIS
        conn.doOutput = method != "GET"
        if (token.isNotBlank()) {
            // Authorization: token <PAT>（不打印到日志）
            conn.setRequestProperty("Authorization", "token $token")
        }
        return conn
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
        private const val API_BASE = "https://api.github.com"
        private const val CONFIG_FILE = "call-forward-config.json"
        private const val FILES_KEY = "files"
        private const val TIMEOUT_MILLIS = 15000
        private const val MAX_ERR_LEN = 200

        /** 设置键：GitHub PAT（敏感，勿打印） */
        const val KEY_GIST_TOKEN = "gist_token"

        /** 设置键：Gist ID */
        const val KEY_GIST_ID = "gist_id"
    }
}
