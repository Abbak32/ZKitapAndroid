package com.abdurahman.zkitap

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCapture = 4102
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFFF7F7F7.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Z-Kitap Görüntü Yakalama\n\nAna alan seçimi • Beyazlatma • Otomatik kırpma • Numaralı JPG"
            textSize = 20f
            setTextColor(0xFF202020.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        val start = Button(this).apply { text = "İzinleri ayarla ve başlat" }
        root.addView(start, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30 })
        root.addView(TextView(this).apply {
            text = "Başlatınca ekran yakalama iznini onayla. Sonra Z-kitap'a geç; yeşil + simgesinden alanları seçip görüntü kaydet."
            textSize = 15f
            setTextColor(0xFF555555.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        setContentView(root)
        start.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this).setTitle("Ekran üstü simge izni")
                    .setMessage("Yüzen kayıt simgesi için diğer uygulamaların üzerinde gösterme iznini açın.")
                    .setPositiveButton("Ayarları aç") { _, _ -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                    .setNegativeButton("İptal", null).show()
            } else {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), requestCapture)
            }
        }
    }

    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            finish()
        }
    }
}
