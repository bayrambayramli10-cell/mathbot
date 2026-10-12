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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class BotService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        @Volatile var instance: BotService? = null

        // ---- AYARLAR (hamısı ekranın faizi ilə: 0.0 .. 1.0) ----
        // Tənliyin olduğu sahə
        const val EQ_LEFT = 0.05f
        const val EQ_TOP = 0.30f
        const val EQ_RIGHT = 0.95f
        const val EQ_BOTTOM = 0.60f
        // Sağdakı ✔ (doğru) düyməsi
        const val TRUE_X = 0.78f
        const val TRUE_Y = 0.83f
        // Soldakı ✘ (yanlış) düyməsi
        const val FALSE_X = 0.36f
        const val FALSE_Y = 0.83f
        // Ekran görüntüsü aralığı (Android ~333ms-dən tez icazə vermir)
        const val INTERVAL_MS = 350L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false
    private var lastText = ""
    private var lastTime = 0L

    private val loop = object : Runnable {
        override fun run() {
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
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
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
                    .addOnSuccessListener { handleText(it.text, w, h); busy = false }
                    .addOnFailureListener { busy = false }
            }

            override fun onFailure(errorCode: Int) { busy = false }
        })
    }

    private fun handleText(raw: String, w: Int, h: Int) {
        val t = raw.replace(Regex("\\s"), "")
            .replace('×', '*').replace('x', '*').replace('X', '*')
            .replace('÷', '/').replace(':', '/')
            .replace('–', '-').replace('−', '-')
            .replace('O', '0').replace('o', '0')

        val m = Regex("^(\\d+(?:[-+*/]\\d+)+)=(-?\\d+)$").matchEntire(t) ?: return
        val real = eval(m.groupValues[1]) ?: return
        val shown = m.groupValues[2].toDouble()

        val now = System.currentTimeMillis()
        if (t == lastText && now - lastTime < 900) return
        lastText = t
        lastTime = now

        val correct = Math.abs(real - shown) < 1e-6
        if (correct) tap(TRUE_X, TRUE_Y, w, h) else tap(FALSE_X, FALSE_Y, w, h)
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
