package com.cryptd.vm

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object QemuInstaller {
    data class QemuBundle(val qemuPath: String, val libDir: String, val shareDir: String)
    private const val BUNDLE_VERSION = 4

    fun ensureQemuBundle(context: Context): QemuBundle? {
        val baseDir = File(context.filesDir, "qemu")
        val versionFile = File(baseDir, ".version")
        if (versionFile.exists()) {
            val current = versionFile.readText().trim().toIntOrNull()
            if (current != BUNDLE_VERSION) {
                deleteRecursively(baseDir)
            }
        }
        val libDir = File(baseDir, "lib")
        val shareDir = File(baseDir, "share")

        if (!libDir.exists() && !libDir.mkdirs()) return null
        if (!shareDir.exists() && !shareDir.mkdirs()) return null

        copyAssetDir(context, "qemu/lib", libDir)
        copyAssetDir(context, "qemu/share", shareDir)

        val qemuPath = File(context.applicationInfo.nativeLibraryDir, "libqemu-system-aarch64.so")
        if (!qemuPath.exists()) return null

        versionFile.writeText(BUNDLE_VERSION.toString())
        return QemuBundle(qemuPath.absolutePath, libDir.absolutePath, shareDir.absolutePath)
    }

    private fun copyAssetDir(context: Context, assetPath: String, outDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        for (name in assets) {
            val childAssetPath = "$assetPath/$name"
            val outFile = File(outDir, name)
            val childAssets = context.assets.list(childAssetPath)
            if (childAssets != null && childAssets.isNotEmpty()) {
                if (!outFile.exists()) outFile.mkdirs()
                copyAssetDir(context, childAssetPath, outFile)
            } else {
                if (!outFile.exists()) {
                    context.assets.open(childAssetPath).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun deleteRecursively(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteRecursively(child)
            } else {
                child.delete()
            }
        }
        dir.delete()
    }
}
