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
    private var map: Map<String, String> = mutableMapOf()
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
                    val url = request?.url.toString()
                    url.takeUnless { it.startsWith("http") }?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        true
                    } ?: false
                } catch (e: Exception) {
                    true
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pickMediaLauncher
    }

    private fun WWAView.runOnUiThread(callback: () -> Unit) =
        (context as? Activity?)?.runOnUiThread { callback.invoke() }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pickMediaLauncher.unregister()
    }

    override suspend fun fetch(value: String) {
        this.mainValue = value
        if (this.map.isNotEmpty()) {
            runOnUiThread { loadUrl(value, map) }
            return
        }
        val u = generate(value)
        runOnUiThread { loadUrl(u) }
    }

    override fun fetch(value: String, value2: Map<String, String>) {
        this.mainValue = value
        runOnUiThread { loadUrl(value, value2) }
    }

    fun setValue(key: String, value: String) {
        json[key] = value
    }

    fun addMap(map: Map<String, String>) {
        this.map = map
    }

    private fun generate(start: String): String {
        var new = start
        json.forEach { (key, value) -> new += "&$key=$value" }
        return new
    }

    @SuppressLint("JavascriptInterface")
    fun addCallback(obj: Any, name: String) = runOnUiThread {
        addJavascriptInterface(obj, name)
    }

}