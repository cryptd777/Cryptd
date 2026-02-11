package com.cryptd.vm

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
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
            VmLogStore.append(this, "VNC disconnected\n")
            finish()
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
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.topMargin = 16
        lp.marginEnd = 16
        root.addView(logsButton, lp)

        setContentView(root)
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
