package com.example.mathbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(this).apply {
            textSize = 15f
            text = "1) Əlçatanlıq ayarlarında \"Math Bot\" servisini aktiv edin.\n" +
                    "2) BAŞLAT basın, sonra oyuna keçin.\n" +
                    "3) Bot dayanarsa, bu səhifəyə qayıdıb vəziyyəti yeniləyin."
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
            refresh()
        })
        layout.addView(button("Botu DAYANDIR") {
            BotService.running = false
            Toast.makeText(this, "Bot dayandı", Toast.LENGTH_SHORT).show()
            refresh()
        })
        layout.addView(button("Vəziyyəti yenilə") { refresh() })

        status = TextView(this).apply { textSize = 14f; setPadding(0, pad / 2, 0, 0) }
        layout.addView(status)

        setContentView(ScrollView(this).apply { addView(layout) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        status.text = "Servis: " + (if (BotService.instance != null) "aktiv" else "AKTİV DEYİL") + "\n" +
                "Bot: " + (if (BotService.running) "işləyir" else "dayanıb") + "\n" +
                "Toxunuş sayı: ${BotService.taps}\n" +
                "Son nəticə: ${BotService.info}\n" +
                "Son oxunmayan mətn: ${BotService.lastFail}"
    }
}
