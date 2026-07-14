package com.example.handcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Bridges between the hand-tracking overlay and the real touch input system.
 * dispatchGesture() is the only public way (without root) to inject synthetic
 * touch events system-wide from a non-root app.
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        // Simple static reference so OverlayCursorService can reach this service.
        // Fine for a single-user personal-use app; for anything shipped more broadly,
        // prefer a bound-service / messenger approach instead.
        var instance: GestureAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Perform a single tap at the given screen coordinates. */
    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** Optional: drag from one point to another (useful later for scroll support). */
    fun performDrag(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
