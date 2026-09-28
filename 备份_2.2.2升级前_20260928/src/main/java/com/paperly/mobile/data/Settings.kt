package com.paperly.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AI 与使用偏好。API Key 用 Android Keystore(AES-GCM) 加密后才写入 SharedPreferences。 */
data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val freeFallback: Boolean = true,
    val autoTranslate: Boolean = true,
    val stylusOnly: Boolean = false,
    val ocrChinese: Boolean = false,
) {
    val hasApi: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && textModel.isNotBlank()
}

data class Preset(val name: String, val baseUrl: String, val textModel: String, val visionModel: String, val note: String)

val PRESETS = listOf(
    Preset("智谱（免费）", "https://open.bigmodel.cn/api/paas/v4", "glm-4.7-flash", "glm-4.6v-flash", "注册 bigmodel.cn 获取 Key，Flash 系列免费，可看图解题"),
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "", "翻译便宜好用；不支持看图，圈题时发送识别文字"),
    Preset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "qwen-vl-plus", "阿里云百炼 Key"),
    Preset("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "moonshot-v1-8k-vision-preview", "月之暗面 Key"),
    Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o-mini", "需可访问的网络"),
)

class SettingsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("paperly2_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        baseUrl = sp.getString("base", "") ?: "",
        apiKey = sp.getString("key", null)?.let { runCatching { decrypt(it) }.getOrDefault("") } ?: "",
        textModel = sp.getString("tm", "") ?: "",
        visionModel = sp.getString("vm", "") ?: "",
        freeFallback = sp.getBoolean("free", true),
        autoTranslate = sp.getBoolean("auto", true),
        stylusOnly = sp.getBoolean("stylus", false),
        ocrChinese = sp.getBoolean("ocrzh", false),
    )

    fun save(s: AppSettings) {
        sp.edit()
            .putString("base", s.baseUrl.trim().trimEnd('/'))
            .putString("key", if (s.apiKey.isBlank()) null else encrypt(s.apiKey.trim()))
            .putString("tm", s.textModel.trim())
            .putString("vm", s.visionModel.trim())
            .putBoolean("free", s.freeFallback)
            .putBoolean("auto", s.autoTranslate)
            .putBoolean("stylus", s.stylusOnly)
            .putBoolean("ocrzh", s.ocrChinese)
            .apply()
    }

    /** 计时器开始时间（墙钟毫秒），进程被杀也能恢复。 */
    fun timerStart(docId: Long): Long = sp.getLong("timer_$docId", 0L)
    fun setTimerStart(docId: Long, v: Long) { sp.edit().putLong("timer_$docId", v).apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12))
    }

    companion object { private const val ALIAS = "paperly2_api_key" }
}
