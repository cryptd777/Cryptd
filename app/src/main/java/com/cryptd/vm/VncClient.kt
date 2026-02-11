package com.cryptd.vm

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class VncClient(
    private val host: String,
    private val port: Int,
    private val listener: Listener
) : Thread("VncClient") {

    interface Listener {
        fun onFramebufferResize(width: Int, height: Int)
        fun onFramebufferUpdate(x: Int, y: Int, w: Int, h: Int, pixels: IntArray)
        fun onDisconnected()
    }

    private val running = AtomicBoolean(false)
    private lateinit var socket: Socket
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private var fbWidth: Int = 0
    private var fbHeight: Int = 0
    @Volatile private var connected: Boolean = false

    @Volatile
    var lastButtons: Int = 0
        private set

    fun stopClient() {
        running.set(false)
        try {
            if (::socket.isInitialized) {
                socket.close()
            }
        } catch (_: Exception) {}
    }

    override fun run() {
        running.set(true)
        try {
            val deadline = System.currentTimeMillis() + 30000
            var didConnect = false
            while (running.get() && System.currentTimeMillis() < deadline) {
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 1500)
                    socket.tcpNoDelay = true
                    didConnect = true
                    break
                } catch (_: Exception) {
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {}
                }
            }
            if (!didConnect) {
                throw IllegalStateException("VNC connect timeout")
            }
            input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            connected = true

            handshake()
            requestFullUpdate()

            while (running.get()) {
                val msgType = input.readUnsignedByte()
                when (msgType) {
                    0 -> handleFramebufferUpdate()
                    2 -> {}
                    3 -> skipServerCutText()
                    else -> {}
                }
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            connected = false
            listener.onDisconnected()
            running.set(false)
        }
    }

    fun shutdown() = stopClient()

    fun sendPointerEvent(x: Int, y: Int, buttons: Int) {
        lastButtons = buttons
        if (!connected) return
        try {
            synchronized(output) {
                output.writeByte(5)
                output.writeByte(buttons)
                output.writeShort(x)
                output.writeShort(y)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    fun sendKeyEvent(androidKeyCode: Int, isDown: Boolean) {
        val keysym = KeyMap.androidToKeysym(androidKeyCode)
        if (keysym == 0) return
        sendKeysym(keysym, isDown)
    }

    fun sendKeysym(keysym: Int, isDown: Boolean) {
        if (!connected) return
        try {
            synchronized(output) {
                output.writeByte(4)
                output.writeByte(if (isDown) 1 else 0)
                output.writeShort(0)
                output.writeInt(keysym)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun handshake() {
        val serverVersion = ByteArray(12)
        input.readFully(serverVersion)
        val serverVerStr = String(serverVersion)
        val clientVersion = "RFB 003.008\n".toByteArray()
        output.write(clientVersion)
        output.flush()

        if (serverVerStr.startsWith("RFB 003.003")) {
            val secType = input.readInt()
            if (secType != 1) {
                throw IllegalStateException("Server does not support None security")
            }
        } else {
            val secTypesCount = input.readUnsignedByte()
            val secTypes = ByteArray(secTypesCount)
            input.readFully(secTypes)
            if (!secTypes.contains(1.toByte())) {
                throw IllegalStateException("Server does not support None security")
            }
            output.writeByte(1)
            output.flush()

            val secResult = input.readInt()
            if (secResult != 0) {
                throw IllegalStateException("Security handshake failed")
            }
        }

        output.writeByte(1)
        output.flush()

        val width = input.readUnsignedShort()
        val height = input.readUnsignedShort()
        val pixelFormat = ByteArray(16)
        input.readFully(pixelFormat)
        val nameLength = input.readInt()
        if (nameLength > 0) {
            input.skipBytes(nameLength)
        }

        fbWidth = width
        fbHeight = height
        listener.onFramebufferResize(width, height)

        // SetPixelFormat: 32bpp, 24 depth, little endian, true color
        output.writeByte(0)
        output.writeByte(0)
        output.writeShort(0)
        output.writeByte(32)
        output.writeByte(24)
        output.writeByte(0)
        output.writeByte(1)
        output.writeShort(255)
        output.writeShort(255)
        output.writeShort(255)
        output.writeByte(16)
        output.writeByte(8)
        output.writeByte(0)
        output.writeByte(0)
        output.flush()

        // SetEncodings: Raw only
        output.writeByte(2)
        output.writeByte(0)
        output.writeShort(1)
        output.writeInt(0)
        output.flush()
    }

    private fun requestFullUpdate() {
        if (!connected) return
        synchronized(output) {
            output.writeByte(3)
            output.writeByte(0)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(fbWidth)
            output.writeShort(fbHeight)
            output.flush()
        }
    }

    private fun handleFramebufferUpdate() {
        input.readUnsignedByte() // padding
        val rects = input.readUnsignedShort()
        for (i in 0 until rects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            val encoding = input.readInt()
            if (encoding != 0) {
                skipEncoding(encoding, w, h)
                continue
            }

            val bytesPerPixel = 4
            val data = ByteArray(w * h * bytesPerPixel)
            input.readFully(data)
            val pixels = IntArray(w * h)
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            for (p in 0 until pixels.size) {
                val v = bb.int
                val r = (v shr 16) and 0xFF
                val g = (v shr 8) and 0xFF
                val b = v and 0xFF
                pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            listener.onFramebufferUpdate(x, y, w, h, pixels)
        }

        if (!connected) return
        synchronized(output) {
            output.writeByte(3)
            output.writeByte(1)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(fbWidth)
            output.writeShort(fbHeight)
            output.flush()
        }
    }

    private fun skipEncoding(encoding: Int, w: Int, h: Int) {
        // Unknown encoding: skip by requesting full update again.
        // For now, just discard using a full update request.
        requestFullUpdate()
    }

    private fun skipServerCutText() {
        input.readInt() // padding
        val length = input.readInt()
        if (length > 0) {
            input.skipBytes(length)
        }
    }
}
