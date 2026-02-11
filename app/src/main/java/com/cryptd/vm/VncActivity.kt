package com.cryptd.vm

import android.os.Bundle
import androidx.activity.ComponentActivity

class VncActivity : ComponentActivity() {
    private var vncView: VncView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 5901)

        vncView = VncView(this)
        vncView?.onDisconnected = {
            android.widget.Toast.makeText(this, "VNC disconnected", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
        setContentView(vncView)
        vncView?.connect(host, port)
    }

    override fun onDestroy() {
        super.onDestroy()
        vncView?.disconnect()
        if (isFinishing) {
            NativeBridge.stopVm()
        }
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }
}
