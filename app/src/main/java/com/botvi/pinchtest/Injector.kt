package com.botvi.pinchtest

import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/** يحقن أحداث لمس متعددة الأصابع عبر IInputManager بصلاحية shell من Shizuku (بدل sendevent). */
object Injector {
    private var im: Any? = null
    private var method: Method? = null

    fun init() {
        if (im != null) return
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("L")
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        im = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        method = Class.forName("android.hardware.input.IInputManager").methods
            .first { it.name == "injectInputEvent" }
    }

    private fun inject(ev: InputEvent): Boolean {
        val m = method!!
        val r = if (m.parameterTypes.size >= 3) m.invoke(im, ev, 0, -1) else m.invoke(im, ev, 0)
        return r as? Boolean ?: true
    }

    private fun touchDeviceId(): Int =
        InputDevice.getDeviceIds().firstOrNull {
            InputDevice.getDevice(it)?.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) == true
        } ?: 0

    /** قرصة أفقية حول مركز الشاشة: من مسافة fromHalf إلى toHalf بين كل إصبع والمركز (بكسل). */
    fun pinch(w: Int, h: Int, fromHalf: Float, toHalf: Float, steps: Int = 24, stepMs: Long = 16): String {
        init()
        val dev = touchDeviceId()
        val cx = w / 2f
        val cy = h / 2f
        val props = Array(2) { i ->
            MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        fun coords(half: Float) = Array(2) { i ->
            MotionEvent.PointerCoords().apply {
                x = if (i == 0) cx - half else cx + half
                y = cy; pressure = 1f; size = 1f
            }
        }
        val down = SystemClock.uptimeMillis()
        var fails = 0
        fun send(action: Int, count: Int, half: Float) {
            val ev = MotionEvent.obtain(
                down, SystemClock.uptimeMillis(), action, count, props, coords(half),
                0, 0, 1f, 1f, dev, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            if (!inject(ev)) fails++
            ev.recycle()
        }
        val shift = MotionEvent.ACTION_POINTER_INDEX_SHIFT
        send(MotionEvent.ACTION_DOWN, 1, fromHalf); Thread.sleep(40)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl shift), 2, fromHalf); Thread.sleep(40)
        for (i in 1..steps) {
            send(MotionEvent.ACTION_MOVE, 2, fromHalf + (toHalf - fromHalf) * i / steps)
            Thread.sleep(stepMs)
        }
        send(MotionEvent.ACTION_POINTER_UP or (1 shl shift), 2, toHalf); Thread.sleep(30)
        send(MotionEvent.ACTION_UP, 1, toHalf)
        return "جهاز اللمس=$dev، فشل الحقن في $fails حدث"
    }
}
