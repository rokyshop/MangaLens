package fr.mangalens.model

import android.graphics.Rect

data class TextRegion(
    val sourceText: String,
    val bounds: Rect,
    val translatedText: String
)
