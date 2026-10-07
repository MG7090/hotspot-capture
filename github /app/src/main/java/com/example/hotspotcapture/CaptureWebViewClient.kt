package com.example.hotspotcapture

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class CaptureWebViewClient(
    private val repo: AssetRepository,
    private val onNewAsset: () -> Unit
) : WebViewClient() {

    private val trustAll: SSLSocketFactory by lazy {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }), SecureRandom())
        ctx.socketFactory
    }

    private val hostnameVerifier = HostnameVerifier { _, _ -> true }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // تجاهل الأنواع التي لا يمكن جلبها
        if (url.startsWith("data:") || url.startsWith("blob:") ||
            url.startsWith("javascript:") || url.startsWith("about:")) {
            return null
        }

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = request.method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            request.requestHeaders.forEach { (k, v) ->
                try { conn.setRequestProperty(k, v) } catch (_: Exception) {}
            }

            if (conn is HttpsURLConnection) {
                conn.sslSocketFactory = trustAll
                conn.hostnameVerifier = hostnameVerifier
            }

            conn.connect()

            val mime = (conn.contentType ?: "application/octet-stream")
                .substringBefore(';').trim()
            val bytes = conn.inputStream.readBytes()

            val asset = repo.save(url, mime, bytes)
            if (asset != null) onNewAsset()

            WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            null
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // التقط HTML النهائي بعد تنفيذ JS
        view.evaluateJavascript(
            "(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()"
        ) { raw ->
            if (raw == null || raw == "null") return@evaluateJavascript
            val html = decodeJsString(raw)
            if (html.isNotBlank()) {
                val asset = repo.saveText(url, "text/html", html)
                if (asset != null) onNewAsset()
            }
        }
    }

    private fun decodeJsString(raw: String): String {
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        handler?.proceed()
    }
}
