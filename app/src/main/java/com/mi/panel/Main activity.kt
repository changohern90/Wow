package com.mi.panel

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 9)

        val sc = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(50, 70, 50, 70)
        sc.addView(ll)
        setContentView(sc)

        val title = TextView(this)
        title.text = "Panel"
        title.textSize = 22f
        ll.addView(title)

        status = TextView(this)
        status.setPadding(0, 20, 0, 30)
        ll.addView(status)

        btn(ll, "1) Permiso de superposición") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btn(ll, "2) Iniciar panel") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Primero da el permiso de superposición", Toast.LENGTH_LONG).show()
                return@btn
            }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), 1)
        }
        btn(ll, "3) Detener") { OverlayService.stop(this) }

        val t2 = TextView(this)
        t2.text = "\nAjuste del minimapa (calibra hasta que el cuadro encaje)"
        t2.setPadding(0, 40, 0, 10)
        ll.addView(t2)
        slider(ll, "Izquierda", "ml", .02f)
        slider(ll, "Arriba", "mt", .68f)
        slider(ll, "Derecha", "mr", .30f)
        slider(ll, "Abajo", "mb", .95f)

        switch(ll, "Radar de enemigos", "radar", true)
        switch(ll, "Asistente de cabeza", "assist", true)
    }

    private fun btn(parent: LinearLayout, text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { action() }
        parent.addView(b)
    }

    private fun slider(parent: LinearLayout, label: String, key: String, def: Float) {
        val tv = TextView(this)
        tv.text = "$label ${(prefs.getFloat(key, def) * 100).toInt()}%"
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = (prefs.getFloat(key, def) * 100).toInt()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                prefs.edit().putFloat(key, v / 100f).apply()
                tv.text = "$label $v%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(tv)
        parent.addView(sb)
    }

    private fun switch(parent: LinearLayout, label: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = label
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply() }
        parent.addView(sw)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data != null) {
            OverlayService.resCode = res
            OverlayService.resData = data
            OverlayService.start(this)
            Toast.makeText(this, "Panel activo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this))
            "Permiso de superposición: OK"
        else
            "Permiso de superposición: FALTA"
    }
}
