package com.example.streaming

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import com.example.data.MediaRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.Executors

class WifiShareServer(
    private val context: Context,
    private val repository: MediaRepository,
    private val port: Int = 8080
) {
    private var server: HttpServer? = null
    var isRunning: Boolean = false
        private set

    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(onStatusChange: (Boolean, String) -> Unit) {
        if (isRunning) return
        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                executor = Executors.newCachedThreadPool()

                // Home Web Portal
                createContext("/", HomeHandler())
                // Media Streaming & Download Handler
                createContext("/media", MediaStreamHandler())
                // File Upload Handler
                createContext("/upload", UploadHandler())

                start()
            }
            isRunning = true
            val ip = getIpAddress()
            val url = "http://$ip:$port"
            onStatusChange(true, url)
        } catch (e: Exception) {
            isRunning = false
            onStatusChange(false, e.localizedMessage ?: "فشل بدء الخادم")
        }
    }

    fun stop(onStatusChange: (Boolean, String) -> Unit) {
        try {
            server?.stop(0)
            server = null
            isRunning = false
            onStatusChange(false, "تم إيقاف الخادم")
        } catch (e: Exception) {
            onStatusChange(false, e.localizedMessage ?: "خطأ أثناء الإيقاف")
        }
    }

    fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "127.0.0.1"
    }

    private inner class HomeHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    val mediaList = repository.allMedia.firstOrNull() ?: emptyList()

                    val itemsHtml = StringBuilder()
                    if (mediaList.isEmpty()) {
                        itemsHtml.append("""<p style="text-align:center; color:#888;">لا توجد ملفات وسائط حالياً في التطبيق.</p>""")
                    } else {
                        mediaList.forEach { item ->
                            val isVid = item.mimeType?.startsWith("video") == true || item.path.endsWith(".mp4", true)
                            val typeBadge = if (isVid) "🎬 فيديو" else "🎵 صوت"
                            val sizeMb = String.format("%.1f", item.sizeBytes / (1024.0 * 1024.0))

                            itemsHtml.append("""
                                <div class="card">
                                    <div class="card-info">
                                        <span class="badge">$typeBadge</span>
                                        <h3>${escapeHtml(item.name)}</h3>
                                        <p>الحجم: ${sizeMb} MB</p>
                                    </div>
                                    <div class="card-actions">
                                        <a href="/media?id=${item.id}&action=play" target="_blank" class="btn btn-play">▶ تشغيل</a>
                                        <a href="/media?id=${item.id}&action=download" class="btn btn-download">⬇ تحميل</a>
                                    </div>
                                </div>
                            """.trimIndent())
                        }
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html lang="ar" dir="rtl">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>SafePlayer - مشاركة وسائط Wi-Fi</title>
                            <style>
                                :root {
                                    --bg: #121316;
                                    --surface: #1e2025;
                                    --primary: #4e8cff;
                                    --text: #f0f2f5;
                                    --text-secondary: #9aa0a6;
                                    --border: #2e323b;
                                }
                                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                                body { background: var(--bg); color: var(--text); padding: 20px; line-height: 1.6; }
                                .container { max-width: 800px; margin: 0 auto; }
                                header { text-align: center; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--border); }
                                header h1 { color: var(--primary); font-size: 26px; margin-bottom: 6px; }
                                header p { color: var(--text-secondary); font-size: 14px; }
                                
                                .upload-box { background: var(--surface); border: 2px dashed var(--primary); border-radius: 12px; padding: 20px; text-align: center; margin-bottom: 24px; }
                                .upload-box input[type=file] { margin: 12px 0; color: var(--text); }
                                .upload-box button { background: var(--primary); color: white; border: none; padding: 10px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; }
                                
                                .section-title { font-size: 18px; margin-bottom: 14px; color: var(--text); }
                                .card { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 14px 18px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; }
                                .card-info h3 { font-size: 15px; margin-bottom: 4px; word-break: break-all; }
                                .card-info p { font-size: 12px; color: var(--text-secondary); }
                                .badge { display: inline-block; background: rgba(78, 140, 255, 0.15); color: var(--primary); font-size: 11px; padding: 2px 8px; border-radius: 4px; margin-bottom: 6px; }
                                .card-actions { display: flex; gap: 8px; }
                                .btn { text-decoration: none; padding: 8px 14px; border-radius: 6px; font-size: 13px; font-weight: bold; }
                                .btn-play { background: #2e7d32; color: white; }
                                .btn-download { background: #37474f; color: white; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <header>
                                    <h1>SafePlayer Wi-Fi Hub 📡</h1>
                                    <p>تصفح وشاهد ونزل ملفات هاتفك مباشرة عبر المتصفح بدون كابل</p>
                                </header>
                                
                                <div class="upload-box">
                                    <h3>📤 رفع ملف من الكمبيوتر إلى الهاتف</h3>
                                    <form action="/upload" method="post" enctype="multipart/form-data">
                                        <input type="file" name="file" required><br>
                                        <button type="submit">بدء الرفع للهاتف</button>
                                    </form>
                                </div>
                                
                                <h2 class="section-title">📂 وسائط الهاتف (${mediaList.size} ملف)</h2>
                                <div class="list">
                                    $itemsHtml
                                </div>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()

                    val bytes = html.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } catch (e: Exception) {
                    val errMsg = "Error: ${e.message}".toByteArray()
                    exchange.sendResponseHeaders(500, errMsg.size.toLong())
                    exchange.responseBody.use { it.write(errMsg) }
                }
            }
        }
    }

    private inner class MediaStreamHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    val query = exchange.requestURI.query ?: ""
                    val params = parseQueryParams(query)
                    val id = params["id"]?.toIntOrNull()
                    val action = params["action"] ?: "play"

                    if (id == null) {
                        exchange.sendResponseHeaders(400, -1)
                        return@launch
                    }

                    val mediaItem = repository.getMediaById(id).firstOrNull()
                    if (mediaItem == null) {
                        exchange.sendResponseHeaders(404, -1)
                        return@launch
                    }

                    val file = File(mediaItem.path)
                    if (!file.exists()) {
                        exchange.sendResponseHeaders(404, -1)
                        return@launch
                    }

                    if (action == "download") {
                        val fileName = file.name
                        exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"$fileName\"")
                        exchange.responseHeaders.set("Content-Type", mediaItem.mimeType ?: "application/octet-stream")
                        exchange.sendResponseHeaders(200, file.length())
                        FileInputStream(file).use { input ->
                            exchange.responseBody.use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        // Embedded HTML5 Player
                        val isVideo = mediaItem.mimeType?.startsWith("video") == true || file.name.endsWith(".mp4", true)
                        val playerTag = if (isVideo) {
                            """<video controls autoplay width="100%" style="max-height:80vh; border-radius:10px;"><source src="/media?id=$id&action=download" type="video/mp4">متصفحك لا يدعم مشغل الفيديو.</video>"""
                        } else {
                            """<audio controls autoplay style="width:100%; margin-top:20px;"><source src="/media?id=$id&action=download" type="audio/mpeg">متصفحك لا يدعم مشغل الصوت.</audio>"""
                        }

                        val html = """
                            <!DOCTYPE html>
                            <html lang="ar" dir="rtl">
                            <head>
                                <meta charset="UTF-8">
                                <title>${escapeHtml(mediaItem.name)} - SafePlayer</title>
                                <style>
                                    body { background: #0f1013; color: white; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; margin:0; font-family: sans-serif; padding: 20px; box-sizing: border-box; }
                                    .container { width: 100%; max-width: 900px; text-align: center; }
                                    h2 { margin-bottom: 16px; font-size: 20px; color: #4e8cff; }
                                    a.back { color: #888; text-decoration: none; margin-top: 20px; display: inline-block; font-size: 14px; }
                                </style>
                            </head>
                            <body>
                                <div class="container">
                                    <h2>${escapeHtml(mediaItem.name)}</h2>
                                    $playerTag
                                    <br>
                                    <a href="/" class="back">⬅ العودة إلى قائمة الوسائط</a>
                                </div>
                            </body>
                            </html>
                        """.trimIndent()

                        val bytes = html.toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                } catch (e: Exception) {
                    exchange.sendResponseHeaders(500, -1)
                }
            }
        }
    }

    private inner class UploadHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                        val contentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
                        if (contentType.contains("multipart/form-data")) {
                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                ?: context.filesDir
                            val targetFile = File(downloadDir, "SafePlayer_Uploaded_${System.currentTimeMillis()}.bin")

                            // Read uploaded payload and save
                            exchange.requestBody.use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }

                            val successHtml = """
                                <!DOCTYPE html>
                                <html lang="ar" dir="rtl">
                                <head><meta charset="UTF-8"><title>نجاح الرفع</title></head>
                                <body style="background:#121316;color:#fff;text-align:center;padding:50px;font-family:sans-serif;">
                                    <h2 style="color:#4caf50;">✅ تم استلام الملف بنجاح على هاتفك!</h2>
                                    <p>تم حفظ الملف في مجلد التنزيلات (Downloads).</p>
                                    <br><a href="/" style="color:#4e8cff;text-decoration:none;">⬅ العودة للقائمة</a>
                                </body>
                                </html>
                            """.trimIndent()
                            val bytes = successHtml.toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.use { it.write(bytes) }
                            return@launch
                        }
                    }
                    exchange.sendResponseHeaders(405, -1)
                } catch (e: Exception) {
                    exchange.sendResponseHeaders(500, -1)
                }
            }
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val pair = param.split("=")
            if (pair.size == 2) {
                result[URLDecoder.decode(pair[0], "UTF-8")] = URLDecoder.decode(pair[1], "UTF-8")
            }
        }
        return result
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
