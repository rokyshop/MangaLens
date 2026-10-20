package fr.mangalens.scan

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView

/** Screenshots only visible web content, never fetching CDN assets or bypassing CORS. */
object ScreenCapture {
    fun capture(activity: Activity, view: WebView, callback: (Bitmap?, String?) -> Unit) {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0 || !view.isAttachedToWindow) {
            callback(null, "Page non visible")
            return
        }
        val bmp = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            callback(null, "Mémoire insuffisante pour capturer l'écran")
            return
        }
        val pos = IntArray(2)
        view.getLocationInWindow(pos)
        val rect = Rect(pos[0], pos[1], pos[0] + width, pos[1] + height)
        try {
            PixelCopy.request(activity.window, rect, bmp, { result ->
                if (result == PixelCopy.SUCCESS) callback(bmp, null)
                else fallback(view, bmp, callback)
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            fallback(view, bmp, callback)
        } catch (_: IllegalStateException) {
            fallback(view, bmp, callback)
        }
    }

    private fun fallback(view: WebView, bitmap: Bitmap, callback: (Bitmap?, String?) -> Unit) {
        try {
            view.draw(Canvas(bitmap))
            callback(bitmap, null)
        } catch (e: Exception) {
            bitmap.recycle()
            callback(null, e.localizedMessage ?: "Capture impossible")
        }
    }
}
