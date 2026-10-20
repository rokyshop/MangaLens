package fr.mangalens

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import fr.mangalens.model.Languages
import fr.mangalens.model.ReaderLanguage
import fr.mangalens.model.TextRegion
import fr.mangalens.ocr.OcrEngine
import fr.mangalens.overlay.TranslationOverlayView
import fr.mangalens.scan.ScreenCapture
import fr.mangalens.translate.OfflineTranslator
import fr.mangalens.translate.QualityTranslator
import fr.mangalens.network.AdBlocker
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import java.util.Locale

/** A self-contained native browser, with a screenshot-based translation overlay. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var overlay: TranslationOverlayView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var autoCheck: CheckBox
    private lateinit var originalButton: Button
    private lateinit var adBlockButton: Button
    private lateinit var qualityButton: Button
    private val ocr = OcrEngine()
    private val translator = OfflineTranslator()
    private lateinit var qualityTranslator: QualityTranslator
    private val adBlockEnabled = AtomicBoolean(true)
    private var qualityEnabled = false
    private var touchStartY = 0f
    private var touchStartScroll = 0
    private var movedByTouch = false
    private var activeCaptureY = 0
    private var generation = 0
    private var scanning = false
    private var capturing = false
    private var imageSignature: String? = null
    private val imageWatcher = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (autoCheck.isChecked && !showOriginal) {
                webView.evaluateJavascript("""
                    JSON.stringify(Array.from(document.images).filter(i => {
                        const r = i.getBoundingClientRect();
                        return r.bottom > 0 && r.top < innerHeight;
                    }).map(i => [i.currentSrc, i.complete, i.naturalWidth,
                        Math.round(i.getBoundingClientRect().top + scrollY),
                        Math.round(i.getBoundingClientRect().height)]))
                """.trimIndent()) { value ->
                    if (!destroyed && imageSignature != value) {
                        imageSignature = value
                        if (autoCheck.isChecked && !showOriginal) queueAutoScan()
                    }
                }
            }
            handler.postDelayed(this, 2500L)
        }
    }
    private var pending = false
    private var destroyed = false
    private var showOriginal = false
    private var translatedCount = 0
    private var translationFailures = 0
    private val background = Color.rgb(48, 48, 48)
    private val panel = Color.rgb(39, 42, 47)
    private val accent = Color.rgb(92, 222, 255)
    private val white = Color.rgb(245, 246, 248)
    private val muted = Color.rgb(182, 189, 197)
    private val scrollRunnable = Runnable { if (autoCheck.isChecked && !showOriginal) requestScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(23, 25, 31)
        window.navigationBarColor = Color.rgb(23, 25, 31)
        qualityTranslator = QualityTranslator(this)
        val prefs = getPreferences(MODE_PRIVATE)
        adBlockEnabled.set(prefs.getBoolean("ad_block_enabled", true))
        qualityEnabled = prefs.getBoolean("quality_translation_enabled", false) && qualityTranslator.hasKey()
        buildUi()
        if (savedInstanceState == null) {
            val last = getPreferences(MODE_PRIVATE).getString("last_site", null)
            if (last == null) loadDemo() else navigate(last)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        setContentView(root)
        // Android 15+ enforces edge-to-edge for targetSdk 35: keep the toolbar
        // and the last manga panel clear of the status/navigation bars.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
            root.requestApplyInsets()
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(4))
        }
        root.addView(titleRow, LinearLayout.LayoutParams(-1, dp(42)))
        titleRow.addView(TextView(this).apply {
            text = "MangaLens"
            textSize = 20f
            setTextColor(accent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            text = "ANDROID · V0.3"
            textSize = 10f
            setTextColor(muted)
        })

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(3))
        }
        root.addView(navRow, LinearLayout.LayoutParams(-1, dp(52)))
        val backButton = button("‹", false)
        navRow.addView(backButton, LinearLayout.LayoutParams(dp(36), dp(43)))
        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(white)
            setHintTextColor(muted)
            hint = "https://site-de-lecture…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(10), 0, dp(9), 0)
            background = rounded(panel, 11f)
            setOnEditorActionListener { _, _, _ ->
                navigate(text.toString()); true
            }
        }
        navRow.addView(address, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        })
        val go = button("→", true)
        navRow.addView(go, LinearLayout.LayoutParams(dp(42), dp(43)))
        go.setOnClickListener { navigate(address.text.toString()) }

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(4))
        }
        root.addView(toolsRow, LinearLayout.LayoutParams(-1, dp(56)))
        sourceSpinner = spinner(Languages.sources.map { it.label })
        targetSpinner = spinner(Languages.targets.map { it.label })
        toolsRow.addView(sourceSpinner, LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { marginEnd = dp(5) })
        toolsRow.addView(TextView(this).apply {
            text = "→"
            textSize = 17f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(21), dp(42)))
        toolsRow.addView(targetSpinner, LinearLayout.LayoutParams(0, dp(42), 1.08f).apply { marginStart = dp(2); marginEnd = dp(5) })
        val translateButton = button("Traduire", true).apply { textSize = 12f }
        toolsRow.addView(translateButton, LinearLayout.LayoutParams(dp(86), dp(42)))
        translateButton.setOnClickListener { requestScan() }

        val modesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(4))
        }
        root.addView(modesRow, LinearLayout.LayoutParams(-1, dp(41)))
        autoCheck = CheckBox(this).apply {
            text = "Trad. continue"
            textSize = 12f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("continuous_translation", true)
        }
        modesRow.addView(autoCheck, LinearLayout.LayoutParams(0, -1, 1f))
        originalButton = button("Voir l’original", false).apply { textSize = 12f }
        modesRow.addView(originalButton, LinearLayout.LayoutParams(dp(127), dp(34)))
        originalButton.setOnClickListener {
            showOriginal = !showOriginal
            overlay.revealOriginal(showOriginal)
            originalButton.text = if (showOriginal) "Voir traduction" else "Voir l’original"
            if (!showOriginal && autoCheck.isChecked) queueAutoScan()
        }
        val demo = button("Démo", false).apply { textSize = 11f }
        modesRow.addView(demo, LinearLayout.LayoutParams(dp(64), dp(34)).apply { marginStart = dp(5) })
        demo.setOnClickListener { loadDemo() }

        val extraRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(3))
        }
        root.addView(extraRow, LinearLayout.LayoutParams(-1, dp(39)))
        adBlockButton = button("", false).apply { textSize = 12f }
        updateAdBlockLabel()
        extraRow.addView(adBlockButton, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(5) })
        adBlockButton.setOnClickListener {
            val enabled = !adBlockEnabled.get()
            adBlockEnabled.set(enabled)
            getPreferences(MODE_PRIVATE).edit().putBoolean("ad_block_enabled", enabled).apply()
            updateAdBlockLabel()
            status.text = "Anti-pub ${if (enabled) "activé" else "désactivé"} — recharge le site pour appliquer"
        }
        qualityButton = button("", false).apply { textSize = 12f }
        updateQualityLabel()
        extraRow.addView(qualityButton, LinearLayout.LayoutParams(0, dp(34), 1f))
        qualityButton.setOnClickListener {
            if (!qualityTranslator.hasKey()) showQualityKeyDialog() else {
                qualityEnabled = !qualityEnabled
                getPreferences(MODE_PRIVATE).edit().putBoolean("quality_translation_enabled", qualityEnabled).apply()
                updateQualityLabel()
                invalidatePage(autoCheck.isChecked)
                status.text = if (qualityEnabled) "Traduction IA activée (Internet, quota/API requis)" else "Traduction locale activée"
            }
        }
        qualityButton.setOnLongClickListener { showQualityKeyDialog(); true }

        status = TextView(this).apply {
            text = "Prêt — choisir les langues puis Traduire"
            setTextColor(muted)
            textSize = 11f
            setPadding(dp(13), dp(4), dp(10), dp(5))
            maxLines = 2
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(36)))

        val browsingArea = FrameLayout(this)
        root.addView(browsingArea, LinearLayout.LayoutParams(-1, 0, 1f))
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    // Called on a background WebView thread: only use AtomicBoolean + pure host check.
                    val req = request ?: return null
                    if (req.isForMainFrame || !adBlockEnabled.get() || !AdBlocker.shouldBlock(req.url.host)) return null
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    invalidatePage(false)
                }
                override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                    if (abs(oldScale - newScale) > 0.02f) invalidatePage(autoCheck.isChecked)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme?.lowercase(Locale.ROOT)
                    // Do not launch intent:, file:, javascript: or custom schemes.
                    return scheme != "https" ||
                        (adBlockEnabled.get() && AdBlocker.shouldBlock(request?.url?.host))
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (destroyed) return
                    if (url != null && url.startsWith("https://") && !url.contains("mangalens.example")) {
                        address.setText(url)
                        getPreferences(MODE_PRIVATE).edit().putString("last_site", url).apply()
                    }
                    invalidatePage(autoCheck.isChecked)
                    status.text = "Page chargée — prêt à traduire"
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) status.text = "Chargement impossible : ${error?.description ?: "erreur réseau"}"
                }
            }
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                if (::overlay.isInitialized) overlay.setDocumentScroll(scrollY)
                // Only a moving screenshot is invalid; OCR/translation already captured
                // remains anchored at activeCaptureY and can finish while reading.
                if (scanning) {
                    if (capturing) generation++
                    pending = autoCheck.isChecked && !showOriginal
                }
                if (autoCheck.isChecked && !showOriginal) queueAutoScan()
            }
            setOnTouchListener { _, motion ->
                when (motion.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = motion.y
                        touchStartScroll = scrollY
                        movedByTouch = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(motion.y - touchStartY) > dp(13)) movedByTouch = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (movedByTouch && touchStartScroll == scrollY) {
                            // Some sites scroll a nested DIV instead of WebView itself.
                            // Its internal offset is unknown: avoid drawing cached regions in wrong places.
                            invalidatePage(autoCheck.isChecked)
                        } else if (movedByTouch && autoCheck.isChecked && !showOriginal) {
                            queueAutoScan()
                        }
                        movedByTouch = false
                    }
                }
                false // All taps, gestures and scrolling continue through to the website.
            }
        }
        browsingArea.addView(webView, FrameLayout.LayoutParams(-1, -1))
        overlay = TranslationOverlayView(this)
        browsingArea.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        handler.postDelayed(imageWatcher, 2500L)
        sourceSpinner.onItemSelectedListener = languageChangedListener()
        targetSpinner.onItemSelectedListener = languageChangedListener()
        autoCheck.setOnCheckedChangeListener { _, checked ->
            getPreferences(MODE_PRIVATE).edit().putBoolean("continuous_translation", checked).apply()
            if (checked) queueAutoScan() else handler.removeCallbacks(scrollRunnable)
        }
    }

    private fun languageChangedListener() = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (::overlay.isInitialized) invalidatePage(autoCheck.isChecked)
        }
    }

    private fun updateAdBlockLabel() {
        adBlockButton.text = if (adBlockEnabled.get()) "Anti-pub : ON" else "Anti-pub : OFF"
    }

    private fun updateQualityLabel() {
        qualityButton.text = if (qualityEnabled) "Qualité IA : ON" else "Qualité IA : OFF"
    }

    private fun showQualityKeyDialog() {
        val field = EditText(this).apply {
            hint = "Clé API Gemini (Google AI Studio)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Traduction IA (facultative)")
            .setMessage("La clé est chiffrée sur ce téléphone. Seuls les dialogues OCR sont envoyés à Gemini, pas les images. Cette option utilise Internet et peut consommer un quota payant. Appui long sur le bouton IA pour modifier/supprimer la clé. Saisis une clé, ou laisse vide pour la supprimer.")
            .setView(field)
            .setPositiveButton("Enregistrer") { _, _ ->
                val entered = field.text.toString().trim()
                if (qualityTranslator.saveKey(entered)) {
                    qualityEnabled = entered.isNotEmpty()
                    getPreferences(MODE_PRIVATE).edit().putBoolean("quality_translation_enabled", qualityEnabled).apply()
                    updateQualityLabel()
                    invalidatePage(autoCheck.isChecked)
                    status.text = if (qualityEnabled) "Qualité IA activée" else "Clé supprimée, mode local"
                } else status.text = "Erreur de stockage sécurisé de la clé"
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun spinner(labels: List<String>): Spinner = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as TextView).setTextColor(white)
                    textSize = 12f
                    setPadding(dp(8), 0, dp(2), 0)
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as TextView).setTextColor(Color.BLACK)
                    textSize = 15f
                }
        }
        background = rounded(panel, 9f)
    }

    private fun button(label: String, primary: Boolean): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) Color.BLACK else white)
        background = rounded(if (primary) accent else panel, 10f)
        setPadding(dp(2), 0, dp(2), 0)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + 0.5f).toInt()

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) {
            status.text = "Entre l'adresse d'un site HTTPS"
            return
        }
        val normalized = if (value.startsWith("https://", true)) value else "https://$value"
        val parsed = try { Uri.parse(normalized) } catch (_: Exception) { null }
        if (parsed?.scheme != "https" || parsed.host.isNullOrBlank() ||
            parsed.host?.contains('.') != true || value.any { it.isWhitespace() }) {
            status.text = "Adresse invalide : utilise https://exemple.com"
            return
        }
        invalidatePage(false)
        status.text = "Chargement…"
        webView.loadUrl(normalized)
    }

    private fun loadDemo() {
        invalidatePage(false)
        address.setText("Démo locale — entre une URL pour lire un chapitre")
        webView.loadDataWithBaseURL("https://mangalens.example/", DEMO_HTML, "text/html", "UTF-8", null)
        status.text = "Démo ouverte — source : Anglais, cible : Français"
    }

    private fun invalidatePage(auto: Boolean) {
        if (destroyed || !::overlay.isInitialized) return
        generation++
        imageSignature = null
        pending = false
        overlay.clear()
        overlay.setDocumentScroll(webView.scrollY)
        handler.removeCallbacks(scrollRunnable)
        if (auto) queueAutoScan()
    }

    private fun queueAutoScan() {
        if (destroyed || !::autoCheck.isInitialized) return
        handler.removeCallbacks(scrollRunnable)
        handler.postDelayed(scrollRunnable, 650L)
    }

    private fun requestScan() {
        if (destroyed) return
        handler.removeCallbacks(scrollRunnable)
        if (scanning) {
            pending = true
            return
        }
        if (webView.width < 1 || webView.height < 1) return
        val src: ReaderLanguage = Languages.sources[sourceSpinner.selectedItemPosition]
        val dst: ReaderLanguage = Languages.targets[targetSpinner.selectedItemPosition]
        if (src.tag == dst.tag) {
            status.text = "Choisis deux langues différentes"
            return
        }
        val prefs = getPreferences(MODE_PRIVATE)
        if (!(qualityEnabled && qualityTranslator.hasKey()) && !prefs.getBoolean("model_download_consent", false)) {
            AlertDialog.Builder(this)
                .setTitle("Modèles de traduction")
                .setMessage("La première traduction peut télécharger des modèles d’environ 30 Mo par langue. Ensuite, la traduction s’effectue sur ton téléphone. Autoriser ces téléchargements ?")
                .setPositiveButton("Wi-Fi seulement") { _, _ ->
                    prefs.edit().putBoolean("model_download_consent", true)
                        .putBoolean("wifi_only", true).apply()
                    requestScan()
                }
                .setNeutralButton("Wi-Fi ou données mobiles") { _, _ ->
                    prefs.edit().putBoolean("model_download_consent", true)
                        .putBoolean("wifi_only", false).apply()
                    requestScan()
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        scanning = true
        capturing = true
        val scanId = ++generation
        showOriginal = false
        overlay.revealOriginal(false)
        originalButton.text = "Voir l’original"
        translatedCount = 0
        translationFailures = 0
        status.text = "Analyse de cette partie du chapitre…"
        activeCaptureY = webView.scrollY
        // PixelCopy captures the whole window, including our overlay: hide it first,
        // otherwise the OCR would read French translations instead of original dialogue.
        overlay.visibility = View.INVISIBLE
        handler.postDelayed({
            if (scanId != generation || destroyed) {
                overlay.visibility = View.VISIBLE
                finishScan()
                return@postDelayed
            }
            ScreenCapture.capture(this, webView) { bitmap, error ->
                capturing = false
                overlay.visibility = View.VISIBLE
                if (bitmap == null) {
                    if (scanId == generation) status.text = error ?: "Capture impossible"
                    finishScan()
                    return@capture
                }
                if (scanId != generation || destroyed) {
                    bitmap.recycle()
                    finishScan()
                    return@capture
                }
                status.text = "Recherche du texte (${src.label})…"
                ocr.scan(bitmap, src.script) { hits, scanError ->
                    if (scanId != generation || destroyed) {
                        bitmap.recycle()
                        finishScan()
                        return@scan
                    }
                    if (hits == null) {
                        status.text = "Reconnaissance échouée : ${scanError?.localizedMessage ?: "erreur"}"
                        bitmap.recycle()
                        finishScan()
                        return@scan
                    }
                    if (hits.isEmpty()) {
                        status.text = "Aucun texte reconnu. Vérifie la langue source ou zoome."
                        bitmap.recycle()
                        finishScan()
                        return@scan
                    }
                    val remaining = hits.filterNot { overlay.hasRegionAt(it.bounds, activeCaptureY) }
                    if (remaining.isEmpty()) {
                        status.text = "Cette partie est déjà traduite — continue à défiler"
                        bitmap.recycle()
                        finishScan()
                        return@scan
                    }
                    if (qualityEnabled && qualityTranslator.hasKey()) {
                        status.text = "Traduction IA : ${remaining.size} dialogues…"
                        qualityTranslator.translateBatch(src.tag, dst.tag, remaining.map { it.text }) { texts, error ->
                            if (scanId != generation || destroyed) {
                                bitmap.recycle()
                                finishScan()
                            } else if (texts != null) {
                                remaining.forEachIndexed { index, hit ->
                                    overlay.addRegion(bitmap, TextRegion(hit.text, hit.bounds, texts[index]), activeCaptureY)
                                }
                                status.text = "IA : ${remaining.size} zones traduites — défile pour continuer"
                                bitmap.recycle()
                                finishScan()
                            } else {
                                status.text = "IA indisponible (${error ?: "erreur"}) — repli local…"
                                translateOffline(scanId, bitmap, remaining, src.tag, dst.tag)
                            }
                        }
                    } else {
                        translateOffline(scanId, bitmap, remaining, src.tag, dst.tag)
                    }
                }
            }
        }, 85L)
    }

    private fun translateOffline(scanId: Int, bitmap: android.graphics.Bitmap, hits: List<OcrEngine.Hit>, source: String, target: String) {
        status.text = "${hits.size} zones — préparation traduction locale…"
        val wifiOnly = getPreferences(MODE_PRIVATE).getBoolean("wifi_only", true)
        translator.prepare(source, target, wifiOnly) { modelError ->
            if (scanId != generation || destroyed) {
                bitmap.recycle()
                finishScan()
                return@prepare
            }
            if (modelError != null) {
                status.text = "Modèle indisponible : ${modelError.localizedMessage}"
                bitmap.recycle()
                finishScan()
                return@prepare
            }
            translateHits(scanId, bitmap, hits, source, target, 0)
        }
    }

    private fun translateHits(
        scanId: Int,
        screenshot: android.graphics.Bitmap,
        hits: List<OcrEngine.Hit>,
        source: String,
        target: String,
        index: Int
    ) {
        if (scanId != generation || destroyed) {
            screenshot.recycle()
            finishScan()
            return
        }
        if (index >= hits.size) {
            status.text = "Traduit : $translatedCount/${hits.size} zones — défile pour continuer" +
                (if (translationFailures > 0) " ($translationFailures erreurs)" else "")
            screenshot.recycle()
            finishScan()
            return
        }
        val hit = hits[index]
        translator.translate(source, target, hit.text) { translated, error ->
            if (scanId != generation || destroyed) {
                screenshot.recycle()
                finishScan()
                return@translate
            }
            if (error == null && !translated.isNullOrBlank()) {
                overlay.addRegion(screenshot, TextRegion(hit.text, hit.bounds, translated), activeCaptureY)
                translatedCount++
            } else {
                translationFailures++
            }
            status.text = "Traduction : ${index + 1}/${hits.size} zones…"
            translateHits(scanId, screenshot, hits, source, target, index + 1)
        }
    }

    private fun finishScan() {
        scanning = false
        capturing = false
        if (destroyed) return
        if (pending && !showOriginal) {
            pending = false
            handler.removeCallbacks(scrollRunnable)
            handler.postDelayed({ requestScan() }, 150L)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Android back dispatcher migration planned")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        destroyed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        ocr.close()
        translator.close()
        qualityTranslator.close()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private val DEMO_HTML = """
            <!doctype html><html lang="en"><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              *{box-sizing:border-box}body{margin:0;background:#d6dce8;color:#1b2534;font-family:Arial,sans-serif}
              .cap{background:#121826;color:white;text-align:center;padding:16px;font-size:13px}
              .panel{max-width:550px;margin:0 auto;background:linear-gradient(145deg,#afc7da,#f3d5c6);min-height:600px;padding:38px 18px}
              .bubble{border:3px solid #15191e;border-radius:45% 45% 43% 43%;background:white;text-align:center;padding:24px 25px;max-width:290px;margin:24px auto;font-weight:bold;font-size:22px;line-height:1.4}
              .shadow{border-radius:18px;background:linear-gradient(125deg,#f3d5c6,#b1bfcf);padding:16px 12px;margin-top:55px;font-size:20px;text-align:center}
              .panel2{background:linear-gradient(90deg,#272f4e,#48617f);min-height:650px;color:#fff;padding:30px 10px}
              .narr{margin:34px auto;background:#f2e4ce;color:#2b211d;border:2px solid #463b2b;padding:22px 14px;text-align:center;font-weight:bold;font-size:22px;max-width:300px}
              .hint{padding:18px;font-size:15px;text-align:center;color:#f4fbff}
            </style></head><body>
            <div class="cap">MANGALENS • CHAPITRE DE DÉMONSTRATION</div>
            <div class="panel"><div class="bubble">Where are we going now?</div>
            <div class="bubble">We have to save the village!</div>
            <div class="shadow">I can hear something behind the trees.</div></div>
            <div class="panel2"><div class="narr">The adventure begins at sunrise.</div>
            <div class="narr">To be continued...</div>
            <div class="hint">Scroll down to test auto-translation.</div></div>
            </body></html>
        """.trimIndent()
    }
}
