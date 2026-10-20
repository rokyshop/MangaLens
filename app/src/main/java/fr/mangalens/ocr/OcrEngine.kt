package fr.mangalens.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.mangalens.model.OcrScript
import kotlin.math.min

/** Two-pass OCR: original + capped upscaled/contrast-enhanced image. */
class OcrEngine {
    data class Hit(val text: String, val bounds: Rect)
    private val clients = mutableMapOf<OcrScript, TextRecognizer>()

    private fun recognizer(script: OcrScript): TextRecognizer = clients.getOrPut(script) {
        when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    /** Overlapping tiles allow small lettering to be enlarged even on tall screens. */
    fun scan(bitmap: Bitmap, script: OcrScript, callback: (List<Hit>?, Exception?) -> Unit) {
        scanPass(bitmap, script) { initial, error ->
            if (initial == null) { callback(null, error); return@scanPass }
            if (bitmap.height < 1000) { callback(initial, null); return@scanPass }
            var combined = initial
            val tileHeight = min(800, bitmap.height)
            fun next(top: Int) {
                if (top >= bitmap.height) { callback(combined, null); return }
                val height = min(tileHeight, bitmap.height - top)
                val tile = try { Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height) }
                    catch (_: OutOfMemoryError) { callback(combined, null); return }
                scanPass(tile, script) { hits, _ ->
                    val shifted = hits.orEmpty().map { hit ->
                        Hit(hit.text, Rect(hit.bounds).apply { offset(0, top) })
                    }.filter { hit ->
                        // Do not replace a complete bubble with an edge-clipped fragment.
                        (top == 0 || hit.bounds.top > top + 6) &&
                            (top + height == bitmap.height || hit.bounds.bottom < top + height - 6)
                    }
                    combined = merge(combined, shifted)
                    tile.recycle()
                    next(if (top + height == bitmap.height) bitmap.height else top + height - 180)
                }
            }
            next(0)
        }
    }

    private fun scanPass(bitmap: Bitmap, script: OcrScript, callback: (List<Hit>?, Exception?) -> Unit) {
        val client = recognizer(script)
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { initial ->
                val baseline = extract(initial.textBlocks.mapNotNull { b ->
                    val rect = b.boundingBox ?: return@mapNotNull null
                    Hit(b.text.trim(), Rect(rect))
                }, bitmap.width, bitmap.height)
                // Avoid giant allocations on high-resolution screens. At most 3.7M pixels.
                val scale = min(1.75f, min(1800f / bitmap.width, kotlin.math.sqrt(3700000.0 / (bitmap.width.toDouble() * bitmap.height)).toFloat()))
                if (scale <= 1.15f) {
                    callback(baseline, null)
                    return@addOnSuccessListener
                }
                val enhanced = try {
                    val bmp = Bitmap.createBitmap((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        // Boost light/dark separation on small outlined lettering.
                        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                            1.22f, 0f, 0f, 0f, -28f,
                            0f, 1.22f, 0f, 0f, -28f,
                            0f, 0f, 1.22f, 0f, -28f,
                            0f, 0f, 0f, 1f, 0f
                        )))
                    }
                    c.scale(scale, scale)
                    c.drawBitmap(bitmap, 0f, 0f, p)
                    bmp
                } catch (_: OutOfMemoryError) {
                    callback(baseline, null)
                    return@addOnSuccessListener
                }
                client.process(InputImage.fromBitmap(enhanced, 0))
                    .addOnSuccessListener { second ->
                        val extra = extract(second.textBlocks.mapNotNull { b ->
                            val box = b.boundingBox ?: return@mapNotNull null
                            Hit(b.text.trim(), Rect(
                                (box.left / scale).toInt(), (box.top / scale).toInt(),
                                (box.right / scale).toInt(), (box.bottom / scale).toInt()
                            ))
                        }, bitmap.width, bitmap.height)
                        enhanced.recycle()
                        callback(merge(baseline, extra), null)
                    }
                    .addOnFailureListener {
                        enhanced.recycle()
                        callback(baseline, null) // first pass still useful
                    }
            }
            .addOnFailureListener { callback(null, it) }
    }

    private fun extract(hits: List<Hit>, width: Int, height: Int): List<Hit> = hits.filter { h ->
        val b = h.bounds
        h.text.length in 2..500 && b.width() >= 14 && b.height() >= 11 &&
            b.width() < width * .96 && b.height() < height * .55 && h.text.any { it.isLetter() }
    }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).take(60)

    private fun merge(original: List<Hit>, enhanced: List<Hit>): List<Hit> {
        val out = original.toMutableList()
        for (candidate in enhanced) {
            val overlapping = out.indexOfFirst { h ->
                val intersection = Rect(h.bounds)
                if (!intersection.intersect(candidate.bounds)) false
                else {
                    val area = intersection.width().toLong() * intersection.height()
                    val smaller = min(h.bounds.width().toLong() * h.bounds.height(), candidate.bounds.width().toLong() * candidate.bounds.height())
                    area >= smaller * 0.55
                }
            }
            if (overlapping < 0) out += candidate
            else {
                val previous = out[overlapping]
                // Keep a legible original unless the second pass recovered substantial missing text.
                val oldLetters = previous.text.count { it.isLetter() }
                val newLetters = candidate.text.count { it.isLetter() }
                if (newLetters > oldLetters * 1.25 && candidate.text.count { !it.isLetterOrDigit() && !it.isWhitespace() } <= candidate.text.length / 4) {
                    out[overlapping] = candidate
                }
            }
        }
        return out.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).take(60)
    }

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
