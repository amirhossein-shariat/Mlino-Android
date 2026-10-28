package app.malino.data

import android.content.Context
import app.malino.core.Jalali
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is one zip: the SQLite database, the JSON collections and a small meta file.
 * Restore never touches live files directly: it validates the zip, stages it, and the next process start swaps it in
 * (before the database is opened).
 */
object Backup {
    private const val DB = "malino.db"
    private val ALLOWED = Regex("^(malino\\.db|meta\\.json|collections/[A-Za-z0-9_.-]+\\.json)$")
    private const val MAX_BYTES = 300L * 1024 * 1024

    private fun autoDir(ctx: Context) = File(ctx.filesDir, "backups").apply { mkdirs() }

    fun write(ctx: Context, repo: Repo, out: OutputStream) {
        repo.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("meta.json"))
            z.write(JSONObject().put("app", "malino").put("format", 1).put("dbVersion", Db.VERSION).put("createdAt", System.currentTimeMillis()).toString().toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry(DB))
            ctx.getDatabasePath(DB).inputStream().use { it.copyTo(z) }
            z.closeEntry()
            File(ctx.filesDir, "collections").listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
                z.putNextEntry(ZipEntry("collections/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    /** One automatic backup per Tehran day, newest 7 kept inside the app's private storage. */
    fun autoIfDue(ctx: Context, repo: Repo) {
        val d = Jalali.today()
        val f = File(autoDir(ctx), "malino-auto-%04d%02d%02d.zip".format(d.y, d.m, d.d))
        if (f.exists()) return
        runCatching { f.outputStream().use { write(ctx, repo, it) } }.onFailure { f.delete() }
        autoDir(ctx).listFiles { x -> x.name.startsWith("malino-auto-") }?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
    }

    /** Taken right before a destructive clean-up; the newest 3 are kept. */
    fun safety(ctx: Context, repo: Repo): Boolean {
        val d = Jalali.today(); val t = Jalali.fromMillis(System.currentTimeMillis())
        val f = File(autoDir(ctx), "malino-safety-%04d%02d%02d-%02d%02d%02d.zip".format(d.y, d.m, d.d, t.h, t.mi, (System.currentTimeMillis() / 1000 % 60).toInt()))
        val ok = runCatching { f.outputStream().use { write(ctx, repo, it) } }.isSuccess
        if (!ok) f.delete()
        autoDir(ctx).listFiles { x -> x.name.startsWith("malino-safety-") }?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        return ok
    }

    fun listAuto(ctx: Context): List<File> = autoDir(ctx).listFiles { x -> x.name.startsWith("malino-auto-") || x.name.startsWith("malino-safety-") }?.sortedByDescending { it.name } ?: emptyList()

    sealed interface Staged { data object Ok : Staged; data class Fail(val why: String) : Staged }

    /** Validates the zip and stages it for the next start. */
    fun stageRestore(ctx: Context, input: InputStream): Staged {
        val tmp = File(ctx.filesDir, "restore.tmp")
        tmp.deleteRecursively(); File(tmp, "collections").mkdirs()
        var total = 0L
        var meta: JSONObject? = null
        try {
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (!ALLOWED.matches(e.name)) return fail(tmp, "فایل پشتیبان معتبر نیست")
                    val dest = File(tmp, e.name)
                    dest.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = z.read(buf); if (n < 0) break
                            total += n; if (total > MAX_BYTES) return fail(tmp, "فایل پشتیبان بیش از حد بزرگ است")
                            o.write(buf, 0, n)
                        }
                    }
                    if (e.name == "meta.json") meta = runCatching { JSONObject(dest.readText()) }.getOrNull()
                }
            }
        } catch (e: Exception) { return fail(tmp, "خواندن فایل پشتیبان ممکن نشد") }
        if (meta?.optString("app") != "malino") return fail(tmp, "این فایل پشتیبان مالینو نیست")
        val db = File(tmp, DB)
        if (!db.exists()) return fail(tmp, "بانک داده در پشتیبان نیست")
        val head = ByteArray(100)
        val read = db.inputStream().use { it.read(head) }
        if (read < 100 || String(head, 0, 15, Charsets.ISO_8859_1) != "SQLite format 3") return fail(tmp, "بانک داده‌ی پشتیبان خراب است")
        val version = ((head[60].toInt() and 0xff) shl 24) or ((head[61].toInt() and 0xff) shl 16) or ((head[62].toInt() and 0xff) shl 8) or (head[63].toInt() and 0xff)
        if (version > Db.VERSION) return fail(tmp, "این پشتیبان از نسخه‌ی جدیدتر برنامه است")
        val final = File(ctx.filesDir, "restore")
        final.deleteRecursively()
        return if (tmp.renameTo(final)) Staged.Ok else fail(tmp, "ذخیره‌ی موقت ممکن نشد")
    }

    private fun fail(tmp: File, why: String): Staged { tmp.deleteRecursively(); return Staged.Fail(why) }

    /** Called from Application.onCreate before the database is opened. */
    fun applyPending(ctx: Context) {
        val staged = File(ctx.filesDir, "restore")
        val db = File(staged, DB)
        if (!db.exists()) { staged.deleteRecursively(); return }
        runCatching {
            val target = ctx.getDatabasePath(DB)
            listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            target.parentFile?.mkdirs()
            db.copyTo(target, overwrite = true)
            val col = File(ctx.filesDir, "collections")
            col.deleteRecursively(); col.mkdirs()
            File(staged, "collections").listFiles()?.forEach { it.copyTo(File(col, it.name), overwrite = true) }
        }
        staged.deleteRecursively()
    }
}
