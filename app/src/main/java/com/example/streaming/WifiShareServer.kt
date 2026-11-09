package com.example.streaming

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.MediaRepository
import com.example.security.SecurityManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale

class WifiShareServer(
    private val context: Context,
    private val repository: MediaRepository,
    private val securityManager: SecurityManager,
    private val port: Int = 8080
) {
    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var pairingCode: String = ""
        private set

    private var server: ShareHttpServer? = null

    fun start(onStatusChange: (Boolean, String) -> Unit) {
        if (isRunning) return
        pairingCode = generatePairingCode()
        try {
            val newServer = ShareHttpServer(port)
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            isRunning = true
            onStatusChange(true, "http://" + getIpAddress() + ":" + port)
        } catch (e: Exception) {
            server?.stop()
            server = null
            pairingCode = ""
            isRunning = false
            onStatusChange(false, e.localizedMessage ?: "فشل بدء الخادم")
        }
    }

    fun stop(onStatusChange: (Boolean, String) -> Unit) {
        try {
            server?.stop()
            server = null
            pairingCode = ""
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
                val addresses = Collections.list(intf.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    private fun generatePairingCode(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private inner class ShareHttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            return try {
                when (session.uri) {
                    "/auth" -> authenticate(session)
                    "/" -> home(session)
                    "/media" -> media(session)
                    "/upload" -> upload(session)
                    else -> text(NanoHTTPD.Response.Status.NOT_FOUND, "المسار غير موجود")
                }
            } catch (_: Exception) {
                text(NanoHTTPD.Response.Status.INTERNAL_ERROR, "تعذر إكمال الطلب.")
            }
        }

        private fun authenticate(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.method != NanoHTTPD.Method.POST) {
                return text(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "استخدم POST.")
            }
            val length = contentLength(session) ?: return text(NanoHTTPD.Response.Status.LENGTH_REQUIRED, "حجم الطلب مطلوب.")
            if (length > MAX_AUTH_BODY_BYTES) {
                return text(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, "طلب المصادقة أكبر من المسموح.")
            }
            val body = String(readRequestBody(session, length), StandardCharsets.UTF_8)
            val suppliedCode = parseForm(body)["code"].orEmpty()
            val expectedCode = pairingCode
            if (expectedCode.isBlank() || !constantTimeEquals(suppliedCode, expectedCode)) {
                return html(NanoHTTPD.Response.Status.UNAUTHORIZED, loginPage("رمز الاقتران غير صحيح."))
            }
            return html(NanoHTTPD.Response.Status.OK, "<!doctype html><html lang=\"ar\" dir=\"rtl\"><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0;url=/\"><body>تم الاقتران. <a href=\"/\">متابعة</a></body></html>")
                .apply {
                    addHeader("Set-Cookie", COOKIE_NAME + "=" + expectedCode + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=3600")
                }
        }

        private fun home(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.method != NanoHTTPD.Method.GET) return text(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "استخدم GET.")
            if (!isAuthorized(session)) return html(NanoHTTPD.Response.Status.UNAUTHORIZED, loginPage(null))

            val protectedIds = runBlocking { securityManager.protectedMediaIds.firstOrNull().orEmpty() }
            val visibleMedia = runBlocking { repository.allMedia.firstOrNull().orEmpty() }
                .filterNot { it.id in protectedIds }
            val body = StringBuilder()
            body.append("<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>SafePlayer Wi-Fi</title>")
            body.append("<style>body{font-family:sans-serif;background:#121316;color:#f0f2f5;padding:20px;max-width:850px;margin:auto}.card{background:#1e2025;border:1px solid #343842;border-radius:12px;padding:14px;margin:12px 0}a,button{color:#fff;background:#276ef1;border:0;border-radius:7px;padding:9px 12px;text-decoration:none;display:inline-block;margin:4px}input{margin:8px 0}small{color:#aab2bf}</style></head><body><h1>SafePlayer Wi-Fi Hub</h1><p>الوسائط المحمية مستبعدة من هذه القائمة.</p>")
            body.append("<section class=\"card\"><h2>رفع ملف إلى الهاتف</h2><p><small>الحد الأقصى 100 MB؛ سيُحفظ في مجلد SafePlayer ضمن التنزيلات.</small></p><form id=\"uploadForm\"><input type=\"file\" id=\"uploadFile\" required><button type=\"submit\">رفع الملف</button></form><p id=\"uploadStatus\" aria-live=\"polite\"></p></section>")
            body.append("<h2>الوسائط المتاحة (" + visibleMedia.size + ")</h2>")
            if (visibleMedia.isEmpty()) body.append("<p>لا توجد وسائط متاحة للمشاركة.</p>")
            visibleMedia.forEach { item ->
                val isVideo = item.mimeType?.startsWith("video") == true || item.path.endsWith(".mp4", true)
                val badge = if (isVideo) "فيديو" else "صوت"
                val sizeMb = String.format(Locale.US, "%.1f", item.sizeBytes / (1024.0 * 1024.0))
                body.append("<div class=\"card\"><small>").append(badge).append(" • ").append(sizeMb).append(" MB</small><h3>")
                    .append(escapeHtml(item.name)).append("</h3><a href=\"/media?id=").append(item.id)
                    .append("&amp;action=play\">تشغيل</a><a href=\"/media?id=").append(item.id)
                    .append("&amp;action=download\">تنزيل</a></div>")
            }
            body.append("<script>document.getElementById('uploadForm').addEventListener('submit',async function(event){event.preventDefault();const input=document.getElementById('uploadFile');const status=document.getElementById('uploadStatus');const file=input.files[0];if(!file)return;if(file.size>104857600){status.textContent='الملف أكبر من 100 MB.';return;}status.textContent='جارٍ الرفع...';try{const response=await fetch('/upload',{method:'POST',headers:{'Content-Type':'application/octet-stream','X-File-Name':encodeURIComponent(file.name)},body:file});status.textContent=await response.text();if(response.ok)input.value='';}catch(error){status.textContent='فشل الاتصال بالخادم.';}});</script></body></html>")
            return html(NanoHTTPD.Response.Status.OK, body.toString())
        }

        private fun media(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.method != NanoHTTPD.Method.GET) return text(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "استخدم GET.")
            if (!isAuthorized(session)) return text(NanoHTTPD.Response.Status.UNAUTHORIZED, "يلزم الاقتران أولاً.")
            val params = session.parameters
            val id = params["id"]?.firstOrNull()?.toIntOrNull()
                ?: return text(NanoHTTPD.Response.Status.BAD_REQUEST, "معرّف الوسائط غير صالح.")
            if (runBlocking { securityManager.isMediaProtected(id) }) {
                return text(NanoHTTPD.Response.Status.NOT_FOUND, "الوسائط غير موجودة.")
            }
            val item = runBlocking { repository.getMediaById(id).firstOrNull() }
                ?: return text(NanoHTTPD.Response.Status.NOT_FOUND, "الوسائط غير موجودة.")
            val file = File(item.path)
            if (!file.isFile || !file.canRead()) return text(NanoHTTPD.Response.Status.NOT_FOUND, "الملف غير متاح.")
            val action = params["action"]?.firstOrNull() ?: "play"
            if (action == "download") {
                val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, safeMimeType(item.mimeType), FileInputStream(file), file.length())
                response.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(file.name, StandardCharsets.UTF_8.name()))
                response.addHeader("X-Content-Type-Options", "nosniff")
                response.addHeader("Cache-Control", "no-store")
                return response
            }
            val video = item.mimeType?.startsWith("video") == true || file.name.endsWith(".mp4", true)
            val mediaTag = if (video) {
                "<video controls autoplay style=\"width:100%;max-height:80vh\"><source src=\"/media?id=" + id + "&amp;action=download\" type=\"" + safeMimeType(item.mimeType) + "\"></video>"
            } else {
                "<audio controls autoplay style=\"width:100%\"><source src=\"/media?id=" + id + "&amp;action=download\" type=\"" + safeMimeType(item.mimeType) + "\"></audio>"
            }
            val page = "<!doctype html><html lang=\"ar\" dir=\"rtl\"><meta charset=\"utf-8\"><title>SafePlayer</title><body style=\"background:#121316;color:white;font-family:sans-serif;text-align:center;padding:20px\"><h2>" + escapeHtml(item.name) + "</h2>" + mediaTag + "<p><a href=\"/\">العودة</a></p></body></html>"
            return html(NanoHTTPD.Response.Status.OK, page)
        }

        private fun upload(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.method != NanoHTTPD.Method.POST) return text(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "استخدم POST.")
            if (!isAuthorized(session)) return text(NanoHTTPD.Response.Status.UNAUTHORIZED, "يلزم الاقتران أولاً.")
            val length = contentLength(session) ?: return text(NanoHTTPD.Response.Status.LENGTH_REQUIRED, "حجم الطلب مطلوب.")
            if (length > MAX_UPLOAD_BYTES) return text(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, "حجم الملف يتجاوز الحد المسموح (100 MB).")
            val contentType = header(session, "content-type").orEmpty()
            if (!contentType.startsWith("application/octet-stream", ignoreCase = true)) {
                return text(NanoHTTPD.Response.Status.UNSUPPORTED_MEDIA_TYPE, "نوع الرفع غير مدعوم.")
            }
            val encodedName = header(session, "x-file-name") ?: return text(NanoHTTPD.Response.Status.BAD_REQUEST, "اسم الملف مطلوب.")
            val decodedName = try {
                URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                return text(NanoHTTPD.Response.Status.BAD_REQUEST, "اسم الملف غير صالح.")
            }
            val safeName = decodedName.substringAfterLast('/').substringAfterLast('\\')
                .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == ' ' }
                .take(100).trim('.', ' ')
                .ifBlank { "upload.bin" }
            saveUpload(session.inputStream, length, safeName)
            return text(NanoHTTPD.Response.Status.OK, "تم حفظ الملف في مجلد SafePlayer ضمن التنزيلات.")
        }
    }

    private fun isAuthorized(session: NanoHTTPD.IHTTPSession): Boolean {
        val expected = pairingCode
        if (expected.isBlank()) return false
        val cookieHeader = header(session, "cookie") ?: return false
        val supplied = cookieHeader.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith(COOKIE_NAME + "=") }
            ?.substringAfter('=') ?: return false
        return constantTimeEquals(supplied, expected)
    }

    private fun loginPage(error: String?): String {
        val errorHtml = if (error.isNullOrBlank()) "" else "<p role=\"alert\">" + escapeHtml(error) + "</p>"
        return "<!doctype html><html lang=\"ar\" dir=\"rtl\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>اقتران SafePlayer</title><body style=\"font-family:sans-serif;max-width:480px;margin:10vh auto;padding:20px\"><h1>اقتران آمن</h1><p>أدخل رمز الاقتران الظاهر في تطبيق SafePlayer.</p>" + errorHtml + "<form action=\"/auth\" method=\"post\"><label for=\"code\">رمز الاقتران</label><input id=\"code\" name=\"code\" type=\"password\" autocomplete=\"off\" required style=\"display:block;width:100%;padding:12px;margin:12px 0\"><button type=\"submit\">اقتران</button></form><p>استخدم هذه الصفحة على شبكة Wi-Fi موثوقة.</p></body></html>"
    }

    private fun saveUpload(input: java.io.InputStream, length: Long, fileName: String) {
        var targetUri: Uri? = null
        var fallbackFile: File? = null
        try {
            val storedName = "SafePlayer_" + System.currentTimeMillis() + "_" + fileName
            val output: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, storedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafePlayer")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                targetUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("تعذر إنشاء ملف التنزيل.")
                output = context.contentResolver.openOutputStream(targetUri, "w")
                    ?: throw IOException("تعذر فتح ملف التنزيل.")
            } else {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!directory.exists() && !directory.mkdirs()) throw IOException("تعذر إنشاء مجلد التنزيلات.")
                fallbackFile = File(directory, storedName)
                output = fallbackFile.outputStream()
            }
            output.use { destination ->
                val buffer = ByteArray(8192)
                var remaining = length
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw IOException("انقطع اتصال الرفع قبل اكتماله.")
                    destination.write(buffer, 0, count)
                    remaining -= count
                }
            }
            targetUri?.let { uri ->
                val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            targetUri?.let { context.contentResolver.delete(it, null, null) }
            fallbackFile?.delete()
            throw e
        }
    }

    private fun html(status: NanoHTTPD.Response.Status, body: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, "text/html; charset=utf-8", body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }

    private fun text(status: NanoHTTPD.Response.Status, body: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, "text/plain; charset=utf-8", body).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }

    private fun contentLength(session: NanoHTTPD.IHTTPSession): Long? =
        header(session, "content-length")?.toLongOrNull()?.takeIf { it >= 0L }

    private fun header(session: NanoHTTPD.IHTTPSession, name: String): String? =
        session.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun readRequestBody(session: NanoHTTPD.IHTTPSession, length: Long): ByteArray {
        val output = ByteArrayOutputStream(length.toInt())
        val buffer = ByteArray(1024)
        var remaining = length
        while (remaining > 0) {
            val count = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("انقطع طلب المصادقة.")
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&').mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) null else try {
            URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }.toMap()

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(StandardCharsets.UTF_8), right.toByteArray(StandardCharsets.UTF_8))

    private fun safeMimeType(mimeType: String?): String =
        mimeType?.takeIf { it.matches(Regex("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")) } ?: "application/octet-stream"

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    companion object {
        private const val COOKIE_NAME = "SafePlayerSession"
        private const val MAX_UPLOAD_BYTES = 100L * 1024L * 1024L
        private const val MAX_AUTH_BODY_BYTES = 2048L
    }
}
