package com.example.mathbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class BotService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        @Volatile var instance: BotService? = null
        @Volatile var info = "-"        // son nəticə (diaqnostika)
        @Volatile var lastFail = "-"    // son oxuna bilməyən mətn
        @Volatile var taps = 0

        // ---- AYARLAR (ekranın faizi ilə: 0.0 .. 1.0) ----
        const val EQ_LEFT = 0.05f
        const val EQ_TOP = 0.30f
        const val EQ_RIGHT = 0.95f
        const val EQ_BOTTOM = 0.60f
        const val TRUE_X = 0.78f
        const val TRUE_Y = 0.83f
        const val FALSE_X = 0.36f
        const val FALSE_Y = 0.83f
        const val INTERVAL_MS = 350L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false
    private var busySince = 0L
    private var lastText = ""
    private var lastTime = 0L

    private val loop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            // İlişib qalıbsa (2 saniyədən çox) sıfırla
            if (busy && now - busySince > 2000) busy = false
            if (running && !busy) step()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        handler.post(loop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        running = false
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun step() {
        busy = true
        busySince = System.currentTimeMillis()
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (bmp == null) { busy = false; return }

                        val w = bmp.width
                        val h = bmp.height
                        val crop = Bitmap.createBitmap(
                            bmp,
                            (w * EQ_LEFT).toInt(), (h * EQ_TOP).toInt(),
                            (w * (EQ_RIGHT - EQ_LEFT)).toInt(), (h * (EQ_BOTTOM - EQ_TOP)).toInt()
                        )
                        recognizer.process(InputImage.fromBitmap(crop, 0))
                            .addOnSuccessListener {
                                try { handleText(it, w, h) }
                                catch (e: Exception) { info = "Xəta: ${e.message}" }
                                finally { busy = false }
                            }
                            .addOnFailureListener { info = "OCR xətası"; busy = false }
                    } catch (e: Exception) {
                        info = "Xəta: ${e.message}"
                        busy = false
                    }
                }

                override fun onFailure(errorCode: Int) {
                    info = "Ekran görüntüsü xətası: $errorCode"
                    busy = false
                }
            })
        } catch (e: Exception) {
            info = "Xəta: ${e.message}"
            busy = false
        }
    }

    private fun handleText(text: Text, w: Int, h: Int) {
        // Sətirləri yuxarıdan aşağı sıralayıb birləşdiririk
        val lines = text.textBlocks.flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
        val sorted = lines.joinToString("\n") { it.text }

        val verdict = judge(sorted) ?: judge(text.text)
        if (verdict == null) {
            val shown = text.text.replace("\n", " | ")
            info = "Oxuna bilmədi"
            if (shown.isNotBlank()) lastFail = shown
            return
        }
        val (key, correct) = verdict

        val now = System.currentTimeMillis()
        if (key == lastText && now - lastTime < 900) return
        lastText = key
        lastTime = now

        taps++
        info = "$key -> " + if (correct) "DOĞRU" else "YANLIŞ"
        if (correct) tap(TRUE_X, TRUE_Y, w, h) else tap(FALSE_X, FALSE_Y, w, h)
    }

    private fun clean(s: String): String {
        val sb = StringBuilder()
        for (c in s) when (c) {
            ' ', '\t', '\r' -> {}
            '\n' -> sb.append('#')
            '×', 'x', 'X', '*' -> sb.append('*')
            '÷', ':', '/' -> sb.append('/')
            '–', '−', '—', '-' -> sb.append('-')
            'O', 'o', 'D', 'Q' -> sb.append('0')
            'l', 'I', '|' -> sb.append('1')
            'Z' -> sb.append('2')
            'S' -> sb.append('5')
            'B' -> sb.append('8')
            'g' -> sb.append('9')
            't', 'T' -> sb.append('+')
            else -> sb.append(c)
        }
        // operatorların ətrafındakı sətir keçidlərini sil (tənlik iki sətirdə ola bilər)
        return sb.toString().replace(Regex("#?([-+*/=])#?"), "$1")
    }

    /** (tənlik, düzgündürmü) qaytarır; oxuna bilmirsə null. */
    private fun judge(candidate: String): Pair<String, Boolean>? {
        val t = clean(candidate)
        val m = Regex("(\\d+(?:[-+*/]\\d+)+)=(-?\\d+)").find(t) ?: return null
        val real = eval(m.groupValues[1]) ?: return null
        val shown = m.groupValues[2].toDouble()
        return Pair(m.value, Math.abs(real - shown) < 1e-6)
    }

    private fun eval(expr: String): Double? {
        val nums = Regex("\\d+").findAll(expr).map { it.value.toDouble() }.toMutableList()
        val ops = Regex("[-+*/]").findAll(expr).map { it.value[0] }.toMutableList()
        if (nums.size != ops.size + 1) return null
        var i = 0
        while (i < ops.size) {
            if (ops[i] == '*' || ops[i] == '/') {
                nums[i] = if (ops[i] == '*') nums[i] * nums[i + 1] else nums[i] / nums[i + 1]
                nums.removeAt(i + 1)
                ops.removeAt(i)
            } else i++
        }
        var r = nums[0]
        for (j in ops.indices) r = if (ops[j] == '+') r + nums[j + 1] else r - nums[j + 1]
        return r
    }

    private fun tap(fx: Float, fy: Float, w: Int, h: Int) {
        val path = Path().apply { moveTo(w * fx, h * fy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
