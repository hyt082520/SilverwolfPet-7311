package com.example.silverwolf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: DraggableFrame
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private val W_NORMAL = 200
    private val H_NORMAL = 300
    private val W_MINI = 72
    private val H_MINI = 72

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotice()
        addOverlay()
    }

    private fun startForegroundNotice() {
        val ch = NotificationChannel(
            "pet", "桌宠运行中", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)

        val n = NotificationCompat.Builder(this, "pet")
            .setContentTitle("银狼正在挂机")
            .setContentText("下拉通知可关闭")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addOverlay() {
        root = DraggableFrame(this)

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(Bridge(), "AndroidPet")
        }
        webView?.loadUrl("file:///android_asset/pet.html")

        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val close = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            alpha = 0.3f
            setOnClickListener { stopSelf() }
        }
        val c = dp(22)
        root.addView(close, FrameLayout.LayoutParams(c, c).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(2); rightMargin = dp(2)
        })

        params = WindowManager.LayoutParams(
            dp(W_NORMAL), dp(H_NORMAL),
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 500
        }

        wm.addView(root, params)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun minimize() = root.post { setWindowSize(W_MINI, H_MINI) }

        @JavascriptInterface
        fun restore() = root.post { setWindowSize(W_NORMAL, H_NORMAL) }

        @JavascriptInterface
        fun close() = root.post { stopSelf() }
    }

    private fun setWindowSize(wDp: Int, hDp: Int) {
        val p = params ?: return
        p.width = dp(wDp)
        p.height = dp(hDp)
        val dm = resources.displayMetrics
        p.x = p.x.coerceIn(0, (dm.widthPixels - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (dm.heightPixels - p.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(root, p) }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        webView?.let { w ->
            w.stopLoading()
            w.removeJavascriptInterface("AndroidPet")
            w.destroy()
        }
        webView = null
        if (::root.isInitialized) runCatching { wm.removeView(root) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class DraggableFrame(context: Context) : FrameLayout(context) {

        private var startX = 0f
        private var startY = 0f
        private var originX = 0
        private var originY = 0
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX; startY = ev.rawY
                    originX = params?.x ?: 0
                    originY = params?.y ?: 0
                    dragging = false
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        (abs(ev.rawX - startX) > slop || abs(ev.rawY - startY) > slop)
                    ) {
                        dragging = true
                        webView?.evaluateJavascript(
                            "window.__dragBegin&&window.__dragBegin()", null
                        )
                        return true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (!dragging) return super.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val p = params ?: return true
                    p.x = originX + (ev.rawX - startX).toInt()
                    p.y = originY + (ev.rawY - startY).toInt()
                    runCatching { wm.updateViewLayout(this, p) }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    webView?.evaluateJavascript(
                        "window.__dragEnd&&window.__dragEnd()", null
                    )
                }
            }
            return true
        }
    }
}
