package fr.mangalens.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class OcrScript { LATIN, JAPANESE, KOREAN, CHINESE }

data class ReaderLanguage(
    val label: String,
    val tag: String,
    val script: OcrScript
)

object Languages {
    val sources = listOf(
        ReaderLanguage("Anglais", TranslateLanguage.ENGLISH, OcrScript.LATIN),
        ReaderLanguage("Japonais", TranslateLanguage.JAPANESE, OcrScript.JAPANESE),
        ReaderLanguage("Coréen", TranslateLanguage.KOREAN, OcrScript.KOREAN),
        ReaderLanguage("Chinois", TranslateLanguage.CHINESE, OcrScript.CHINESE),
        ReaderLanguage("Français", TranslateLanguage.FRENCH, OcrScript.LATIN),
        ReaderLanguage("Espagnol", TranslateLanguage.SPANISH, OcrScript.LATIN)
    )
    val targets = listOf(
        ReaderLanguage("Français", TranslateLanguage.FRENCH, OcrScript.LATIN),
        ReaderLanguage("Anglais", TranslateLanguage.ENGLISH, OcrScript.LATIN),
        ReaderLanguage("Espagnol", TranslateLanguage.SPANISH, OcrScript.LATIN),
        ReaderLanguage("Allemand", TranslateLanguage.GERMAN, OcrScript.LATIN),
        ReaderLanguage("Italien", TranslateLanguage.ITALIAN, OcrScript.LATIN),
        ReaderLanguage("Portugais", TranslateLanguage.PORTUGUESE, OcrScript.LATIN)
    )
}
