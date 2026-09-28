package com.paperly.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 本地 SQLite（Android 自带，成熟稳定，无需注解处理器）。
 * - docs         文档
 * - strokes      笔迹
 * - translations 全局翻译缓存：同一段文字不论在哪份真题里，只翻译一次
 * - solutions    解题缓存
 * - items        每页侧边栏记录
 * - ocr          每页 OCR 结果缓存
 * - sessions     计时记录
 */
class Db(context: Context) : SQLiteOpenHelper(context, "paperly2.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE docs(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, path TEXT NOT NULL, page_count INTEGER NOT NULL, last_page INTEGER NOT NULL DEFAULT 0, added_at INTEGER NOT NULL, study_ms INTEGER NOT NULL DEFAULT 0, subject TEXT NOT NULL DEFAULT '', fav INTEGER NOT NULL DEFAULT 0, opened_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE strokes(id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER NOT NULL, page INTEGER NOT NULL, hl INTEGER NOT NULL, color INTEGER NOT NULL, width REAL NOT NULL, points BLOB NOT NULL)")
        db.execSQL("CREATE INDEX idx_strokes ON strokes(doc_id, page)")
        db.execSQL("CREATE TABLE translations(k TEXT PRIMARY KEY, source TEXT NOT NULL, result TEXT NOT NULL, provider TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE solutions(k TEXT PRIMARY KEY, result TEXT NOT NULL, provider TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER NOT NULL, page INTEGER NOT NULL, kind INTEGER NOT NULL, source TEXT NOT NULL, result TEXT NOT NULL, provider TEXT NOT NULL, l REAL, t REAL, r REAL, b REAL, cached INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_items ON items(doc_id, page)")
        db.execSQL("CREATE TABLE ocr(doc_id INTEGER NOT NULL, page INTEGER NOT NULL, lang TEXT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(doc_id, page, lang))")
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER NOT NULL, started_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：资料库新增 科目 / 收藏 / 最近打开。只加列，原有数据全部保留。
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE docs ADD COLUMN subject TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE docs ADD COLUMN fav INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE docs ADD COLUMN opened_at INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---------------- docs ----------------
    fun docs(): List<Doc> = readableDatabase.rawQuery(
        "SELECT id,title,path,page_count,last_page,added_at,study_ms,subject,fav,opened_at FROM docs ORDER BY added_at DESC", null
    ).use { c ->
        buildList { while (c.moveToNext()) add(readDoc(c)) }
    }

    fun doc(id: Long): Doc? = readableDatabase.rawQuery(
        "SELECT id,title,path,page_count,last_page,added_at,study_ms,subject,fav,opened_at FROM docs WHERE id=?", arrayOf(id.toString())
    ).use { c -> if (c.moveToFirst()) readDoc(c) else null }

    fun insertDoc(title: String, path: String, pages: Int): Long = writableDatabase.insert("docs", null, ContentValues().apply {
        put("title", title); put("path", path); put("page_count", pages); put("added_at", System.currentTimeMillis())
    })

    private fun readDoc(c: android.database.Cursor) = Doc(
        c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getLong(5), c.getLong(6),
        subject = c.getString(7) ?: "", fav = c.getInt(8) == 1, openedAt = c.getLong(9),
    )

    fun setSubject(id: Long, subject: String) { writableDatabase.execSQL("UPDATE docs SET subject=? WHERE id=?", arrayOf<Any>(subject, id)) }
    fun setFav(id: Long, fav: Boolean) { writableDatabase.execSQL("UPDATE docs SET fav=? WHERE id=?", arrayOf<Any>(if (fav) 1 else 0, id)) }
    fun touchOpened(id: Long) { writableDatabase.execSQL("UPDATE docs SET opened_at=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id)) }
    fun rename(id: Long, title: String) { writableDatabase.execSQL("UPDATE docs SET title=? WHERE id=?", arrayOf<Any>(title, id)) }

    fun setLastPage(id: Long, page: Int) {
        writableDatabase.execSQL("UPDATE docs SET last_page=? WHERE id=?", arrayOf<Any>(page, id))
    }

    fun deleteDoc(id: Long) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            for (t in listOf("strokes", "items", "ocr", "sessions")) w.delete(t, "doc_id=?", arrayOf(id.toString()))
            w.delete("docs", "id=?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
    }

    // ---------------- strokes ----------------
    fun strokes(docId: Long, page: Int): List<Stroke> = readableDatabase.rawQuery(
        "SELECT id,hl,color,width,points FROM strokes WHERE doc_id=? AND page=? ORDER BY id", arrayOf(docId.toString(), page.toString())
    ).use { c ->
        buildList { while (c.moveToNext()) add(Stroke(c.getLong(0), page, c.getInt(1) == 1, c.getInt(2), c.getFloat(3), c.getBlob(4).toFloats())) }
    }

    fun insertStroke(docId: Long, s: Stroke): Long = writableDatabase.insert("strokes", null, ContentValues().apply {
        if (s.id > 0) put("id", s.id)
        put("doc_id", docId); put("page", s.page); put("hl", if (s.highlighter) 1 else 0)
        put("color", s.color); put("width", s.width); put("points", s.points.toBytes())
    })

    fun deleteStrokes(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL("DELETE FROM strokes WHERE id IN (${ids.joinToString(",")})")
    }

    // ---------------- caches ----------------
    fun cachedTranslation(key: String): Pair<String, String>? = readableDatabase.rawQuery(
        "SELECT result, provider FROM translations WHERE k=?", arrayOf(key)
    ).use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }

    fun putTranslation(key: String, source: String, result: String, provider: String) {
        writableDatabase.insertWithOnConflict("translations", null, ContentValues().apply {
            put("k", key); put("source", source); put("result", result); put("provider", provider); put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteTranslation(key: String) { writableDatabase.delete("translations", "k=?", arrayOf(key)) }

    fun cachedSolution(key: String): Pair<String, String>? = readableDatabase.rawQuery(
        "SELECT result, provider FROM solutions WHERE k=?", arrayOf(key)
    ).use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }

    fun putSolution(key: String, result: String, provider: String) {
        writableDatabase.insertWithOnConflict("solutions", null, ContentValues().apply {
            put("k", key); put("result", result); put("provider", provider); put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSolution(key: String) { writableDatabase.delete("solutions", "k=?", arrayOf(key)) }

    fun cacheStats(): Pair<Int, Int> {
        val a = readableDatabase.rawQuery("SELECT COUNT(*) FROM translations", null).use { it.moveToFirst(); it.getInt(0) }
        val b = readableDatabase.rawQuery("SELECT COUNT(*) FROM solutions", null).use { it.moveToFirst(); it.getInt(0) }
        return a to b
    }

    fun clearCaches() {
        writableDatabase.delete("translations", null, null)
        writableDatabase.delete("solutions", null, null)
    }

    // ---------------- items ----------------
    fun items(docId: Long, page: Int): List<AssistItem> = readableDatabase.rawQuery(
        "SELECT id,kind,source,result,provider,l,t,r,b,cached,created_at FROM items WHERE doc_id=? AND page=? ORDER BY id DESC",
        arrayOf(docId.toString(), page.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val box = if (c.isNull(5)) null else RectF(c.getFloat(5), c.getFloat(6), c.getFloat(7), c.getFloat(8))
                add(AssistItem(c.getLong(0), docId, page, ItemKind.entries[c.getInt(1)], c.getString(2), c.getString(3), c.getString(4), box, c.getInt(9) == 1, c.getLong(10)))
            }
        }
    }

    fun insertItem(it: AssistItem): Long = writableDatabase.insert("items", null, ContentValues().apply {
        put("doc_id", it.docId); put("page", it.page); put("kind", it.kind.ordinal); put("source", it.source)
        put("result", it.result); put("provider", it.provider)
        it.box?.let { b -> put("l", b.left); put("t", b.top); put("r", b.right); put("b", b.bottom) }
        put("cached", if (it.fromCache) 1 else 0); put("created_at", it.createdAt)
    })

    fun updateItem(id: Long, result: String, provider: String, cached: Boolean) {
        writableDatabase.update("items", ContentValues().apply {
            put("result", result); put("provider", provider); put("cached", if (cached) 1 else 0)
        }, "id=?", arrayOf(id.toString()))
    }

    fun deleteItem(id: Long) { writableDatabase.delete("items", "id=?", arrayOf(id.toString())) }

    // ---------------- OCR ----------------
    fun ocr(docId: Long, page: Int, lang: String): String? = readableDatabase.rawQuery(
        "SELECT data FROM ocr WHERE doc_id=? AND page=? AND lang=?", arrayOf(docId.toString(), page.toString(), lang)
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun putOcr(docId: Long, page: Int, lang: String, data: String) {
        writableDatabase.insertWithOnConflict("ocr", null, ContentValues().apply {
            put("doc_id", docId); put("page", page); put("lang", lang); put("data", data)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------------- sessions ----------------
    fun addSession(docId: Long, startedAt: Long, durationMs: Long) {
        val w = writableDatabase
        w.insert("sessions", null, ContentValues().apply { put("doc_id", docId); put("started_at", startedAt); put("duration_ms", durationMs) })
        w.execSQL("UPDATE docs SET study_ms = study_ms + ? WHERE id=?", arrayOf<Any>(durationMs, docId))
    }

    // ---------------- 统计 ----------------
    /** 自 since 起的专注时长（毫秒）与计时次数。 */
    fun studySince(since: Long): Pair<Long, Int> = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(duration_ms),0), COUNT(*) FROM sessions WHERE started_at>=?", arrayOf(since.toString())
    ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else 0L to 0 }

    /** 每份文档的查词（翻译）条数。 */
    fun lookupCounts(): Map<Long, Int> = readableDatabase.rawQuery(
        "SELECT doc_id, COUNT(*) FROM items WHERE kind=0 GROUP BY doc_id", null
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    /** 某文档自 since 起新增的 翻译 / 解题 条数（结束计时的统计用）。 */
    fun itemCountsSince(docId: Long, since: Long): Pair<Int, Int> = readableDatabase.rawQuery(
        "SELECT SUM(kind=0), SUM(kind=1) FROM items WHERE doc_id=? AND created_at>=?", arrayOf(docId.toString(), since.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else 0 to 0 }

    /** 有笔迹的页（缩略图上打点）。 */
    fun pagesWithStrokes(docId: Long): Set<Int> = readableDatabase.rawQuery(
        "SELECT DISTINCT page FROM strokes WHERE doc_id=?", arrayOf(docId.toString())
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getInt(0)) } }

    /** 生词本：全部文档的翻译记录，新的在前。 */
    fun vocab(limit: Int = 500): List<VocabEntry> = readableDatabase.rawQuery(
        "SELECT i.id,i.doc_id,COALESCE(d.title,''),i.page,i.source,i.result,i.created_at FROM items i LEFT JOIN docs d ON d.id=i.doc_id WHERE i.kind=0 ORDER BY i.created_at DESC LIMIT $limit", null
    ).use { c -> buildList { while (c.moveToNext()) add(VocabEntry(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4), c.getString(5), c.getLong(6))) } }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(context: Context): Db = inst ?: synchronized(this) { inst ?: Db(context.applicationContext).also { inst = it } }
    }
}

private fun FloatArray.toBytes(): ByteArray {
    val bb = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (f in this) bb.putFloat(f)
    return bb.array()
}

private fun ByteArray.toFloats(): FloatArray {
    val bb = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { bb.getFloat() }
}
