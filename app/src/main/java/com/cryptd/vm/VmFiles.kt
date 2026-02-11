package com.cryptd.vm

import android.content.Context
import android.net.Uri
import java.io.File

object VmFiles {
    fun openDocumentFd(context: Context, uri: Uri, mode: String): Int? {
        val pfd = context.contentResolver.openFileDescriptor(uri, mode) ?: return null
        return pfd.detachFd()
    }

    fun saveLastDiskPath(context: Context, path: String) {
        val f = File(context.filesDir, "last-disk.txt")
        f.writeText(path)
    }

    fun loadLastDiskPath(context: Context): String? {
        val f = File(context.filesDir, "last-disk.txt")
        return if (f.exists()) f.readText().trim().ifEmpty { null } else null
    }

    fun saveLastIsoUri(context: Context, uri: Uri) {
        val f = File(context.filesDir, "last-iso-uri.txt")
        f.writeText(uri.toString())
    }

    fun loadLastIsoUri(context: Context): Uri? {
        val f = File(context.filesDir, "last-iso-uri.txt")
        if (!f.exists()) return null
        val raw = f.readText().trim()
        return if (raw.isEmpty()) null else Uri.parse(raw)
    }

    fun clearLastIsoUri(context: Context) {
        val f = File(context.filesDir, "last-iso-uri.txt")
        if (f.exists()) f.delete()
    }
}
