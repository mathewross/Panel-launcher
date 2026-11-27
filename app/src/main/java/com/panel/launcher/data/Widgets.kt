package com.panel.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Hosts other apps' widgets (the same mechanism every home-screen app uses). */
class PanelWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        PanelWidgetView(context)

    companion object { const val HOST_ID = 4711 }
}

/** A widget view that also notices a long-press anywhere on it (to show Panel's widget menu). */
class PanelWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y
                postDelayed(trigger, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(trigger)
        }
        // After a long-press, swallow the rest of the gesture so the widget doesn't also get a tap.
        return longPressed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(trigger)
        }
        return longPressed || super.onTouchEvent(event)
    }
}
