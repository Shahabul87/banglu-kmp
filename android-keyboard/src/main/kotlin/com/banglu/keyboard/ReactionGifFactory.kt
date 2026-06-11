package com.banglu.keyboard

import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object ReactionGifFactory {
    private const val CANVAS = 72
    private const val SCALE = 3
    private const val SIZE = CANVAS * SCALE
    private const val MIN_CODE_SIZE = 3
    private const val CLEAR_CODE = 1 shl MIN_CODE_SIZE
    private const val END_CODE = CLEAR_CODE + 1
    private const val CODE_SIZE = MIN_CODE_SIZE + 1

    fun build(assetName: String): ByteArray {
        val safeAsset = assetName.ifBlank { "laugh" }
        val frame = drawFrame(safeAsset, phase = 0)
        return ByteArrayOutputStream().apply {
            writeAscii("GIF89a")
            writeShort(SIZE)
            writeShort(SIZE)
            writeByte(0x80 or 0x70 or 0x02)
            writeByte(0)
            writeByte(0)
            palette().forEach { color ->
                writeByte((color shr 16) and 0xff)
                writeByte((color shr 8) and 0xff)
                writeByte(color and 0xff)
            }
            writeLoopExtension()
            writeFrame(frame, delayCs = 100)
            writeByte(0x3b)
        }.toByteArray()
    }

    private fun palette(): IntArray = intArrayOf(
        0xffffff,
        0x202124,
        0xffd54f,
        0xe53935,
        0x34a853,
        0x4285f4,
        0xfb8c00,
        0xec407a
    )

    private fun drawFrame(assetName: String, phase: Int): ByteArray {
        val pixels = ByteArray(SIZE * SIZE)
        when (assetName) {
            "thanks" -> drawThanks(pixels, phase)
            "love" -> drawHeart(pixels, phase)
            "angry" -> drawFace(pixels, phase, Mood.ANGRY)
            "sad" -> drawFace(pixels, phase, Mood.SAD)
            "congrats" -> drawConfetti(pixels, phase)
            "birthday" -> drawCake(pixels, phase)
            "ok" -> drawCheck(pixels, phase)
            "wow" -> drawFace(pixels, phase, Mood.WOW)
            "fire" -> drawFire(pixels, phase)
            else -> drawFace(pixels, phase, Mood.LAUGH)
        }
        return pixels
    }

    private enum class Mood { LAUGH, ANGRY, SAD, WOW }

    private fun drawFace(pixels: ByteArray, phase: Int, mood: Mood) {
        val dy = if (phase == 0) 0 else -2
        val faceColor = if (mood == Mood.ANGRY) 3 else 2
        fillCircle(pixels, 36, 34 + dy, 26, faceColor)
        when (mood) {
            Mood.LAUGH -> {
                fillCircle(pixels, 26, 27 + dy, 4, 1)
                fillCircle(pixels, 46, 27 + dy, 4, 1)
                fillCircle(pixels, 36, 44 + dy, 12, 1)
                rect(pixels, 24, 39 + dy, 48, 45 + dy, faceColor)
                fillCircle(pixels, 55, 42 + dy, if (phase == 0) 5 else 6, 5)
            }
            Mood.ANGRY -> {
                line(pixels, 22, 24 + dy, 33, 30 + dy, 1)
                line(pixels, 50, 24 + dy, 39, 30 + dy, 1)
                fillCircle(pixels, 29, 35 + dy, 4, 1)
                fillCircle(pixels, 43, 35 + dy, 4, 1)
                line(pixels, 27, 53 + dy, 47, 48 + dy, 1)
            }
            Mood.SAD -> {
                fillCircle(pixels, 28, 31 + dy, 4, 1)
                fillCircle(pixels, 44, 31 + dy, 4, 1)
                arcSadMouth(pixels, dy)
                fillCircle(pixels, 53, 43 + dy, if (phase == 0) 5 else 6, 5)
            }
            Mood.WOW -> {
                fillCircle(pixels, 27, 28 + dy, 5, 1)
                fillCircle(pixels, 45, 28 + dy, 5, 1)
                fillCircle(pixels, 36, 47 + dy, if (phase == 0) 8 else 10, 1)
                fillCircle(pixels, 55, 18, 5, 7)
            }
        }
    }

    private fun drawThanks(pixels: ByteArray, phase: Int) {
        val spread = if (phase == 0) 0 else 2
        roundBlock(pixels, 18 - spread, 28, 34 - spread, 58, 6)
        roundBlock(pixels, 38 + spread, 28, 54 + spread, 58, 6)
        fillCircle(pixels, 26 - spread, 24, 6, 2)
        fillCircle(pixels, 46 + spread, 24, 6, 2)
        line(pixels, 34 - spread, 29, 38 + spread, 29, 1)
        line(pixels, 34 - spread, 58, 38 + spread, 58, 1)
    }

    private fun drawHeart(pixels: ByteArray, phase: Int) {
        val scale = if (phase == 0) 1.0 else 1.08
        for (y in 0 until CANVAS) {
            for (x in 0 until CANVAS) {
                val nx = (x - 36) / (22.0 * scale)
                val ny = (y - 40) / (20.0 * scale)
                val value = (nx * nx + ny * ny - 1).pow(3.0) - nx * nx * ny.pow(3.0)
                if (value <= 0) set(pixels, x, y, 7)
            }
        }
        fillCircle(pixels, 54, 21, 5, 3)
    }

    private fun drawConfetti(pixels: ByteArray, phase: Int) {
        rect(pixels, 30, 38, 42, 62, 7)
        fillCircle(pixels, 36, 45, 12, 6)
        val pieces = listOf(
            Triple(12, 15, 3), Triple(25, 9, 5), Triple(37, 13, 4), Triple(56, 15, 7),
            Triple(14, 38, 2), Triple(58, 38, 5), Triple(35, 6, 3), Triple(47, 24, 6)
        )
        pieces.forEachIndexed { index, (x, y, color) ->
            val drift = if ((index + phase) % 2 == 0) 2 else -2
            rect(pixels, x + drift, y + phase, x + drift + 4, y + phase + 4, color)
        }
    }

    private fun drawCake(pixels: ByteArray, phase: Int) {
        rect(pixels, 16, 40, 56, 59, 7)
        rect(pixels, 21, 31, 51, 41, 0)
        rect(pixels, 21, 35, 51, 42, 7)
        rect(pixels, 27, 22, 30, 33, 6)
        rect(pixels, 42, 22, 45, 33, 6)
        fillCircle(pixels, 28, if (phase == 0) 19 else 17, 3, 2)
        fillCircle(pixels, 43, if (phase == 0) 19 else 17, 3, 2)
        rect(pixels, 15, 59, 57, 62, 1)
    }

    private fun drawCheck(pixels: ByteArray, phase: Int) {
        val inset = if (phase == 0) 8 else 6
        roundBlock(pixels, inset, inset, CANVAS - inset, CANVAS - inset, 4)
        repeat(4) { offset ->
            line(pixels, 20, 40 + offset, 32, 52 + offset, 0)
            line(pixels, 32, 52 + offset, 54, 26 + offset, 0)
        }
    }

    private fun drawFire(pixels: ByteArray, phase: Int) {
        val lift = if (phase == 0) 0 else -3
        for (y in 14 until 62) {
            val half = ((62 - y) * 0.42).toInt()
            val center = 36 + if ((y + phase) % 8 < 4) 1 else -1
            rect(pixels, center - half, y + lift, center + half, y + lift, 6)
        }
        for (y in 29 until 63) {
            val half = ((63 - y) * 0.28).toInt()
            rect(pixels, 36 - half, y + lift, 36 + half, y + lift, 3)
        }
        fillCircle(pixels, 36, 55, 13, 6)
    }

    private fun arcSadMouth(pixels: ByteArray, dy: Int) {
        for (x in 25..47) {
            val distance = abs(x - 36)
            val y = 55 - sqrt((11 * 11 - distance * distance).coerceAtLeast(0).toDouble()).toInt()
            set(pixels, x, y + dy, 1)
            set(pixels, x, y + dy + 1, 1)
        }
    }

    private fun roundBlock(pixels: ByteArray, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        rect(pixels, left + 5, top, right - 5, bottom, color)
        rect(pixels, left, top + 5, right, bottom - 5, color)
        fillCircle(pixels, left + 5, top + 5, 5, color)
        fillCircle(pixels, right - 5, top + 5, 5, color)
        fillCircle(pixels, left + 5, bottom - 5, 5, color)
        fillCircle(pixels, right - 5, bottom - 5, 5, color)
    }

    private fun fillCircle(pixels: ByteArray, cx: Int, cy: Int, radius: Int, color: Int) {
        val r2 = radius * radius
        for (y in cy - radius..cy + radius) {
            for (x in cx - radius..cx + radius) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) set(pixels, x, y, color)
            }
        }
    }

    private fun rect(pixels: ByteArray, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top..bottom) {
            for (x in left..right) set(pixels, x, y, color)
        }
    }

    private fun line(pixels: ByteArray, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(pixels, x, y, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun set(pixels: ByteArray, x: Int, y: Int, color: Int) {
        if (x in 0 until CANVAS && y in 0 until CANVAS) {
            val startX = x * SCALE
            val startY = y * SCALE
            for (py in startY until startY + SCALE) {
                for (px in startX until startX + SCALE) {
                    pixels[py * SIZE + px] = color.toByte()
                }
            }
        }
    }

    private fun ByteArrayOutputStream.writeLoopExtension() {
        writeByte(0x21)
        writeByte(0xff)
        writeByte(11)
        writeAscii("NETSCAPE2.0")
        writeByte(3)
        writeByte(1)
        writeShort(0)
        writeByte(0)
    }

    private fun ByteArrayOutputStream.writeFrame(pixels: ByteArray, delayCs: Int) {
        writeByte(0x21)
        writeByte(0xf9)
        writeByte(4)
        writeByte(0x09)
        writeShort(delayCs)
        writeByte(0)
        writeByte(0)
        writeByte(0x2c)
        writeShort(0)
        writeShort(0)
        writeShort(SIZE)
        writeShort(SIZE)
        writeByte(0)
        writeByte(MIN_CODE_SIZE)
        writeSubBlocks(lzwData(pixels))
    }

    private fun lzwData(pixels: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var current = 0
        var bitCount = 0
        fun emit(code: Int) {
            current = current or (code shl bitCount)
            bitCount += CODE_SIZE
            while (bitCount >= 8) {
                out.writeByte(current and 0xff)
                current = current ushr 8
                bitCount -= 8
            }
        }
        pixels.forEach { pixel ->
            emit(CLEAR_CODE)
            emit(pixel.toInt() and 0xff)
        }
        emit(END_CODE)
        if (bitCount > 0) out.writeByte(current and 0xff)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val length = minOf(255, data.size - offset)
            writeByte(length)
            write(data, offset, length)
            offset += length
        }
        writeByte(0)
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeByte(value: Int) {
        write(value and 0xff)
    }
}
