package fr.mangalens.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import fr.mangalens.model.TextRegion
import kotlin.math.max
import kotlin.math.min

/**
 * Cached overlays are anchored to WebView document coordinates, NOT screen
 * coordinates. Scrolling therefore never destroys a translated bubble.
 * This handles normal WebView scrolling; pages with independent nested
 * scrollers need a rescan after their gesture completes.
 */
class TranslationOverlayView(context: Context) : View(context) {
    private data class Rendered(
        val documentBounds: Rect,
        val background: Bitmap,
        val translated: String,
        val textColor: Int
    )

    private val rendered = mutableListOf<Rendered>()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 27, 30)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private var scrollY = 0
    private var originalVisible = false
    private var cachedBytes = 0L
    private val maxCacheBytes = 24L * 1024 * 1024
    private val maxRegions = 280 // bound bitmap memory on very long chapters

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setDocumentScroll(y: Int) {
        if (scrollY == y) return
        scrollY = y
        invalidate()
    }

    fun clear() {
        rendered.forEach { it.background.recycle() }
        rendered.clear()
        cachedBytes = 0L
        invalidate()
    }

    fun revealOriginal(reveal: Boolean) {
        originalVisible = reveal
        invalidate()
    }

    fun hasRegionAt(viewportBounds: Rect, documentScroll: Int): Boolean {
        val candidate = Rect(viewportBounds).apply { offset(0, documentScroll) }
        return rendered.any { item ->
            val r = item.documentBounds
            val intersect = Rect(candidate)
            intersect.intersect(r) &&
                intersect.width().toLong() * intersect.height().toLong() >=
                    (candidate.width().toLong() * candidate.height() * 0.54).toLong()
        }
    }

    fun addRegion(screenshot: Bitmap, region: TextRegion, documentScroll: Int) {
        val viewportRect = Rect(region.bounds)
        val padX = max(4, (viewportRect.width() * 0.055f).toInt())
        val padY = max(3, (viewportRect.height() * 0.11f).toInt())
        viewportRect.inset(-padX, -padY)
        if (!viewportRect.intersect(0, 0, screenshot.width, screenshot.height)) return
        val background = EdgeInpainter.reconstruct(screenshot, viewportRect) ?: return
        val sample = background.getPixel(background.width / 2, background.height / 2)
        val luminance = 0.2126 * Color.red(sample) +
            0.7152 * Color.green(sample) + 0.0722 * Color.blue(sample)
        val foreground = if (luminance < 120) Color.WHITE else Color.rgb(27, 27, 30)
        val documentRect = Rect(viewportRect).apply { offset(0, documentScroll) }
        rendered += Rendered(documentRect, background, region.translatedText, foreground)
        cachedBytes += background.allocationByteCount.toLong()
        while (rendered.size > maxRegions || cachedBytes > maxCacheBytes) {
            val evicted = rendered.removeAt(0)
            cachedBytes -= evicted.background.allocationByteCount.toLong()
            evicted.background.recycle()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (originalVisible) return
        for (item in rendered) {
            val rect = Rect(item.documentBounds).apply { offset(0, -scrollY) }
            if (rect.bottom <= 0 || rect.top >= height || rect.right <= 0 || rect.left >= width) continue
            canvas.drawBitmap(item.background, null, RectF(rect), bitmapPaint)
            textPaint.color = item.textColor
            drawFittedText(canvas, item.translated, rect)
        }
    }

    private fun drawFittedText(canvas: Canvas, raw: String, region: Rect) {
        val text = raw.trim().replace(Regex("[\\t\\r\\n]+"), " ")
        if (text.isEmpty()) return
        val inset = max(2, (min(region.width(), region.height()) * 0.06f).toInt())
        val boxWidth = max(1, region.width() - inset * 2)
        val boxHeight = max(1, region.height() - inset * 2)
        var low = 8f
        var high = min(60f, max(14f, boxHeight * 0.75f))
        repeat(10) {
            val mid = (low + high) / 2
            textPaint.textSize = mid
            val trial = makeLayout(text, boxWidth)
            if (trial.height <= boxHeight && trial.lineCount <= 9) low = mid else high = mid
        }
        textPaint.textSize = low
        val layout = makeLayout(text, boxWidth)
        canvas.save()
        canvas.clipRect(region)
        canvas.translate((region.left + inset).toFloat(), region.top + inset + max(0, (boxHeight - layout.height) / 2).toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    private fun makeLayout(text: String, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }
}
