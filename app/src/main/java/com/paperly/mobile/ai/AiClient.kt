package com.paperly.mobile.ai

import android.util.Base64
import com.paperly.mobile.data.AppSettings
import com.paperly.mobile.data.Granularity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AiException(msg: String) : Exception(msg)

/**
 * OpenAI 兼容 Chat Completions 客户端（智谱 / DeepSeek / 通义 / Kimi / OpenAI 通用），
 * 外加免 Key 的 MyMemory 免费翻译作为兜底。
 */
object AiClient {

    data class Result(val text: String, val provider: String)

    suspend fun translate(s: AppSettings, text: String, g: Granularity): Result {
        if (s.hasApi) {
            try {
                val prompt = when (g) {
                    Granularity.WORD -> "你是考研英语词典。给出英文单词“$text”的：音标；常见词性与中文释义（最多 4 条，考研高频义项在前）；1 个常用搭配。简洁排版，不要寒暄。"
                    Granularity.PHRASE -> "把下面的英文短语译成中文，并用一句话说明用法（如有固定搭配请指出）。不要寒暄。\n\n$text"
                    Granularity.SENTENCE -> "把下面的考研英语句子翻译成通顺的中文。若句子较长，另起一行用“结构：”简要拆解主干。不要寒暄。\n\n$text"
                }
                return Result(chat(s, s.textModel, prompt, null), s.textModel)
            } catch (e: Exception) {
                if (!s.freeFallback) throw e
            }
        }
        if (s.freeFallback) return Result(myMemory(text), "免费 · MyMemory")
        throw AiException("未配置 AI 接口，且已关闭免费翻译。请到设置中填写。")
    }

    /**
     * 解题：直接把框选区域截图发给多模态模型识图解题（不再依赖本地 OCR，排版乱的文档也能识别）。
     * 解题模型留空时，按 Base URL 自动选用对应服务的视觉模型。
     */
    suspend fun solve(s: AppSettings, jpeg: ByteArray): Result {
        if (!s.hasApi) throw AiException("解题需要 AI 接口。推荐在设置里选择“智谱（免费）”，注册后填写 Key。")
        val model = s.visionModel.ifBlank { defaultVision(s.baseUrl) }
            ?: throw AiException("识图解题需要支持图片的模型，当前服务（如 DeepSeek）不支持看图。请在设置中填写“解题模型”，或切换到智谱（免费 glm-4.6v-flash）/通义 qwen-vl。")
        val prompt = "你是考研数学/英语/专业课辅导老师。图片是从试卷上框选截取的一道或几道题，版面可能不规整。\n" +
            "请先准确识别题目（必要时用一两行复述题干），然后用一行给出最终答案，再分步骤给出简洁推导。" +
            "数学式用纯文本和 Unicode 书写（如 x²、√、∫、≤、π），不要使用 LaTeX 或 \$ 符号。若图片不完整，请指出缺少的部分。"
        return Result(chat(s, model, prompt, jpeg), model)
    }

    fun defaultVision(baseUrl: String): String? = com.paperly.mobile.data.PRESETS
        .firstOrNull { it.baseUrl.trimEnd('/') == baseUrl.trimEnd('/') }?.visionModel?.ifBlank { null }

    suspend fun test(s: AppSettings): String = chat(s, s.textModel, "只回复：OK", null)

    private suspend fun chat(s: AppSettings, model: String, prompt: String, image: ByteArray?): String = withContext(Dispatchers.IO) {
        val content: Any = if (image == null) prompt else JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            val b64 = Base64.encodeToString(image, Base64.NO_WRAP)
            // 智谱接受裸 base64；其余 OpenAI 兼容服务使用 data URL。
            val url = if (s.baseUrl.contains("bigmodel.cn")) b64 else "data:image/jpeg;base64,$b64"
            put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
        }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val conn = (URL(s.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${s.apiKey}")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(txt).optJSONObject("error")?.optString("message") }.getOrNull()
                throw AiException("接口错误 $code：${msg?.takeIf { it.isNotBlank() } ?: txt.take(200)}")
            }
            JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } finally { conn.disconnect() }
    }

    private suspend fun myMemory(text: String): String = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(text.take(480), "UTF-8")
        val conn = (URL("https://api.mymemory.translated.net/get?q=$q&langpair=en|zh-CN").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
        }
        try {
            if (conn.responseCode !in 200..299) throw AiException("免费翻译不可用（${conn.responseCode}）")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val out = j.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (out.isBlank() || out.contains("MYMEMORY WARNING", true)) throw AiException("免费翻译今日额度已用完，请配置 AI 接口")
            out
        } finally { conn.disconnect() }
    }
}
