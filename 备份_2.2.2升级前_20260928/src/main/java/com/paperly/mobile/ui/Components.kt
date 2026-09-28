package com.paperly.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paperly.mobile.theme.Paper

/** 三级阴影：贴面 / 悬浮 / 弹出。 */
enum class Elev(val dp: Dp) { Flat(1.dp), Float(6.dp), Pop(18.dp) }

/** 卡片面：白底 + 柔和阴影 + 圆角。 */
@Composable
fun Modifier.paperCard(shape: Shape = RoundedCornerShape(18.dp), elev: Elev = Elev.Flat, color: Color? = null, border: Boolean = false): Modifier {
    val c = Paper.c
    val shadowColor = Color(0xFF16221D).copy(alpha = if (c.dark) 0.5f else 0.18f)
    return this
        .shadow(elev.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(color ?: c.surface)
        .then(if (border) Modifier.border(1.dp, c.line2, shape) else Modifier)
}

/** 38dp 圆角图标按钮；active 时为主色浅底。 */
@Composable
fun IconBtn(icon: ImageVector, desc: String, active: Boolean = false, enabled: Boolean = true, size: Dp = 38.dp, tint: Color? = null, onClick: () -> Unit) {
    val c = Paper.c
    Box(
        Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(if (active) c.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(20.dp).alpha(if (enabled) 1f else 0.3f), tint = tint ?: if (active) c.accent else c.ink2)
    }
}

/** 悬浮胶囊容器（工具栏、页码）。 */
@Composable
fun Pill(modifier: Modifier = Modifier, round: Dp = 16.dp, padding: PaddingValues = PaddingValues(5.dp), content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.paperCard(RoundedCornerShape(round), Elev.Float, Paper.c.surface.copy(alpha = 0.97f), border = true).padding(padding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), content = content,
    )
}

/** 工具胶囊里的工具键：选中为深色实心（AI 工具选中为琥珀色）。 */
@Composable
fun ToolKey(icon: ImageVector, desc: String, selected: Boolean, ai: Boolean = false, enabled: Boolean = true, label: String? = null, onClick: () -> Unit) {
    val c = Paper.c
    val bg by animateColorAsState(if (selected) (if (ai) c.ai else c.chip) else Color.Transparent, tween(160), label = "tool")
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = desc, onClick = onClick)
            .padding(horizontal = if (label != null) 10.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, desc, Modifier.size(20.dp).alpha(if (enabled) 1f else 0.3f), tint = if (selected) (if (ai) Color.White else c.onChip) else c.ink2)
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) (if (ai) Color.White else c.onChip) else c.ink2, maxLines = 1)
        }
    }
}

@Composable
fun PillDivider() = Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(Paper.c.line))

/** 做题 | 辅助 分段切换。 */
@Composable
fun ModeSwitch(assist: Boolean, onChange: (Boolean) -> Unit, compact: Boolean = false) {
    val c = Paper.c
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.line2 else Color(0xFFECEBE5)).padding(3.dp)) {
        listOf(false to "做题", true to "辅助").forEach { (isAssist, label) ->
            val on = assist == isAssist
            val bg by animateColorAsState(if (on) c.surface else Color.Transparent, tween(180), label = "seg")
            Row(
                Modifier.clip(RoundedCornerShape(9.dp)).background(bg).clickable(role = Role.Tab) { onChange(isAssist) }
                    .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (isAssist) PIcons.Spark else PIcons.Pen, null, Modifier.size(16.dp), tint = if (on) (if (isAssist) c.ai else c.ink) else c.muted)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) (if (isAssist) c.ai else c.ink) else c.ink2)
            }
        }
    }
}

/** 筛选胶囊。 */
@Composable
fun FilterPill(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val c = Paper.c
    Row(
        Modifier.clip(CircleShape).background(if (selected) c.chip else Color.Transparent)
            .border(1.dp, if (selected) c.chip else c.line, CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onChip else c.ink2, maxLines = 1)
    }
}

/** 主按钮 / 次按钮。 */
@Composable
fun PaperButton(text: String, icon: ImageVector? = null, primary: Boolean = true, trailing: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Paper.c
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(42.dp)
            .then(if (primary) Modifier.shadow(if (enabled) 4.dp else 0.dp, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape).background(if (primary) c.accent else c.surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.line, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (primary) c.onAccent else c.ink
        if (icon != null && !trailing) { Icon(icon, null, Modifier.size(17.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null && trailing) { Spacer(Modifier.width(6.dp)); Icon(icon, null, Modifier.size(17.dp), tint = fg) }
    }
}

/** 骨架屏：AI 结果加载时显示几条流动的灰条，而不是转圈。 */
@Composable
fun Skeleton(lines: List<Float> = listOf(0.92f, 0.74f, 0.55f)) {
    val c = Paper.c
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "ska")
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { w -> Box(Modifier.fillMaxWidth(w).height(9.dp).clip(RoundedCornerShape(5.dp)).alpha(a).background(c.line)) }
    }
}

/** 小标签：如“adj.”“本地缓存”。 */
@Composable
fun MiniTag(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** 科目色（固定映射，保证同一科目颜色稳定）。 */
fun subjectColor(subject: String): Color = when {
    subject.startsWith("英语") -> Color(0xFF3F8466)
    subject.startsWith("数学") -> Color(0xFF5B7DB1)
    subject == "政治" -> Color(0xFFC08A4A)
    subject == "专业课" -> Color(0xFF8264A4)
    else -> Color(0xFFA3ACA7)
}
