package com.sitelog.labourapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
        }

        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("whatsapp:") || url.startsWith("https://wa.me") || 
                    url.startsWith("tel:") || url.startsWith("mailto:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return false
                    }
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .create()
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .create()
                    .show()
                return true
            }
        }
        
        webView.loadUrl("file:///android_asset/labour-report.html")
    }

    class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun printHtml(html: String) {
            (mContext as? MainActivity)?.runOnUiThread {
                val printWebView = WebView(mContext)
                printWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val printManager = mContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val printAdapter = view.createPrintDocumentAdapter("Labour Report")
                        printManager.print("SiteLog_Report", printAdapter, PrintAttributes.Builder().build())
                    }
                }
                printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

        @JavascriptInterface
        fun shareAsPdf(html: String, filename: String) {
            (mContext as? MainActivity)?.runOnUiThread {
                val shareWebView = WebView(mContext)
                shareWebView.settings.javaScriptEnabled = true
                shareWebView.layout(0, 0, 1200, 1800) 
                shareWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            createPdfAndShare(view, filename)
                        }, 800)
                    }
                }
                shareWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

        @JavascriptInterface
        fun shareAsCsv(csvContent: String, filename: String) {
            try {
                val file = File(mContext.cacheDir, "$filename.csv")
                val fos = FileOutputStream(file)
                fos.write(csvContent.toByteArray())
                fos.close()
                
                val uri = FileProvider.getUriForFile(mContext, "com.sitelog.labourapp.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/csv"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                mContext.startActivity(Intent.createChooser(intent, "Export Excel (CSV)"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun createPdfAndShare(view: WebView, filename: String) {
            try {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(view.width, view.height, 1).create()
                val page = document.startPage(pageInfo)
                view.draw(page.canvas)
                document.finishPage(page)

                val file = File(mContext.cacheDir, "$filename.pdf")
                val fos = FileOutputStream(file)
                document.writeTo(fos)
                document.close()
                fos.close()
                
                val uri = FileProvider.getUriForFile(mContext, "com.sitelog.labourapp.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "application/pdf"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                mContext.startActivity(Intent.createChooser(intent, "Share Report as PDF"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
