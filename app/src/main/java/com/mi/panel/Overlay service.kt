package com.mi.panel

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.ImageView
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val CH = "panel"
        var resCode = 0
        var resData: Intent? = null
        fun start(c: Context) = c.startForegroundService(Intent(c, OverlayService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, OverlayService::class.java))
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var th: HandlerThread
    private lateinit var h: Handler
    private val main = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null

    private var radar: ImageView? = null
    private var radarLp: WindowManager.LayoutParams? = null
    private var assist: AssistView? = null
    private var collapsed = false
    private var last = 0L
    private var capW = 180
    private var capH = 400

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("p", MODE_PRIVATE)
        th = HandlerThread("cap").also { it.start() }
        h = Handler(th.looper)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        startFg()
        if (vd == null) {
            val d = resData
            if (d == null || resCode == 0) { stopSelf(); return START_NOT_STICKY }
            val dm = resources.displayMetrics
            capW = (dm.widthPixels / 4).coerceAtLeast(120).let { it - it % 2 }
            capH = (dm.heightPixels / 4).coerceAtLeast(200).let { it - it % 2 }
            showRadar()
            showAssist()
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(resCode, d) ?: run { stopSelf(); return START_NOT_STICKY }
            projection = p
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, h)
            ir = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 3)
            ir!!.setOnImageAvailableListener({ r -> onFrame(r) }, h)
            vd = p.createVirtualDisplay(
                "cap", capW, capH, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir!!.surface, null, h
            )
        }
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Panel", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = Notification.Builder(this, CH)
            .setContentTitle("Utilidades")
            .setContentText("Panel activo")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(7, n)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showRadar() {
        val v = ImageView(this)
        v.setBackgroundColor(0x99101418.toInt())
        v.setPadding(4, 4, 4, 4)
        val s = dp(120)
        val lp = WindowManager.LayoutParams(
            s, s,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(140)
        v.setOnTouchListener(object : View.OnTouchListener {
            private var dx = 0
            private var dy = 0
            private var sx = 0f
            private var sy = 0f
            private var t0 = 0L
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = lp.x - e.rawX.toInt()
                        dy = lp.y - e.rawY.toInt()
                        sx = e.rawX; sy = e.rawY
                        t0 = SystemClock.elapsedRealtime()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = e.rawX.toInt() + dx
                        lp.y = e.rawY.toInt() + dy
                        wm.updateViewLayout(view, lp)
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = abs(e.rawX - sx) + abs(e.rawY - sy) > dp(12)
                        if (!moved && SystemClock.elapsedRealtime() - t0 < 300) toggle()
                    }
                }
                return true
            }
        })
        wm.addView(v, lp)
        radar = v
        radarLp = lp
    }

    private fun toggle() {
        collapsed = !collapsed
        val lp = radarLp ?: return
        val v = radar ?: return
        val s = if (collapsed) dp(26) else dp(120)
        lp.width = s
        lp.height = s
        v.alpha = if (collapsed) 0.55f else 1f
        wm.updateViewLayout(v, lp)
    }

    private fun showAssist() {
        val v = AssistView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        wm.addView(v, lp)
        assist = v
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            val pl = img.planes[0]
            val pad = pl.rowStride - pl.pixelStride * img.width
            val full = Bitmap.createBitmap(
                img.width + pad / pl.pixelStride, img.height, Bitmap.Config.ARGB_8888
            )
            full.copyPixelsFromBuffer(pl.buffer)
            val f = if (pad == 0) full else Bitmap.createBitmap(full, 0, 0, img.width, img.height)
            val now = SystemClock.elapsedRealtime()
            if (now - last > 55) {
                last = now
                doRadar(f)
                doAssist(f)
            }
            if (f !== full) f.recycle()
            full.recycle()
        } catch (_: Throwable) {
        } finally {
            try { img.close() } catch (_: Throwable) {}
        }
    }

    private fun red(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return r > 135 && r - g > 55 && r - b > 55 && g < 110 && b < 110
    }

    private fun doRadar(f: Bitmap) {
        val v = radar ?: return
        if (!prefs.getBoolean("radar", true)) {
            main.post { v.visibility = View.GONE }
            return
        }
        val w = f.width
        val hh = f.height
        val l = (prefs.getFloat("ml", .02f) * w).toInt().coerceIn(0, w - 6)
        val t = (prefs.getFloat("mt", .68f) * hh).toInt().coerceIn(0, hh - 6)
        val rr = (prefs.getFloat("mr", .30f) * w).toInt().coerceIn(l + 6, w)
        val bb = (prefs.getFloat("mb", .95f) * hh).toInt().coerceIn(t + 6, hh)
        val cw = rr - l
        val chh = bb - t

        val out = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(f, Rect(l, t, rr, bb), Rect(0, 0, 200, 200), paint)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFF2D2D.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f

        var y = 0
        while (y < chh) {
            var x = 0
            while (x < cw) {
                if (red(f.getPixel(l + x, t + y))) {
                    c.drawCircle(x * 200f / cw, y * 200f / chh, 5f, p)
                    x += 5
                }
                x += 3
            }
            y += 3
        }

        main.post {
            v.visibility = View.VISIBLE
            v.setImageBitmap(out)
        }
    }

    private fun doAssist(f: Bitmap) {
        val v = assist ?: return
        if (!prefs.getBoolean("assist", true)) {
            main.post { v.target = -1f; v.invalidate() }
            return
        }
        val w = f.width
        val hh = f.height
        val l = (w * .28f).toInt()
        val rr = (w * .72f).toInt()
        val t = (hh * .22f).toInt()
        val bb = (hh * .78f).toInt()

        var best = 0
        var bx = -1
        var by = -1
        var y = t
        while (y < bb) {
            var x = l
            while (x < rr) {
                var n = 0
                for (yy in 0..2) for (xx in 0..2)
                    if (red(f.getPixel(x + xx, y + yy))) n++
                if (n >= 4) {
                    val score = n * 12 - abs(x - w / 2) / 3
                    if (score > best) { best = score; bx = x + 1; by = y + 1 }
                }
                x += 5
            }
            y += 5
        }

        main.post {
            if (bx < 0) {
                v.target = -1f
            } else {
                v.target = bx * v.width.toFloat() / w
                v.targetY = by * v.height.toFloat() / hh
            }
            v.invalidate()
        }
    }

    override fun onDestroy() {
        try { vd?.release() } catch (_: Throwable) {}
        try { ir?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { radar?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { assist?.let { wm.removeView(it) } } catch (_: Throwable) {}
        th.quitSafely()
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}
