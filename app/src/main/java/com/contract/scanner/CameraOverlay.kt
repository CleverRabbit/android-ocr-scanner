package com.contract.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Custom View for drawing bounding boxes around detected contract numbers
 */
class CameraOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.overlay_color)
        style = Paint.Style.FILL
        alpha = 100
    }

    private val strokePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.overlay_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var boundingBoxes: List<RectF> = emptyList()

    fun setBoundingBoxes(boxes: List<RectF>) {
        boundingBoxes = boxes
        invalidate()
    }

    fun clearBoxes() {
        boundingBoxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (box in boundingBoxes) {
            // Draw filled rectangle
            canvas.drawRect(box, boxPaint)
            // Draw stroke border
            canvas.drawRect(box, strokePaint)
        }
    }
}
