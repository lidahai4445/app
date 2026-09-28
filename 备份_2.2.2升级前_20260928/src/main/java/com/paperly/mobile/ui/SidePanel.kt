package com.paperly.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.data.AssistItem
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.ItemKind
import com.paperly.mobile.theme.Paper

/** 辅助模式侧边栏：本页 / 生词 / 解题 三个标签，卡片式结果，加载中显示骨架屏。 */
@Composable
fun SidePanel(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val c = Paper.c
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var tab by rememberSaveable { mutableStateOf(0) }   // 0 本页 1 生词 2 解题
    val vocab by androidx.compose.runtime.produceState(emptyList<com.paperly.mobile.data.VocabEntry>(), tab) {
        value = if (tab == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.vocab() } else emptyList()
    }
    val stats = remember { db.cacheStats() }

    Column(modifier.background(c.surface)) {
        // ---- 标签行 ----
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val counts = listOf(vm.items.size, stats.first, vm.items.count { it.kind == ItemKind.SOLVE })
            listOf("本页", "生词", "解题").forEachIndexed { i, label ->
                val on = tab == i
                Column(
                    Modifier.clickable { tab = i }.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.muted)
                        Spacer(Modifier.width(5.dp))
                        Text("${counts[i]}", style = MaterialTheme.typography.labelSmall,
                            color = if (on) c.ink else c.muted,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) c.line else Color.Transparent).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.width(26.dp).height(2.dp).background(if (on) c.ink else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = c.line2)

        // ---- 内容 ----
        val list = when (tab) {
            2 -> vm.items.filter { it.kind == ItemKind.SOLVE }
            else -> vm.items
        }
        val state = rememberLazyListState()
        val focus = vm.focusedItem
        LaunchedEffect(focus, tab) {
            val i = list.indexOfFirst { it.id == focus }
            if (i >= 0) state.animateScrollToItem(i)
        }
        val bgList = Modifier.fillMaxSize().background(c.surface2)
        val listModifier: androidx.compose.foundation.layout.PaddingValues = PaddingValues(14.dp)
        when (tab) {
            1 -> if (vocab.isEmpty()) Column(bgList) { PanelHint("还没有生词", "回到页面点词或划句，译文会自动收进生词本。") }
            else LazyColumn(contentPadding = listModifier, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = bgList) {
                items(vocab, key = { it.id }) { v ->
                    Column(Modifier.fillMaxWidth().paperCard(RoundedCornerShape(14.dp), border = true).padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(v.source, style = MaterialTheme.typography.titleSmall)
                        Text(v.result, style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        Text(listOf(v.docTitle, "第 ${v.page + 1} 页").filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            2 -> if (list.isEmpty()) Column(bgList) { PanelHint("还没有解题记录", "用“圈题”工具圈出一道题，松手后确认即可让 AI 解答。") }
            else LazyColumn(state = state, contentPadding = listModifier, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = bgList) {
                items(list, key = { it.id }) { ItemCard(vm, it) }
            }
            else -> if (vm.items.isEmpty()) Column(bgList) { PanelHint("这一页还没有辅助记录", "点词、划句看翻译；圈出一道题让 AI 解答。\n相同内容会读本机缓存，不重复消耗额度。") }
            else LazyColumn(state = state, contentPadding = listModifier, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = bgList) {
                items(vm.items, key = { it.id }) { ItemCard(vm, it) }
            }
        }
        if (tab != 1) {
            // ---- 底部说明 ----
            Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtnSmall(PIcons.Database, null)
                Spacer(Modifier.width(7.dp))
                Text("已缓存 ${stats.first} 条翻译 · ${stats.second} 条解答，相同内容不再请求", style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PanelHint(title: String, sub: String, pad: Boolean = true) {
    val c = Paper.c
    Column(Modifier.fillMaxWidth().padding(if (pad) PaddingValues(14.dp) else PaddingValues(2.dp, 26.dp, 2.dp, 6.dp))) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink2)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp), lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
    }
}

@Composable
private fun ItemCard(vm: ReaderViewModel, item: AssistItem) {
    val c = Paper.c
    val clip = LocalClipboardManager.current
    val solve = item.kind == ItemKind.SOLVE
    val tagFg = if (solve) c.ai else c.accent
    val tagBg = if (solve) c.aiSoft else c.accentSoft
    Column(
        Modifier.fillMaxWidth().paperCard(RoundedCornerShape(14.dp), border = item.id != vm.focusedItem)
            .then(if (item.id == vm.focusedItem) Modifier.border(1.5.dp, if (solve) c.ai else c.accent, RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tagFg))
            Spacer(Modifier.width(7.dp))
            Text(if (solve) "AI 解题" else "翻译", style = MaterialTheme.typography.labelSmall, color = tagFg)
            if (item.fromCache) { Spacer(Modifier.width(7.dp)); MiniTag("本地缓存", c.muted, c.line2) }
            Spacer(Modifier.weight(1f))
            if (item.provider.isNotBlank()) Text(item.provider, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.source, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, color = c.ink2,
            maxLines = if (item.id == vm.focusedItem) 8 else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
        if (item.loading) {
            Skeleton(if (solve) listOf(0.95f, 0.88f, 0.8f, 0.5f) else listOf(0.9f, 0.62f))
        } else {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(item.result, style = MaterialTheme.typography.bodyMedium, color = if (item.error) c.danger else c.ink,
                    modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                IconBtnSmall(PIcons.Copy, "复制") { clip.setText(AnnotatedString(item.result)) }
                IconBtnSmall(PIcons.Refresh, "重新生成") { vm.regenerate(item) }
                IconBtnSmall(PIcons.Trash, "删除", tint = c.muted) { vm.deleteItem(item) }
            }
        }
    }
}

@Composable
private fun IconBtnSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String?, tint: androidx.compose.ui.graphics.Color = Color.Unspecified, onClick: () -> Unit = {}) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).clickable(enabled = desc != null, onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, desc, Modifier.size(15.dp), tint = if (tint == Color.Unspecified) Paper.c.muted else tint)
    }
}
