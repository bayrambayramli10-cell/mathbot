package com.example.mathbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(this).apply {
            textSize = 16f
            text = "1) Əlçatanlıq ayarlarında \"Math Bot\" servisini aktiv edin.\n" +
                    "2) Oyunu açın, tənlik ekranda olanda geri qayıdıb BAŞLAT basın " +
                    "(və ya əvvəlcə BAŞLAT basıb sonra oyuna keçin).\n" +
                    "3) Bot ekranı izləyib düzgün düyməyə toxunacaq."
        })

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

        layout.addView(button("1. Əlçatanlıq ayarlarını aç") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("2. Botu BAŞLAT") {
            if (BotService.instance == null) {
                Toast.makeText(this, "Əvvəlcə servisi aktiv edin!", Toast.LENGTH_LONG).show()
            } else {
                BotService.running = true
                Toast.makeText(this, "Bot işləyir", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(button("Botu DAYANDIR") {
            BotService.running = false
            Toast.makeText(this, "Bot dayandı", Toast.LENGTH_SHORT).show()
        })

        setContentView(layout)
    }
}
