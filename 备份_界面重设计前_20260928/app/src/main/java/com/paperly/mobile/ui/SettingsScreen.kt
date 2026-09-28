package com.paperly.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paperly.mobile.ai.AiClient
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.PRESETS
import com.paperly.mobile.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val db = remember { Db.get(ctx) }
    var s by remember { mutableStateOf(store.load()) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf(db.cacheStats()) }
    val scope = rememberCoroutineScope()

    fun update(n: com.paperly.mobile.data.AppSettings) { s = n; store.save(n) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("AI 接口（OpenAI 兼容）")
            Text("一键填入：", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(s.baseUrl == p.baseUrl, { update(s.copy(baseUrl = p.baseUrl, textModel = p.textModel, visionModel = p.visionModel)); testMsg = p.note }, { Text(p.name) })
                }
            }
            OutlinedTextField(s.baseUrl, { update(s.copy(baseUrl = it)) }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(s.apiKey, { update(s.copy(apiKey = it)) }, label = { Text("API Key（加密保存在本机）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.textModel, { update(s.copy(textModel = it)) }, label = { Text("翻译模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.visionModel, { update(s.copy(visionModel = it)) }, label = { Text("解题模型（支持图片；留空则发送识别文字）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({
                    testMsg = "测试中…"
                    scope.launch { testMsg = runCatching { "连接成功：" + AiClient.test(s).take(40) }.getOrElse { "失败：${it.message}" } }
                }, enabled = s.hasApi) { Text("测试连接") }
            }
            testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ToggleRow("没有接口或接口失败时，使用免费翻译", "MyMemory 公共翻译，无需 Key，每天有额度上限，只翻译不讲解", s.freeFallback) { update(s.copy(freeFallback = it)) }

            HorizontalDivider()
            Section("做题")
            ToggleRow("选中后自动翻译", "关闭后需手动点“翻译”", s.autoTranslate) { update(s.copy(autoTranslate = it)) }
            ToggleRow("仅手写笔书写", "开启后手指只用来翻页和缩放（平板 + 手写笔推荐）", s.stylusOnly) { update(s.copy(stylusOnly = it)) }
            ToggleRow("识别中文", "默认只识别英文；做中文题目时开启", s.ocrChinese) { update(s.copy(ocrChinese = it)) }

            HorizontalDivider()
            Section("本地缓存")
            Text("已缓存 ${stats.first} 条翻译、${stats.second} 条解答。相同内容再次查询直接读取，不消耗 token。", style = MaterialTheme.typography.bodySmall)
            Button({ db.clearCaches(); stats = db.cacheStats() }) { Text("清空缓存") }
            Text("\n纸间 2.0 · PDF、笔迹、识别结果都只存在本机；只有你选中的文字 / 圈出的区域会发给你配置的 AI 接口。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Section(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun ToggleRow(title: String, sub: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(v, on)
    }
}
