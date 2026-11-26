package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.service.PulsoRadioService
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingContainer: LinearLayout

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private var isEmitterSilentMode: Boolean = false
    private val savedVolumes = mutableMapOf<Int, Int>()
    private var fallbackLoaded: Boolean = false

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Resolver solicitudes pendientes del WebView (micrófono / cámara)
        pendingWebPermissionRequest?.let { request ->
            val grantedResources = mutableListOf<String>()
            request.resources.forEach { res ->
                when (res) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                            grantedResources.add(res)
                        }
                    }
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        if (hasPermission(Manifest.permission.CAMERA)) {
                            grantedResources.add(res)
                        }
                    }
                }
            }
            if (grantedResources.isNotEmpty()) {
                request.grant(grantedResources.toTypedArray())
            } else {
                request.deny()
            }
            pendingWebPermissionRequest = null
        }

        // Resolver solicitud pendiente de geolocalización
        val geoCallback = pendingGeoCallback
        val geoOrigin = pendingGeoOrigin
        if (geoCallback != null && geoOrigin != null) {
            val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            geoCallback.invoke(geoOrigin, hasLocation, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

        // Notificar a la web el estado actualizado de permisos nativos
        val statusJson = buildPermissionsJson().toString()
        webView.post {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('pulsoNativePermissionsUpdated',{detail:$statusJson}));",
                null
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val rootContainer: View = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.pulsoWebView)
        loadingContainer = findViewById(R.id.loadingContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupBackNavigation()
        configureWebView()
        requestEssentialRuntimePermissions()

        val anthiParams = extractAndPersistAnthiParams(intent)
        loadPulsoWebApp(anthiParams)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val updatedParams = extractAndPersistAnthiParams(intent)
        val paramsJson = JSONObject(updatedParams).toString()
        webView.post {
            webView.evaluateJavascript(
                "if(window.onAnthiDeepLinkUpdate){window.onAnthiDeepLinkUpdate($paramsJson);}",
                null
            )
        }
        if (updatedParams["role"].equals("ALARM_EMITTER", ignoreCase = true)) {
            applyStrictEmitterSilentMode(true)
        }
    }

    override fun onDestroy() {
        if (isEmitterSilentMode) {
            applyStrictEmitterSilentMode(false)
        }
        webView.removeJavascriptInterface(JS_BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::webView.isInitialized && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.parseColor("#070B12"))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }

        webView.addJavascriptInterface(AndroidPulsoBridge(), JS_BRIDGE_NAME)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                if (isTrustedPulsoUri(uri)) {
                    return false
                }
                // Abrir enlaces externos en el navegador del sistema sin exponer AndroidPulsoBridge
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingContainer.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !fallbackLoaded) {
                    loadLocalBundledFallback()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val requested = request.resources ?: emptyArray()
                    val grantedNow = mutableListOf<String>()
                    val missingAndroidPerms = mutableListOf<String>()

                    requested.forEach { res ->
                        when (res) {
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                    grantedNow.add(res)
                                } else {
                                    missingAndroidPerms.add(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                if (hasPermission(Manifest.permission.CAMERA)) {
                                    grantedNow.add(res)
                                } else {
                                    missingAndroidPerms.add(Manifest.permission.CAMERA)
                                }
                            }
                        }
                    }

                    if (missingAndroidPerms.isEmpty()) {
                        if (grantedNow.isNotEmpty()) {
                            request.grant(grantedNow.toTypedArray())
                        } else {
                            request.deny()
                        }
                    } else {
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = request
                        runtimePermissionsLauncher.launch(missingAndroidPerms.distinct().toTypedArray())
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val hasLoc = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (hasLoc) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    runtimePermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }

    private fun isTrustedPulsoUri(uri: Uri): Boolean {
        if (uri.scheme == "file" && uri.path?.startsWith("/android_asset/") == true) {
            return true
        }
        val trustedHost = Uri.parse(PULSO_WEB_URL).host
        return uri.scheme == "https" && !trustedHost.isNullOrBlank() && uri.host == trustedHost
    }

    private fun loadPulsoWebApp(params: Map<String, String>) {
        fallbackLoaded = false
        val targetUrl = appendAnthiQueryParams(PULSO_WEB_URL, params)
        webView.loadUrl(targetUrl)

        // Si el dominio remoto aún no está desplegado, conmutar rápido al módulo táctico empaquetado
        webView.postDelayed({
            if (!fallbackLoaded && loadingContainer.visibility == View.VISIBLE) {
                webView.stopLoading()
                loadLocalBundledFallback()
            }
        }, 1500L)
    }

    private fun loadLocalBundledFallback() {
        fallbackLoaded = true
        val params = readPersistedAnthiParams()
        val fallbackUrl = appendAnthiQueryParams(LOCAL_ASSET_FALLBACK_URL, params)
        webView.loadUrl(fallbackUrl)
    }

    private fun appendAnthiQueryParams(baseUrl: String, params: Map<String, String>): String {
        val builder = Uri.parse(baseUrl).buildUpon()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) {
                builder.appendQueryParameter(key, value)
            }
        }
        return builder.build().toString()
    }

    private fun extractAndPersistAnthiParams(intent: Intent?): Map<String, String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uri = intent?.data
        val keys = listOf("uid", "alias", "role", "activeEvent", "confirmedEvents")

        val resolved = mutableMapOf<String, String>()
        val editor = prefs.edit()

        keys.forEach { key ->
            val fromUri = uri?.getQueryParameter(key)
            val fromExtra = intent?.getStringExtra(key)
            val fromPrefs = prefs.getString(key, null)
            val finalValue = when {
                !fromUri.isNullOrBlank() -> fromUri
                !fromExtra.isNullOrBlank() -> fromExtra
                !fromPrefs.isNullOrBlank() -> fromPrefs
                else -> defaultParamValue(key)
            }
            resolved[key] = finalValue
            editor.putString(key, finalValue)
        }
        editor.apply()
        return resolved
    }

    private fun readPersistedAnthiParams(): Map<String, String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = listOf("uid", "alias", "role", "activeEvent", "confirmedEvents")
        return keys.associateWith { key ->
            prefs.getString(key, null) ?: defaultParamValue(key)
        }
    }

    private fun defaultParamValue(key: String): String = when (key) {
        "uid" -> "anthi_driver_07"
        "alias" -> "Móvil 07"
        "role" -> "DRIVER_UNCONFIRMED"
        "activeEvent" -> "1"
        "confirmedEvents" -> ""
        else -> ""
    }

    private fun requestEssentialRuntimePermissions() {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { !hasPermission(it) }

        if (required.isNotEmpty()) {
            runtimePermissionsLauncher.launch(required.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Regla crítica de seguridad del Emisor en Alarma:
     * Fuerza el silenciado total de salida multimedia/altavoz para garantizar
     * 0% reproducción de audio entrante, manteniendo intacta la captura de mic, cámara y GPS.
     */
    private fun applyStrictEmitterSilentMode(enable: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val outputStreams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_SYSTEM
        )

        if (enable) {
            if (!isEmitterSilentMode) {
                outputStreams.forEach { stream ->
                    savedVolumes[stream] = audioManager.getStreamVolume(stream)
                }
            }
            isEmitterSilentMode = true
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            outputStreams.forEach { stream ->
                try {
                    audioManager.setStreamVolume(stream, 0, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    }
                } catch (_: SecurityException) {
                    // Algunas políticas de DND restringen STREAM_RING en ciertos dispositivos
                }
            }
        } else {
            isEmitterSilentMode = false
            outputStreams.forEach { stream ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    }
                    savedVolumes[stream]?.let { prevVol ->
                        audioManager.setStreamVolume(stream, prevVol, 0)
                    }
                } catch (_: SecurityException) {
                }
            }
            savedVolumes.clear()
        }
    }

    private fun buildPermissionsJson(): JSONObject {
        return JSONObject().apply {
            put("camera", hasPermission(Manifest.permission.CAMERA))
            put("recordAudio", hasPermission(Manifest.permission.RECORD_AUDIO))
            put("fineLocation", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            put("coarseLocation", hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            put(
                "postNotifications",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    true
                }
            )
            put("emitterSilentMode", isEmitterSilentMode)
            put("tacticalServiceRunning", PulsoRadioService.isRunning)
        }
    }

    /**
     * Puente nativo expuesto a la app web de Pulso M34 bajo `window.AndroidPulsoBridge`.
     */
    inner class AndroidPulsoBridge {

        @JavascriptInterface
        fun startTacticalService(role: String?, eventId: String?) {
            runOnUiThread {
                val safeRole = role.orEmpty().ifBlank { "OPERADOR" }
                val safeEventId = eventId.orEmpty().ifBlank { "1" }
                val intent = Intent(this@MainActivity, PulsoRadioService::class.java).apply {
                    putExtra(PulsoRadioService.EXTRA_ROLE, safeRole)
                    putExtra(PulsoRadioService.EXTRA_EVENT_ID, safeEventId)
                    putExtra(PulsoRadioService.EXTRA_EMITTER_SILENT, isEmitterSilentMode)
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }

        @JavascriptInterface
        fun stopTacticalService() {
            runOnUiThread {
                val stopIntent = Intent(this@MainActivity, PulsoRadioService::class.java).apply {
                    action = PulsoRadioService.ACTION_STOP
                }
                startService(stopIntent)
            }
        }

        @JavascriptInterface
        fun setEmitterSilentMode(enabled: Boolean) {
            runOnUiThread {
                applyStrictEmitterSilentMode(enabled)
                if (enabled && PulsoRadioService.isRunning) {
                    startTacticalService("ALARM_EMITTER", readPersistedAnthiParams()["activeEvent"])
                }
            }
        }

        @JavascriptInterface
        fun getNativePermissionsStatus(): String {
            return buildPermissionsJson().toString()
        }

        @JavascriptInterface
        fun getApkVersion(): String {
            return JSONObject().apply {
                put("versionName", BuildConfig.VERSION_NAME)
                put("versionCode", BuildConfig.VERSION_CODE)
                put("applicationId", BuildConfig.APPLICATION_ID)
                put("sdkInt", Build.VERSION.SDK_INT)
            }.toString()
        }

        @JavascriptInterface
        fun getAnthiLaunchParams(): String {
            return JSONObject(readPersistedAnthiParams()).toString()
        }
    }

    companion object {
        const val PULSO_WEB_URL = "https://pulso-m34.anthi.app"
        private const val LOCAL_ASSET_FALLBACK_URL = "file:///android_asset/pulso_m34.html"
        private const val JS_BRIDGE_NAME = "AndroidPulsoBridge"
        private const val PREFS_NAME = "pulso_m34_anthi_prefs"
    }
}
