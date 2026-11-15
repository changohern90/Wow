package com.mi.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class AssistView(c: Context) : View(c) {
    var target = -1f
    var targetY = -1f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0xCC00E676.toInt()
        canvas.drawCircle(cx, cy, 5f, p)
        if (target >= 0) {
            p.color = 0xFFFF3B30.toInt()
            p.strokeWidth = 4f
            canvas.drawLine(target - 30f, targetY, target - 10f, targetY, p)
            canvas.drawLine(target + 10f, targetY, target + 30f, targetY, p)
            canvas.drawLine(target, targetY, target, targetY + 40f, p)
        }
    }
}
