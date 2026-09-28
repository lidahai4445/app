package com.paperly.mobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.mobile.ai.AiClient
import com.paperly.mobile.data.AppSettings
import com.paperly.mobile.data.AssistItem
import com.paperly.mobile.data.AssistTool
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.Doc
import com.paperly.mobile.data.Granularity
import com.paperly.mobile.data.ItemKind
import com.paperly.mobile.data.OcrWord
import com.paperly.mobile.data.SettingsStore
import com.paperly.mobile.data.Stroke
import com.paperly.mobile.data.Tool
import com.paperly.mobile.ocr.Ocr
import com.paperly.mobile.pdf.PdfDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class ReaderViewModel(app: Application, private val docId: Long) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val store = SettingsStore(app)

    val doc: Doc? = db.doc(docId)
    val pdf: PdfDoc? = doc?.let { runCatching { PdfDoc(File(it.path)) }.getOrNull() }
    val pageCount: Int = pdf?.pageCount ?: 0

    var settings by mutableStateOf(store.load()); private set

    // ------------ 页面 ------------
    var page by mutableStateOf((doc?.lastPage ?: 0).coerceIn(0, max(0, pageCount - 1))); private set
    var aspect by mutableStateOf(1.414f); private set

    // ------------ 模式与工具 ------------
    var assist by mutableStateOf(false); private set
    var tool by mutableStateOf(Tool.PEN)
    var penColor by mutableStateOf(PEN_COLORS[0])
    var hlColor by mutableStateOf(HL_COLORS[0])
    var penWidthIdx by mutableStateOf(1)
    var hlWidthIdx by mutableStateOf(1)
    var assistTool by mutableStateOf(AssistTool.SELECT)
    var granularity by mutableStateOf(Granularity.WORD)

    // ------------ 笔迹 ------------
    var strokes by mutableStateOf<List<Stroke>>(emptyList()); private set
    private sealed interface Action { data class Add(val s: Stroke) : Action; data class Remove(val list: List<Stroke>) : Action }
    private val undoStacks = HashMap<Int, ArrayDeque<Action>>()
    private val redoStacks = HashMap<Int, ArrayDeque<Action>>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    private val erasing = ArrayList<Stroke>()

    // ------------ 辅助模式 ------------
    var ocrWords by mutableStateOf<List<OcrWord>?>(null); private set
    var ocrBusy by mutableStateOf(false); private set
    var ocrError by mutableStateOf<String?>(null); private set
    var selection by mutableStateOf<IntRange?>(null)
    var items by mutableStateOf<List<AssistItem>>(emptyList()); private set
    var focusedItem by mutableStateOf<Long?>(null)
    private var ocrJob: Job? = null

    // ------------ 计时（隐藏） ------------
    var timerRunning by mutableStateOf(store.timerStart(docId) > 0); private set

    /** 结束计时后的一次性结果：用时 + 本次新增查词/解题数。 */
    data class TimerOutcome(val durationMs: Long, val lookups: Int, val solves: Int)
    var timerResult by mutableStateOf<TimerOutcome?>(null)

    /** Must be initialized before init calls refreshMarks(). */
    var strokePages by mutableStateOf(emptySet<Int>()); private set

    init {
        db.touchOpened(docId)
        refreshMarks()
        loadPage()
    }

    private fun refreshMarks() { strokePages = db.pagesWithStrokes(docId) }

    fun reloadSettings() {
        val old = settings
        settings = store.load()
        if (assist && old.ocrChinese != settings.ocrChinese) runOcr()
    }

    fun goTo(p: Int) {
        if (p !in 0 until pageCount || p == page) return
        page = p
        db.setLastPage(docId, p)
        loadPage()
    }

    private fun loadPage() {
        val p = page
        selection = null; focusedItem = null; ocrWords = null; ocrError = null
        strokes = db.strokes(docId, p)
        items = db.items(docId, p)
        refreshUndo()
        viewModelScope.launch { pdf?.let { aspect = it.aspect(p) } }
        if (assist) runOcr()
    }

    fun setAssistMode(on: Boolean) {
        if (assist == on) return
        assist = on
        selection = null
        if (on && ocrWords == null) runOcr()
    }

    // ================= 笔迹 =================
    fun addStroke(points: FloatArray, highlighter: Boolean) {
        if (points.size < 2) return
        val color = if (highlighter) hlColor else penColor
        val width = if (highlighter) HL_WIDTHS[hlWidthIdx] else PEN_WIDTHS[penWidthIdx]
        var s = Stroke(0, page, highlighter, color, width, points)
        s = s.copy(id = db.insertStroke(docId, s))
        strokes = strokes + s
        push(Action.Add(s))
    }

    fun eraseAt(x: Float, y: Float, r: Float) {
        val hit = strokes.filter { s -> hits(s, x, y, r) }
        if (hit.isEmpty()) return
        erasing += hit
        db.deleteStrokes(hit.map { it.id })
        strokes = strokes - hit.toSet()
    }

    fun endErase() {
        if (erasing.isNotEmpty()) push(Action.Remove(erasing.toList()))
        erasing.clear()
    }

    private fun hits(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val rr = r + s.width / 2
        val p = s.points
        var i = 0
        while (i + 1 < p.size) {
            if (hypot(p[i] - x, p[i + 1] - y) < rr) return true
            i += 2
        }
        return false
    }

    private fun push(a: Action) {
        refreshMarks()
        undoStacks.getOrPut(page) { ArrayDeque() }.addLast(a)
        redoStacks[page]?.clear()
        refreshUndo()
    }

    fun undo() {
        val a = undoStacks[page]?.removeLastOrNull() ?: return
        apply(a, reverse = true)
        refreshMarks()
        redoStacks.getOrPut(page) { ArrayDeque() }.addLast(a)
        refreshUndo()
    }

    fun redo() {
        val a = redoStacks[page]?.removeLastOrNull() ?: return
        apply(a, reverse = false)
        refreshMarks()
        undoStacks.getOrPut(page) { ArrayDeque() }.addLast(a)
        refreshUndo()
    }

    private fun apply(a: Action, reverse: Boolean) {
        val add = (a is Action.Add) != reverse
        val list = when (a) { is Action.Add -> listOf(a.s); is Action.Remove -> a.list }
        if (add) {
            list.forEach { db.insertStroke(docId, it) }
            strokes = (strokes + list).sortedBy { it.id }
        } else {
            db.deleteStrokes(list.map { it.id })
            val ids = list.map { it.id }.toSet()
            strokes = strokes.filterNot { it.id in ids }
        }
    }

    private fun refreshUndo() {
        canUndo = undoStacks[page]?.isNotEmpty() == true
        canRedo = redoStacks[page]?.isNotEmpty() == true
    }

    // ================= OCR =================
    fun runOcr(force: Boolean = false) {
        val p = page
        val pdf = pdf ?: return
        val lang = if (settings.ocrChinese) "zh" else "en"
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            ocrBusy = true; ocrError = null
            try {
                val cached = if (force) null else withContext(Dispatchers.IO) { db.ocr(docId, p, lang) }
                val words = if (cached != null) Ocr.fromJson(cached) else {
                    val bmp = pdf.render(p, 2000)
                    val w = Ocr.recognize(bmp, settings.ocrChinese)
                    withContext(Dispatchers.IO) { db.putOcr(docId, p, lang, Ocr.toJson(w)) }
                    w
                }
                if (page == p) ocrWords = words
            } catch (e: Exception) {
                if (page == p) ocrError = "识别失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                if (page == p) ocrBusy = false
            }
        }
    }

    // ================= 翻译 =================
    /** 手势结束后调用。range 为 ocrWords 下标区间。 */
    fun onSelected(range: IntRange, dragged: Boolean) {
        val words = ocrWords ?: return
        val r = if (!dragged && granularity == Granularity.SENTENCE) Ocr.sentenceRange(words, range.first) else range
        selection = r
        if (settings.autoTranslate) translateSelection()
    }

    fun translateSelection(force: Boolean = false) {
        val words = ocrWords ?: return
        val r = selection ?: return
        if (r.first < 0 || r.last >= words.size || r.first > r.last) return
        val picked = words.slice(r)
        val text = Ocr.join(picked)
        if (text.isBlank()) return
        val g = when {
            r.count() == 1 -> Granularity.WORD
            granularity == Granularity.SENTENCE || r.count() >= 8 -> Granularity.SENTENCE
            else -> Granularity.PHRASE
        }
        val box = RectF(picked.minOf { it.box.left }, picked.minOf { it.box.top }, picked.maxOf { it.box.right }, picked.maxOf { it.box.bottom })
        // 本页已有同一条就直接定位
        items.firstOrNull { it.kind == ItemKind.TRANSLATE && it.source == text && !it.loading }?.let { if (!force) { focusedItem = it.id; return } }

        val key = Ocr.cacheKey(text, g.name)
        if (force) db.deleteTranslation(key)
        val hit = db.cachedTranslation(key)
        if (hit != null) {
            addItem(AssistItem(0, docId, page, ItemKind.TRANSLATE, text, hit.first, hit.second, box, true, System.currentTimeMillis()))
            return
        }
        val tmp = AssistItem(-System.nanoTime(), docId, page, ItemKind.TRANSLATE, text, "", "", box, false, System.currentTimeMillis(), loading = true)
        items = listOf(tmp) + items
        focusedItem = tmp.id
        val p = page
        val s = settings
        viewModelScope.launch {
            try {
                val res = AiClient.translate(s, text, g)
                withContext(Dispatchers.IO) { db.putTranslation(key, text, res.text, res.provider) }
                replaceTmp(tmp, p, tmp.copy(result = res.text, provider = res.provider, loading = false))
            } catch (e: Exception) {
                replaceTmp(tmp, p, tmp.copy(result = e.message ?: "请求失败", loading = false, error = true), persist = false)
            }
        }
    }

    // ================= 圈题解答 =================
    fun solveRegion(rect: RectF) {
        val pdf = pdf ?: return
        val pad = 0.01f
        val r = RectF((rect.left - pad).coerceAtLeast(0f), (rect.top - pad).coerceAtLeast(0f), (rect.right + pad).coerceAtMost(1f), (rect.bottom + pad).coerceAtMost(1f))
        if (r.width() < 0.03f || r.height() < 0.015f) return
        val key = "${doc?.path}|$page|${r.left.q()},${r.top.q()},${r.right.q()},${r.bottom.q()}|${settings.visionModel.ifBlank { settings.textModel }}"
        val ocrText = ocrWords?.filter { r.contains(it.box.centerX(), it.box.centerY()) }?.let { Ocr.join(it) }.orEmpty()
        val label = ocrText.ifBlank { "圈选区域（第 ${page + 1} 页）" }.take(160)
        db.cachedSolution(key)?.let { hit ->
            addItem(AssistItem(0, docId, page, ItemKind.SOLVE, label, hit.first, hit.second, r, true, System.currentTimeMillis()))
            return
        }
        val tmp = AssistItem(-System.nanoTime(), docId, page, ItemKind.SOLVE, label, "", "", r, false, System.currentTimeMillis(), loading = true)
        items = listOf(tmp) + items
        focusedItem = tmp.id
        val p = page
        val s = settings
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { crop(pdf.render(p, 2000), r) }
                val res = AiClient.solve(s, jpeg, ocrText)
                withContext(Dispatchers.IO) { db.putSolution(key, res.text, res.provider) }
                replaceTmp(tmp, p, tmp.copy(result = res.text, provider = res.provider, loading = false))
            } catch (e: Exception) {
                replaceTmp(tmp, p, tmp.copy(result = e.message ?: "请求失败", loading = false, error = true), persist = false)
            }
        }
    }

    private fun crop(full: Bitmap, r: RectF): ByteArray {
        val x = (r.left * full.width).roundToInt(); val y = (r.top * full.height).roundToInt()
        val w = (r.width() * full.width).roundToInt().coerceAtMost(full.width - x)
        val h = (r.height() * full.height).roundToInt().coerceAtMost(full.height - y)
        var b = Bitmap.createBitmap(full, x, y, w, h)
        val longest = max(w, h)
        if (longest > 1600) b = Bitmap.createScaledBitmap(b, w * 1600 / longest, h * 1600 / longest, true)
        return ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it); it.toByteArray() }
    }

    private fun Float.q() = (this * 100).roundToInt()

    fun regenerate(item: AssistItem) {
        deleteItem(item)
        if (item.kind == ItemKind.TRANSLATE) {
            Granularity.entries.forEach { db.deleteTranslation(Ocr.cacheKey(item.source, it.name)) }
            val words = ocrWords ?: return
            // 按框重新找回选区
            val b = item.box ?: return
            val idx = words.indices.filter { b.contains(words[it].box.centerX(), words[it].box.centerY()) }
            if (idx.isEmpty()) return
            selection = idx.first()..idx.last()
            translateSelection(force = true)
        } else {
            item.box?.let { box ->
                val key = "${doc?.path}|$page|"
                // 清掉该区域的缓存后重算
                db.deleteSolution("$key${box.left.q()},${box.top.q()},${box.right.q()},${box.bottom.q()}|${settings.visionModel.ifBlank { settings.textModel }}")
                solveRegion(RectF(box.left + 0.01f, box.top + 0.01f, box.right - 0.01f, box.bottom - 0.01f))
            }
        }
    }

    fun deleteItem(item: AssistItem) {
        if (item.id > 0) db.deleteItem(item.id)
        items = items.filterNot { it.id == item.id }
        if (focusedItem == item.id) focusedItem = null
    }

    private fun addItem(it: AssistItem) {
        val id = db.insertItem(it)
        items = listOf(it.copy(id = id)) + items
        focusedItem = id
    }

    private fun replaceTmp(tmp: AssistItem, p: Int, done: AssistItem, persist: Boolean = true) {
        val saved = if (persist) done.copy(id = db.insertItem(done)) else done
        if (page == p) {
            items = items.map { if (it.id == tmp.id) saved else it }
            if (focusedItem == tmp.id) focusedItem = saved.id
        }
    }

    // ================= 计时 =================
    fun startTimer() {
        store.setTimerStart(docId, System.currentTimeMillis())
        timerRunning = true
    }

    fun stopTimer() {
        val start = store.timerStart(docId)
        if (start <= 0) { timerRunning = false; return }
        val dur = (System.currentTimeMillis() - start).coerceAtLeast(0)
        db.addSession(docId, start, dur)
        store.setTimerStart(docId, 0)
        timerRunning = false
        val counts = db.itemCountsSince(docId, start)
        timerResult = TimerOutcome(dur, counts.first, counts.second)
    }

    override fun onCleared() { pdf?.close() }

    companion object {
        // 与效果图一致：墨蓝黑、松绿、朱红、钴蓝
        val PEN_COLORS = listOf(0xFF1F2B45.toInt(), 0xFF2F6B52.toInt(), 0xFFC0473B.toInt(), 0xFF3F74C9.toInt())
        val HL_COLORS = listOf(0xFFF3CD54.toInt(), 0xFF9BE37A.toInt(), 0xFF7FD3FF.toInt(), 0xFFF5A8B8.toInt())
        // 粗细按页宽归一化：细 / 中 / 粗
        val PEN_WIDTHS = listOf(0.0020f, 0.0028f, 0.0042f)
        val HL_WIDTHS = listOf(0.014f, 0.018f, 0.024f)
    }
}
