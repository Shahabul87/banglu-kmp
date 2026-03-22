package com.banglu.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.banglu.engine.types.SmartSuggestion

/**
 * Horizontal suggestion bar that displays Bengali word candidates.
 *
 * Shows up to 6 suggestions in equal-width slots. The first slot is highlighted
 * as the primary recommendation. Tapping a slot selects that suggestion.
 */
class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onSuggestionClick: ((SmartSuggestion) -> Unit)? = null

    private var suggestions = listOf<SmartSuggestion>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
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

    fun showSuggestions(newSuggestions: List<SmartSuggestion>) {
        suggestions = newSuggestions.take(6)
        invalidate()
    }

    fun clear() {
        suggestions = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1E1E1E"))

        if (suggestions.isEmpty()) return

        val slotWidth = width.toFloat() / suggestions.size
        for ((i, suggestion) in suggestions.withIndex()) {
            val x = i * slotWidth

            // Highlight the first (primary) suggestion
            if (i == 0) {
                canvas.drawRect(x, 0f, x + slotWidth, height.toFloat(), highlightPaint)
            }

            // Draw divider between slots
            if (i > 0) {
                canvas.drawLine(x, 4f, x, height.toFloat() - 4f, dividerPaint)
            }

            // Draw Bengali text centered in slot
            val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(suggestion.bengali, x + slotWidth / 2, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && suggestions.isNotEmpty()) {
            val slotWidth = width.toFloat() / suggestions.size
            val idx = (event.x / slotWidth).toInt().coerceIn(0, suggestions.size - 1)
            onSuggestionClick?.invoke(suggestions[idx])
            return true
        }
        return super.onTouchEvent(event)
    }
}
