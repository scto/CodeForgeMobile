package com.nyamux.view

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class GestureAndScaleRecognizer(
    context: Context,
    val listener: Listener
) {
    interface Listener {
        fun onSingleTapUp(e: MotionEvent?): Boolean = false
        fun onDoubleTap(e: MotionEvent?): Boolean = false
        fun onScroll(e: MotionEvent?, distanceX: Float, distanceY: Float): Boolean = false
        fun onFling(e: MotionEvent?, velocityX: Float, velocityY: Float): Boolean = false
        fun onScale(focusX: Float, focusY: Float, scaleFactor: Float): Boolean = false
        fun onLongPress(e: MotionEvent?) {}
        fun onDown(x: Float, y: Float): Boolean = false
        fun onUp(e: MotionEvent?): Boolean = false
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return listener.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return listener.onDoubleTap(e)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            return listener.onScroll(e2, distanceX, distanceY)
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            return listener.onFling(e2, velocityX, velocityY)
        }

        override fun onLongPress(e: MotionEvent) {
            listener.onLongPress(e)
        }

        override fun onDown(e: MotionEvent): Boolean {
            return listener.onDown(e.x, e.y)
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            return listener.onScale(detector.focusX, detector.focusY, detector.scaleFactor)
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        if (event.action == MotionEvent.ACTION_UP) {
            handled = listener.onUp(event) || handled
        }
        return handled
    }

    fun isInProgress(): Boolean = scaleGestureDetector.isInProgress
}
