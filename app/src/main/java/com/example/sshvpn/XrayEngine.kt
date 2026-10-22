package com.example.sshvpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs embedded Xray-core as a local SOCKS5 proxy for VLESS / VMess / Trojan / Shadowsocks.
 * Traffic path: App -> hev TUN -> SOCKS(xray) -> remote.
 */
class XrayEngine(private val context: Context) {

    private var process: Process? = null
    private val running = AtomicBoolean(false)
    private var socksPort: Int = 0

    fun start(cfg: SshConfig, socksPort: Int): Int {
        stop()
        this.socksPort = socksPort
        val binary = ensureBinary()
        val confFile = File(context.filesDir, "xray-config.json")
        confFile.writeText(buildConfigJson(cfg, socksPort))
        LogStore.append("Xray config written (${cfg.displayProtocol()} -> 127.0.0.1:$socksPort)")

        val pb = ProcessBuilder(binary.absolutePath, "run", "-c", confFile.absolutePath)
        pb.directory(context.filesDir)
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p
        running.set(true)

        Thread({
            try {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        if (line.contains("\"level\":\"debug\"")) return@forEach
                        val short = line.take(220)
                        if (short.contains("error", true) ||
                            short.contains("failed", true) ||
                            short.contains("warning", true)
                        ) {
                            LogStore.append("Xray: $short")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }, "xray-log").also { it.isDaemon = true; it.start() }

        var ready = false
        var i = 0
        while (i < 30 && running.get() && !ready) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("127.0.0.1", socksPort), 200)
                    ready = true
                }
            } catch (_: Exception) {
                Thread.sleep(100)
            }
            i++
        }
        if (!ready) {
            val alive = try { p.isAlive } catch (_: Exception) { false }
            if (!alive) {
                stop()
                throw IllegalStateException("Xray exited early — check config / binary")
            }
            LogStore.append("Xray SOCKS not confirmed yet; continuing")
        } else {
            LogStore.append("Xray SOCKS ready on $socksPort")
        }
        return socksPort
    }

    fun isRunning(): Boolean {
        val p = process ?: return false
        return try { p.isAlive && running.get() } catch (_: Exception) { false }
    }

    fun stop() {
        running.set(false)
        try { process?.destroy() } catch (_: Exception) {}
        try {
            Thread.sleep(150)
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
    }

    private fun ensureBinary(): File {
        val out = File(context.filesDir, "xray")
        if (out.exists() && out.canExecute() && out.length() > 1_000_000) return out

        val abis = BuildAbis.preferred()
        val assetManager = context.assets
        var copied = false
        for (abi in abis) {
            val assetPath = "xray/$abi/xray"
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                copied = true
                LogStore.append("Xray binary extracted ($abi)")
                break
            } catch (_: Exception) {
            }
        }
        if (!copied) {
            try {
                assetManager.open("xray/xray").use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                copied = true
                LogStore.append("Xray binary extracted (generic)")
            } catch (_: Exception) {
            }
        }
        if (!copied || !out.exists()) {
            throw IllegalStateException(
                "Xray binary missing. Rebuild APK with Xray assets (CI downloads Xray-core).",
            )
        }
        out.setExecutable(true, false)
        return out
    }

    companion object {
        fun buildConfigJson(cfg: SshConfig, socksPort: Int): String {
            val root = JSONObject()
            root.put("log", JSONObject().put("loglevel", "warning"))

            val inbounds = JSONArray()
            inbounds.put(
                JSONObject()
                    .put("tag", "socks-in")
                    .put("port", socksPort)
                    .put("listen", "127.0.0.1")
                    .put("protocol", "socks")
                    .put(
                        "settings",
                        JSONObject()
                            .put("udp", true)
                            .put("auth", "noauth"),
                    ),
            )
            root.put("inbounds", inbounds)

            val outbound = when (cfg.protocol.uppercase()) {
                "VLESS" -> vlessOutbound(cfg)
                "VMESS" -> vmessOutbound(cfg)
                "TROJAN" -> trojanOutbound(cfg)
                "SHADOWSOCKS", "SS" -> ssOutbound(cfg)
                else -> throw IllegalArgumentException("Unsupported Xray protocol: ${cfg.protocol}")
            }
            val outbounds = JSONArray()
            outbounds.put(outbound)
            outbounds.put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom"),
            )
            outbounds.put(
                JSONObject()
                    .put("tag", "block")
                    .put("protocol", "blackhole"),
            )
            root.put("outbounds", outbounds)
            return root.toString(2)
        }

        private fun streamSettings(cfg: SshConfig): JSONObject {
            val stream = JSONObject()
            val net = cfg.network.ifBlank { "tcp" }.lowercase()
            stream.put("network", net)

            when (net) {
                "ws" -> {
                    val ws = JSONObject()
                    if (cfg.path.isNotBlank()) ws.put("path", cfg.path)
                    val headers = JSONObject()
                    val host = cfg.hostHeader.ifBlank { cfg.sni }
                    if (host.isNotBlank()) headers.put("Host", host)
                    if (headers.length() > 0) ws.put("headers", headers)
                    stream.put("wsSettings", ws)
                }
                "grpc" -> {
                    val grpc = JSONObject()
                    val sn = cfg.serviceName.ifBlank { cfg.path }
                    if (sn.isNotBlank()) grpc.put("serviceName", sn)
                    stream.put("grpcSettings", grpc)
                }
                "h2", "http" -> {
                    val h2 = JSONObject()
                    if (cfg.path.isNotBlank()) h2.put("path", cfg.path)
                    val host = cfg.hostHeader.ifBlank { cfg.sni }
                    if (host.isNotBlank()) h2.put("host", JSONArray().put(host))
                    stream.put("httpSettings", h2)
                }
            }

            val sec = cfg.security.ifBlank { "none" }.lowercase()
            when {
                sec == "tls" || sec == "xtls" -> {
                    stream.put("security", "tls")
                    val tls = JSONObject()
                    val sni = cfg.sni.ifBlank { cfg.hostHeader.ifBlank { cfg.host } }
                    if (sni.isNotBlank()) tls.put("serverName", sni)
                    if (cfg.fingerprint.isNotBlank()) tls.put("fingerprint", cfg.fingerprint)
                    if (cfg.alpn.isNotBlank()) {
                        val arr = JSONArray()
                        cfg.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            .forEach { arr.put(it) }
                                               if (arr.length() > 0) tls.put("alpn", arr)
                    }
                    tls.put("allowInsecure", true)
                    stream.put("tlsSettings", tls)
                }
                sec == "reality" -> {
                    stream.put("security", "reality")
                    val reality = JSONObject()
                    val sni = cfg.sni.ifBlank { cfg.host }
                    if (sni.isNotBlank()) reality.put("serverName", sni)
                    if (cfg.fingerprint.isNotBlank()) reality.put("fingerprint", cfg.fingerprint)
                    else reality.put("fingerprint", "chrome")
                    if (cfg.publicKey.isNotBlank()) reality.put("publicKey", cfg.publicKey)
                    if (cfg.shortId.isNotBlank()) reality.put("shortId", cfg.shortId)
                    if (cfg.spiderX.isNotBlank()) reality.put("spiderX", cfg.spiderX)
                    stream.put("realitySettings", reality)
                }
                else -> stream.put("security", "none")
            }
            return stream
        }

        private fun vlessOutbound(cfg: SshConfig): JSONObject {
            val user = JSONObject()
                .put("id", cfg.uuid)
                .put("encryption", cfg.encryption.ifBlank { "none" })
            if (cfg.flow.isNotBlank()) user.put("flow", cfg.flow)

            val settings = JSONObject()
                .put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", cfg.host)
                            .put("port", cfg.port)
                            .put("users", JSONArray().put(user)),
                    ),
                )

            return JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put("settings", settings)
                .put("streamSettings", streamSettings(cfg))
        }

        private fun vmessOutbound(cfg: SshConfig): JSONObject {
            val user = JSONObject()
                .put("id", cfg.uuid)
                .put("alterId", cfg.alterId)
                .put("security", cfg.encryption.ifBlank { "auto" })

            val settings = JSONObject()
                .put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", cfg.host)
                            .put("port", cfg.port)
                            .put("users", JSONArray().put(user)),
                    ),
                )

            return JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vmess")
                .put("settings", settings)
                .put("streamSettings", streamSettings(cfg))
        }

        private fun trojanOutbound(cfg: SshConfig): JSONObject {
            val settings = JSONObject()
                .put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", cfg.host)
                            .put("port", cfg.port)
                            .put("password", cfg.password),
                    ),
                )

            return JSONObject()
                .put("tag", "proxy")
                .put("protocol", "trojan")
                .put("settings", settings)
                .put("streamSettings", streamSettings(cfg))
        }

        private fun ssOutbound(cfg: SshConfig): JSONObject {
            val settings = JSONObject()
                .put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", cfg.host)
                            .put("port", cfg.port)
                            .put("method", cfg.method.ifBlank { "aes-256-gcm" })
                            .put("password", cfg.password),
                    ),
                )

            return JSONObject()
                .put("tag", "proxy")
                .put("protocol", "shadowsocks")
                .put("settings", settings)
        }
    }
}

object BuildAbis {
    fun preferred(): List<String> {
        val list = android.os.Build.SUPPORTED_ABIS?.toList().orEmpty()
        if (list.isNotEmpty()) return list
        return listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
