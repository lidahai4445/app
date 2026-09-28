package com.paperly.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.data.AssistItem
import com.paperly.mobile.data.AssistTool
import com.paperly.mobile.data.Granularity
import com.paperly.mobile.data.ItemKind

/** 辅助模式侧边栏：顶部工具，下方为本页翻译/解题结果列表。 */
@Composable
fun SidePanel(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    Surface(modifier, tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize()) {
            // 工具行
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(vm.assistTool == AssistTool.SELECT, { vm.assistTool = AssistTool.SELECT }, { Text("选词翻译") },
                    leadingIcon = { Icon(PIcons.Translate, null, Modifier.size(16.dp)) })
                FilterChip(vm.assistTool == AssistTool.LASSO, { vm.assistTool = AssistTool.LASSO; vm.selection = null }, { Text("圈题解答") },
                    leadingIcon = { Icon(PIcons.Lasso, null, Modifier.size(16.dp)) })
            }
            if (vm.assistTool == AssistTool.SELECT) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Granularity.entries.forEach { g ->
                        FilterChip(vm.granularity == g, { vm.granularity = g }, { Text(g.label) })
                    }
                    Spacer(Modifier.weight(1f))
                    if (!vm.settings.autoTranslate && vm.selection != null) TextButton({ vm.translateSelection() }) { Text("翻译") }
                }
            }
            // 状态行
            val status = when {
                vm.ocrBusy -> "正在本地识别本页文字…"
                vm.ocrError != null -> vm.ocrError!!
                vm.ocrWords != null -> if (vm.assistTool == AssistTool.SELECT) "已识别 ${vm.ocrWords!!.size} 个词 · 点词或拖选" else "用手指圈出题目，松手即发送"
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (vm.ocrError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (vm.ocrError != null || (vm.ocrWords?.isEmpty() == true)) TextButton({ vm.runOcr(force = true) }) { Text("重试") }
            }
            if (vm.ocrBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 14.dp))
            HorizontalDivider(Modifier.padding(top = 4.dp))

            val state = rememberLazyListState()
            val focus = vm.focusedItem
            LaunchedEffect(focus) {
                val i = vm.items.indexOfFirst { it.id == focus }
                if (i >= 0) state.animateScrollToItem(i)
            }
            if (vm.items.isEmpty()) {
                Text(
                    if (vm.assistTool == AssistTool.SELECT) "轻点单词即可翻译；拖动选择短语或句子。\n翻译结果会存到本机，下次同一内容直接读取，不再消耗 token。"
                    else "圈出一道题，AI 会给出答案和步骤。\n解答同样缓存在本机。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(state = state, contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(vm.items, key = { it.id }) { item -> ItemCard(vm, item) }
            }
        }
    }
}

@Composable
private fun ItemCard(vm: ReaderViewModel, item: AssistItem) {
    val clip = LocalClipboardManager.current
    val focused = vm.focusedItem == item.id
    Card(
        Modifier.fillMaxWidth().clickable { vm.focusedItem = item.id },
        colors = CardDefaults.cardColors(containerColor = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.kind == ItemKind.SOLVE) "解题" else "翻译", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                if (item.fromCache) Text("本地缓存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.weight(1f))
                Text(item.provider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
            }
            Text(item.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (focused) 8 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
            if (item.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 0.dp).padding(end = 8.dp))
            } else {
                SelectionContainer {
                    Text(item.result, style = MaterialTheme.typography.bodyMedium, color = if (item.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, end = 8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton({ clip.setText(AnnotatedString(item.result)) }) { Icon(PIcons.Copy, "复制", Modifier.size(18.dp)) }
                    IconButton({ vm.regenerate(item) }) { Icon(Icons.Default.Refresh, "重新生成", Modifier.size(18.dp)) }
                    IconButton({ vm.deleteItem(item) }) { Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp)) }
                }
            }
        }
    }
}
