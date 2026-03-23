package com.banglu.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.banglu.engine.types.SmartSuggestion

class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onSuggestionClick: ((SmartSuggestion) -> Unit)? = null

    private var suggestions = listOf<SmartSuggestion>()
    private var scrollOffset = 0f
    private var lastTouchX = 0f
    private var isDragging = false
    private var totalContentWidth = 0f

    private data class SuggestionSlot(
        val suggestion: SmartSuggestion,
        val rect: RectF,
        val textWidth: Float
    )
    private var slots = listOf<SuggestionSlot>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#444444")
        strokeWidth = 1f
    }

    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#3D5AFE")
        style = Paint.Style.FILL
    }

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val density = context.resources.displayMetrics.density
    private val chipPadding = (12 * density)   // Horizontal padding inside each chip
    private val chipGap = (6 * density)        // Gap between chips
    private val chipRadius = (16 * density)    // Rounded corner radius

    fun showSuggestions(newSuggestions: List<SmartSuggestion>) {
        suggestions = newSuggestions.take(8)
        scrollOffset = 0f
        buildSlots()
        invalidate()
    }

    fun clear() {
        suggestions = emptyList()
        slots = emptyList()
        scrollOffset = 0f
        invalidate()
    }

    private fun buildSlots() {
        if (suggestions.isEmpty()) { slots = emptyList(); return }

        val result = mutableListOf<SuggestionSlot>()
        var x = chipGap

        for (suggestion in suggestions) {
            val textWidth = textPaint.measureText(suggestion.bengali)
            val chipWidth = textWidth + chipPadding * 2
            result.add(SuggestionSlot(
                suggestion = suggestion,
                rect = RectF(x, chipGap, x + chipWidth, height.toFloat() - chipGap),
                textWidth = textWidth
            ))
            x += chipWidth + chipGap
        }

        totalContentWidth = x
        slots = result
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildSlots()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1E1E1E"))

        if (slots.isEmpty()) return

        canvas.save()
        canvas.translate(-scrollOffset, 0f)

        for ((i, slot) in slots.withIndex()) {
            val rect = slot.rect

            // Draw chip background
            val paint = if (i == 0) highlightPaint else chipPaint
            canvas.drawRoundRect(rect, chipRadius, chipRadius, paint)

            // Draw text centered in chip
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(slot.suggestion.bengali, rect.centerX(), textY, textPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                if (kotlin.math.abs(dx) > 8) isDragging = true
                if (isDragging) {
                    scrollOffset = (scrollOffset + dx).coerceIn(0f, maxOf(0f, totalContentWidth - width))
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && slots.isNotEmpty()) {
                    // Tap — find which chip was tapped
                    val tapX = event.x + scrollOffset
                    val tappedSlot = slots.find { tapX >= it.rect.left && tapX <= it.rect.right }
                    tappedSlot?.let { onSuggestionClick?.invoke(it.suggestion) }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
