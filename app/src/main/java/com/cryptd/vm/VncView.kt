package com.cryptd.vm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class VncView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var framebuffer: Bitmap? = null
    private var client: VncClient? = null
    private var buttonMask: Int = 0
    private var shiftDown = false
    private var ctrlDown = false
    private var altDown = false
    private var metaDown = false
    var onDisconnected: (() -> Unit)? = null
    private var inputEnabled = false

    fun connect(host: String, port: Int) {
        client = VncClient(host, port, object : VncClient.Listener {
            override fun onFramebufferResize(width: Int, height: Int) {
                framebuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                inputEnabled = true
                invalidate()
            }

            override fun onFramebufferUpdate(x: Int, y: Int, w: Int, h: Int, pixels: IntArray) {
                val fb = framebuffer ?: return
                if (x + w > fb.width || y + h > fb.height) return
                fb.setPixels(pixels, 0, w, x, y, w, h)
                postInvalidate()
            }

            override fun onDisconnected() {
                post { onDisconnected?.invoke() }
            }
        })
        client?.start()
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun disconnect() {
        client?.stop()
        client = null
        inputEnabled = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fb = framebuffer ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / fb.width, viewH / fb.height)
        val drawW = fb.width * scale
        val drawH = fb.height * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(fb, 0f, 0f, paint)
        canvas.restore()
    }

    private fun mapToFramebuffer(x: Float, y: Float): Pair<Int, Int> {
        val fb = framebuffer ?: return 0 to 0
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / fb.width, viewH / fb.height)
        val drawW = fb.width * scale
        val drawH = fb.height * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f
        val fx = ((x - left) / scale).toInt()
        val fy = ((y - top) / scale).toInt()
        return max(0, min(fb.width - 1, fx)) to max(0, min(fb.height - 1, fy))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return true
        val (fx, fy) = mapToFramebuffer(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> buttonMask = 1
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    buttonMask = 2
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> buttonMask = 0
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    buttonMask = 0
                }
            }
        }
        client?.sendPointerEvent(fx, fy, buttonMask)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputEnabled) return true
        if (isModifier(keyCode)) {
            client?.sendKeyEvent(keyCode, true)
            updateModifierState(keyCode, true)
            return true
        }
        syncModifiers(event)
        client?.sendKeyEvent(keyCode, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputEnabled) return true
        if (isModifier(keyCode)) {
            client?.sendKeyEvent(keyCode, false)
            updateModifierState(keyCode, false)
            return true
        }
        client?.sendKeyEvent(keyCode, false)
        syncModifiers(event)
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                for (ch in text) {
                    val code = ch.code
                    val keysym = if (code <= 0xFF) code else (0x01000000 or code)
                    client?.sendKeysym(keysym, true)
                    client?.sendKeysym(keysym, false)
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    client?.sendKeyEvent(KeyEvent.KEYCODE_DEL, true)
                    client?.sendKeyEvent(KeyEvent.KEYCODE_DEL, false)
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    private fun isModifier(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_META_LEFT ||
            keyCode == KeyEvent.KEYCODE_META_RIGHT
    }

    private fun updateModifierState(keyCode: Int, down: Boolean) {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftDown = down
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrlDown = down
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> altDown = down
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> metaDown = down
        }
    }

    private fun syncModifiers(event: KeyEvent) {
        val wantShift = event.isShiftPressed
        val wantCtrl = event.isCtrlPressed
        val wantAlt = event.isAltPressed
        val wantMeta = event.isMetaPressed

        if (wantShift != shiftDown) {
            client?.sendKeyEvent(KeyEvent.KEYCODE_SHIFT_LEFT, wantShift)
            shiftDown = wantShift
        }
        if (wantCtrl != ctrlDown) {
            client?.sendKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, wantCtrl)
            ctrlDown = wantCtrl
        }
        if (wantAlt != altDown) {
            client?.sendKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, wantAlt)
            altDown = wantAlt
        }
        if (wantMeta != metaDown) {
            client?.sendKeyEvent(KeyEvent.KEYCODE_META_LEFT, wantMeta)
            metaDown = wantMeta
        }
    }
}
