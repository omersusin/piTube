package com.omersusin.pitube.recognition

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.omersusin.pitube.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Floating "song recognition" button drawn above other apps
 * (`SYSTEM_ALERT_WINDOW`). Tapping it reopens the recognition modal in the
 * app. Deliberately NOT a background listening service — the button only
 * reopens the modal for a manual trigger, per spec.
 */
class RecognitionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val touchSlop = 8f
    private var startX = 0f
    private var startY = 0f
    private var initialX = 0
    private var initialY = 0
    private var moved = false
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (overlayView == null) {
            addOverlayButton()
        }
        return START_NOT_STICKY
    }

    private fun addOverlayButton() {
        val view =
            LayoutInflater.from(this).inflate(R.layout.view_recognition_overlay_button, null)
                as FrameLayout
        val icon = view.findViewById<ImageView>(R.id.recognition_overlay_icon)

        val background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FF0000.toInt())
                setStroke(2.dpPx, 0xFFFFFFFF.toInt())
            }
        icon.background = background

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 8 * resources.displayMetrics.density.toInt()
                y = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            }
        layoutParams = params

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - startX) > touchSlop ||
                        kotlin.math.abs(event.rawY - startY) > touchSlop
                    ) {
                        isDragging = true
                    }
                    if (isDragging) {
                        moved = true
                        params.x = initialX + (event.rawX - startX).toInt()
                        params.y = initialY + (event.rawY - startY).toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                    } else if (!moved) {
                        openModal()
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { windowManager.addView(view, params) }
        overlayView = view
    }

    private fun dpPx: Int = (64 * resources.displayMetrics.density).toInt()

    private fun openModal() {
        val intent = RecognitionNotifier.openRecognitionModalIntent(this)
        runCatching { startActivity(intent) }
    }

    override fun onDestroy() {
        isRunning.set(false)
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: AtomicBoolean = AtomicBoolean(false)

        fun start(context: Context) {
            runCatching {
                if (isRunning.compareAndSet(false, true)) {
                    context.startService(Intent(context, RecognitionOverlayService::class.java))
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                if (isRunning.compareAndSet(true, false)) {
                    context.stopService(Intent(context, RecognitionOverlayService::class.java))
                }
            }
        }
    }
}