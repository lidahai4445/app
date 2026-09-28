package com.paperly.mobile.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.theme.Paper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.paperly.mobile.data.AssistTool
import com.paperly.mobile.data.ItemKind
import com.paperly.mobile.data.OcrWord
import com.paperly.mobile.data.Stroke
import com.paperly.mobile.data.Tool
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Gesture { DRAW, ERASE, SELECT, LASSO, NAV }

/**
 * 单页视图：PDF 位图 + 笔迹 + OCR 选区 + 结果框 + 圈题轨迹，全部画在一个 Canvas 上。
 * 手势：单指按当前工具处理；双指始终缩放/平移；浏览工具下单指左右滑翻页。
 */
@Composable
fun PageView(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        if (cw < 1f || ch < 1f) return@BoxWithConstraints
        val aspect = vm.aspect
        val pageW = min(cw, ch / aspect)
        val pageH = pageW * aspect
        val left0 = (cw - pageW) / 2
        val top0 = (ch - pageH) / 2

        val page = vm.page
        var scale by remember(page) { mutableStateOf(1f) }
        var offset by remember(page) { mutableStateOf(Offset.Zero) }

        // 位图：按显示宽度 ×2 渲染，放大后依然清晰；并预取下一页
        var bmp by remember(page) { mutableStateOf<Bitmap?>(null) }
        val renderW = (pageW * 2).roundToInt()
        LaunchedEffect(page, renderW) {
            val pdf = vm.pdf ?: return@LaunchedEffect
            bmp = pdf.render(page, renderW)
            if (page + 1 < vm.pageCount) pdf.render(page + 1, renderW)
        }

        val live = remember { mutableStateListOf<Offset>() }  // 归一化坐标
        var liveKind by remember { mutableStateOf(Gesture.NAV) }
        var selStart by remember { mutableStateOf(-1) }
        var pendingLasso by remember(page) { mutableStateOf<RectF?>(null) }

        val cur by rememberUpdatedState(Geo(pageW, pageH, left0, top0))
        val assist by rememberUpdatedState(vm.assist)
        val tool by rememberUpdatedState(vm.tool)
        val assistTool by rememberUpdatedState(vm.assistTool)
        val stylusOnly by rememberUpdatedState(vm.settings.stylusOnly)
        val words by rememberUpdatedState(vm.ocrWords)

        fun toPage(p: Offset): Offset {
            val bx = (p.x - offset.x) / scale
            val by = (p.y - offset.y) / scale
            return Offset((bx - cur.left) / cur.w, (by - cur.top) / cur.h)
        }

        fun clampOffset(o: Offset, s: Float) = Offset(o.x.coerceIn(cw - cw * s, 0f), o.y.coerceIn(ch - ch * s, 0f))

        val gestureMod = Modifier.pointerInput(page, cw, ch) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                val kind = when {
                    assist && assistTool == AssistTool.SELECT -> Gesture.SELECT
                    assist && assistTool == AssistTool.LASSO -> Gesture.LASSO
                    assist -> Gesture.NAV
                    tool == Tool.HAND -> Gesture.NAV
                    stylusOnly && !stylus -> Gesture.NAV
                    down.type == PointerType.Eraser || tool == Tool.ERASER -> Gesture.ERASE
                    else -> Gesture.DRAW
                }
                liveKind = kind
                live.clear()
                var multi = false
                var multiAt = 0L; var multiTravel = 0f; var multiLast: Offset? = null; var multiMax = 0
                val start = down.position
                var last = down.position
                var moved = false
                val p0 = toPage(down.position)
                when (kind) {
                    Gesture.DRAW, Gesture.LASSO -> live.add(p0)
                    Gesture.ERASE -> vm.eraseAt(p0.x, p0.y, 0.012f)
                    Gesture.SELECT -> { selStart = words?.let { nearestWord(it, p0) } ?: -1; vm.selection = if (selStart >= 0) selStart..selStart else null }
                    Gesture.NAV -> Unit
                }
                down.consume()

                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        if (!multi) { multi = true; multiAt = System.currentTimeMillis(); live.clear(); if (kind == Gesture.SELECT) vm.selection = null }
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        val c = ev.calculateCentroid(useCurrent = true)
                        multiLast?.let { multiTravel += hypot(c.x - it.x, c.y - it.y) }
                        multiLast = c
                        multiMax = maxOf(multiMax, pressed.size)
                        val ns = (scale * zoom).coerceIn(1f, 5f)
                        val o = c - (c - offset) * (ns / scale) + pan
                        scale = ns
                        offset = clampOffset(o, ns)
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                    if (multi) { ev.changes.forEach { it.consume() }; continue }
                    val ch0 = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    val pos = ch0.position
                    if (hypot(pos.x - start.x, pos.y - start.y) > viewConfiguration.touchSlop) moved = true
                    val np = toPage(pos)
                    when (kind) {
                        Gesture.DRAW, Gesture.LASSO -> {
                            val lp = live.lastOrNull()
                            if (lp == null || hypot(np.x - lp.x, np.y - lp.y) > 0.0015f) live.add(np)
                        }
                        Gesture.ERASE -> vm.eraseAt(np.x, np.y, 0.012f)
                        Gesture.SELECT -> {
                            val ws = words
                            if (ws != null && selStart >= 0) {
                                val i = nearestWord(ws, np)
                                if (i >= 0) vm.selection = min(selStart, i)..maxOf(selStart, i)
                            } else if (ws != null) {
                                selStart = nearestWord(ws, np)
                            }
                        }
                        Gesture.NAV -> if (scale > 1f) offset = clampOffset(offset + (pos - last), scale)
                    }
                    last = pos
                    ch0.consume()
                }

                if (!multi) when (kind) {
                    Gesture.DRAW -> {
                        val pts = if (live.size == 1) listOf(live[0], live[0] + Offset(0.0005f, 0.0005f)) else live.toList()
                        vm.addStroke(FloatArray(pts.size * 2) { i -> if (i % 2 == 0) pts[i / 2].x else pts[i / 2].y }, tool == Tool.HIGHLIGHTER)
                    }
                    Gesture.ERASE -> vm.endErase()
                    Gesture.SELECT -> vm.selection?.let { vm.onSelected(it, moved) }
                    Gesture.LASSO -> if (live.size > 3) {
                        pendingLasso = RectF(live.minOf { it.x }, live.minOf { it.y }, live.maxOf { it.x }, live.maxOf { it.y })
                    }
                    Gesture.NAV -> {
                        val dx = last.x - start.x; val dy = last.y - start.y
                        if (scale <= 1.01f && abs(dx) > 90 && abs(dx) > abs(dy) * 1.5f) vm.goTo(if (dx < 0) vm.page + 1 else vm.page - 1)
                    }
                } else {
                    // 双指快速轻点（未拖动）= 撤销，GoodNotes 习惯
                    val quickTap = multiMax <= 2 && System.currentTimeMillis() - multiAt < 280 && multiTravel < 36f
                    when {
                        quickTap -> vm.undo()
                        kind == Gesture.ERASE -> vm.endErase()
                    }
                }
                live.clear()
            }
        }

        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        Box(
            Modifier.fillMaxSize().then(gestureMod).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
                transformOrigin = TransformOrigin(0f, 0f)
            }
        ) {
            val strokes = vm.strokes
            val img = remember(bmp) { bmp?.asImageBitmap() }
            val paths = remember(strokes, pageW, pageH, left0, top0) { strokes.map { it to strokePath(it.points, pageW, pageH, left0, top0) } }
            Canvas(Modifier.fillMaxSize()) {
                // 纸张
                drawRect(Color.White, Offset(left0, top0), Size(pageW, pageH))
                img?.let {
                    drawImage(it, dstOffset = IntOffset(left0.roundToInt(), top0.roundToInt()),
                        dstSize = IntSize(pageW.roundToInt(), pageH.roundToInt()), filterQuality = FilterQuality.Medium)
                }
                // 已翻译/已解答区域：淡色底线/框
                if (vm.assist) for (it in vm.items) {
                    val b = it.box ?: continue
                    val focused = it.id == vm.focusedItem
                    val tl = Offset(left0 + b.left * pageW, top0 + b.top * pageH)
                    val sz = Size(b.width() * pageW, b.height() * pageH)
                    if (it.kind == ItemKind.SOLVE) {
                        // 圈题框只在"解答中"显示；出结果后立即消失，不再永久残留在页面上
                        if (it.loading) drawRoundRect(tertiary.copy(alpha = 0.85f), tl, sz, CornerRadius(8f),
                            style = DrawStroke(width = 3f / scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                    } else {
                        drawRect(primary.copy(alpha = if (focused) 0.22f else 0.08f), tl, sz)
                    }
                }
                // 待确认的圈题区域（确认或取消后消失）
                pendingLasso?.let { r ->
                    drawRoundRect(tertiary.copy(alpha = 0.85f), Offset(left0 + r.left * pageW, top0 + r.top * pageH),
                        Size(r.width() * pageW, r.height() * pageH), CornerRadius(8f),
                        style = DrawStroke(width = 3f / scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                }
                // 笔迹
                for ((s, path) in paths) drawStrokePath(path, s.color, s.highlighter, s.width * pageW)
                // 实时笔迹
                if (live.size > 0) {
                    val lp = strokePath(FloatArray(live.size * 2) { i -> if (i % 2 == 0) live[i / 2].x else live[i / 2].y }, pageW, pageH, left0, top0)
                    when (liveKind) {
                        Gesture.DRAW -> {
                            val hl = vm.tool == Tool.HIGHLIGHTER
                            drawStrokePath(lp, if (hl) vm.hlColor else vm.penColor, hl, (if (hl) ReaderViewModel.HL_WIDTHS[vm.hlWidthIdx] else ReaderViewModel.PEN_WIDTHS[vm.penWidthIdx]) * pageW)
                        }
                        Gesture.LASSO -> drawPath(lp, tertiary, style = DrawStroke(width = 3f / scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                        else -> Unit
                    }
                }
                // 当前选区
                val sel = vm.selection
                val ws = vm.ocrWords
                if (vm.assist && sel != null && ws != null) {
                    for (i in sel) {
                        if (i !in ws.indices) continue
                        val b = ws[i].box
                        drawRect(primary.copy(alpha = 0.30f), Offset(left0 + b.left * pageW, top0 + b.top * pageH), Size(b.width() * pageW, b.height() * pageH))
                    }
                }
            }
        }

        if (bmp == null) CircularProgressIndicator(Modifier.align(Alignment.Center))

        // ---- 圈题确认：松手后先确认再发送，避免误触消耗额度 ----
        pendingLasso?.let { r ->
            val cx = left0 + r.centerX() * pageW
            val cy = top0 + r.bottom * pageH
            // A floating action must also work in split-screen and small landscape windows.
            val marginX = min(150f, cw / 2)
            val marginY = min(40f, ch / 2)
            val x = (cx * scale + offset.x).coerceIn(marginX, cw - marginX)
            val y = (cy * scale + offset.y).coerceIn(marginY, ch - marginY)
            Row(
                Modifier.align(Alignment.Center).graphicsLayer { translationX = x - cw / 2; translationY = y - ch / 2 }
                    .paperCard(RoundedCornerShape(50), Elev.Float, Paper.c.ai).clickable {
                        pendingLasso = null; vm.solveRegion(r)
                    }.padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(PIcons.Spark, null, Modifier.size(15.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
                Text("截图发给 AI 解题", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(9.dp))
                Text("取消", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { pendingLasso = null }.padding(3.dp))
            }
        }

        // ---- 点词/划句：译文就地弹出 ----
        val focused = vm.items.firstOrNull { it.id == vm.focusedItem }
        if (vm.assist && focused != null && focused.kind == com.paperly.mobile.data.ItemKind.TRANSLATE && focused.box != null) {
            val b = focused.box
            val cx = left0 + b.centerX() * pageW
            val cy = top0 + b.bottom * pageH
            val cardW = 290f.coerceAtMost((cw - 24f).coerceAtLeast(1f))
            val maxX = (cw - cardW - 12f).coerceAtLeast(0f)
            val x = (cx * scale + offset.x - cardW / 2).coerceIn(0f, maxX)
            val y = (cy * scale + offset.y + 14f).coerceIn(12f, (ch - 180f).coerceAtLeast(12f))
            Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { vm.focusedItem = null })
            Column(
                Modifier.align(Alignment.TopStart).graphicsLayer { translationX = x; translationY = y }
                    .width(with(LocalDensity.current) { cardW.toDp() })
                    .heightIn(max = with(LocalDensity.current) { (ch - 24f).coerceAtLeast(1f).toDp() })
                    .paperCard(RoundedCornerShape(16.dp), Elev.Pop, border = true)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                Text(focused.source, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Serif, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (focused.loading) Skeleton(listOf(0.9f, 0.6f))
                else Text(focused.result, style = MaterialTheme.typography.bodyMedium, color = if (focused.error) MaterialTheme.colorScheme.error else Paper.c.ink2, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniTag(if (focused.kind == com.paperly.mobile.data.ItemKind.TRANSLATE) "翻译" else "解题", Paper.c.accent, Paper.c.accentSoft)
                    if (focused.fromCache) { Spacer(Modifier.width(6.dp)); MiniTag("本地缓存", Paper.c.muted, Paper.c.line2) }
                    Spacer(Modifier.weight(1f))
                    Text("在侧栏查看全部", style = MaterialTheme.typography.labelSmall, color = Paper.c.muted)
                }
            }
        }
    }
}

private data class Geo(val w: Float, val h: Float, val left: Float, val top: Float)

/** 最近的词：先看是否落在（略放大的）词框里，否则取中心距离最近且不太远的。 */
private fun nearestWord(words: List<OcrWord>, p: Offset): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    words.forEachIndexed { i, w ->
        val b = w.box
        if (p.x >= b.left - 0.004f && p.x <= b.right + 0.004f && p.y >= b.top - 0.004f && p.y <= b.bottom + 0.004f) return i
        val d = hypot(p.x - b.centerX(), (p.y - b.centerY()) * 1.6f)
        if (d < bestD) { bestD = d; best = i }
    }
    return if (bestD < 0.04f) best else -1
}

/** 用二次贝塞尔经过中点平滑。 */
private fun strokePath(p: FloatArray, w: Float, h: Float, l: Float, t: Float): Path {
    val path = Path()
    if (p.size < 2) return path
    fun x(i: Int) = l + p[i * 2] * w
    fun y(i: Int) = t + p[i * 2 + 1] * h
    val n = p.size / 2
    path.moveTo(x(0), y(0))
    if (n == 1) { path.lineTo(x(0) + 0.1f, y(0)); return path }
    for (i in 1 until n - 1) {
        val mx = (x(i) + x(i + 1)) / 2; val my = (y(i) + y(i + 1)) / 2
        path.quadraticTo(x(i), y(i), mx, my)
    }
    path.lineTo(x(n - 1), y(n - 1))
    return path
}

private fun DrawScope.drawStrokePath(path: Path, color: Int, highlighter: Boolean, widthPx: Float) {
    val c = Color(color)
    drawPath(
        path,
        if (highlighter) c.copy(alpha = 0.38f) else c,
        style = DrawStroke(width = widthPx.coerceAtLeast(1.2f), cap = if (highlighter) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
    )
}
