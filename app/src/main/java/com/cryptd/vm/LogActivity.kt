package com.cryptd.vm

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class LogActivity : ComponentActivity() {
    private var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val text = TextView(this)
        text.textSize = 12f
        text.text = VmLogStore.readAll(this)
        textView = text
        scroll.addView(text)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        textView?.text = VmLogStore.readAll(this)
    }
}
