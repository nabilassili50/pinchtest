package com.botvi.pinchtest

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var logView: TextView
    private val ui = Handler(Looper.getMainLooper())

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        log(if (result == PackageManager.PERMISSION_GRANTED) "تم منح الإذن" else "رُفض الإذن")
    }

    private fun log(s: String) = ui.post { logView.append(s + "\n") }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }.also { root.addView(it) }
        btn("1) فحص Shizuku وطلب الإذن") { checkShizuku() }
        btn("2) تصغير القلعة بعد 8 ثوانٍ (افتح اللعبة فوراً)") { pinchLater(3) }
        btn("3) قرصة واحدة بعد 8 ثوانٍ") { pinchLater(1) }
        logView = TextView(this).apply { textSize = 15f }
        root.addView(ScrollView(this).apply { addView(logView) })
        setContentView(root)
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun checkShizuku() {
        if (!Shizuku.pingBinder()) { log("Shizuku لا يعمل: شغّله أولاً"); return }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) log("Shizuku جاهز والإذن ممنوح")
        else Shizuku.requestPermission(100)
    }

    private fun pinchLater(times: Int) {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            log("اضغط الزر 1 أولاً وامنح الإذن"); return
        }
        log("سأنفّذ بعد 8 ثوانٍ: انتقل إلى اللعبة (القلعة)")
        thread {
            try {
                Thread.sleep(8000)
                val p = Point(); @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealSize(p)
                val w = maxOf(p.x, p.y); val h = minOf(p.x, p.y)       // اللعبة أفقية دائماً
                repeat(times) {
                    val r = Injector.pinch(w, h, fromHalf = w * 0.30f, toHalf = w * 0.04f)
                    log("قرصة ${it + 1}: $r")
                    Thread.sleep(500)
                }
                log("انتهى. هل صغّرت القلعة؟")
            } catch (e: Throwable) {
                log("خطأ: " + (e.cause ?: e).toString())
            }
        }
    }
}
