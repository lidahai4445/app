package com.paperly.mobile.data

import android.graphics.RectF

/** 一份导入的 PDF。PDF 被复制到 App 私有目录，原文件不受影响。 */
data class Doc(
    val id: Long,
    val title: String,
    val path: String,
    val pageCount: Int,
    val lastPage: Int,
    val addedAt: Long,
    val studyMs: Long,
    val subject: String = "",   // 科目，如“英语一”；空 = 未分类
    val fav: Boolean = false,   // 收藏
    val openedAt: Long = 0,     // 最近打开时间，0 = 从未打开
)

/** 资料库里可选的科目（固定列表，简单可靠）。 */
val SUBJECTS = listOf("英语一", "英语二", "数学一", "数学二", "数学三", "政治", "专业课")

/** 生词本里的一条：来自全部文档的翻译记录。 */
data class VocabEntry(val id: Long, val docId: Long, val docTitle: String, val page: Int, val source: String, val result: String, val createdAt: Long)

/** 做题模式工具。 */
enum class Tool { HAND, PEN, HIGHLIGHTER, ERASER }

/** 辅助模式工具。 */
enum class AssistTool { SELECT, LASSO }

/** 辅助模式选词粒度。 */
enum class Granularity(val label: String) { WORD("单词"), PHRASE("短语"), SENTENCE("句子") }

/**
 * 一条笔迹。坐标全部按页面宽高归一化到 0..1，缩放/换设备都不失真。
 * points = [x0, y0, x1, y1, ...]；width 也按页面宽度归一化。
 */
data class Stroke(
    val id: Long,
    val page: Int,
    val highlighter: Boolean,
    val color: Int,
    val width: Float,
    val points: FloatArray,
)

/** OCR 识别出的一个词及其归一化框。line/block 用于整句扩展与换行拼接。 */
data class OcrWord(
    val text: String,
    val box: RectF,
    val line: Int,
    val block: Int,
)

enum class ItemKind { TRANSLATE, SOLVE }

/** 侧边栏里的一条结果（翻译或解题），按文档页保存。 */
data class AssistItem(
    val id: Long,
    val docId: Long,
    val page: Int,
    val kind: ItemKind,
    val source: String,
    val result: String,
    val provider: String,
    val box: RectF?,
    val fromCache: Boolean,
    val createdAt: Long,
    val loading: Boolean = false,
    val error: Boolean = false,
)
