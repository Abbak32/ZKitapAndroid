package com.abdurahman.zkitap

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.widget.*
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

class CaptureService : Service() {
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var bubble: View? = null
    private var selectionView: SelectView? = null
    private var mainRect: Rect? = null
    private var excludeRect: Rect? = null
    private var nextNumber = 1
    private var capturing = false
    private var screenW = 0
    private var screenH = 0
    private var density = 1

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels; screenH = dm.heightPixels; density = dm.densityDpi
        createNotification()
        val prefs = getSharedPreferences("capture", Context.MODE_PRIVATE)
        nextNumber = prefs.getInt("next", 1)
        createReader()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && projection == null) {
            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("resultData", Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra("resultData")
            if (data != null && resultCode == Activity.RESULT_OK) {
                val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = pm.getMediaProjection(resultCode, data)
                projection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, null)
                createVirtualDisplay()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channelId = "zkitap_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Z-Kitap ekran yakalama", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val n = Notification.Builder(this, if (Build.VERSION.SDK_INT >= 26) channelId else "")
            .setContentTitle("Z-Kitap görüntü yakalama açık")
            .setContentText("Yüzen yeşil + simgesine dokunarak kayıt yapın")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7, n)
    }

    private fun createReader() {
        reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 3)
    }

    private fun createVirtualDisplay() {
        virtualDisplay = projection?.createVirtualDisplay("ZKitapCapture", screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
    }

    private fun bubbleParams(x: Int = screenW - (72 * resources.displayMetrics.density).toInt(), y: Int = screenH / 3): WindowManager.LayoutParams {
        return WindowManager.LayoutParams( (56 * resources.displayMetrics.density).toInt(), (56 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; this.x = x; this.y = y }
    }

    private fun showBubble() {
        val b = TextView(this).apply {
            text = "+"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.rgb(22, 145, 73)); setStroke(2, Color.WHITE) }
        }
        val p = bubbleParams()
        b.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=p.x; startY=p.y; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> { val dx=(e.rawX-downX).toInt(); val dy=(e.rawY-downY).toInt(); if (kotlin.math.abs(dx)+kotlin.math.abs(dy)>8) moved=true; p.x=startX+dx; p.y=startY+dy; try { wm.updateViewLayout(b,p) } catch (_: Exception) {}; return true }
                    MotionEvent.ACTION_UP -> { if (!moved) onBubbleTap(); return true }
                }; return true
            }
        })
        try { wm.addView(b, p); bubble = b } catch (_: Exception) {}
    }

    private fun onBubbleTap() {
        if (projection == null) { Toast.makeText(this, "Ekran yakalama oturumu kapalı. Uygulamayı yeniden başlatın.", Toast.LENGTH_LONG).show(); return }
        if (mainRect == null) showSelection(true) else showSelection(false)
    }

    private fun showSelection(chooseMain: Boolean) {
        bubble?.visibility = View.GONE
        val sv = SelectView(this, chooseMain, mainRect, excludeRect) { rect, isMain ->
            if (isMain) {
                mainRect = rect
                selectionView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                selectionView = null
                showSelection(false)
            }
            else {
                excludeRect = rect
                selectionView?.let { try { wm.removeView(it) } catch (_: Exception) {} }; selectionView = null
                bubble?.visibility = View.VISIBLE
                captureImage()
            }
        }
        selectionView = sv
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        try { wm.addView(sv, params) } catch (e: Exception) { Toast.makeText(this, "Alan seçimi açılamadı: ${e.message}", Toast.LENGTH_LONG).show(); bubble?.visibility = View.VISIBLE }
    }

    private fun captureImage() {
        if (capturing) return
        capturing = true
        bubble?.visibility = View.GONE
        android.os.Handler(mainLooper).postDelayed({
            try {
                val image = reader?.acquireLatestImage()
                if (image == null) { Toast.makeText(this, "Görüntü alınamadı; tekrar deneyin.", Toast.LENGTH_SHORT).show(); return@postDelayed }
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenW
                val padded = Bitmap.createBitmap(screenW + rowPadding / pixelStride, screenH, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(buffer)
                image.close()
                var bmp = Bitmap.createBitmap(padded, 0, 0, screenW, screenH)
                padded.recycle()
                val r = mainRect ?: Rect(0, 0, screenW, screenH)
                val safe = Rect(max(0,r.left), max(0,r.top), min(screenW,r.right), min(screenH,r.bottom))
                if (safe.width() < 2 || safe.height() < 2) throw Exception("Ana alan geçersiz")
                bmp = Bitmap.createBitmap(bmp, safe.left, safe.top, safe.width(), safe.height())
                excludeRect?.let { ex ->
                    val local = Rect(ex.left-safe.left, ex.top-safe.top, ex.right-safe.left, ex.bottom-safe.top)
                    val canvas = Canvas(bmp); val paint = Paint().apply { color = Color.WHITE }
                    if (local.right > 0 && local.bottom > 0 && local.left < bmp.width && local.top < bmp.height) canvas.drawRect(local, paint)
                }
                bmp = trimWhite(bmp)
                saveJpg(bmp)
                bmp.recycle()
                Toast.makeText(this, "Kaydedildi: ${String.format("%03d", nextNumber-1)}.jpg", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Kayıt hatası: ${e.message}", Toast.LENGTH_LONG).show() }
            finally { capturing=false; bubble?.visibility=View.VISIBLE }
        }, 250)
    }

    private fun trimWhite(src: Bitmap): Bitmap {
        val w=src.width; val h=src.height
        val pixels=IntArray(w*h); src.getPixels(pixels,0,w,0,0,w,h)
        var left=w; var top=h; var right=-1; var bottom=-1
        for (y in 0 until h step 2) for (x in 0 until w step 2) {
            val c=pixels[y*w+x]; val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
            if (r<242 || g<242 || b<242) { left=min(left,x); top=min(top,y); right=max(right,x); bottom=max(bottom,y) }
        }
        if (right < left || bottom < top) return src
        val pad=4; left=max(0,left-pad); top=max(0,top-pad); right=min(w-1,right+pad); bottom=min(h-1,bottom+pad)
        return Bitmap.createBitmap(src,left,top,right-left+1,bottom-top+1)
    }

    private fun saveJpg(bitmap: Bitmap) {
        val name=String.format("%03d.jpg", nextNumber)
        val values=android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ZKitapYakalama")
        }
        val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: throw Exception("Dosya oluşturulamadı")
        val out: OutputStream = contentResolver.openOutputStream(uri) ?: throw Exception("Dosyaya yazılamadı")
        out.use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) throw Exception("JPG sıkıştırılamadı") }
        nextNumber++; getSharedPreferences("capture",0).edit().putInt("next",nextNumber).apply()
    }

    override fun onDestroy() {
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { selectionView?.let { wm.removeView(it) } } catch (_: Exception) {}
        virtualDisplay?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private class SelectView(context: Context, private val chooseMain: Boolean, private val existingMain: Rect?, private val existingExclude: Rect?, private val done: (Rect,Boolean)->Unit) : View(context) {
        private val dim=Paint().apply { color=0x99000000.toInt() }
        private val line=Paint().apply { color=Color.YELLOW; style=Paint.Style.STROKE; strokeWidth=4f }
        private val text=Paint().apply { color=Color.WHITE; textSize=18f; isFakeBoldText=true }
        private var sx=0f; private var sy=0f; private var ex=0f; private var ey=0f; private var drawing=false
        init { isFocusable=true; isFocusableInTouchMode=true }
        override fun onDraw(c: Canvas) {
            super.onDraw(c); c.drawColor(0x66000000)
            val title=if (chooseMain) "Ana alanı parmağınla çerçevele" else "Beyazlatılacak alanı çerçevele (yoksa küçük alan seç)"
            c.drawRect(12f,35f,width-12f,92f,Paint().apply { color=0xDD168447.toInt() }); c.drawText(title,24f,70f,text)
            if (drawing) { c.drawRect(min(sx,ex),min(sy,ey),max(sx,ex),max(sy,ey),line) }
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.x; sy=e.y; ex=sx; ey=sy; drawing=true; invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { ex=e.x; ey=e.y; invalidate(); return true }
                MotionEvent.ACTION_UP -> {
                    ex=e.x; ey=e.y; drawing=false
                    val r=Rect(min(sx,ex).toInt(),min(sy,ey).toInt(),max(sx,ex).toInt(),max(sy,ey).toInt())
                    if (r.width()>12 && r.height()>12) done(r,chooseMain)
                    else if (!chooseMain) done(Rect(0,0,0,0),false)
                    else Toast.makeText(context,"Daha büyük bir alan seçin.",Toast.LENGTH_SHORT).show()
                    return true
                }
            }; return true
        }
    }
}
