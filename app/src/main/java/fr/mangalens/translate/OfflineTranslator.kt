package fr.mangalens.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** On-device translator with model readiness and bounded translation-memory cache. */
class OfflineTranslator {
    private val clients = mutableMapOf<Pair<String, String>, Translator>()
    private val ready = mutableSetOf<Pair<String, String>>()
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 500
    }

    fun prepare(source: String, target: String, wifiOnly: Boolean, callback: (Exception?) -> Unit) {
        if (source == target) {
            callback(null)
            return
        }
        val key = source to target
        if (key in ready) {
            callback(null)
            return
        }
        val translator = clients.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }
        // Only download over mobile data if the user explicitly chose that option.
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                ready.add(key)
                callback(null)
            }
            .addOnFailureListener { callback(it) }
    }

    fun translate(source: String, target: String, text: String, callback: (String?, Exception?) -> Unit) {
        if (source == target) {
            callback(text, null)
            return
        }
        val cacheKey = "$source|$target|$text"
        cache[cacheKey]?.let {
            callback(it, null)
            return
        }
        val client = clients[source to target]
        if (client == null || (source to target) !in ready) {
            callback(null, IllegalStateException("Modèle de traduction non prêt"))
            return
        }
        client.translate(text)
            .addOnSuccessListener {
                cache[cacheKey] = it
                callback(it, null)
            }
            .addOnFailureListener { callback(null, it) }
    }

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
        ready.clear()
        cache.clear()
    }
}
