package com.paperly.mobile.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperly.mobile.data.Tool

@Composable
fun ReaderScreen(docId: Long, onBack: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val vm: ReaderViewModel = viewModel(key = "reader-$docId") { ReaderViewModel(ctx.applicationContext as Application, docId) }
    LaunchedEffect(Unit) { vm.reloadSettings() }

    if (vm.pdf == null) {
        AlertDialog(onDismissRequest = onBack, confirmButton = { TextButton(onBack) { Text("返回") } },
            title = { Text("无法打开") }, text = { Text("PDF 文件丢失或已损坏，请删除后重新导入。") })
        return
    }

    var askStop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // ---------- 顶栏 ----------
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text(vm.doc?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow(Modifier.width(168.dp)) {
                SegmentedButton(!vm.assist, { vm.setAssistMode(false) }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("做题") }
                SegmentedButton(vm.assist, { vm.setAssistMode(true) }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("辅助") }
            }
            // 计时：运行中不显示任何时间，只保留一个不起眼的停止键
            IconButton({
                if (vm.timerRunning) askStop = true else {
                    vm.startTimer(); Toast.makeText(ctx, "计时已开始并隐藏，专心做题", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(if (vm.timerRunning) PIcons.Stop else PIcons.Timer, if (vm.timerRunning) "结束计时" else "开始计时",
                    tint = if (vm.timerRunning) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            IconButton(onSettings) { Icon(Icons.Default.Settings, "设置") }
        }

        // ---------- 主体：宽屏右侧栏 / 窄屏底部栏 ----------
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val panelW = if (maxWidth > 900.dp) 380.dp else 320.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    PageArea(vm, Modifier.weight(1f).fillMaxHeight())
                    AnimatedVisibility(vm.assist, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        SidePanel(vm, Modifier.width(panelW).fillMaxHeight())
                    }
                }
            } else {
                val panelH = maxHeight * 0.42f
                Column(Modifier.fillMaxSize()) {
                    PageArea(vm, Modifier.weight(1f).fillMaxWidth())
                    AnimatedVisibility(vm.assist, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SidePanel(vm, Modifier.fillMaxWidth().height(panelH))
                    }
                }
            }
        }

        // ---------- 做题工具栏 ----------
        AnimatedVisibility(!vm.assist, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                HorizontalDivider()
                ToolBar(vm)
            }
        }
    }

    if (askStop) AlertDialog(
        onDismissRequest = { askStop = false },
        confirmButton = { TextButton({ askStop = false; vm.stopTimer() }) { Text("结束") } },
        dismissButton = { TextButton({ askStop = false }) { Text("继续做题") } },
        title = { Text("结束计时？") },
        text = { Text("结束后显示本次用时。") },
    )
    vm.timerResult?.let { ms ->
        AlertDialog(
            onDismissRequest = { vm.timerResult = null },
            confirmButton = { TextButton({ vm.timerResult = null }) { Text("好") } },
            title = { Text("本次用时") },
            text = { Text(formatDuration(ms), style = MaterialTheme.typography.displaySmall) },
        )
    }
}

@Composable
private fun PageArea(vm: ReaderViewModel, modifier: Modifier) {
    Box(modifier) {
        PageView(vm, Modifier.fillMaxSize())
        // 页码胶囊
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton({ vm.goTo(vm.page - 1) }, enabled = vm.page > 0, modifier = Modifier.size(36.dp)) { Icon(PIcons.ChevronLeft, "上一页") }
            Text("${vm.page + 1} / ${vm.pageCount}", style = MaterialTheme.typography.labelMedium)
            IconButton({ vm.goTo(vm.page + 1) }, enabled = vm.page < vm.pageCount - 1, modifier = Modifier.size(36.dp)) { Icon(PIcons.ChevronRight, "下一页") }
        }
    }
}

@Composable
private fun ToolBar(vm: ReaderViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(PIcons.Hand, "浏览", vm.tool == Tool.HAND) { vm.tool = Tool.HAND }
            ToolButton(PIcons.Pen, "笔", vm.tool == Tool.PEN) { vm.tool = Tool.PEN }
            ToolButton(PIcons.Highlighter, "荧光笔", vm.tool == Tool.HIGHLIGHTER) { vm.tool = Tool.HIGHLIGHTER }
            ToolButton(PIcons.Eraser, "橡皮", vm.tool == Tool.ERASER) { vm.tool = Tool.ERASER }
            Spacer(Modifier.width(6.dp))
            val colors = if (vm.tool == Tool.HIGHLIGHTER) ReaderViewModel.HL_COLORS else ReaderViewModel.PEN_COLORS
            val current = if (vm.tool == Tool.HIGHLIGHTER) vm.hlColor else vm.penColor
            if (vm.tool == Tool.PEN || vm.tool == Tool.HIGHLIGHTER) colors.forEach { c ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(22.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == current) 2.5.dp else 0.5.dp, if (c == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { if (vm.tool == Tool.HIGHLIGHTER) vm.hlColor = c else vm.penColor = c }
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(vm::undo, enabled = vm.canUndo) { Icon(PIcons.Undo, "撤销") }
            IconButton(vm::redo, enabled = vm.canRedo) { Icon(PIcons.Redo, "重做") }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, desc: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick,
        colors = if (selected) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
    ) { Icon(icon, desc, Modifier.size(22.dp)) }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
