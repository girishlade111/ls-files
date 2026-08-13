package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder

class FileTransferService : Service() {

    private var httpServer: HttpServer? = null
    private val port = 8080

    companion object {
        const val ACTION_START = "ACTION_START_WIFI_TRANSFER"
        const val ACTION_STOP = "ACTION_STOP_WIFI_TRANSFER"
        const val CHANNEL_ID = "wifi_transfer_channel"
        var isServerRunning = false
            private set
        var serverIpAddress: String = ""
            private set

        fun getWifiIpAddress(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "127.0.0.1"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (isServerRunning) return
        createNotificationChannel()

        try {
            val ip = getWifiIpAddress(this)
            serverIpAddress = "http://$ip:$port"

            httpServer = HttpServer.create(InetSocketAddress(port), 0)
            httpServer?.createContext("/", WebHandler(this))
            httpServer?.start()

            isServerRunning = true

            val notification = buildNotification("Server running at $serverIpAddress")
            startForeground(1001, notification)
        } catch (e: Exception) {
            e.printStackTrace()
            stopServer()
        }
    }

    private fun stopServer() {
        try {
            httpServer?.stop(0)
            httpServer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isServerRunning = false
        serverIpAddress = ""
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LS Files Wireless Transfer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Wi-Fi file server active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LS Files PC Web Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    private class WebHandler(val service: FileTransferService) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val uri = exchange.requestURI.toString()
                val root = Environment.getExternalStorageDirectory()

                if (uri.startsWith("/download")) {
                    val query = exchange.requestURI.query ?: ""
                    val pathParam = query.split("&").find { it.startsWith("path=") }?.substringAfter("path=") ?: ""
                    val decodedPath = URLDecoder.decode(pathParam, "UTF-8")
                    val file = File(decodedPath)

                    if (file.exists() && file.isFile) {
                        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                        exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"${file.name}\"")
                        exchange.sendResponseHeaders(200, file.length())

                        FileInputStream(file).use { fis ->
                            fis.copyTo(exchange.responseBody)
                        }
                        exchange.responseBody.close()
                        return
                    }
                }

                // Handle file upload
                if (exchange.requestMethod.equals("POST", ignoreCase = true) && uri.startsWith("/upload")) {
                    val query = exchange.requestURI.query ?: ""
                    val targetDirParam = query.split("&").find { it.startsWith("dir=") }?.substringAfter("dir=") ?: ""
                    val targetDirPath = if (targetDirParam.isNotEmpty()) URLDecoder.decode(targetDirParam, "UTF-8") else root.absolutePath
                    val targetDir = File(targetDirPath)

                    val contentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
                    if (contentType.contains("multipart/form-data")) {
                        val boundary = contentType.substringAfter("boundary=").trim()
                        val inputStream = exchange.requestBody
                        saveUploadedFile(inputStream, boundary, targetDir)
                    }

                    // Redirect back
                    exchange.responseHeaders.add("Location", "/?dir=" + URLEncoder.encode(targetDirPath, "UTF-8"))
                    exchange.sendResponseHeaders(302, -1)
                    return
                }

                // Default: HTML File Browser UI
                val query = exchange.requestURI.query ?: ""
                val dirParam = query.split("&").find { it.startsWith("dir=") }?.substringAfter("dir=") ?: ""
                val currentDir = if (dirParam.isNotEmpty()) File(URLDecoder.decode(dirParam, "UTF-8")) else root

                val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

                val html = StringBuilder()
                html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                html.append("<title>LS Files - PC Web Transfer</title>")
                html.append("<style>")
                html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 20px; }")
                html.append(".container { max-width: 900px; margin: 0 auto; background: #1e293b; padding: 24px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }")
                html.append("h1 { margin-top: 0; color: #38bdf8; font-size: 24px; display: flex; align-items: center; gap: 10px; }")
                html.append(".path { background: #0f172a; padding: 12px; border-radius: 6px; font-family: monospace; word-break: break-all; margin-bottom: 20px; border: 1px solid #334155; }")
                html.append(".upload-box { background: #334155; padding: 16px; border-radius: 8px; margin-bottom: 20px; border: 2px dashed #38bdf8; text-align: center; }")
                html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; }")
                html.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #334155; }")
                html.append("th { background: #0f172a; color: #94a3b8; }")
                html.append("tr:hover { background: #334155; }")
                html.append("a { color: #38bdf8; text-decoration: none; font-weight: 500; }")
                html.append("a:hover { text-decoration: underline; }")
                html.append(".btn { background: #0284c7; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; }")
                html.append("</style></head><body>")
                html.append("<div class='container'>")
                html.append("<h1>📁 LS Files - Wireless PC Transfer</h1>")
                html.append("<div class='path'>Directory: ${currentDir.absolutePath}</div>")

                // Upload Form
                html.append("<div class='upload-box'>")
                html.append("<h3>📤 Upload File to Phone</h3>")
                html.append("<form action='/upload?dir=${URLEncoder.encode(currentDir.absolutePath, "UTF-8")}' method='post' enctype='multipart/form-data'>")
                html.append("<input type='file' name='file' required style='margin-right: 10px;'>")
                html.append("<input type='submit' value='Upload File' class='btn'>")
                html.append("</form></div>")

                // Directory listing table
                html.append("<table><tr><th>Name</th><th>Size</th><th>Action</th></tr>")

                // Parent Dir
                if (currentDir.parentFile != null && currentDir.absolutePath != root.absolutePath) {
                    val parentLink = "/?dir=" + URLEncoder.encode(currentDir.parentFile!!.absolutePath, "UTF-8")
                    html.append("<tr><td><a href='$parentLink'>📁 .. (Parent Directory)</a></td><td>-</td><td>-</td></tr>")
                }

                for (f in files) {
                    val encodedPath = URLEncoder.encode(f.absolutePath, "UTF-8")
                    if (f.isDirectory) {
                        html.append("<tr><td><a href='/?dir=$encodedPath'>📁 ${f.name}/</a></td><td>-</td><td>-</td></tr>")
                    } else {
                        val dlLink = "/download?path=$encodedPath"
                        val sizeMB = String.format("%.2f MB", f.length().toDouble() / (1024 * 1024))
                        html.append("<tr><td>📄 ${f.name}</td><td>$sizeMB</td><td><a href='$dlLink' class='btn' style='padding: 4px 10px; font-size: 12px;'>Download</a></td></tr>")
                    }
                }
                html.append("</table></div></body></html>")

                val bytes = html.toString().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.responseBody.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun saveUploadedFile(inputStream: InputStream, boundary: String, targetDir: File) {
            try {
                val boundaryPattern = ("--" + boundary).toByteArray(Charsets.UTF_8)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var filename = "uploaded_file_${System.currentTimeMillis()}"

                var headerDone = false
                var lineBaos = ByteArrayOutputStream()

                // Read line by line until empty line after header
                while (inputStream.read().also { bytesRead = it } != -1) {
                    if (bytesRead == '\n'.code) {
                        val line = lineBaos.toString("UTF-8").trim()
                        if (line.contains("filename=")) {
                            val extracted = line.substringAfter("filename=").replace("\"", "").trim()
                            if (extracted.isNotEmpty()) {
                                filename = File(extracted).name
                            }
                        }
                        if (line.isEmpty() && headerDone) {
                            break
                        }
                        if (line.isEmpty()) {
                            headerDone = true
                        }
                        lineBaos.reset()
                    } else if (bytesRead != '\r'.code) {
                        lineBaos.write(bytesRead)
                    }
                }

                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, filename)

                FileOutputStream(targetFile).use { fos ->
                    val fileBuffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(fileBuffer).also { read = it } != -1) {
                        fos.write(fileBuffer, 0, read)
                    }
                }

                // Strip trailing boundary from uploaded file
                val fileLen = targetFile.length()
                if (fileLen > 0) {
                    val searchWindow = (1024 * 4).coerceAtMost(fileLen.toInt())
                    RandomAccessFile(targetFile, "rw").use { raf ->
                        val startPos = fileLen - searchWindow
                        raf.seek(startPos)
                        val tailBuffer = ByteArray(searchWindow)
                        raf.readFully(tailBuffer)

                        val crlfBoundary = ("\r\n--" + boundary).toByteArray(Charsets.UTF_8)
                        val plainBoundary = ("--" + boundary).toByteArray(Charsets.UTF_8)

                        var matchOffset = indexOfBytes(tailBuffer, crlfBoundary)
                        if (matchOffset == -1) {
                            matchOffset = indexOfBytes(tailBuffer, plainBoundary)
                        }

                        if (matchOffset != -1) {
                            val cleanLength = startPos + matchOffset
                            raf.setLength(cleanLength)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun indexOfBytes(source: ByteArray, target: ByteArray): Int {
            if (target.isEmpty() || source.size < target.size) return -1
            for (i in 0..(source.size - target.size)) {
                var found = true
                for (j in target.indices) {
                    if (source[i + j] != target[j]) {
                        found = false
                        break
                    }
                }
                if (found) return i
            }
            return -1
        }
    }
}
