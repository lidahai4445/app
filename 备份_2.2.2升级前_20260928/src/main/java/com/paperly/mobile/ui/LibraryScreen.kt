package com.paperly.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.Doc
import com.paperly.mobile.data.SUBJECTS
import com.paperly.mobile.data.VocabEntry
import com.paperly.mobile.pdf.PdfDoc
import com.paperly.mobile.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 资料库：宽屏（≥840dp，平板/手机横屏）为 左导航 + 内容；窄屏为顶部筛选 + 内容。
 * 内容 = 问候与统计、“继续上次”卡片、封面网格（或生词本列表）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit, incoming: Uri? = null, onIncomingConsumed: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var docs by remember { mutableStateOf(db.docs()) }
    var lookups by remember { mutableStateOf(db.lookupCounts()) }
    var week by remember { mutableStateOf(db.studySince(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)) }
    var vocab by remember { mutableStateOf(emptyList<VocabEntry>()) }
    var tab by rememberSaveable { mutableStateOf("all") }   // all / recent / fav / vocab / subject:xxx
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Doc?>(null) }
    var menuFor by remember { mutableStateOf<Doc?>(null) }
    var renameFor by remember { mutableStateOf<Doc?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        docs = db.docs(); lookups = db.lookupCounts()
        week = db.studySince(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
        if (tab == "vocab") vocab = db.vocab()
    }

    fun doImport(uri: Uri) {
        busy = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { importPdf(ctx, db, uri) } }
            busy = false
            r.onSuccess { refresh(); onOpen(it) }
                .onFailure { Toast.makeText(ctx, "导入失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doImport(uri) }
    LaunchedEffect(incoming) { if (incoming != null) { onIncomingConsumed(); doImport(incoming) } }
    LaunchedEffect(tab) { if (tab == "vocab") vocab = db.vocab() }

    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when { h < 5 -> "夜深了"; h < 11 -> "早上好"; h < 13 -> "中午好"; h < 18 -> "下午好"; else -> "晚上好" }
    val lastOpened = docs.filter { it.openedAt > 0 }.maxByOrNull { it.openedAt }
    val onSubject = tab.startsWith("subject:")
    val filtered = docs.filter {
        val ok = when { tab == "all" || tab == "recent" -> true; tab == "fav" -> it.fav; onSubject -> it.subject == tab.removePrefix("subject:"); else -> true }
        ok && (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.subject.contains(query, true))
    }.let { if (tab == "recent") it.filter { d -> d.openedAt > 0 }.sortedByDescending { d -> d.openedAt } else it }

    val sortRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when { tab == "fav" -> "收藏"; tab == "vocab" -> "生词本"; onSubject -> tab.removePrefix("subject:"); else -> "全部试卷" },
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text("${if (tab == "vocab") vocab.size else filtered.size}", style = MaterialTheme.typography.labelMedium, color = Paper.c.muted)
            Spacer(Modifier.weight(1f))
            Text(if (tab == "recent") "按最近打开" else "按导入时间", style = MaterialTheme.typography.labelSmall, color = Paper.c.muted)
        }
    }

    val content: @Composable () -> Unit = {
        when {
            tab == "vocab" -> Column(Modifier.fillMaxSize()) {
                sortRow()
                if (vocab.isEmpty()) EmptyHint("还没有生词", "在辅助模式下点词或划句，译文会自动收进生词本。")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                    items(vocab, key = { it.id }) { v ->
                        Column(Modifier.fillMaxWidth().paperCard(RoundedCornerShape(14.dp), border = true).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(v.source, style = MaterialTheme.typography.titleSmall)
                            Text(v.result, style = MaterialTheme.typography.bodySmall, color = Paper.c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            Text(listOf(v.docTitle, "第 ${v.page + 1} 页", relTime(v.createdAt)).filter { it.isNotBlank() }.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall, color = Paper.c.muted, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
            docs.isEmpty() -> EmptyState(onImport = { if (!busy) picker.launch(arrayOf("application/pdf")) }, busy = busy)
            else -> Column(Modifier.fillMaxSize()) {
                if (tab == "all" && query.isBlank()) lastOpened?.let { d ->
                    ContinueCard(d, lookups[d.id] ?: 0, Modifier.padding(bottom = 22.dp)) { onOpen(d.id) }
                }
                sortRow()
                if (filtered.isEmpty()) EmptyHint("没有符合条件的试卷", if (query.isBlank()) "换一个分类看看" else "换个关键词试试")
                LazyVerticalGrid(GridCells.Adaptive(158.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
                    items(filtered, key = { it.id }) { d ->
                        DocCard(d, onClick = { onOpen(d.id) }, onLongClick = { menuFor = d })
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper.c.paper)) {
        val wide = maxWidth >= 840.dp
        if (wide) Row(Modifier.fillMaxSize()) {
            NavRail(tab, docs.size, onTab = { tab = it }, onSettings = onSettings, onImport = { if (!busy) picker.launch(arrayOf("application/pdf")) }, busy = busy)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hello + "，继续加油", style = MaterialTheme.typography.headlineMedium)
                        val hrs = week.first / 3600000; val mins = week.first / 60000 % 60
                        Text("本周专注 ${if (hrs > 0) "$hrs 小时 " else ""}$mins 分钟 · ${week.second} 次计时",
                            style = MaterialTheme.typography.bodySmall, color = Paper.c.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    SearchBox(query, { query = it }, Modifier.width(250.dp))
                    Spacer(Modifier.width(10.dp))
                    PaperButton(if (busy) "正在导入…" else "导入 PDF", PIcons.Plus) { if (!busy) picker.launch(arrayOf("application/pdf")) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 34.dp, vertical = 18.dp)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("纸间", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconBtn(PIcons.Settings, "设置", onClick = onSettings)
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(query, { query = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PaperButton(if (busy) "导入中…" else "导入", PIcons.Plus) { if (!busy) picker.launch(arrayOf("application/pdf")) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "全部", "recent" to "最近", "fav" to "收藏", "vocab" to "生词本").forEach { (k, label) ->
                        FilterPill(label, tab == k) { tab = k }
                    }
                    SUBJECTS.forEach { s -> FilterPill(s, tab == "subject:$s", dot = subjectColor(s)) { tab = "subject:$s" } }
                }
                Column(Modifier.weight(1f).padding(horizontal = 20.dp)) { content() }
            }
        }
    }

    // ---- 长按菜单 ----
    menuFor?.let { d ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = Paper.c.surface) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 30.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("科目", style = MaterialTheme.typography.labelSmall, color = Paper.c.muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("未分类", d.subject.isBlank()) { db.setSubject(d.id, ""); refresh(); menuFor = null }
                    SUBJECTS.forEach { s -> FilterPill(s, d.subject == s, dot = subjectColor(s)) { db.setSubject(d.id, s); refresh(); menuFor = null } }
                }
                Spacer(Modifier.height(8.dp))
                SheetAction(PIcons.Star, if (d.fav) "取消收藏" else "收藏") { db.setFav(d.id, !d.fav); refresh(); menuFor = null }
                SheetAction(PIcons.Pen, "重命名") { renameFor = d; menuFor = null }
                SheetAction(PIcons.Trash, "删除这份试卷", tint = Paper.c.danger) { toDelete = d; menuFor = null }
            }
        }
    }
    renameFor?.let { d ->
        var t by remember { mutableStateOf(d.title) }
        AlertDialog(onDismissRequest = { renameFor = null }, title = { Text("重命名") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ if (t.isNotBlank()) db.rename(d.id, t.trim()); refresh(); renameFor = null }) { Text("保存") } },
            dismissButton = { TextButton({ renameFor = null }) { Text("取消") } })
    }
    toDelete?.let { d ->
        AlertDialog(onDismissRequest = { toDelete = null },
            title = { Text("删除《${d.title}》？") },
            text = { Text("将删除 App 内的副本、笔迹和本书的翻译记录（全局翻译缓存保留）。手机里的原 PDF 不受影响。") },
            confirmButton = { TextButton({ db.deleteDoc(d.id); File(d.path).delete(); thumbFile(ctx, d.id).delete(); refresh(); toDelete = null }) { Text("删除", color = Paper.c.danger) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("取消") } })
    }
}

// ---------------- 左侧导航 ----------------
@Composable
private fun NavRail(tab: String, count: Int, onTab: (String) -> Unit, onSettings: () -> Unit, onImport: () -> Unit, busy: Boolean) {
    val c = Paper.c
    Column(Modifier.width(232.dp).fillMaxHeight().background(c.surface2).padding(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 20.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B7C60), Color(0xFF245641)))), contentAlignment = Alignment.Center) {
                Icon(PIcons.Book, null, Modifier.size(18.dp), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("纸间", style = MaterialTheme.typography.titleMedium)
                Text("让思考留在纸上", style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
        NavItem(PIcons.Grid, "全部试卷", tab == "all", badge = count.toString()) { onTab("all") }
        NavItem(PIcons.Clock, "最近打开", tab == "recent") { onTab("recent") }
        NavItem(PIcons.Star, "收藏", tab == "fav") { onTab("fav") }
        NavItem(PIcons.Bookmark, "生词本", tab == "vocab") { onTab("vocab") }
        Text("科目", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
        SUBJECTS.forEach { s -> NavItem(null, s, tab == "subject:$s", dot = subjectColor(s)) { onTab("subject:$s") } }
        Spacer(Modifier.weight(1f))
        PaperButton(if (busy) "正在导入…" else "导入 PDF", PIcons.Plus, modifier = Modifier.fillMaxWidth(), onClick = onImport, enabled = !busy)
        Spacer(Modifier.height(6.dp))
        NavItem(PIcons.Settings, "设置", false) { onSettings() }
    }
}

@Composable
private fun NavItem(icon: ImageVector?, label: String, selected: Boolean, badge: String? = null, dot: Color? = null, onClick: () -> Unit) {
    val c = Paper.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> Icon(icon, null, Modifier.size(17.dp), tint = if (selected) c.accent else c.ink2)
            dot != null -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        }
        Spacer(Modifier.width(11.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) c.accent else c.ink2,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
        Spacer(Modifier.weight(1f))
        if (badge != null && badge != "0") Text(badge, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}

@Composable
private fun SearchBox(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Paper.c
    OutlinedTextField(
        text, onChange, singleLine = true, placeholder = { Text("搜索试卷、科目", style = MaterialTheme.typography.bodySmall, color = c.muted) },
        leadingIcon = { Icon(PIcons.Search, null, Modifier.size(16.dp), tint = c.muted) },
        shape = RoundedCornerShape(12.dp), modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.line,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        ),
    )
}

// ---------------- “继续上次”卡片 ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(d: Doc, lookups: Int, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = Paper.c
    Row(modifier.fillMaxWidth().paperCard(RoundedCornerShape(20.dp), Elev.Float).combinedClickable(onClick = onOpen).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(84.dp).aspectRatio(0.72f).paperCard(RoundedCornerShape(10.dp), Elev.Flat).padding(6.dp)) { Thumb(d) }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("继续上次", style = MaterialTheme.typography.labelSmall, color = c.accent)
            Text(d.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, bottom = 3.dp))
            Text("第 ${d.lastPage + 1} / ${d.pageCount} 页  ·  ${relTime(d.openedAt)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Box(Modifier.padding(top = 12.dp).width(320.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line2)) {
                Box(Modifier.fillMaxWidth(progressOf(d)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(d.studyMs), style = MaterialTheme.typography.titleMedium)
            Text("已用时 · $lookups 次查词", style = MaterialTheme.typography.labelSmall, color = c.muted)
            Spacer(Modifier.height(10.dp))
            PaperButton("继续做题", PIcons.ChevronRight, trailing = true, onClick = onOpen)
        }
    }
}

private fun progressOf(d: Doc): Float =
    if (d.pageCount <= 0) 0f else ((d.lastPage + 1).toFloat() / d.pageCount).coerceIn(0f, 1f)

// ---------------- 封面卡片 ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocCard(d: Doc, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Paper.c
    val sc = subjectColor(d.subject)
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.76f).paperCard(RoundedCornerShape(12.dp), Elev.Flat)) {
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth(0.68f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.line))
                Spacer(Modifier.height(10.dp))
                repeat(6) { i ->
                    Box(Modifier.fillMaxWidth(if (i == 3) 0.55f else if (i == 5) 0.8f else 0.95f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line2))
                    Spacer(Modifier.height(7.dp))
                }
            }
            Thumb(d, Modifier.fillMaxSize().padding(start = 5.dp))
            Box(Modifier.fillMaxHeight().width(5.dp).background(sc))
            if (d.studyMs > 0 && progressOf(d) >= 1f) MiniTag("已完成", c.accent, c.accentSoft, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (d.fav) Icon(PIcons.Star, "收藏", Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp), tint = Color(0xFFD9A23F))
        }
        Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
            if (d.subject.isNotBlank()) { Text(d.subject, style = MaterialTheme.typography.labelSmall, color = sc); Spacer(Modifier.width(7.dp)) }
            Text(if (d.lastPage > 0) "第 ${d.lastPage + 1}/${d.pageCount} 页" else "${d.pageCount} 页", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line2)) {
            Box(Modifier.fillMaxWidth(progressOf(d)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(sc))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val c = Paper.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (tint == Color.Unspecified) c.ink2 else tint)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (tint == Color.Unspecified) c.ink else tint)
    }
}

@Composable
private fun EmptyHint(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Paper.c.muted, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, busy: Boolean) {
    val c = Paper.c
    Column(Modifier.fillMaxSize().padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(PIcons.Book, null, Modifier.size(34.dp), tint = c.accent)
        }
        Text("导入一份真题，开始做题", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        Text("PDF 会复制到本机，笔迹、翻译、计时都留在你的设备上。", style = MaterialTheme.typography.bodySmall, color = c.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        PaperButton(if (busy) "正在导入…" else "导入 PDF", PIcons.Plus, onClick = onImport, enabled = !busy)
    }
}

fun relTime(ms: Long): String {
    if (ms <= 0) return ""
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "刚刚"
        s < 3600 -> "${s / 60} 分钟前"
        s < 86400 -> "${s / 3600} 小时前"
        s < 86400 * 30 -> "${s / 86400} 天前"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINESE).format(java.util.Date(ms))
    }
}

@Composable
private fun Thumb(d: Doc, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, d.id) {
        value = withContext(Dispatchers.IO) {
            val f = thumbFile(ctx, d.id)
            if (f.exists()) BitmapFactory.decodeFile(f.path) else runCatching {
                PdfDoc(File(d.path)).use { pdf -> pdf.render(0, 360) }.also { b ->
                    f.parentFile?.mkdirs(); f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) }
    }
}

private fun thumbFile(ctx: Context, id: Long) = File(ctx.cacheDir, "thumbs/$id.jpg")

/** 复制到私有目录（PdfRenderer 需要可随机读取的文件），返回新文档 id。 */
private fun importPdf(ctx: Context, db: Db, uri: Uri): Long {
    var name = "未命名"
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0) ?: name
    }
    val dir = File(ctx.filesDir, "pdfs").apply { mkdirs() }
    val out = File(dir, UUID.randomUUID().toString() + ".pdf")
    ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it, 1 shl 16) } }
        ?: error("无法读取文件")
    val pages = try { PdfDoc.pageCountOf(out) } catch (e: Exception) { out.delete(); error("不是有效的 PDF 或已加密") }
    return db.insertDoc(name.removeSuffix(".pdf").removeSuffix(".PDF"), out.path, pages)
}
