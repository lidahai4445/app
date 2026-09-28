package com.paperly.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.Doc
import com.paperly.mobile.data.StudySession
import com.paperly.mobile.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class ReviewData(
    val weekMs: Long, val sessions: Int, val lookups: Int,
    val documents: List<Doc>, val recent: List<StudySession>,
)

/** 平板优先的学习回顾；所有数字取自本机数据库，而非效果图中的示例数据。 */
@Composable
fun StudyReviewScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val data by produceState<ReviewData?>(null) {
        value = withContext(Dispatchers.IO) {
            val db = Db.get(ctx)
            val week = db.studySince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
            ReviewData(week.first, week.second, db.lookupCounts().values.sum(), db.docs(), db.recentSessions())
        }
    }
    val c = Paper.c
    Column(Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(PIcons.Back, "返回资料库", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("学习回顾", style = MaterialTheme.typography.titleLarge)
        }
        val review = data
        if (review == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 34.dp else 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ReviewHero(review, Modifier.weight(1.5f))
                    ReviewSummary(review, Modifier.weight(1f))
                } else {
                    ReviewHero(review, Modifier.fillMaxWidth())
                    ReviewSummary(review, Modifier.fillMaxWidth())
                }
                Text("最近的练习", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                Column(Modifier.fillMaxWidth().paperCard(RoundedCornerShape(18.dp), border = true).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    if (review.recent.isEmpty()) Text("还没有计时练习。开始一次专注，回顾便会记录在这里。",
                        color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    review.recent.forEach { session ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(34.dp).background(c.accent, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(session.docTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAt)),
                                    style = MaterialTheme.typography.labelSmall, color = c.muted)
                            }
                            Text(formatDuration(session.durationMs), style = MaterialTheme.typography.labelLarge, color = c.accent)
                        }
                    }
                }
                val last = review.documents.maxByOrNull { it.openedAt }
                if (last != null) PaperButton("继续阅读 · ${last.title}", PIcons.ChevronRight, trailing = true) { onOpen(last.id) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ReviewHero(d: ReviewData, modifier: Modifier) {
    val c = Paper.c
    Column(modifier.background(c.accent, RoundedCornerShape(22.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("STUDY REVIEW  /  学习回顾", style = MaterialTheme.typography.labelSmall, color = c.onAccent.copy(alpha = .78f))
        Text("每一页认真，都算数。", style = MaterialTheme.typography.headlineMedium, color = c.onAccent, fontWeight = FontWeight.SemiBold)
        Text("把注意力留给试卷；学习时间、查词与练习轨迹会帮你记下来。", style = MaterialTheme.typography.bodySmall,
            color = c.onAccent.copy(alpha = .85f))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ReviewFigure(formatDuration(d.weekMs), "本周专注", c.onAccent)
            ReviewFigure("${d.sessions}", "练习次数", c.onAccent)
            ReviewFigure("${d.lookups}", "累计查词", c.onAccent)
        }
    }
}

@Composable
private fun ReviewFigure(value: String, label: String, color: Color) {
    Column { Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = .75f)) }
}

@Composable
private fun ReviewSummary(d: ReviewData, modifier: Modifier) {
    val c = Paper.c
    Column(modifier.paperCard(RoundedCornerShape(22.dp), Elev.Float, border = true).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Text("这周的小结", style = MaterialTheme.typography.titleMedium)
        listOf("资料库" to "${d.documents.size} 份试卷", "本周练习" to "${d.sessions} 次", "累计查词" to "${d.lookups} 条").forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.ink2)
                Text(value, style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
        Text("所有记录只存储在本机。", style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}
