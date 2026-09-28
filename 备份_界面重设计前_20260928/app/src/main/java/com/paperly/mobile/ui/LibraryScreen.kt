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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.mobile.data.Db
import com.paperly.mobile.data.Doc
import com.paperly.mobile.pdf.PdfDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit, incoming: Uri? = null, onIncomingConsumed: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var docs by remember { mutableStateOf(db.docs()) }
    var busy by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Doc?>(null) }
    val scope = rememberCoroutineScope()

    fun doImport(uri: Uri) {
        android.util.Log.i("Paperly", "import start: ${uri.scheme}")
        busy = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { importPdf(ctx, db, uri) } }
            busy = false
            r.onSuccess { docs = db.docs(); onOpen(it) }
                .onFailure { android.util.Log.e("Paperly", "import failed", it); Toast.makeText(ctx, "导入失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doImport(uri) }
    LaunchedEffect(incoming) { if (incoming != null) { onIncomingConsumed(); doImport(incoming) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("纸间", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onSettings) { Icon(Icons.Default.Settings, "设置") }
            }
            if (docs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("导入一份 PDF 真题开始做题", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    items(docs, key = { it.id }) { d ->
                        Column(Modifier.combinedClickable(onClick = { onOpen(d.id) }, onLongClick = { toDelete = d })) {
                            Card(shape = RoundedCornerShape(10.dp)) { Thumb(d) }
                            Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            val t = if (d.studyMs > 0) " · 已做 ${formatDuration(d.studyMs)}" else ""
                            Text("${d.lastPage + 1}/${d.pageCount} 页$t", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!busy) picker.launch(arrayOf("application/pdf")) },
            icon = { if (busy) CircularProgressIndicator(Modifier.height(20.dp)) else Icon(Icons.Default.Add, null) },
            text = { Text(if (busy) "正在导入…" else "导入 PDF") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除《${d.title}》？") },
            text = { Text("将删除 App 内的副本、笔迹和本书的翻译记录（全局翻译缓存保留）。手机里的原 PDF 不受影响。") },
            confirmButton = {
                TextButton({
                    db.deleteDoc(d.id); File(d.path).delete(); thumbFile(ctx, d.id).delete()
                    docs = db.docs(); toDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Thumb(d: Doc) {
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
    Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
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
