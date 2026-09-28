package com.paperly.mobile.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paperly.mobile.data.OcrWord
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google ML Kit 离线文字识别（模型随 APK 打包，不联网）。 */
object Ocr {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val chinese by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    suspend fun recognize(bmp: Bitmap, chineseModel: Boolean): List<OcrWord> = suspendCancellableCoroutine { cont ->
        val client = if (chineseModel) chinese else latin
        client.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
                val out = ArrayList<OcrWord>()
                var lineIdx = 0
                text.textBlocks.forEachIndexed { bi, block ->
                    for (line in block.lines) {
                        for (el in line.elements) {
                            val b = el.boundingBox ?: continue
                            out += OcrWord(el.text, RectF(b.left / w, b.top / h, b.right / w, b.bottom / h), lineIdx, bi)
                        }
                        lineIdx++
                    }
                }
                cont.resume(out)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun toJson(words: List<OcrWord>): String = JSONArray().apply {
        for (w in words) put(JSONObject().put("t", w.text).put("l", w.box.left.toDouble()).put("u", w.box.top.toDouble())
            .put("r", w.box.right.toDouble()).put("b", w.box.bottom.toDouble()).put("n", w.line).put("k", w.block))
    }.toString()

    fun fromJson(s: String): List<OcrWord> {
        val a = JSONArray(s)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            OcrWord(o.getString("t"), RectF(o.getDouble("l").toFloat(), o.getDouble("u").toFloat(), o.getDouble("r").toFloat(), o.getDouble("b").toFloat()), o.getInt("n"), o.getInt("k"))
        }
    }

    /** 把选中的词按阅读顺序拼成文本：同行空格分隔；行尾连字符自动合并。 */
    fun join(words: List<OcrWord>): String {
        val sb = StringBuilder()
        words.forEachIndexed { i, w ->
            if (i > 0) {
                val prev = words[i - 1]
                if (prev.line != w.line && sb.endsWith("-")) sb.setLength(sb.length - 1) else sb.append(' ')
            }
            sb.append(w.text)
        }
        return sb.toString().trim()
    }

    private val sentenceEnd = Regex("[.!?;。！？；][\"'”’)]*$")

    /** 从 index 扩展到整句（不跨段落块）。 */
    fun sentenceRange(words: List<OcrWord>, index: Int): IntRange {
        var s = index
        while (s > 0 && words[s - 1].block == words[index].block && !sentenceEnd.containsMatchIn(words[s - 1].text)) s--
        var e = index
        while (e < words.lastIndex && words[e + 1].block == words[index].block && !sentenceEnd.containsMatchIn(words[e].text)) e++
        return s..e
    }

    /** 缓存键：规范化空白、去掉首尾标点、小写。 */
    fun cacheKey(text: String, g: String): String {
        val norm = text.replace(Regex("\\s+"), " ").trim().trim { !it.isLetterOrDigit() }.lowercase()
        return "zh|$g|$norm"
    }
}
