package com.paperly.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 线性图标（Lucide 风格，ISC 许可，与网页版一致）。24 网格、1.8 描边、圆头圆角。
 * 颜色由 Icon(tint=…) 决定，这里只给路径。
 */
object PIcons {
    private fun icon(name: String, vararg paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(
            PathParser().parsePathString(d).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    // —— 导航 ——
    val Back = icon("back", "M15 18l-6-6 6-6")
    val ChevronLeft = icon("cl", "M15 18l-6-6 6-6")
    val ChevronRight = icon("cr", "M9 18l6-6-6-6")
    val ChevronDown = icon("cd", "M6 9l6 6 6-6")
    val Close = icon("x", "M18 6L6 18", "M6 6l12 12")
    val More = icon("more", "M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M19 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M5 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0")
    val Settings = icon("gear",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val Search = icon("search", "M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-4.3-4.3")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Panel = icon("panel", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M15 3v18")
    val Pages = icon("pages", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 3v18")

    // —— 资料库 ——
    val Grid = icon("grid", "M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 3 8.5v-4A1.5 1.5 0 0 1 4.5 3z", "M15.5 3h4A1.5 1.5 0 0 1 21 4.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 14 8.5v-4A1.5 1.5 0 0 1 15.5 3z", "M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 21h-4A1.5 1.5 0 0 1 3 19.5v-4A1.5 1.5 0 0 1 4.5 14z", "M15.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z")
    val Clock = icon("clock", "M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7v5l3 2")
    val Star = icon("star", "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z")
    val Bookmark = icon("bm", "M19 21l-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    val Book = icon("book", "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z", "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z")
    val Tag = icon("tag", "M12.59 2.59A2 2 0 0 0 11.17 2H4a2 2 0 0 0-2 2v7.17a2 2 0 0 0 .59 1.42l8.7 8.7a2.4 2.4 0 0 0 3.42 0l6.58-6.58a2.4 2.4 0 0 0 0-3.42z", "M7.5 7.5m-.5 0a.5 .5 0 1 0 1 0a.5 .5 0 1 0 -1 0")
    val Trash = icon("trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")

    // —— 做题工具 ——
    val Hand = icon("hand", "M18 11V6a2 2 0 0 0-4 0v0", "M14 10V4a2 2 0 0 0-4 0v2", "M10 10.5V6a2 2 0 0 0-4 0v8", "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")
    val Pen = icon("pen", "M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z")
    val Highlighter = icon("hl", "M9 11l-6 6v3h9l3-3", "M22 12l-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4")
    val Eraser = icon("eraser", "M7 21l-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21", "M22 21H7", "M5 11l9 9")
    val Lasso = icon("lasso", "M7 22a5 5 0 0 1-2-4", "M3.3 14A6.8 6.8 0 0 1 2 10c0-4.4 4.5-8 10-8s10 3.6 10 8-4.5 8-10 8a12 12 0 0 1-5-1", "M5 16m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Redo = icon("redo", "M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 0 0 0 11H13")

    // —— 辅助 ——
    val Spark = icon("spark", "M12 3v3", "M12 18v3", "M3 12h3", "M18 12h3", "M5.6 5.6l2.1 2.1", "M16.3 16.3l2.1 2.1", "M5.6 18.4l2.1-2.1", "M16.3 7.7l2.1-2.1")
    val Word = icon("word", "M4 20L9 4h2l5 16", "M6 14h8", "M19 9v11")
    val Phrase = icon("phrase", "M4 12h16", "M4 8v8", "M20 8v8")
    val Sentence = icon("sentence", "M4 18h16", "M4 13h9", "M4 8h13")
    val Translate = icon("tr", "M5 8l6 6", "M4 14l6-6 2-3", "M2 5h12", "M7 2h1", "M22 22l-5-10-5 10", "M14 18h6")
    val Copy = icon("copy", "M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")
    val Refresh = icon("refresh", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")
    val Check = icon("check", "M20 6L9 17l-5-5")
    val Database = icon("db", "M12 5m-8 0a8 3 0 1 0 16 0a8 3 0 1 0 -16 0", "M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5", "M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3")
    val Scan = icon("scan", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 12h10")
    val Alert = icon("alert", "M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 8v4", "M12 16h.01")

    // —— 计时 ——
    val Timer = icon("timer", "M10 2h4", "M12 14l3-3", "M12 14m-8 0a8 8 0 1 0 16 0a8 8 0 1 0 -16 0")
    val Stop = icon("stop", "M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z")
}
