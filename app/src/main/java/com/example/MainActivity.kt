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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.firebase.AdminGrant
import com.example.firebase.AdminInvite
import com.example.firebase.AnthiAuthManager
import com.example.firebase.AnthiFirestoreRepository
import com.example.firebase.AnthiHierarchyLevel
import com.example.firebase.TacticalEvent
import com.example.firebase.UserProfile
import com.example.firebase.authStateFlow
import com.example.service.PulsoRadioService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var authContainer: LinearLayout
    private lateinit var authErrorText: TextView
    private lateinit var authProgress: ProgressBar
    private lateinit var btnGoogleSignIn: Button

    private lateinit var authManager: AnthiAuthManager
    private lateinit var firestoreRepository: AnthiFirestoreRepository

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private var isEmitterSilentMode: Boolean = false
    private val savedVolumes = mutableMapOf<Int, Int>()
    private var fallbackLoaded: Boolean = false
    private var webPageReady: Boolean = false

    private var observedUid: String? = null
    private var firestoreJobs = mutableListOf<Job>()
    private var activeEvents: List<TacticalEvent> = emptyList()
    private var currentAdminGrant: AdminGrant? = null
    private var currentProfile: UserProfile? = null
    private var activeAdminInvites: List<AdminInvite> = emptyList()

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
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

        val geoCallback = pendingGeoCallback
        val geoOrigin = pendingGeoOrigin
        if (geoCallback != null && geoOrigin != null) {
            val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            geoCallback.invoke(geoOrigin, hasLocation, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

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

        authManager = AnthiAuthManager(applicationContext)
        firestoreRepository = AnthiFirestoreRepository(applicationContext)

        val rootContainer: View = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.pulsoWebView)
        loadingContainer = findViewById(R.id.loadingContainer)
        authContainer = findViewById(R.id.authContainer)
        authErrorText = findViewById(R.id.authErrorText)
        authProgress = findViewById(R.id.authProgress)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupBackNavigation()
        setupAuthUi()
        configureWebView()
        requestEssentialRuntimePermissions()

        val anthiParams = extractAndPersistAnthiParams(intent)
        if (isEmitterRole(anthiParams["role"]) || isM34SkullTrigger(anthiParams)) {
            applyStrictEmitterSilentMode(true)
            startBackgroundTacticalService(
                role = "ALARM_EMITTER",
                eventId = resolveEventIdParam(anthiParams),
                emitterSilent = true
            )
        }

        observeAuthState(anthiParams)
        lifecycleScope.launch {
            if (Firebase.auth.currentUser == null) {
                authManager.attemptAutoSignIn()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val updatedParams = extractAndPersistAnthiParams(intent)
        val paramsJson = JSONObject(updatedParams).toString()
        if (webPageReady) {
            webView.post {
                webView.evaluateJavascript(
                    "if(window.onAnthiDeepLinkUpdate){window.onAnthiDeepLinkUpdate($paramsJson);}",
                    null
                )
            }
        }
        val emitterActive = isEmitterRole(updatedParams["role"]) || isM34SkullTrigger(updatedParams)
        applyStrictEmitterSilentMode(emitterActive)
        if (emitterActive) {
            startBackgroundTacticalService(
                role = "ALARM_EMITTER",
                eventId = resolveEventIdParam(updatedParams),
                emitterSilent = true
            )
        }
        Firebase.auth.currentUser?.let {
            lifecycleScope.launch {
                syncAnthiUserAndAlarmIfNeeded(updatedParams)
            }
        }
    }

    override fun onDestroy() {
        stopFirestoreObservers()
        // Mantener silencio estricto y servicio activo si hay un evento Emisor SOS corriendo en 2º plano
        if (isEmitterSilentMode && !PulsoRadioService.isRunning) {
            applyStrictEmitterSilentMode(false)
        }
        webView.removeJavascriptInterface(JS_BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    private fun setupAuthUi() {
        btnGoogleSignIn.setOnClickListener {
            setAuthLoading(true, null)
            lifecycleScope.launch {
                val result = authManager.signInInteractive(this@MainActivity)
                result.onSuccess {
                    setAuthLoading(false, null)
                }.onFailure { error ->
                    val message = if (error is GetCredentialCancellationException) {
                        "Inicio de sesión cancelado. Selecciona una cuenta Google vinculada a ANTHI para continuar."
                    } else {
                        error.localizedMessage ?: "No se pudo autenticar con Google."
                    }
                    setAuthLoading(false, message)
                }
            }
        }
    }

    private fun setAuthLoading(loading: Boolean, errorMessage: String?) {
        btnGoogleSignIn.isEnabled = !loading
        authProgress.visibility = if (loading) View.VISIBLE else View.GONE
        if (errorMessage.isNullOrBlank()) {
            authErrorText.visibility = View.GONE
        } else {
            authErrorText.text = errorMessage
            authErrorText.visibility = View.VISIBLE
        }
    }

    private fun observeAuthState(initialAnthiParams: Map<String, String>) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Firebase.auth.authStateFlow().collect { user ->
                    if (user == null) {
                        stopFirestoreObservers()
                        observedUid = null
                        loadingContainer.visibility = View.GONE
                        authContainer.visibility = View.VISIBLE
                    } else {
                        authContainer.visibility = View.GONE
                        val uid = user.uid
                        if (observedUid != uid) {
                            observedUid = uid
                            val params = readPersistedAnthiParams().ifEmpty { initialAnthiParams }
                            ensureProfileAndObserve(uid, params)
                            if (!webPageReady) {
                                loadingContainer.visibility = View.VISIBLE
                                loadPulsoWebApp(params)
                            } else {
                                pushFirebaseStateToWeb()
                            }
                        } else {
                            pushFirebaseStateToWeb()
                        }
                    }
                }
            }
        }
    }

    private fun ensureProfileAndObserve(uid: String, params: Map<String, String>) {
        stopFirestoreObservers()
        lifecycleScope.launch {
            syncAnthiUserAndAlarmIfNeeded(params)
            if (firestoreRepository.isOwnerUser()) {
                firestoreRepository.seedInitialEventsIfAllowed()
            }
        }

        val eventsJob = lifecycleScope.launch {
            firestoreRepository.observeActiveEvents()
                .catch { error ->
                    if (error is CancellationException) throw error
                    pushActionResult("observeEvents", false, error.localizedMessage ?: "Error leyendo eventos.")
                }
                .collect { events ->
                    activeEvents = events
                    pushFirebaseStateToWeb()
                }
        }

        val adminJob = lifecycleScope.launch {
            firestoreRepository.observeAdminGrant(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { grant ->
                    currentAdminGrant = grant
                    pushFirebaseStateToWeb()
                }
        }

        val profileJob = lifecycleScope.launch {
            firestoreRepository.observeUserProfile(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { profile ->
                    currentProfile = profile
                    pushFirebaseStateToWeb()
                }
        }

        firestoreJobs.addAll(listOf(eventsJob, adminJob, profileJob))

        if (firestoreRepository.isOwnerUser()) {
            val invitesJob = lifecycleScope.launch {
                firestoreRepository.observeAdminInvites()
                    .catch { error ->
                        if (error is CancellationException) throw error
                    }
                    .collect { invites ->
                        activeAdminInvites = invites
                        pushFirebaseStateToWeb()
                    }
            }
            firestoreJobs.add(invitesJob)
        }
    }

    private fun stopFirestoreObservers() {
        firestoreJobs.forEach { it.cancel() }
        firestoreJobs.clear()
        activeEvents = emptyList()
        currentAdminGrant = null
        currentProfile = null
        activeAdminInvites = emptyList()
    }

    private fun currentHierarchyLevel(): AnthiHierarchyLevel {
        if (firestoreRepository.isOwnerUser()) return AnthiHierarchyLevel.SUPERADMIN
        if (currentAdminGrant?.role == "ADMIN") return AnthiHierarchyLevel.ADMIN
        return AnthiHierarchyLevel.DRIVER
    }

    private fun buildFirebaseStateJson(): JSONObject {
        val user = Firebase.auth.currentUser
        val hierarchy = currentHierarchyLevel()
        val isOwner = hierarchy == AnthiHierarchyLevel.SUPERADMIN
        val isAdmin = hierarchy == AnthiHierarchyLevel.ADMIN || isOwner

        val eventsArray = JSONArray()
        activeEvents.forEach { ev ->
            eventsArray.put(
                JSONObject().apply {
                    put("id", ev.id)
                    put("number", ev.eventNumber)
                    put("code", ev.code)
                    put("callsign", ev.callsign)
                    put("sector", ev.sector)
                    put("lat", ev.lat)
                    put("lng", ev.lng)
                    put("bearing", ev.bearing)
                    put("speedKmh", ev.speedKmh)
                    put("audioDb", ev.audioDb)
                    put("status", ev.status)
                    put("createdBy", ev.createdBy)
                }
            )
        }

        val invitesArray = JSONArray()
        if (isOwner) {
            activeAdminInvites.forEach { inv ->
                invitesArray.put(
                    JSONObject().apply {
                        put("hash", inv.id)
                        put("label", inv.label)
                        put("active", inv.active)
                    }
                )
            }
        }

        val confirmedIdsArray = JSONArray()
        currentProfile?.confirmedEventIds.orEmpty().forEach { confirmedIdsArray.put(it) }

        return JSONObject().apply {
            put("authenticated", user != null)
            put("uid", user?.uid.orEmpty())
            put("email", user?.email.orEmpty())
            put("displayName", user?.displayName.orEmpty())
            put("alias", currentProfile?.alias ?: user?.displayName ?: "Móvil ANTHI")
            put("anthiUid", currentProfile?.anthiUid.orEmpty())
            put("confirmedEvent", currentProfile?.confirmedEvent ?: 0L)
            put("confirmedEventIds", confirmedIdsArray)
            put("hierarchy", hierarchy.name)
            put("isOwner", isOwner)
            put("isAdmin", isAdmin)
            put("canCloseEvents", isAdmin)
            put("canDeleteEvents", isOwner)
            put("canManagePermissions", isOwner)
            put("events", eventsArray)
            put("adminInvites", invitesArray)
        }
    }

    private fun pushFirebaseStateToWeb() {
        if (!webPageReady) return
        val payload = buildFirebaseStateJson().toString()
        webView.post {
            webView.evaluateJavascript(
                "if(window.onFirebaseStateUpdate){window.onFirebaseStateUpdate($payload);}",
                null
            )
        }
    }

    private fun pushActionResult(action: String, ok: Boolean, message: String) {
        if (!webPageReady) return
        val resultJson = JSONObject().apply {
            put("action", action)
            put("ok", ok)
            put("message", message)
        }.toString()
        webView.post {
            webView.evaluateJavascript(
                "if(window.onFirebaseActionResult){window.onFirebaseActionResult($resultJson);}",
                null
            )
        }
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
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webPageReady = true
                loadingContainer.visibility = View.GONE
                pushFirebaseStateToWeb()
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
        webPageReady = false
        val targetUrl = appendAnthiQueryParams(PULSO_WEB_URL, params)
        webView.loadUrl(targetUrl)

        webView.postDelayed({
            if (!fallbackLoaded && loadingContainer.visibility == View.VISIBLE) {
                webView.stopLoading()
                loadLocalBundledFallback()
            }
        }, 1500L)
    }

    private fun loadLocalBundledFallback() {
        fallbackLoaded = true
        webPageReady = false
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
        val fragmentParams = parseFragmentQueryParams(uri?.fragment)
        val resolved = linkedMapOf<String, String>()

        ANTHI_PARAM_KEYS.forEach { key ->
            val fromUri = uri?.getQueryParameter(key)
            val fromFragment = fragmentParams[key]
            val fromExtra = intent?.getStringExtra(key)
            val value = when {
                !fromUri.isNullOrBlank() -> fromUri
                !fromFragment.isNullOrBlank() -> fromFragment
                !fromExtra.isNullOrBlank() -> fromExtra
                else -> null
            }
            if (!value.isNullOrBlank()) {
                resolved[key] = value
            }
        }

        val editor = prefs.edit().clear()
        if (resolved.isEmpty()) {
            editor.apply()
            return emptyMap()
        }

        resolved.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
        return resolved
    }

    private fun parseFragmentQueryParams(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank()) return emptyMap()
        val queryPart = if (fragment.contains("?")) fragment.substringAfter("?") else fragment
        if (!queryPart.contains("=")) return emptyMap()
        val fakeUri = Uri.parse("https://pulso.local?$queryPart")
        val map = mutableMapOf<String, String>()
        fakeUri.queryParameterNames.forEach { name ->
            fakeUri.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let { map[name] = it }
        }
        return map
    }

    private fun readPersistedAnthiParams(): Map<String, String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = linkedMapOf<String, String>()
        ANTHI_PARAM_KEYS.forEach { key ->
            prefs.getString(key, null)?.takeIf { it.isNotBlank() }?.let { result[key] = it }
        }
        return result
    }

    private fun isEmitterRole(role: String?): Boolean {
        return when (role?.trim()?.lowercase()) {
            "emisor_alarma", "alarm_emitter", "victima", "sos" -> true
            else -> false
        }
    }

    private fun isM34SkullTrigger(params: Map<String, String>): Boolean {
        val trigger = params["trigger"]?.trim()?.lowercase().orEmpty()
        return trigger == "calavera_m34" || trigger == "escudo_m34" || trigger == "sos_m34"
    }

    private fun resolveEventIdParam(params: Map<String, String>): String {
        val rawId = params["eventId"]
        val num = AnthiFirestoreRepository.extractEventNumber(rawId)
            ?: params["activeEvent"]?.toLongOrNull()
            ?: params["focusedEvent"]?.toLongOrNull()
            ?: 1L
        return AnthiFirestoreRepository.sanitizeEventId(rawId, num)
    }

    private fun parseConfirmedEventIds(params: Map<String, String>): List<String> {
        val raw = params["confirmedEvents"].orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { token -> token.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                if (token.all { it.isDigit() }) "ev_$token" else AnthiFirestoreRepository.sanitizeEventId(token, 1L)
            }
            .distinct()
    }

    private suspend fun syncAnthiUserAndAlarmIfNeeded(params: Map<String, String>) {
        val eventId = resolveEventIdParam(params)
        val eventNumber = AnthiFirestoreRepository.extractEventNumber(eventId)
            ?: params["activeEvent"]?.toLongOrNull()
            ?: 1L
        val alias = params["alias"] ?: params["nombre"] ?: params["movil"]
        val confirmedIds = parseConfirmedEventIds(params)

        firestoreRepository.ensureUserProfile(
            anthiUid = params["uid"],
            alias = alias,
            focusedEvent = eventNumber,
            confirmedEventIdsFromAnthi = confirmedIds
        )

        if (isEmitterRole(params["role"]) || isM34SkullTrigger(params)) {
            val lat = params["lat"]?.toDoubleOrNull() ?: 7.1524
            val lng = params["lng"]?.toDoubleOrNull() ?: -73.1296
            val sector = params["sector"].orEmpty().ifBlank { "Alerta Escudito M34 · En ruta" }
            val callsign = params["callsign"] ?: alias ?: "Móvil SOS"
            firestoreRepository.activateEmergencyEvent(
                eventId = eventId,
                eventNumber = eventNumber,
                callsign = callsign,
                sector = sector,
                lat = lat,
                lng = lng
            )
        }
    }

    private fun startBackgroundTacticalService(role: String, eventId: String, emitterSilent: Boolean) {
        val intent = Intent(this, PulsoRadioService::class.java).apply {
            putExtra(PulsoRadioService.EXTRA_ROLE, role.ifBlank { "OPERADOR" })
            putExtra(PulsoRadioService.EXTRA_EVENT_ID, eventId.ifBlank { "ev_1" })
            putExtra(PulsoRadioService.EXTRA_EMITTER_SILENT, emitterSilent)
        }
        ContextCompat.startForegroundService(this, intent)
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

    inner class AndroidPulsoBridge {

        @JavascriptInterface
        fun startTacticalService(role: String?, eventId: String?) {
            runOnUiThread {
                val safeRole = role.orEmpty().ifBlank { "OPERADOR" }
                val safeEventId = eventId.orEmpty().ifBlank { "ev_1" }
                startBackgroundTacticalService(
                    role = safeRole,
                    eventId = safeEventId,
                    emitterSilent = isEmitterSilentMode || isEmitterRole(safeRole)
                )
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
                if (enabled || PulsoRadioService.isRunning) {
                    val activeEv = resolveEventIdParam(readPersistedAnthiParams())
                    startBackgroundTacticalService(
                        role = if (enabled) "ALARM_EMITTER" else "OPERADOR",
                        eventId = activeEv,
                        emitterSilent = enabled
                    )
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

        @JavascriptInterface
        fun getFirebaseSessionJson(): String {
            return buildFirebaseStateJson().toString()
        }

        @JavascriptInterface
        fun seedInitialEvents() {
            lifecycleScope.launch {
                val res = firestoreRepository.seedInitialEventsIfAllowed()
                pushActionResult(
                    "seedEvents",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Eventos iniciales sincronizados en Firestore."
                )
            }
        }

        @JavascriptInterface
        fun confirmSiVoyFirestore(eventId: String?, eventNumber: Int, callsign: String?, lat: Double, lng: Double) {
            lifecycleScope.launch {
                val safeId = eventId.orEmpty().ifBlank { "ev_$eventNumber" }
                val res = firestoreRepository.confirmSiVoy(
                    eventId = safeId,
                    eventNumber = eventNumber.toLong(),
                    callsign = callsign ?: "Móvil ANTHI",
                    lat = lat,
                    lng = lng
                )
                pushActionResult(
                    "confirmSiVoy",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Apoyo Sí voy confirmado en tiempo real."
                )
            }
        }

        @JavascriptInterface
        fun closeEventFirestore(eventId: String?) {
            lifecycleScope.launch {
                val safeId = eventId.orEmpty()
                val res = firestoreRepository.closeEvent(safeId)
                pushActionResult(
                    "closeEvent",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Evento cerrado en tiempo real."
                )
            }
        }

        @JavascriptInterface
        fun deleteEventFirestore(eventId: String?) {
            lifecycleScope.launch {
                val safeId = eventId.orEmpty()
                val res = firestoreRepository.deleteEvent(safeId)
                pushActionResult(
                    "deleteEvent",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Evento eliminado definitivamente por Superadministrador."
                )
            }
        }

        @JavascriptInterface
        fun redeemAdminCode(rawCode: String?, alias: String?) {
            lifecycleScope.launch {
                val res = firestoreRepository.redeemAdminCode(rawCode.orEmpty(), alias)
                pushActionResult(
                    "redeemAdminCode",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Rol Administrador habilitado correctamente."
                )
            }
        }

        @JavascriptInterface
        fun createAdminInviteCode(label: String?, rawCode: String?) {
            lifecycleScope.launch {
                val res = firestoreRepository.createAdminInvite(label.orEmpty(), rawCode.orEmpty())
                pushActionResult(
                    "createAdminInvite",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Código de permiso administrativo guardado en Firebase."
                )
            }
        }

        @JavascriptInterface
        fun revokeAdminInvite(inviteHash: String?) {
            lifecycleScope.launch {
                val res = firestoreRepository.revokeAdminInvite(inviteHash.orEmpty())
                pushActionResult(
                    "revokeAdminInvite",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Código de permiso revocado."
                )
            }
        }

        @JavascriptInterface
        fun activateM34AlarmEvent(eventId: String?, eventNumber: Int, callsign: String?, sector: String?, lat: Double, lng: Double) {
            runOnUiThread {
                applyStrictEmitterSilentMode(true)
                val safeId = AnthiFirestoreRepository.sanitizeEventId(eventId, eventNumber.toLong())
                startBackgroundTacticalService("ALARM_EMITTER", safeId, true)
            }
            lifecycleScope.launch {
                val res = firestoreRepository.activateEmergencyEvent(
                    eventId = eventId.orEmpty(),
                    eventNumber = eventNumber.toLong().coerceIn(1L, 999L),
                    callsign = callsign ?: "Móvil SOS",
                    sector = sector ?: "Alerta Escudito M34 · En ruta",
                    lat = lat,
                    lng = lng
                )
                pushActionResult(
                    "activateM34AlarmEvent",
                    res.isSuccess,
                    res.exceptionOrNull()?.localizedMessage ?: "Alarma M34 activada en Firebase (persistente en segundo plano)."
                )
            }
        }

        @JavascriptInterface
        fun signOutAnthi() {
            lifecycleScope.launch {
                authManager.signOut {
                    stopFirestoreObservers()
                }
            }
        }
    }

    companion object {
        const val PULSO_WEB_URL = "https://pulso-m34.anthi.app"
        private const val LOCAL_ASSET_FALLBACK_URL = "file:///android_asset/pulso_m34.html"
        private const val JS_BRIDGE_NAME = "AndroidPulsoBridge"
        private const val PREFS_NAME = "pulso_m34_anthi_prefs"
        private val ANTHI_PARAM_KEYS = listOf(
            "uid",
            "alias",
            "nombre",
            "movil",
            "role",
            "trigger",
            "eventId",
            "events",
            "activeEvents",
            "activeEvent",
            "focusedEvent",
            "confirmedEvents",
            "callsign",
            "sector",
            "lat",
            "lng"
        )
    }
}
