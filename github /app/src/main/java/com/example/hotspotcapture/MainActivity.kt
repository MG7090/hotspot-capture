package com.example.hotspotcapture

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var repo: AssetRepository
    private lateinit var adapter: AssetAdapter
    private lateinit var tvCount: TextView
    private lateinit var btnZip: Button

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AssetRepository(this)
        adapter = AssetAdapter(repo.assets) { asset -> shareAsset(asset) }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tvCount = findViewById(R.id.tvCount)
        btnZip = findViewById(R.id.btnZip)

        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = userAgentString + " HotspotCapture/1.0"
        }

        webView.webViewClient = CaptureWebViewClient(repo) {
            runOnUiThread { refreshList() }
        }

        findViewById<Button>(R.id.btnGo).setOnClickListener {
            val url = findViewById<EditText>(R.id.etUrl).text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "أدخل رابطاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val normalized = if (url.startsWith("http://") || url.startsWith("https://"))
                url else "http://$url"
            webView.loadUrl(normalized)
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            repo.clear()
            refreshList()
            Toast.makeText(this, "تم المسح", Toast.LENGTH_SHORT).show()
        }

        btnZip.setOnClickListener {
            if (repo.assets.isEmpty()) return@setOnClickListener
            try {
                val zip = repo.zipAll()
                shareFile(zip, "application/zip", "مشاركة ZIP")
            } catch (e: Exception) {
                Toast.makeText(this, "فشل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshList() {
        adapter.notifyDataSetChanged()
        tvCount.text = "${repo.assets.size} ملف"
        btnZip.isEnabled = repo.assets.isNotEmpty()
    }

    private fun shareAsset(asset: CapturedAsset) {
        val mime = when (asset.type) {
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "img" -> "image/*"
            "font" -> "font/*"
            else -> "application/octet-stream"
        }
        shareFile(asset.file, mime, "مشاركة الملف")
    }

    private fun shareFile(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
