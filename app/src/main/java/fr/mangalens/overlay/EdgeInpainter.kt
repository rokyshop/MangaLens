package fr.mangalens.overlay

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * V0 reconstruction for flat colors and gradients: interpolate from the 4
 * edges around a text rectangle. This is NOT generative inpainting and cannot
 * reliably restore line art, textured panels or detailed illustrations.
 */
object EdgeInpainter {
    fun reconstruct(screenshot: Bitmap, target: Rect): Bitmap? {
        val rect = Rect(target)
        if (!rect.intersect(0, 0, screenshot.width, screenshot.height)) return null
        val width = rect.width()
        val height = rect.height()
        if (width < 2 || height < 2) return null
        val pixels = IntArray(width * height)
        val maxX = screenshot.width - 1
        val maxY = screenshot.height - 1
        // Pre-read borders: avoid thousands of JNI Bitmap.getPixel calls per rectangle.
        val left = IntArray(height) { y ->
            screenshot.getPixel((rect.left - 3).coerceIn(0, maxX), rect.top + y)
        }
        val right = IntArray(height) { y ->
            screenshot.getPixel((rect.right + 2).coerceIn(0, maxX), rect.top + y)
        }
        val top = IntArray(width) { x ->
            screenshot.getPixel(rect.left + x, (rect.top - 3).coerceIn(0, maxY))
        }
        val bottom = IntArray(width) { x ->
            screenshot.getPixel(rect.left + x, (rect.bottom + 2).coerceIn(0, maxY))
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = BorderBlend.interpolate(
                    left[y], right[y], top[x], bottom[x],
                    x + 1, width - x, y + 1, height - y
                )
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
