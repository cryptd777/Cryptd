package com.cryptd.vm

import android.content.Context
import java.io.File

object VmLogStore {
    fun logFile(context: Context): File = File(context.filesDir, "vm-qemu.log")

    fun readAll(context: Context): String {
        val f = logFile(context)
        return if (f.exists()) f.readText() else "(no logs yet)"
    }

    fun clear(context: Context) {
        val f = logFile(context)
        if (f.exists()) f.writeText("")
    }

    fun append(context: Context, text: String) {
        val f = logFile(context)
        f.appendText(text)
    }
}
