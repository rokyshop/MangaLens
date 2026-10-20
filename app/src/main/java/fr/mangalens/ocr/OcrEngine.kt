package fr.mangalens.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.mangalens.model.OcrScript

/** Retains and closes one recognizer per writing system. */
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

    fun scan(
        bitmap: Bitmap,
        script: OcrScript,
        callback: (List<Hit>?, Exception?) -> Unit
    ) {
        recognizer(script).process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val hits = result.textBlocks.mapNotNull { block ->
                    val rect = block.boundingBox ?: return@mapNotNull null
                    val content = block.text.trim()
                    if (content.length < 2 || content.length > 500 ||
                        rect.width() < 16 || rect.height() < 12 ||
                        rect.width() > bitmap.width * 0.95 ||
                        rect.height() > bitmap.height * 0.50 ||
                        !content.any { it.isLetter() }
                    ) null else Hit(content, Rect(rect))
                }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).take(32)
                callback(hits, null)
            }
            .addOnFailureListener { callback(null, it) }
    }

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
