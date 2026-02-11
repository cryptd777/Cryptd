package com.cryptd.vm

import android.content.Context
import android.net.Uri
import java.io.File

object VmFiles {
    fun copyToPrivateStorage(context: Context, uri: Uri): String? {
        val destDir = File(context.filesDir, "vm")
        if (!destDir.exists() && !destDir.mkdirs()) {
            return null
        }

        val name = buildName(context, uri)
        val destFile = File(destDir, name)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
            return destFile.absolutePath
        }

        return null
    }

    private fun buildName(context: Context, uri: Uri): String {
        val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
        val rawName = doc?.name ?: "disk"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val base = if (safe.contains('.')) safe else "disk-${System.currentTimeMillis()}"
        val destDir = File(context.filesDir, "vm")
        val candidate = File(destDir, base)
        return if (!candidate.exists()) base else "${base}.${System.currentTimeMillis()}"
    }

    fun saveLastDiskPath(context: Context, path: String) {
        val f = File(context.filesDir, "last-disk.txt")
        f.writeText(path)
    }

    fun loadLastDiskPath(context: Context): String? {
        val f = File(context.filesDir, "last-disk.txt")
        return if (f.exists()) f.readText().trim().ifEmpty { null } else null
    }
}
