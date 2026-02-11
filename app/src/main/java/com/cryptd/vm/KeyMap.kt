package com.cryptd.vm

import android.view.KeyEvent

object KeyMap {
    fun androidToKeysym(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> 0xFF0D
            KeyEvent.KEYCODE_DEL -> 0xFF08
            KeyEvent.KEYCODE_TAB -> 0xFF09
            KeyEvent.KEYCODE_ESCAPE -> 0xFF1B
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xFFE1
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0xFFE3
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0xFFE9
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> 0xFFEB
            KeyEvent.KEYCODE_DPAD_UP -> 0xFF52
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xFF54
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xFF51
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xFF53
            KeyEvent.KEYCODE_MOVE_HOME -> 0xFF50
            KeyEvent.KEYCODE_MOVE_END -> 0xFF57
            KeyEvent.KEYCODE_PAGE_UP -> 0xFF55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xFF56
            KeyEvent.KEYCODE_INSERT -> 0xFF63
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xFFFF
            KeyEvent.KEYCODE_SPACE -> 0x0020
            KeyEvent.KEYCODE_MINUS -> 0x002D
            KeyEvent.KEYCODE_EQUALS -> 0x003D
            KeyEvent.KEYCODE_LEFT_BRACKET -> 0x005B
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x005D
            KeyEvent.KEYCODE_BACKSLASH -> 0x005C
            KeyEvent.KEYCODE_SEMICOLON -> 0x003B
            KeyEvent.KEYCODE_APOSTROPHE -> 0x0027
            KeyEvent.KEYCODE_COMMA -> 0x002C
            KeyEvent.KEYCODE_PERIOD -> 0x002E
            KeyEvent.KEYCODE_SLASH -> 0x002F
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                0x0061 + (keyCode - KeyEvent.KEYCODE_A)
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                0x0030 + (keyCode - KeyEvent.KEYCODE_0)
            KeyEvent.KEYCODE_F1 -> 0xFFBE
            KeyEvent.KEYCODE_F2 -> 0xFFBF
            KeyEvent.KEYCODE_F3 -> 0xFFC0
            KeyEvent.KEYCODE_F4 -> 0xFFC1
            KeyEvent.KEYCODE_F5 -> 0xFFC2
            KeyEvent.KEYCODE_F6 -> 0xFFC3
            KeyEvent.KEYCODE_F7 -> 0xFFC4
            KeyEvent.KEYCODE_F8 -> 0xFFC5
            KeyEvent.KEYCODE_F9 -> 0xFFC6
            KeyEvent.KEYCODE_F10 -> 0xFFC7
            KeyEvent.KEYCODE_F11 -> 0xFFC8
            KeyEvent.KEYCODE_F12 -> 0xFFC9
            else -> 0
        }
    }
}
