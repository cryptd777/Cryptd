package com.cryptd.vm

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class LogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val text = TextView(this)
        text.textSize = 12f
        text.text = VmLogStore.readAll(this)
        scroll.addView(text)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val scroll = findViewById<ScrollView>(android.R.id.content)
        val text = (scroll.getChildAt(0) as? TextView) ?: return
        text.text = VmLogStore.readAll(this)
    }
}
