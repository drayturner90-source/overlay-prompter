package com.overlayprompter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shows the script in a floating, see-through window on top of whatever app is in front
 * (the camera). Touches outside the window go straight to the camera app.
 */
class OverlayService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: View? = null
    private lateinit var scroll: ScrollView
    private lateinit var text: TextView
    private lateinit var play: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var countdown = 0
    private var lastFrameNanos = 0L
    private var pendingPx = 0f

    private val density get() = resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (root == null) showOverlay() else reloadFromPrefs()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopScrolling()
        handler.removeCallbacksAndMessages(null)
        root?.let { wm.removeView(it) }
        root = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notification

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Prompter overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Prompter is showing")
            .setContentText("Tap to edit your script")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Close prompter", stop).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------------------------------------------------------- window

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        val themed = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay, null)
        root = view
        scroll = view.findViewById(R.id.scroll)
        text = view.findViewById(R.id.text)
        play = view.findViewById(R.id.play)

        val screen = resources.displayMetrics
        val margin = (12 * density).toInt()
        params = WindowManager.LayoutParams(
            prefs.winW.takeIf { it > 0 } ?: (screen.widthPixels - 2 * margin),
            prefs.winH.takeIf { it > 0 } ?: (screen.heightPixels * 0.35f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the camera app keeps its buttons; touches outside us pass through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default: near the top of the screen, right under the selfie lens, so your eyes stay close to it.
            x = prefs.winX.takeIf { it >= 0 } ?: margin
            y = prefs.winY.takeIf { it >= 0 } ?: (32 * density).toInt()
        }

        reloadFromPrefs()
        wireControls(view)
        wm.addView(view, params)
    }

    private fun reloadFromPrefs() {
        text.text = prefs.script
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSize.toFloat())
        // Extra space after the last line so it can scroll all the way up.
        text.setPadding(text.paddingLeft, text.paddingTop, text.paddingRight, (params.height * 0.8f).toInt())
        val alpha = (prefs.opacity * 255 / 100)
        root?.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireControls(view: View) {
        play.setOnClickListener { if (playing || countdown > 0) pause() else startWithCountdown() }
        text.setOnClickListener { if (playing) pause() else if (countdown == 0) startWithCountdown() }

        view.findViewById<View>(R.id.restart).setOnClickListener {
            pause()
            scroll.scrollTo(0, 0)
        }
        view.findViewById<View>(R.id.slower).setOnClickListener { prefs.speed = prefs.speed - 5; flash("Speed ${prefs.speed}") }
        view.findViewById<View>(R.id.faster).setOnClickListener { prefs.speed = prefs.speed + 5; flash("Speed ${prefs.speed}") }
        view.findViewById<View>(R.id.smaller).setOnClickListener { prefs.textSize = prefs.textSize - 2; reloadFromPrefs() }
        view.findViewById<View>(R.id.bigger).setOnClickListener { prefs.textSize = prefs.textSize + 2; reloadFromPrefs() }
        view.findViewById<View>(R.id.close).setOnClickListener { stopSelf() }

        // Drag the grip to move the window.
        view.findViewById<View>(R.id.grip).setOnTouchListener(object : View.OnTouchListener {
            var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (e.rawX - touchX).toInt()
                        params.y = (startY + (e.rawY - touchY).toInt()).coerceAtLeast(0)
                        wm.updateViewLayout(view, params)
                    }
                    MotionEvent.ACTION_UP -> { prefs.winX = params.x; prefs.winY = params.y }
                }
                return true
            }
        })

        // Drag the corner to resize the window.
        view.findViewById<View>(R.id.resize).setOnTouchListener(object : View.OnTouchListener {
            var startW = 0; var startH = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = params.width; startH = params.height; touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = (startW + (e.rawX - touchX).toInt()).coerceAtLeast((200 * density).toInt())
                        params.height = (startH + (e.rawY - touchY).toInt()).coerceAtLeast((120 * density).toInt())
                        wm.updateViewLayout(view, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.winW = params.width; prefs.winH = params.height
                        reloadFromPrefs()
                    }
                }
                return true
            }
        })
    }

    private fun flash(message: String) {
        play.text = message.substringAfterLast(' ')
        handler.postDelayed({ updatePlayIcon() }, 700)
    }

    private fun updatePlayIcon() {
        play.text = when {
            countdown > 0 -> countdown.toString()
            playing -> "❚❚"
            else -> "▶"
        }
    }

    // ---------------------------------------------------------------- scrolling

    /** 3-2-1 so you have time to look at the lens and get set before the text moves. */
    private fun startWithCountdown() {
        handler.removeCallbacks(tick)
        countdown = 3
        updatePlayIcon()
        handler.postDelayed(tick, 1000)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (countdown == 0) return
            countdown--
            if (countdown == 0) startScrolling() else {
                updatePlayIcon()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun startScrolling() {
        playing = true
        lastFrameNanos = 0L
        pendingPx = 0f
        updatePlayIcon()
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun pause() {
        countdown = 0
        handler.removeCallbacks(tick)
        stopScrolling()
        updatePlayIcon()
    }

    private fun stopScrolling() {
        playing = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!playing) return
            if (lastFrameNanos != 0L) {
                val seconds = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                pendingPx += prefs.speed * density * seconds
                val whole = pendingPx.toInt()
                if (whole > 0) {
                    scroll.scrollBy(0, whole)
                    pendingPx -= whole
                }
                if (!scroll.canScrollVertically(1)) {
                    pause()
                    return
                }
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.overlayprompter.STOP"
    }
}
