package com.cryptd.vm

object NativeBridge {
    init {
        System.loadLibrary("vmbridge")
    }

    external fun startVm(
        isoPath: String,
        isoFd: Int,
        qemuPath: String,
        libDir: String,
        shareDir: String,
        diskPath: String,
        diskFd: Int,
        logPath: String,
        ramMb: Int,
        cpuCores: Int,
        gfx: String,
        vncPort: Int,
        useKvm: Boolean
    ): Int

    external fun stopVm()

    external fun sendMouseEvent(dx: Int, dy: Int, buttons: Int)
    external fun sendKeyEvent(keyCode: Int, isDown: Boolean)
}
