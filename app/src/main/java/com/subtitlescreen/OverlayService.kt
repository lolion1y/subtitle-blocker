package com.subtitlescreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_REFRESH = "com.subtitlescreen.REFRESH"
        const val ACTION_HIDE = "com.subtitlescreen.HIDE"
        const val ACTION_SHOW = "com.subtitlescreen.SHOW"
        private const val CHANNEL_ID = "subtitle_screen_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var collapseTabView: CollapseTabView? = null
    private var isCollapsed = false
    private var isHidden = false
    private var wasCollapsedBeforeHide = false
    private var savedBandY = 0
    private var overlayType = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        overlayView = OverlayView(this)
        applySettings()

        val screenHeight = resources.displayMetrics.heightPixels

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayView.getTotalHeight(),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeight - screenHeight / 4
        }

        // Restore saved position if enabled
        if (Prefs.getRememberPosition(this) && Prefs.hasSavedPosition(this)) {
            layoutParams.y = Prefs.getSavedBandY(this)
            val savedWidth = Prefs.getSavedInnerWidth(this)
            val savedHeight = Prefs.getSavedInnerHeight(this)
            val savedLeft = Prefs.getSavedInnerLeft(this)
            if (savedWidth > 0) overlayView.innerWidth = savedWidth
            if (savedHeight > 0) overlayView.innerHeight = savedHeight
            if (savedLeft >= 0) overlayView.innerLeft = savedLeft
            layoutParams.height = overlayView.getTotalHeight()
        }

        overlayView.onLayoutUpdate = { deltaY, newTotalHeight ->
            layoutParams.y += deltaY
            layoutParams.height = newTotalHeight
            windowManager.updateViewLayout(overlayView, layoutParams)
        }

        overlayView.onCollapse = { collapse() }

        overlayView.onOpenSettings = {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        windowManager.addView(overlayView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> {
                applySettings()
                if (!isCollapsed && !isHidden) {
                    overlayView.invalidate()
                }
            }
            ACTION_HIDE -> hideOverlay()
            ACTION_SHOW -> showOverlay()
        }
        return START_STICKY
    }

    private fun applySettings() {
        overlayView.innerColor = Prefs.getInnerColor(this)
        overlayView.innerAlpha = Prefs.getInnerAlpha(this)
        overlayView.bandColor = Prefs.getBandColor(this)
        overlayView.bandAlpha = Prefs.getBandAlpha(this)
        overlayView.featherPx = Prefs.featherToPx(
            Prefs.getFeather(this), resources.displayMetrics.density
        )
        overlayView.handleColorOverride = Prefs.getHandleColor(this)
    }

    private var tabParams: WindowManager.LayoutParams? = null

    private fun collapse() {
        if (isCollapsed) return
        savedBandY = layoutParams.y
        windowManager.removeView(overlayView)

        val d = resources.displayMetrics.density
        val tabWidth = (36 * d).toInt()
        val tabHeight = (56 * d).toInt()

        val tp = WindowManager.LayoutParams(
            tabWidth, tabHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = savedBandY
        }
        tabParams = tp

        collapseTabView = CollapseTabView(this).also {
            it.tabColor = Prefs.getTabColor(this)
            it.tabAlpha = Prefs.getTabAlpha(this).coerceAtLeast(Prefs.MIN_TAB_ALPHA)
            it.onClick = { expand() }
            it.onDragY = { dy ->
                tp.y += dy
                savedBandY = tp.y
                windowManager.updateViewLayout(it, tp)
            }
            windowManager.addView(it, tp)
        }
        isCollapsed = true
    }

    private fun expand() {
        collapseTabView?.let {
            windowManager.removeView(it)
            collapseTabView = null
        }
        layoutParams.y = savedBandY
        windowManager.addView(overlayView, layoutParams)
        isCollapsed = false
    }

    private fun hideOverlay() {
        if (isHidden) return
        wasCollapsedBeforeHide = isCollapsed
        if (isCollapsed) {
            collapseTabView?.let {
                windowManager.removeView(it)
            }
        } else {
            windowManager.removeView(overlayView)
        }
        isHidden = true
    }

    private fun showOverlay() {
        if (!isHidden) return
        isHidden = false
        if (wasCollapsedBeforeHide) {
            collapseTabView?.let {
                tabParams?.let { tp -> windowManager.addView(it, tp) }
            }
        } else {
            applySettings()
            windowManager.addView(overlayView, layoutParams)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Save position if enabled
        if (Prefs.getRememberPosition(this) && !isCollapsed) {
            Prefs.savePosition(
                this,
                layoutParams.y,
                overlayView.innerLeft,
                overlayView.innerWidth,
                overlayView.innerHeight
            )
        }

        if (!isHidden) {
            if (isCollapsed) {
                collapseTabView?.let { windowManager.removeView(it) }
            } else {
                windowManager.removeView(overlayView)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subtitle Screen Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the subtitle blocker overlay running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Subtitle Screen")
            .setContentText("Overlay active. Tap to open app.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .build()
    }
}
