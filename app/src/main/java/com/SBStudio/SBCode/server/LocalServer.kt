package com.SBStudio.SBCode.server

import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Serves the files of one project folder on 127.0.0.1 (this phone only).
 * It needs no internet; the INTERNET permission is only there because Android
 * requires it to open any socket.
 */
class LocalServer(private val root: File) {

    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = running && serverSocket?.isClosed == false

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        serverSocket = ss
        running = true
        pool.execute {
            while (running) {
                try {
                    val s = ss.accept()
                    if (!running) {
                        s.close()
                        break
                    }
                    try {
                        pool.execute { handle(s) }
                    } catch (e: RejectedExecutionException) {
                        s.close()
                        break
                    }
                } catch (e: IOException) {
                    if (!running) break
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 15_000
                val input = s.getInputStream()
                val out = BufferedOutputStream(s.getOutputStream())
                val requestLine = readLine(input) ?: return
                while (true) {
                    val h = readLine(input)
                    if (h == null || h.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                if (method != "GET" && method != "HEAD") {
                    respond(out, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method not allowed".toByteArray(), true)
                    return
                }
                var path = parts[1]
                path.indexOf('?').let { if (it >= 0) path = path.substring(0, it) }
                path.indexOf('#').let { if (it >= 0) path = path.substring(0, it) }
                val decoded = try {
                    URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
                } catch (e: Exception) {
                    path
                }
                val file = resolve(decoded)
                if (file == null) {
                    val body = notFoundPage(decoded).toByteArray(Charsets.UTF_8)
                    respond(out, 404, "Not Found", "text/html; charset=utf-8", body, method == "GET")
                    return
                }
                val length = file.length()
                val head = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${mimeFor(file)}\r\n" +
                    "Content-Length: $length\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                if (method == "GET") {
                    file.inputStream().use { it.copyTo(out) }
                }
                out.flush()
            }
        } catch (_: Exception) {
            // Browsers sometimes open and drop connections. That's normal.
        }
    }

    /** Maps a URL path to a real file inside [root]. Returns null if it doesn't exist or tries to leave the folder. */
    internal fun resolve(urlPath: String): File? {
        val rootCanon = root.canonicalFile
        var f = File(rootCanon, urlPath.trimStart('/')).canonicalFile
        val inside = f.path == rootCanon.path || f.path.startsWith(rootCanon.path + File.separator)
        if (!inside) return null
        if (f.isDirectory) f = File(f, "index.html")
        return if (f.isFile) f else null
    }

    private fun respond(out: BufferedOutputStream, code: Int, text: String, type: String, body: ByteArray, withBody: Boolean) {
        val head = "HTTP/1.1 $code $text\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        if (withBody) out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun notFoundPage(path: String): String {
        val safe = path.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>Not found</title></head><body style=\"font-family:sans-serif;padding:24px\">" +
            "<h2>404 - File not found</h2><p>SBCode couldn't find <b>$safe</b> in this project.</p></body></html>"
    }

    companion object {
        fun mimeFor(file: File): String = when (file.extension.lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "json", "map" -> "application/json; charset=utf-8"
            "txt", "md" -> "text/plain; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}

/** Keeps one server alive at a time (the one for the project being previewed). */
object ServerController {
    private var server: LocalServer? = null
    private var root: File? = null

    @Synchronized
    fun start(dir: File): Int {
        val cur = server
        if (cur != null && cur.isRunning && root == dir) return cur.port
        cur?.stop()
        val s = LocalServer(dir)
        s.start()
        server = s
        root = dir
        return s.port
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        root = null
    }
}
