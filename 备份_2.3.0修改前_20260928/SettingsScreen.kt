package com.paperly.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paperly.mobile.ai.AiClient
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.PRESETS
import com.paperly.mobile.data.SettingsStore
import com.paperly.mobile.theme.Paper
import kotlinx.coroutines.launch

/** 设置：分组卡片式，配色跟随全局设计令牌。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val db = remember { Db.get(ctx) }
    var s by remember { mutableStateOf(store.load()) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf(db.cacheStats()) }
    val scope = rememberCoroutineScope()
    val c = Paper.c

    fun update(n: com.paperly.mobile.data.AppSettings) { s = n; store.save(n) }

    Column(Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(PIcons.Back, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        // 两栏只改变设置的呈现方式；所有表单和存储调用仍使用原来的 store / db。
        val aiGroup: @Composable () -> Unit = {
            Group("AI 接口 · OpenAI 兼容") {
                Text("一键填入：", style = MaterialTheme.typography.labelMedium, color = c.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p -> FilterPill(p.name, s.baseUrl == p.baseUrl) {
                        update(s.copy(baseUrl = p.baseUrl, textModel = p.textModel, visionModel = p.visionModel)); testMsg = p.note
                    } }
                }
                Field("Base URL", s.baseUrl, { update(s.copy(baseUrl = it)) }, KeyboardType.Uri)
                Field("API Key（加密保存在本机）", s.apiKey, { update(s.copy(apiKey = it)) }, password = true)
                Field("翻译模型", s.textModel, { update(s.copy(textModel = it)) })
                Field("解题模型（支持图片；留空则发送识别文字）", s.visionModel, { update(s.copy(visionModel = it)) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperButton("测试连接", primary = false, enabled = s.hasApi) {
                        testMsg = "测试中…"
                        scope.launch { testMsg = runCatching { "连接成功：" + AiClient.test(s).take(40) }.getOrElse { "失败：${it.message}" } }
                    }
                    testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f)) }
                }
                ToggleRow("没有接口或接口失败时，使用免费翻译", "MyMemory 公共翻译，无需 Key，每天有额度上限，只翻译不讲解", s.freeFallback) { update(s.copy(freeFallback = it)) }
            }
        }
        val optionsGroup: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Group("做题") {
                ToggleRow("选中后自动翻译", "关闭后松手会出现“翻译”按钮，点一下才请求", s.autoTranslate) { update(s.copy(autoTranslate = it)) }
                ToggleRow("仅手写笔书写", "开启后手指只用来翻页和缩放（平板 + 手写笔推荐）", s.stylusOnly) { update(s.copy(stylusOnly = it)) }
                ToggleRow("识别中文", "默认只识别英文；做中文题目时开启", s.ocrChinese) { update(s.copy(ocrChinese = it)) }
            }
            Group("本地缓存") {
                Text("已缓存 ${stats.first} 条翻译、${stats.second} 条解答。相同内容再次查询直接读取，不消耗 token。",
                    style = MaterialTheme.typography.bodySmall, color = c.ink2)
                PaperButton("清空缓存", primary = false) { db.clearCaches(); stats = db.cacheStats() }
            }
            Group("关于纸间") {
                Text("纸间 2.2.2", style = MaterialTheme.typography.titleMedium, color = c.accent)
                Text("PDF、笔迹、识别结果只存储在本机；只有你选中的文字或圈出的区域会发送至你配置的 AI 接口。",
                    style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            Text("给思考留一点安静的空间。",
                style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(bottom = 26.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 850.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 34.dp else 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.padding(bottom = 2.dp)) {
                    Text("把学习调整到刚刚好。", style = MaterialTheme.typography.headlineMedium)
                    Text("个性化做题、辅助与本地数据", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1.15f)) { aiGroup() }
                    Column(Modifier.weight(.85f)) { optionsGroup() }
                } else {
                    aiGroup()
                    optionsGroup()
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = Paper.c
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = c.accent, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().paperCard(RoundedCornerShape(18.dp), border = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun ToggleRow(title: String, sub: String, v: Boolean, on: (Boolean) -> Unit) {
    val c = Paper.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(v, on)
    }
}
