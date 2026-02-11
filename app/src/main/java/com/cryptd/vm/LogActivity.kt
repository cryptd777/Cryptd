package com.cryptd.vm

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class LogActivity : ComponentActivity() {
    private var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val copyButton = Button(this).apply {
            text = "Copy Logs"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val data = android.content.ClipData.newPlainText("VM Logs", textView?.text ?: "")
                cm.setPrimaryClip(data)
                android.widget.Toast.makeText(this@LogActivity, "Logs copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val scroll = ScrollView(this)
        val text = TextView(this)
        text.textSize = 12f
        text.text = VmLogStore.readAll(this)
        textView = text
        scroll.addView(text)

        root.addView(copyButton)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        textView?.text = VmLogStore.readAll(this)
    }
}
