package com.omersusin.pitube.recognition

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.omersusin.pitube.MainActivity
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.data.local.RecognitionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Floating "song recognition" button drawn above other apps
 * (`SYSTEM_ALERT_WINDOW`).
 *
 * Tapping it runs a full recognition pass **in the background** (no app
 * opening, matching Audile): it records ~12 s and identifies the song with the
 * configured provider. The button then reflects the result:
 *
 *  - IDLE (red mic): tap → start background recognition
 *  - RECOGNIZING (blue mic): tap → cancel
 *  - DONE (green note): tap → open the app with the search prefilled
 *  - FAILED (gray close): tap → arm again for a retry
 *
 * A successful background match also posts a result notification (when the
 * "Notifications" preference is on) whose tap opens the search prefilled too.
 *
 * Drag-to-remove: while dragging, a red "X" dismiss target appears; when the
 * button is dropped on it, the button is removed, the floating-button
 * preference is turned off and the service is stopped.
 */
class RecognitionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: RecognitionPreferences
    private var overlayView: View? = null
    private var dismissTargetView: View? = null
    private var dismissTargetMagnetized = false
    private var dismissScrimWindow: View? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognitionJob: Job? = null
    private var lastTrack: TrackMatch? = null

    @Volatile
    private var cancelRequested = false

    private enum class ButtonState {
        IDLE,
        RECOGNIZING,
        DONE,
        FAILED,
    }

    private var state = ButtonState.IDLE
        set(value) {
            field = value
            updateButton()
        }

    private val touchSlop = 8f
    private var startX = 0f
    private var startY = 0f
    private var initialX = 0
    private var initialY = 0
    private var moved = false
    private var isDragging = false

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 4102
        const val PERMISSION_NOTIFICATION_ID = 4103
        const val EXTRA_START_RECOGNITION = "recognition_overlay_start_recognition"
        const val EXTRA_RECOGNITION_SEARCH_QUERY = "recognition_overlay_search_query"

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

        /** Intent that brings the app forward and opens Search pre-filled with
         *  the recognized song's query. */
        fun openSearchPreloadedIntent(context: Context, query: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_RECOGNITION_SEARCH_QUERY, query)
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferences = RecognitionPreferences(this)
        RecognitionNotifier.ensureChannel(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (overlayView == null) {
            addOverlayButton()
        }
        if (intent?.getBooleanExtra(EXTRA_START_RECOGNITION, false) == true) {
            intent.removeExtra(EXTRA_START_RECOGNITION)
            startRecognition()
        }
        return START_NOT_STICKY
    }

    private fun addOverlayButton() {
        val view =
            LayoutInflater.from(this).inflate(R.layout.view_recognition_overlay_button, null)
                as FrameLayout

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

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    isDragging = false
                    dismissTargetMagnetized = false
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
                        if (dismissTargetView == null) {
                            showDismissTarget()
                        } else {
                            val buttonW = view.width
                            val buttonH = view.height
                            val distance =
                                kotlin.math.hypot(
                                    (params.x + buttonW / 2f) - targetCenterX,
                                    (params.y + buttonH / 2f) - targetCenterY,
                                )
                            val magnetRadius = magnetRadiusDp * resources.displayMetrics.density
                            val near = distance < magnetRadius
                            if (near != dismissTargetMagnetized) {
                                dismissTargetMagnetized = near
                                updateDismissTarget()
                            }
                            if (dismissTargetMagnetized) {
                                val cx = targetCenterX - buttonW / 2f
                                val cy = targetCenterY - buttonH / 2f
                                params.x = cx.toInt()
                                params.y = cy.toInt()
                            }
                        }
                        runCatching {
                            windowManager.updateViewLayout(view, params)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        val shouldRemove = dismissTargetMagnetized
                        hideDismissTarget()
                        if (shouldRemove) {
                            removeFloatingButton()
                        }
                    } else if (!moved) {
                        handleTap()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    hideDismissTarget()
                    true
                }

                else -> false
            }
        }

        runCatching { windowManager.addView(view, params) }
        overlayView = view
        state = ButtonState.IDLE
    }

    private fun handleTap() {
        when (state) {
            ButtonState.IDLE -> startRecognition()
            ButtonState.RECOGNIZING -> cancelRecognition()
            ButtonState.DONE -> lastTrack?.let { openSearchPreloaded(it.searchQuery) }
            ButtonState.FAILED -> state = ButtonState.IDLE
        }
    }

    private fun startRecognition() {
        if (state == ButtonState.RECOGNIZING) return
        if (!hasMicPermission()) {
            state = ButtonState.FAILED
            showPermissionNotification()
            return
        }
        cancelRequested = false
        state = ButtonState.RECOGNIZING
        startAsForeground()

        recognitionJob =
            scope.launch {
                var outcome: SongRecognitionOutcome? = null
                try {
                    val repository = RecognitionRepository(this@RecognitionOverlayService)
                    // Recording + fingerprinting are blocking; run off the main
                    // thread. State updates after it resume back on Main.
                    outcome =
                        withContext(Dispatchers.IO) {
                            val capturer = MicAudioCapturer()
                            try {
                                val captured =
                                    capturer.record(
                                        RecognitionRepository.SONG_RECORDING_MS,
                                        interrupted = { cancelRequested },
                                    )
                                if (cancelRequested) {
                                    null
                                } else {
                                    repository.recognizeCapturedSong(captured)
                                }
                            } finally {
                                capturer.stop()
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    outcome =
                        SongRecognitionOutcome.Failed(
                            type = RecognitionFailureType.OTHER,
                            message = e.message ?: "Recognition failed",
                        )
                }

                stopAsForeground()
                if (cancelRequested) return@launch
                when (outcome) {
                    is SongRecognitionOutcome.Matched -> {
                        lastTrack = outcome.track
                        state = ButtonState.DONE
                        showResultNotification(outcome.track)
                    }
                    is SongRecognitionOutcome.NoMatch,
                    is SongRecognitionOutcome.Failed,
                    null,
                    -> {
                        state = ButtonState.FAILED
                    }
                }
            }
    }

    private fun cancelRecognition() {
        cancelRequested = true
        recognitionJob?.cancel()
        recognitionJob = null
        stopAsForeground()
        state = ButtonState.IDLE
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        runCatching {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.onFailure {
            runCatching { startForeground(FOREGROUND_NOTIFICATION_ID, notification) }
        }
    }

    private fun stopAsForeground() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, RecognitionNotifier.CHANNEL_RECOGNITION)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle(getString(R.string.recognition_overlay_foreground_title))
            .setContentText(getString(R.string.recognition_listening_song))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateButton() {
        val icon = overlayView?.findViewById<ImageView>(R.id.recognition_overlay_icon) ?: return
        val (color, drawable, description) =
            when (state) {
                ButtonState.IDLE -> Triple(
                    0xE6FF0000.toInt(),
                    R.drawable.ic_recognition_mic,
                    R.string.recognition_overlay_content_description,
                )
                ButtonState.RECOGNIZING -> Triple(
                    0xE62196F3.toInt(),
                    R.drawable.ic_recognition_mic,
                    R.string.recognition_overlay_recognizing_description,
                )
                ButtonState.DONE -> Triple(
                    0xE64CAF50.toInt(),
                    R.drawable.ic_music_note,
                    R.string.recognition_overlay_done_description,
                )
                ButtonState.FAILED -> Triple(
                    0xE678909C.toInt(),
                    R.drawable.ic_close,
                    R.string.recognition_overlay_failed_description,
                )
            }
        icon.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2.dpPx, 0xFFFFFFFF.toInt())
            }
        icon.setImageResource(drawable)
        icon.contentDescription = getString(description)
    }

    // ---- Dismiss target (drag onto the "X" to remove the button) ----
    // Adapts Audile's DismissWindow: a bottom-half scrim fading to the surface
    // color with a circular errorContainer stop, a large onErrorContainer close
    // icon, a 0.8x scale when the button is magnetized, and haptic feedback on
    // magnetize (see B4a). Drawn with Views since the overlay isn't Compose.

    private var targetCenterX = 0f
    private var targetCenterY = 0f
    private var magnetizedHapticsPlayed = false

    // Material 3 palette (dark theme, matching Audile's DismissWindow).
    // errorContainer / onErrorContainer from the M3 default dark scheme.
    private val dismissErrorContainer = 0xFF93000A.toInt()
    private val dismissOnErrorContainer = 0xFFFFDAD6.toInt()
    private val magnetRadiusDp = 64f

    private fun performHaptic() {
        val vibrator =
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12)
        }
    }

    private fun showDismissTarget() {
        if (dismissTargetView != null) return
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Bottom-half scrim matching Audile: a gradient box (transparent at its
        // top fading down to surface@0.6) anchored to the bottom of the screen.
        val scrimHeight = (screenHeight / 2f).toInt()
        val scrimSurface = 0x99151515.toInt() // surface #151515 at ~60% alpha
        val scrimView =
            FrameLayout(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.TRANSPARENT, scrimSurface),
                    )
            }
        val scrimParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                scrimHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
            }
        runCatching { windowManager.addView(scrimView, scrimParams) }
        dismissScrimWindow = scrimView

        // The circle + close icon sits 64dp above the bottom (Audile: bottom=64dp,
        // 80dp circle, 48dp icon).
        val circleSize = (80f * density).toInt()
        val iconSize = (48f * density).toInt()
        val bottomPad = (64f * density).toInt()
        val circle =
            FrameLayout(this).apply {
                val circleBg =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dismissErrorContainer)
                    }
                background = circleBg
                val close =
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_close)
                        setColorFilter(dismissOnErrorContainer)
                        contentDescription =
                            getString(R.string.recognition_overlay_delete_description)
                        layoutParams =
                            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                    }
                addView(close)
            }
        val circleParams =
            WindowManager.LayoutParams(
                circleSize,
                circleSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = bottomPad
            }
        runCatching { windowManager.addView(circle, circleParams) }

        targetCenterX = (screenWidth / 2f)
        targetCenterY = (screenHeight - bottomPad - circleSize / 2f)

        dismissTargetMagnetized = false
        magnetizedHapticsPlayed = false
        dismissTargetView = circle
    }

    private fun updateDismissTarget() {
        val view = dismissTargetView ?: return
        // Audile shrinks the container to 0.8x when the button magnets in.
        val scale = if (dismissTargetMagnetized) 0.8f else 1f
        view.scaleX = scale
        view.scaleY = scale
        if (dismissTargetMagnetized && !magnetizedHapticsPlayed) {
            magnetizedHapticsPlayed = true
            performHaptic()
        }
    }

    private fun hideDismissTarget() {
        val view = dismissTargetView
        if (view != null) {
            runCatching { windowManager.removeView(view) }
            dismissTargetView = null
            dismissTargetMagnetized = false
            magnetizedHapticsPlayed = false
        }
        dismissScrimWindow?.let {
            runCatching { windowManager.removeView(it) }
            dismissScrimWindow = null
        }
    }

    private fun removeFloatingButton() {
        recognitionJob?.cancel()
        recognitionJob = null
        cancelRequested = true
        stopAsForeground()
        hideDismissTarget()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        val prefs = preferences
        scope.launch {
            prefs.setFloatingButtonEnabled(false)
            stopSelf()
        }
    }

    private fun showPermissionNotification() {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                FOREGROUND_NOTIFICATION_ID,
                RecognitionNotifier.openRecognitionModalIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, RecognitionNotifier.CHANNEL_RECOGNITION)
                .setSmallIcon(R.drawable.ic_recognition_mic)
                .setContentTitle(getString(R.string.recognition_overlay_foreground_title))
                .setContentText(getString(R.string.recognition_overlay_not_recording))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(PERMISSION_NOTIFICATION_ID, notification)
        }
    }

    private fun showResultNotification(track: TrackMatch) {
        val prefs = preferences
        scope.launch {
            if (prefs.notificationsEnabled.first()) {
                RecognitionNotifier.getInstance(this@RecognitionOverlayService)
                    .showMatchedTrackNotification(track, openSearch = true)
            }
        }
    }

    private fun openSearchPreloaded(query: String) {
        runCatching { startActivity(openSearchPreloadedIntent(this, query)) }
    }

    private val Int.dpPx: Int
        get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        isRunning.set(false)
        recognitionJob?.cancel()
        recognitionJob = null
        cancelRequested = true
        stopAsForeground()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        hideDismissTarget()
        scope.cancel()
        super.onDestroy()
    }
}