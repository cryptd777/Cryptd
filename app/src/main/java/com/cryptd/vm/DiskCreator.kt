package com.cryptd.vm

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object DiskCreator {
    fun createRawDisk(context: Context, sizeGb: Int): String? {
        if (sizeGb <= 0) return null
        val dir = File(context.filesDir, "vm")
        if (!dir.exists() && !dir.mkdirs()) return null
        val out = File(dir, "disk-${System.currentTimeMillis()}.img")
        try {
            RandomAccessFile(out, "rw").use { raf ->
                raf.setLength(sizeGb.toLong() * 1024L * 1024L * 1024L)
            }
        } catch (_: Exception) {
            return null
        }
        return out.absolutePath
    }
}
