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
    private val ocr = OcrEngine()
    private val translator = OfflineTranslator()
    private var generation = 0
    private var scanning = false
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
    private val scrollRunnable = Runnable { requestScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(23, 25, 31)
        window.navigationBarColor = Color.rgb(23, 25, 31)
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
            setBackgroundColor(background)
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
            text = "ANDROID · V0.1"
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
            singleLine = true
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
            text = "Auto-défilement"
            textSize = 12f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = false
        }
        modesRow.addView(autoCheck, LinearLayout.LayoutParams(0, -1, 1f))
        originalButton = button("Voir l’original", false).apply { textSize = 12f }
        modesRow.addView(originalButton, LinearLayout.LayoutParams(dp(127), dp(34)))
        originalButton.setOnClickListener {
            showOriginal = !showOriginal
            overlay.revealOriginal(showOriginal)
            originalButton.text = if (showOriginal) "Voir traduction" else "Voir l’original"
        }
        val demo = button("Démo", false).apply { textSize = 11f }
        modesRow.addView(demo, LinearLayout.LayoutParams(dp(64), dp(34)).apply { marginStart = dp(5) })
        demo.setOnClickListener { loadDemo() }

        status = TextView(this).apply {
            text = "Prêt — choisir les langues puis Traduire"
            setTextColor(muted)
            textSize = 11f
            setPadding(dp(13), dp(4), dp(10), dp(5))
            maxLines = 2
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(34)))

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
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme?.lowercase(Locale.ROOT)
                    // Do not launch intent:, file:, javascript: or custom schemes.
                    return scheme != "https"
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
            setOnScrollChangeListener { _, _, _, _, _ -> invalidatePage(autoCheck.isChecked) }
            setOnTouchListener { _, motion ->
                when (motion.actionMasked) {
                    MotionEvent.ACTION_MOVE -> invalidatePage(autoCheck.isChecked)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (autoCheck.isChecked) queueAutoScan()
                    }
                }
                false // Preserve all scrolling, zoom, and taps inside WebView.
            }
        }
        browsingArea.addView(webView, FrameLayout.LayoutParams(-1, -1))
        overlay = TranslationOverlayView(this)
        browsingArea.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        sourceSpinner.onItemSelectedListener = languageChangedListener()
        targetSpinner.onItemSelectedListener = languageChangedListener()
        autoCheck.setOnCheckedChangeListener { _, checked ->
            if (checked) queueAutoScan()
            else handler.removeCallbacks(scrollRunnable)
        }
    }

    private fun languageChangedListener() = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (::overlay.isInitialized) invalidatePage(autoCheck.isChecked)
        }
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
        overlay.clear()
        handler.removeCallbacks(scrollRunnable)
        if (auto) queueAutoScan()
    }

    private fun queueAutoScan() {
        if (destroyed) return
        handler.removeCallbacks(scrollRunnable)
        handler.postDelayed(scrollRunnable, 950L)
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
        if (!prefs.getBoolean("model_download_consent", false)) {
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
        val scanId = ++generation
        showOriginal = false
        overlay.revealOriginal(false)
        originalButton.text = "Voir l’original"
        overlay.clear()
        translatedCount = 0
        translationFailures = 0
        status.text = "Capture de la page visible…"
        // Let the compositor display the unaltered page before PixelCopy.
        handler.postDelayed({
            if (scanId != generation || destroyed) { finishScan(); return@postDelayed }
            ScreenCapture.capture(this, webView) { bitmap, error ->
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
                    status.text = "${hits.size} zones — préparation des modèles de traduction…"
                    val wifiOnly = getPreferences(MODE_PRIVATE).getBoolean("wifi_only", true)
                    translator.prepare(src.tag, dst.tag, wifiOnly) { modelError ->
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
                        translateHits(scanId, bitmap, hits, src.tag, dst.tag, 0)
                    }
                }
            }
        }, 85L)
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
            status.text = "Terminé : $translatedCount/${hits.size} zones traduites" +
                if (translationFailures > 0) " ($translationFailures erreurs)" else ""
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
                overlay.addRegion(screenshot, TextRegion(hit.text, hit.bounds, translated))
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
        if (destroyed) return
        if (pending) {
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
