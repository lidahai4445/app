package com.paperly.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.paperly.mobile.data.Tool
import com.paperly.mobile.data.AssistTool
import com.paperly.mobile.theme.Paper

/** 阅读器：顶栏（模式切换 + 隐藏计时）+ 缩略图栏 + 纸张 + 悬浮工具胶囊 + 页码胶囊。 */
@Composable
fun ReaderScreen(docId: Long, onBack: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val vm: ReaderViewModel = viewModel(key = "reader-$docId") { ReaderViewModel(ctx.applicationContext as android.app.Application, docId) }
    LaunchedEffect(Unit) { vm.reloadSettings() }

    if (vm.pdf == null) {
        AlertDialog(onDismissRequest = onBack, confirmButton = { TextButton(onBack) { Text("返回") } },
            title = { Text("无法打开") }, text = { Text("PDF 文件丢失或已损坏，请删除后重新导入。") })
        return
    }

    var askStop by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(true) }
    val c = Paper.c

    Column(Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ReaderTopBar(vm, onBack, onSettings, panelOpen, onPanelToggle = { panelOpen = !panelOpen }, onStopTimer = { askStop = true })

        // ---------- 主体 ----------
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val maxW = maxWidth; val maxH = maxHeight
            // A rail and a 300dp panel need room for a usable PDF page.
            val wide = maxW >= 900.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    AnimatedVisibility(!vm.assist, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        ThumbsRail(vm, Modifier.fillMaxHeight())
                    }
                    PageArea(vm, Modifier.weight(1f).fillMaxHeight(), toolPill = { FocusPill(vm) }, assistPill = { AssistPill(vm) })
                    AnimatedVisibility(vm.assist && panelOpen, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        SidePanel(vm, Modifier.width(if (maxW >= 1100.dp) 372.dp else 300.dp).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PageArea(vm, Modifier.weight(1f).fillMaxWidth(), toolPill = { FocusPill(vm) }, assistPill = { AssistPill(vm) })
                    AnimatedVisibility(vm.assist && panelOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SidePanel(vm, Modifier.fillMaxWidth().height(maxH * 0.42f))
                    }
                }
            }
        }
    }

    if (askStop) AlertDialog(
        onDismissRequest = { askStop = false },
        confirmButton = { TextButton({ askStop = false; vm.stopTimer() }) { Text("结束") } },
        dismissButton = { TextButton({ askStop = false }) { Text("继续做题") } },
        title = { Text("结束计时？") },
        text = { Text("结束后才会显示本次用时。") },
    )
    vm.timerResult?.let { r ->
        Box(Modifier.fillMaxSize().background(Color(0xFF1D2926).copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 380.dp)
                .paperCard(RoundedCornerShape(24.dp), Elev.Pop).padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("本次练习完成", style = MaterialTheme.typography.labelLarge, color = c.accent)
                Text(formatDuration(r.durationMs), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
                Text("结束前不显示用时，避免分心", style = MaterialTheme.typography.labelSmall, color = c.muted)
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("${r.lookups}" to "查词", "${r.solves}" to "AI 解题", "${vm.page + 1}/${vm.pageCount}" to "读到").forEach { (v, l) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, style = MaterialTheme.typography.titleMedium)
                            Text(l, style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton("切换辅助模式对答案", primary = true, modifier = Modifier.fillMaxWidth()) { vm.timerResult = null; vm.setAssistMode(true) }
                    PaperButton("返回继续看", primary = false, modifier = Modifier.fillMaxWidth()) { vm.timerResult = null }
                }
            }
        }
    }
}

/** Keep the document title readable on phones by giving mode and timer their own row. */
@Composable
private fun ReaderTopBar(vm: ReaderViewModel, onBack: () -> Unit, onSettings: () -> Unit,
                         panelOpen: Boolean, onPanelToggle: () -> Unit, onStopTimer: () -> Unit) {
    val ctx = LocalContext.current
    val c = Paper.c
    val title: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        IconBtn(PIcons.Back, "返回", onClick = onBack)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.doc?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("第 ${vm.page + 1} / ${vm.pageCount} 页", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
    }
    val startTimer = {
        vm.startTimer()
        Toast.makeText(ctx, "计时已开始并隐藏，专心做题", Toast.LENGTH_SHORT).show()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 700.dp) {
            Column {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    title()
                    IconBtn(PIcons.Settings, "设置", onClick = onSettings)
                }
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeSwitch(vm.assist, onChange = vm::setAssistMode, compact = true)
                    Spacer(Modifier.weight(1f))
                    IconBtn(PIcons.Timer, if (vm.timerRunning) "结束计时（用时已隐藏）" else "开始计时",
                        tint = if (vm.timerRunning) c.accent else null,
                        onClick = if (vm.timerRunning) onStopTimer else startTimer)
                    if (vm.assist) IconBtn(PIcons.Panel, "辅助面板", active = panelOpen, onClick = onPanelToggle)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                title()
                ModeSwitch(vm.assist, onChange = vm::setAssistMode)
                Spacer(Modifier.weight(0.3f))
                if (vm.timerRunning) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50))
                        .clickable(onClick = onStopTimer).padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFD26B4E)).alpha(0.9f))
                        Spacer(Modifier.width(7.dp))
                        Text("计时中 · 已隐藏", style = MaterialTheme.typography.labelMedium, color = c.ink2)
                    }
                } else IconBtn(PIcons.Timer, "开始计时", onClick = startTimer)
                if (vm.assist) IconBtn(PIcons.Panel, "辅助面板", active = panelOpen, onClick = onPanelToggle)
                IconBtn(PIcons.Settings, "设置", onClick = onSettings)
            }
        }
    }
}

// ---------------- 页面区 + 悬浮胶囊 ----------------
@Composable
private fun PageArea(vm: ReaderViewModel, modifier: Modifier, toolPill: @Composable () -> Unit, assistPill: @Composable () -> Unit) {
    Box(modifier) {
        PageView(vm, Modifier.fillMaxSize())
        // 模式工具胶囊（悬浮在纸张上方，随模式切换淡入淡出）
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)
            .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(!vm.assist, enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f), exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.96f)) { toolPill() }
            AnimatedVisibility(vm.assist, enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f), exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.96f)) { assistPill() }
        }
        // 页码胶囊
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).paperCard(RoundedCornerShape(50), Elev.Float, Paper.c.surface.copy(alpha = 0.94f), border = true).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(PIcons.ChevronLeft, "上一页", enabled = vm.page > 0, size = 32.dp) { vm.goTo(vm.page - 1) }
            Text("${vm.page + 1} / ${vm.pageCount}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
            IconBtn(PIcons.ChevronRight, "下一页", enabled = vm.page < vm.pageCount - 1, size = 32.dp) { vm.goTo(vm.page + 1) }
        }
    }
}

@Composable
private fun FocusPill(vm: ReaderViewModel) {
    Pill {
        ToolKey(PIcons.Hand, "浏览", vm.tool == Tool.HAND) { vm.tool = Tool.HAND }
        ToolKey(PIcons.Pen, "笔", vm.tool == Tool.PEN) { vm.tool = Tool.PEN }
        ToolKey(PIcons.Highlighter, "荧光笔", vm.tool == Tool.HIGHLIGHTER) { vm.tool = Tool.HIGHLIGHTER }
        ToolKey(PIcons.Eraser, "橡皮", vm.tool == Tool.ERASER) { vm.tool = Tool.ERASER }
        if (vm.tool == Tool.PEN || vm.tool == Tool.HIGHLIGHTER) {
            PillDivider()
            val colors = if (vm.tool == Tool.HIGHLIGHTER) ReaderViewModel.HL_COLORS else ReaderViewModel.PEN_COLORS
            val current = if (vm.tool == Tool.HIGHLIGHTER) vm.hlColor else vm.penColor
            colors.forEach { col ->
                val selected = col == current
                Box(
                    Modifier.padding(horizontal = 5.dp).size(22.dp).clip(CircleShape).background(Color(col))
                        .border(if (selected) 2.dp else 0.5.dp, if (selected) Paper.c.accent else Paper.c.line, CircleShape)
                        .clickable { if (vm.tool == Tool.HIGHLIGHTER) vm.hlColor = col else vm.penColor = col },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(PIcons.Check, null, Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.9f)) }
            }
            PillDivider()
            val wIdx = if (vm.tool == Tool.HIGHLIGHTER) vm.hlWidthIdx else vm.penWidthIdx
            listOf(4, 7, 10).forEachIndexed { i, dot ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(28.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (wIdx == i) Paper.c.line2 else Color.Transparent)
                        .clickable { if (vm.tool == Tool.HIGHLIGHTER) vm.hlWidthIdx = i else vm.penWidthIdx = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(dot.dp).clip(CircleShape).background(Paper.c.ink)) }
            }
            PillDivider()
        }
        IconBtn(PIcons.Undo, "撤销", enabled = vm.canUndo) { vm.undo() }
        IconBtn(PIcons.Redo, "重做", enabled = vm.canRedo) { vm.redo() }
    }
}

@Composable
private fun AssistPill(vm: ReaderViewModel) {
    val c = Paper.c
    Pill {
        ToolKey(PIcons.Word, "点词", vm.assistTool == AssistTool.SELECT && vm.granularity == com.paperly.mobile.data.Granularity.WORD, ai = true) {
            vm.assistTool = AssistTool.SELECT; vm.granularity = com.paperly.mobile.data.Granularity.WORD
        }
        ToolKey(PIcons.Sentence, "划句", vm.assistTool == AssistTool.SELECT && vm.granularity == com.paperly.mobile.data.Granularity.SENTENCE, ai = true) {
            vm.assistTool = AssistTool.SELECT; vm.granularity = com.paperly.mobile.data.Granularity.SENTENCE
        }
        ToolKey(PIcons.Lasso, "圈题", vm.assistTool == AssistTool.LASSO, ai = true) { vm.assistTool = AssistTool.LASSO; vm.selection = null }
        if (vm.selection != null && !vm.settings.autoTranslate) {
            ToolKey(PIcons.Translate, "翻译", false, ai = true) { vm.translateSelection() }
            PillDivider()
        }
        val words = vm.ocrWords
        when {
            vm.ocrBusy -> Text("正在识别本页…", style = MaterialTheme.typography.labelMedium, color = c.muted)
            vm.ocrError != null -> Row(Modifier.clip(RoundedCornerShape(9.dp)).clickable { vm.runOcr(force = true) }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PIcons.Alert, null, Modifier.size(15.dp), tint = c.danger)
                Spacer(Modifier.width(6.dp)); Text("识别失败 · 点按重试", style = MaterialTheme.typography.labelMedium, color = c.danger)
            }
            words != null -> Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PIcons.Check, null, Modifier.size(15.dp), tint = c.accent)
                Spacer(Modifier.width(6.dp)); Text("本页已识别 · ${words.size} 词", style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
        }
    }
}

// ---------------- 缩略图栏 ----------------
@Composable
private fun ThumbsRail(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val c = Paper.c
    Column(modifier.width(88.dp).fillMaxHeight().background(c.surface2).padding(top = 12.dp, bottom = 10.dp)) {
        val state = rememberLazyListState()
        LaunchedEffect(vm.page) { if (vm.page in 0 until vm.pageCount) state.animateScrollToItem(vm.page) }
        LazyColumn(state = state, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed((0 until vm.pageCount).toList()) { i, _ ->
                val bmp by produceState<android.graphics.Bitmap?>(null, i, vm.pageCount) {
                    value = runCatching { vm.pdf?.render(i, 132) }.getOrNull()
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(56.dp).height(74.dp).paperCard(RoundedCornerShape(5.dp), Elev.Flat, if (i == vm.page) c.sheet else c.surface,
                            border = i != vm.page)
                        .then(if (i == vm.page) Modifier.border(2.dp, c.accent, RoundedCornerShape(5.dp)) else Modifier)
                        .clickable { vm.goTo(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
                        if (i in vm.strokePages) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp).clip(CircleShape).background(c.accent))
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (i == vm.page) c.accent else c.muted, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
