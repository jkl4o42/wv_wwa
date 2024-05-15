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

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
class WWAView(
    context: Context,
    attrs: AttributeSet?
) : WebView(context, attrs), Fetch {

    private var json: MutableMap<String, String> = mutableMapOf()
    private var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var mainValue = "null"
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pickMediaLauncher
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pickMediaLauncher.unregister()
    }

    override suspend fun fetch(value: String) {
        this.mainValue = value
        val u = generate(value)
        (context as? Activity?)?.runOnUiThread { loadUrl(u) }
    }

    override fun fetch(value: String, value2: Map<String, String>) {
        this.mainValue = value
        (context as? Activity?)?.runOnUiThread { loadUrl(value, value2) }
    }

    fun setValue(key: String, value: String) {
        json[key] = value
    }

    private fun generate(start: String): String {
        var new = start
        json.forEach { (key, value) -> new += "&$key=$value" }
        return new
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