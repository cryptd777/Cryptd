package com.cryptd.vm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

class VncActivity : ComponentActivity() {
    private var vncView: VncView? = null
    private var hasFrame = false
    private var autoGfx = false
    private var gfxIndex = 0
    private val gfxOrder = listOf("virtio", "virtio-device", "ramfb")
    private val handler = Handler(Looper.getMainLooper())
    private var retrying = false
    private var isoUri: String = ""
    private var isoFallbackPath: String = ""
    private var diskPath: String = ""
    private var isDisk = false
    private var ramMb = 1024
    private var cpuCores = 2
    private var useKvm = false
    private var logPath: String = ""
    private var frameTimeoutMs: Long = 20000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 5901)
        isoUri = intent.getStringExtra(EXTRA_ISO_URI) ?: ""
        isoFallbackPath = intent.getStringExtra(EXTRA_ISO_PATH) ?: ""
        diskPath = intent.getStringExtra(EXTRA_DISK_PATH) ?: ""
        isDisk = intent.getBooleanExtra(EXTRA_IS_DISK, false)
        ramMb = intent.getIntExtra(EXTRA_RAM_MB, 1024)
        cpuCores = intent.getIntExtra(EXTRA_CPU, 2)
        useKvm = intent.getBooleanExtra(EXTRA_KVM, false)
        logPath = intent.getStringExtra(EXTRA_LOG_PATH) ?: ""
        autoGfx = intent.getBooleanExtra(EXTRA_AUTO_GFX, false)
        val initialGfx = intent.getStringExtra(EXTRA_GFX) ?: "virtio"
        gfxIndex = gfxOrder.indexOf(initialGfx).let { if (it >= 0) it else 0 }
        val timeoutSec = intent.getIntExtra(EXTRA_FRAME_TIMEOUT_SEC, 20)
        frameTimeoutMs = (timeoutSec.coerceIn(5, 120) * 1000L)

        vncView = VncView(this)
        vncView?.onFirstFrame = {
            hasFrame = true
        }
        vncView?.onDisconnected = {
            handleDisconnect()
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this)
        root.addView(vncView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val logsButton = Button(this).apply {
            text = "Logs"
            setOnClickListener {
                startActivity(android.content.Intent(this@VncActivity, LogActivity::class.java))
            }
        }
        val retryButton = Button(this).apply {
            text = "Retry GPU"
            setOnClickListener {
                tryNextGfx()
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.topMargin = 16
        lp.marginEnd = 16
        root.addView(logsButton, lp)
        val lpRetry = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpRetry.gravity = Gravity.TOP or Gravity.END
        lpRetry.topMargin = 16
        lpRetry.marginEnd = 16
        lpRetry.topMargin += 100
        root.addView(retryButton, lpRetry)

        setContentView(root)
        vncView?.connect(host, port)
        if (autoGfx) {
            scheduleFrameTimeout()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vncView?.disconnect()
        if (isFinishing) {
            NativeBridge.stopVm()
        }
    }

    private fun scheduleFrameTimeout() {
        handler.postDelayed({
            if (!hasFrame && autoGfx && !retrying) {
                tryNextGfx()
            }
        }, frameTimeoutMs)
    }

    private fun handleDisconnect() {
        if (autoGfx && !hasFrame && !retrying) {
            tryNextGfx()
            return
        }
        android.widget.Toast.makeText(this, "VNC disconnected", android.widget.Toast.LENGTH_SHORT).show()
        VmLogStore.append(this, "VNC disconnected\n")
        finish()
    }

    private fun tryNextGfx() {
        if (gfxIndex + 1 >= gfxOrder.size) {
            android.widget.Toast.makeText(this, "All GPU options failed.", android.widget.Toast.LENGTH_LONG).show()
            VmLogStore.append(this, "All GPU options failed.\n")
            finish()
            return
        }
        retrying = true
        gfxIndex += 1
        val nextGfx = gfxOrder[gfxIndex]
        VmLogStore.append(this, "Retrying with GFX: $nextGfx\n")
        restartVm(nextGfx)
    }

    private fun restartVm(gfx: String) {
        vncView?.disconnect()
        NativeBridge.stopVm()
        hasFrame = false
        val bundle = QemuInstaller.ensureQemuBundle(this)
        if (bundle == null) {
            VmLogStore.append(this, "Missing QEMU bundle in assets\n")
            finish()
            return
        }
        if (!QemuInstaller.verifyRequiredLibs(this)) {
            VmLogStore.append(this, "QEMU deps missing. Reinstall app.\n")
            finish()
            return
        }

        var isoPath = ""
        var isoFd = -1
        var diskFd = -1
        var diskPathLocal = diskPath

        if (isDisk) {
            if (diskPathLocal.startsWith("/proc/self/fd/") || diskPathLocal.isEmpty()) {
                val fd = VmFiles.openDocumentFd(this, android.net.Uri.parse(isoUri), "rw")
                if (fd == null) {
                    val copied = VmFiles.copyToPrivateStorage(this, android.net.Uri.parse(isoUri))
                    if (copied == null) {
                        VmLogStore.append(this, "Failed to open disk image for retry\n")
                        finish()
                        return
                    }
                    diskPathLocal = copied
                } else {
                    diskFd = fd
                    diskPathLocal = "/proc/self/fd/$fd"
                }
            }
        } else {
            if (isoFallbackPath.isNotEmpty()) {
                isoPath = isoFallbackPath
            } else {
                val fd = VmFiles.openDocumentFd(this, android.net.Uri.parse(isoUri), "r")
                if (fd == null) {
                    val copied = VmFiles.copyToPrivateStorage(this, android.net.Uri.parse(isoUri))
                    if (copied == null) {
                        VmLogStore.append(this, "Failed to open ISO for retry\n")
                        finish()
                        return
                    }
                    isoPath = copied
                } else {
                    isoFd = fd
                    isoPath = "/proc/self/fd/$fd"
                }
            }
        }

        val rc = NativeBridge.startVm(
            isoPath,
            isoFd,
            bundle.qemuPath,
            bundle.libDir,
            bundle.shareDir,
            diskPathLocal,
            diskFd,
            logPath,
            ramMb,
            cpuCores,
            gfx,
            intent.getIntExtra(EXTRA_PORT, 5901),
            useKvm
        )
        if (rc != 0) {
            VmLogStore.append(this, "Retry start failed (code $rc)\n")
            finish()
            return
        }
        vncView?.connect(intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1", intent.getIntExtra(EXTRA_PORT, 5901))
        retrying = false
        scheduleFrameTimeout()
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_ISO_URI = "iso_uri"
        const val EXTRA_ISO_PATH = "iso_path"
        const val EXTRA_IS_DISK = "is_disk"
        const val EXTRA_DISK_PATH = "disk_path"
        const val EXTRA_RAM_MB = "ram_mb"
        const val EXTRA_CPU = "cpu"
        const val EXTRA_KVM = "kvm"
        const val EXTRA_LOG_PATH = "log_path"
        const val EXTRA_GFX = "gfx"
        const val EXTRA_AUTO_GFX = "auto_gfx"
        const val EXTRA_FRAME_TIMEOUT_SEC = "frame_timeout_sec"
    }
}
