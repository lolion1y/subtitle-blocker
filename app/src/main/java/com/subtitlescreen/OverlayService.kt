package com.subtitlescreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
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
    private var isViewAttached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = OverlayView(this)
        applySettings()

        val screenHeight = resources.displayMetrics.heightPixels

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayView.getTotalHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeight - screenHeight / 4
        }

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
            if (isViewAttached && !isHidden && !isCollapsed) {
                layoutParams.y += deltaY
                layoutParams.height = newTotalHeight
                windowManager.updateViewLayout(overlayView, layoutParams)
            }
        }

        overlayView.onCollapse = { collapse() }

        overlayView.onOpenSettings = {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        windowManager.addView(overlayView, layoutParams)
        isViewAttached = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> {
                applySettings()
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
        isViewAttached = false

        val d = resources.displayMetrics.density
        val tabWidth = (36 * d).toInt()
        val tabHeight = (56 * d).toInt()

        val tp = WindowManager.LayoutParams(
            tabWidth, tabHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        isViewAttached = true
        isCollapsed = false
    }

    private fun hideOverlay() {
        if (isHidden) return
        wasCollapsedBeforeHide = isCollapsed
        if (isCollapsed) {
            collapseTabView?.let { windowManager.removeView(it) }
        } else {
            windowManager.removeView(overlayView)
            isViewAttached = false
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
            isViewAttached = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Save position whether expanded or collapsed
        if (Prefs.getRememberPosition(this)) {
            val y = if (isCollapsed) savedBandY else layoutParams.y
            if (!isCollapsed) {
                Prefs.savePosition(
                    this, y,
                    overlayView.innerLeft,
                    overlayView.innerWidth,
                    overlayView.innerHeight
                )
            } else {
                Prefs.savePosition(
                    this, y,
                    Prefs.getSavedInnerLeft(this).takeIf { it >= 0 } ?: 0,
                    Prefs.getSavedInnerWidth(this).takeIf { it > 0 } ?: 0,
                    Prefs.getSavedInnerHeight(this).takeIf { it > 0 } ?: 0
                )
            }
        }

        if (!isHidden) {
            if (isCollapsed) {
                collapseTabView?.let { windowManager.removeView(it) }
            } else {
                windowManager.removeView(overlayView)
            }
        }
        isViewAttached = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Subtitle Blocker Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the subtitle blocker overlay running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Subtitle Blocker")
            .setContentText("Overlay active. Tap to open app.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .build()
    }
}
