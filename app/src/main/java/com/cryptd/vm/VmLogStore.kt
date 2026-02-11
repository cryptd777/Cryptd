package com.cryptd.vm

import android.content.Context
import java.io.File

object VmLogStore {
    private fun logDir(context: Context): File = File(context.filesDir, "vm-logs")
    private fun indexFile(context: Context): File = File(logDir(context), "latest.txt")

    fun startSession(context: Context): File {
        val dir = logDir(context)
        if (!dir.exists()) dir.mkdirs()
        val name = "vm-${System.currentTimeMillis()}.log"
        val f = File(dir, name)
        f.writeText("=== VM LOG START ${System.currentTimeMillis()} ===\n")
        indexFile(context).writeText(f.absolutePath)
        pruneOldLogs(dir, keep = 10)
        return f
    }

    fun readAll(context: Context): String {
        val dir = logDir(context)
        if (!dir.exists()) return "(no logs yet)"
        val latest = indexFile(context).takeIf { it.exists() }?.readText()?.trim()
        val files = dir.listFiles { _, n -> n.endsWith(".log") }?.sortedByDescending { it.name } ?: emptyList()
        val sb = StringBuilder()
        if (latest != null && latest.isNotEmpty()) {
            val lf = File(latest)
            if (lf.exists()) {
                sb.append("=== Latest ===\n")
                sb.append(lf.readText())
                sb.append("\n")
            }
        }
        for (f in files) {
            if (latest != null && f.absolutePath == latest) continue
            sb.append("=== ").append(f.name).append(" ===\n")
            sb.append(f.readText()).append("\n")
        }
        return if (sb.isNotEmpty()) sb.toString() else "(no logs yet)"
    }

    fun clear(context: Context) {
        val dir = logDir(context)
        dir.listFiles()?.forEach { it.delete() }
        indexFile(context).delete()
    }

    fun append(context: Context, text: String) {
        val dir = logDir(context)
        if (!dir.exists()) dir.mkdirs()
        val latest = indexFile(context).takeIf { it.exists() }?.readText()?.trim()
        val f = if (!latest.isNullOrEmpty()) File(latest) else startSession(context)
        f.appendText(text)
    }

    fun currentLogPath(context: Context): String? {
        val latest = indexFile(context).takeIf { it.exists() }?.readText()?.trim()
        return if (!latest.isNullOrEmpty()) latest else null
    }

    private fun pruneOldLogs(dir: File, keep: Int) {
        val files = dir.listFiles { _, n -> n.endsWith(".log") }?.sortedByDescending { it.name } ?: return
        files.drop(keep).forEach { it.delete() }
    }
}
