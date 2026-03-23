package com.banglu.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class BangluKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onKeyPress: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onShift: (() -> Unit)? = null

    private var isShifted = false
    private var pressedKey: KeyInfo? = null
    private var actionHandled = false

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2C")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A4A4A")
        style = Paint.Style.FILL
    }

    private val rows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "BACK"),
        listOf("123", "SPACE", ".", "ENTER")
    )

    private var keys = mutableListOf<KeyInfo>()

    data class KeyInfo(
        val label: String,
        val rect: RectF,
        val row: Int,
        val col: Int
    )

    /** Extra bottom padding to clear the navigation bar. Set by IME service. */
    var bottomInset: Int = 0
        set(value) { field = value; requestLayout() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowHeightPx = (52 * resources.displayMetrics.density).toInt()
        val keysHeight = rowHeightPx * 4
        // Add bottom inset so the keyboard draws above the nav bar
        setMeasuredDimension(width, keysHeight + bottomInset)
        buildKeys(width.toFloat(), keysHeight.toFloat())
    }

    private fun buildKeys(totalWidth: Float, totalHeight: Float) {
        keys.clear()
        val rowHeight = totalHeight / rows.size
        val padding = 3f

        for ((rowIdx, row) in rows.withIndex()) {
            val y = rowIdx * rowHeight
            when (rowIdx) {
                0 -> {
                    val keyWidth = totalWidth / 10
                    for ((colIdx, key) in row.withIndex()) {
                        keys.add(KeyInfo(key, RectF(
                            colIdx * keyWidth + padding, y + padding,
                            (colIdx + 1) * keyWidth - padding, y + rowHeight - padding
                        ), rowIdx, colIdx))
                    }
                }
                1 -> {
                    val keyWidth = totalWidth / 10
                    val offset = keyWidth * 0.5f
                    for ((colIdx, key) in row.withIndex()) {
                        keys.add(KeyInfo(key, RectF(
                            offset + colIdx * keyWidth + padding, y + padding,
                            offset + (colIdx + 1) * keyWidth - padding, y + rowHeight - padding
                        ), rowIdx, colIdx))
                    }
                }
                2 -> {
                    val modWidth = totalWidth * 0.15f
                    val letterKeyWidth = (totalWidth - 2 * modWidth) / 7
                    var x = 0f
                    for ((colIdx, key) in row.withIndex()) {
                        val w = when (key) { "SHIFT", "BACK" -> modWidth; else -> letterKeyWidth }
                        keys.add(KeyInfo(key, RectF(
                            x + padding, y + padding, x + w - padding, y + rowHeight - padding
                        ), rowIdx, colIdx))
                        x += w
                    }
                }
                3 -> {
                    val widths = listOf(0.15f, 0.55f, 0.10f, 0.20f)
                    var x = 0f
                    for ((colIdx, key) in row.withIndex()) {
                        val w = totalWidth * widths[colIdx]
                        keys.add(KeyInfo(key, RectF(
                            x + padding, y + padding, x + w - padding, y + rowHeight - padding
                        ), rowIdx, colIdx))
                        x += w
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1B1B1B"))

        for (key in keys) {
            val paint = if (key == pressedKey) pressedPaint else keyPaint
            canvas.drawRoundRect(key.rect, 12f, 12f, paint)

            val displayLabel = when (key.label) {
                "SHIFT" -> if (isShifted) "\u21E7" else "\u21E7"
                "BACK" -> "\u232B"
                "SPACE" -> ""  // No text on spacebar — just the bar
                "ENTER" -> "\u21B5"
                "123" -> "123"
                else -> if (isShifted) key.label.uppercase() else key.label
            }

            val fontSize = when (key.label) {
                "SPACE" -> 36f
                "SHIFT", "BACK", "ENTER", "123" -> 40f
                else -> 48f
            }
            textPaint.textSize = fontSize
            val textY = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(displayLabel, key.rect.centerX(), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = findKey(event.x, event.y)
                actionHandled = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Track finger movement — update pressed key visual
                val currentKey = findKey(event.x, event.y)
                if (currentKey != pressedKey) {
                    pressedKey = currentKey
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Fire the key that the finger is on when released
                val releasedKey = findKey(event.x, event.y)
                if (releasedKey != null && !actionHandled) {
                    hapticFeedback()
                    fireKey(releasedKey)
                }
                pressedKey = null
                actionHandled = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                actionHandled = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun fireKey(key: KeyInfo) {
        when (key.label) {
            "SHIFT" -> onShift?.invoke()
            "BACK" -> onBackspace?.invoke()
            "SPACE" -> onSpace?.invoke()
            "ENTER" -> onEnter?.invoke()
            "123" -> {} // TODO: number/symbol layer
            else -> {
                val char = if (isShifted) key.label.uppercase()[0] else key.label[0]
                onKeyPress?.invoke(char)
                // Auto-unshift after typing a letter (like real keyboards)
                if (isShifted) {
                    isShifted = false
                    invalidate()
                }
            }
        }
    }

    fun toggleShift() {
        isShifted = !isShifted
        invalidate()
    }

    private fun findKey(x: Float, y: Float): KeyInfo? {
        return keys.find { it.rect.contains(x, y) }
    }

    private fun hapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }
}
