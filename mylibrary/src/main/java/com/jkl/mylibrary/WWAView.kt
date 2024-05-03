package com.jkl.mylibrary

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
class WWAView(
    context: Context,
    attrs: AttributeSet?
) : WebView(context, attrs) {

    private val json: MutableMap<String, String> = mutableMapOf()
    private var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var mainValue = "null"
    private val Context.dataStore by preferencesDataStore("data_store")

    private object PreferencesKeys {
        val CAKE = stringPreferencesKey("cake")
        val UU = stringPreferencesKey("uu")
    }

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        isSaveEnabled = true
        isFocusable = true
        isFocusableInTouchMode = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        settings.apply {
            mixedContentMode = 0
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            databaseEnabled = true
            useWideViewPort = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            allowContentAccess = true
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) saveFormData = true
        }

        val owner = context as ActivityResultRegistryOwner
        pickMediaLauncher = owner.activityResultRegistry.register(
            "pickMedia",
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            filePathCallback?.onReceiveValue(
                try {
                    if (uri != null) arrayOf(uri) else arrayOf()
                } catch (e: Exception) {
                    arrayOf()
                }
            )
            filePathCallback = null
        }

        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@WWAView.filePathCallback = filePathCallback
                pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return try {
                    if (!isInternetAvailable()) {
                        showNoInternetDialog()
                        return true
                    }
                    val cake = request?.url?.toString()
                    cake(cake.toString())
                    val intent = createIntent(cake.toString())
                    if (intent != null && view?.context != null) {
                        view.context.startActivity(intent)
                        true
                    } else false
                } catch (e: Exception) {
                    true
                }
            }
        }
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(context)
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and try again.")
            .setCancelable(false)
            .setPositiveButton("Try Again") { d, _ ->
                if (!isInternetAvailable()) {
                    d.dismiss()
                    showNoInternetDialog()
                    return@setPositiveButton
                }
                d.dismiss()
            }.show()
    }

    fun isInternetAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        ))
    }

    private fun cake(value: String) {
        if (!value.contains(mainValue)) {
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.data.firstOrNull()?.let { preferences ->
                    val cake = preferences[PreferencesKeys.CAKE] ?: ""
                    if (cake.isEmpty()) {
                        context.dataStore.edit { edit ->
                            edit[PreferencesKeys.CAKE] = value
                        }
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pickMediaLauncher
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pickMediaLauncher.unregister()
    }

    suspend fun getUu(): String {
        val preferences = context.dataStore.data.firstOrNull()
        val uu = preferences?.get(PreferencesKeys.UU) ?: ""
        if (uu.isNotEmpty()) return uu
        val uu2 = UUID.randomUUID().toString()
        context.dataStore.edit { edit -> edit[PreferencesKeys.UU] = uu2 }
        return uu2
    }

    suspend fun fetch(value: String) {
        mainValue = value
        val u = check().ifEmpty { generate(value) }
        (context as? Activity?)?.runOnUiThread { loadUrl(u) }
    }

    fun setValue(key: String, value: String) {
        json[key] = value
    }

    private fun generate(start: String): String {
        var new = start
        json.forEach { (key, value) -> new += "&$key=$value" }
        return new
    }

    private suspend fun check(): String {
        val preferences = context.dataStore.data.firstOrNull()
        return preferences?.get(PreferencesKeys.CAKE) ?: ""
    }

    fun createIntent(url: String): Intent? {
        val urlActionMappings = listOf(
            Pair("tel:", Intent.ACTION_DIAL),
            Pair("mailto:", Intent.ACTION_SENDTO),
            Pair("https://t.me/joinchat", Intent.ACTION_VIEW)
        )
        for ((urlStart, action) in urlActionMappings) {
            if (url.startsWith(urlStart)) return Intent(action, Uri.parse(url))
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    @SuppressLint("JavascriptInterface")
    fun addCallback(obj: Any, name: String) {
        (context as? Activity?)?.runOnUiThread {
            addJavascriptInterface(obj, name)
        }
    }
}