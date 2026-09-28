package com.paperly.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.paperly.mobile.theme.PaperlyTheme
import com.paperly.mobile.ui.LibraryScreen
import com.paperly.mobile.ui.ReaderScreen
import com.paperly.mobile.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** 从文件管理器/微信等“用纸间打开”的 PDF，交给资料库导入。 */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = pdfFrom(intent)
        android.util.Log.i("Paperly", "onCreate intent=${intent?.action} data=${intent?.data != null} incoming=${incoming.value != null}")
        setContent { PaperlyTheme { Surface(color = MaterialTheme.colorScheme.surface) { AppRoot(incoming.value) { incoming.value = null } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pdfFrom(intent)?.let { incoming.value = it }
    }

    @Suppress("DEPRECATION")
    private fun pdfFrom(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND -> i.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
}

/** 极简导航：用一个字符串栈表示页面，例如 "lib/r:3/set"。 */
@Composable
private fun AppRoot(incoming: Uri?, onConsumed: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf("lib") }
    val parts = stack.split('/')
    val top = parts.last()
    fun push(s: String) { stack = "$stack/$s" }
    fun pop() { stack = parts.dropLast(1).joinToString("/").ifEmpty { "lib" } }

    BackHandler(enabled = parts.size > 1) { pop() }
    // 外部打开 PDF 时回到资料库，由资料库导入后自动进入
    LaunchedEffect(incoming) { if (incoming != null) stack = "lib" }
    AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { t ->
        when {
            t == "lib" -> LibraryScreen(onOpen = { push("r:$it") }, onSettings = { push("set") }, incoming = incoming, onIncomingConsumed = onConsumed)
            t.startsWith("r:") -> ReaderScreen(t.removePrefix("r:").toLong(), onBack = ::pop, onSettings = { push("set") })
            else -> SettingsScreen(onBack = ::pop)
        }
    }
}
