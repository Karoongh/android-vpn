package com.example.sshvpn

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Unified profile for SSH and Xray-family protocols (VLESS / VMess / Trojan / Shadowsocks).
 * SSH path uses JSch; other protocols use embedded Xray-core -> local SOCKS -> hev TUN.
 */
data class SshConfig(
    val host: String,
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val name: String = "",
    val udpgwPort: Int = 7300,
    val transparentDns: Boolean = true,
    /** SSH-Direct | SSH-UDPGW | VLESS | VMESS | TROJAN | SHADOWSOCKS */
    val protocol: String = "SSH-Direct",
    val maxChannels: Int = 48,
    val uuid: String = "",
    val encryption: String = "none",
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val network: String = "tcp",
    val path: String = "",
    val serviceName: String = "",
    val hostHeader: String = "",
    val alpn: String = "",
    val flow: String = "",
    val alterId: Int = 0,
    val method: String = "aes-256-gcm",
    val packetEncoding: String = "",
) {
    val isSsh: Boolean
        get() {
            val p = protocol.uppercase()
            return p.startsWith("SSH")
        }

    val isXrayFamily: Boolean
        get() = !isSsh

    fun displayProtocol(): String = when (protocol.uppercase()) {
        "SSH-DIRECT", "SSH" -> "SSH-Direct"
        "SSH-UDPGW" -> "SSH+UDPGW"
        "VLESS" -> "VLESS"
        "VMESS" -> "VMess"
        "TROJAN" -> "Trojan"
        "SHADOWSOCKS", "SS" -> "Shadowsocks"
        else -> protocol.ifBlank { "SSH-Direct" }
    }

    fun toJson(): String = JSONObject().apply {
        put("host", host)
        put("port", port)
        put("username", username)
        put("password", password)
        put("privateKey", privateKey)
        put("passphrase", passphrase)
        put("name", name)
        put("udpgwPort", udpgwPort)
        put("transparentDns", transparentDns)
        put("protocol", protocol)
        put("maxChannels", maxChannels.coerceIn(8, 128))
        put("uuid", uuid)
        put("encryption", encryption)
        put("security", security)
        put("sni", sni)
        put("fingerprint", fingerprint)
        put("publicKey", publicKey)
        put("shortId", shortId)
        put("spiderX", spiderX)
        put("network", network)
        put("path", path)
        put("serviceName", serviceName)
        put("hostHeader", hostHeader)
        put("alpn", alpn)
        put("flow", flow)
        put("alterId", alterId)
        put("method", method)
        put("packetEncoding", packetEncoding)
    }.toString()

    companion object {
        fun fromJson(text: String): SshConfig? = try {
            val o = JSONObject(text)
            val host = o.optString("host", o.optString("server", o.optString("ip", ""))).trim()
            val protocol = o.optString("protocol", "SSH-Direct").ifBlank { "SSH-Direct" }
            val user = o.optString("username", o.optString("user", "")).trim()
            val uuid = o.optString("uuid", o.optString("id", "")).trim()
            val isSshProto = protocol.uppercase().startsWith("SSH")
            if (host.isEmpty()) null
            else if (isSshProto && user.isEmpty()) null
            else SshConfig(
                host = host,
                port = o.optInt("port", if (isSshProto) 22 else 443).let { if (it in 1..65535) it else if (isSshProto) 22 else 443 },
                username = user,
                password = o.optString("password", o.optString("pass", "")),
                privateKey = o.optString("privateKey", o.optString("key", "")),
                passphrase = o.optString("passphrase", ""),
                name = o.optString("name", o.optString("remarks", "")),
                udpgwPort = o.optInt("udpgwPort", o.optInt("udpgw", 7300)).let { if (it in 1..65535) it else 7300 },
                transparentDns = o.optBoolean("transparentDns", o.optBoolean("dns", true)),
                protocol = protocol,
                maxChannels = o.optInt("maxChannels", 48).coerceIn(8, 128),
                uuid = uuid,
                encryption = o.optString("encryption", o.optString("scy", "none")),
                security = o.optString("security", "none"),
                sni = o.optString("sni", ""),
                fingerprint = o.optString("fingerprint", o.optString("fp", "")),
                publicKey = o.optString("publicKey", o.optString("pbk", "")),
                shortId = o.optString("shortId", o.optString("sid", "")),
                spiderX = o.optString("spiderX", o.optString("spx", "")),
                network = o.optString("network", o.optString("type", o.optString("net", "tcp"))),
                path = o.optString("path", ""),
                serviceName = o.optString("serviceName", ""),
                hostHeader = o.optString("hostHeader", ""),
                alpn = o.optString("alpn", ""),
                flow = o.optString("flow", ""),
                alterId = o.optInt("alterId", o.optInt("aid", 0)),
                method = o.optString("method", "aes-256-gcm"),
                packetEncoding = o.optString("packetEncoding", ""),
            )
        } catch (_: Exception) {
            null
        }
    }
}

object ConfigParser {

    fun parse(raw: String): SshConfig? = parse(raw, allowBase64 = true)

    fun parseMany(raw: String): List<SshConfig> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size > 1) {
            val out = ArrayList<SshConfig>()
            for (line in lines) parse(line)?.let { out.add(it) }
            if (out.isNotEmpty()) return out
        }
        if (looksLikeBase64(text) && !text.contains("://")) {
            decodeBase64(text)?.let { decoded ->
                val multi = decoded.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                    .mapNotNull { parse(it, allowBase64 = false) }.toList()
                if (multi.isNotEmpty()) return multi
            }
        }
        return listOfNotNull(parse(text))
    }

    private fun parse(raw: String, allowBase64: Boolean): SshConfig? {
        val text = raw.trim().trim('"', '\'')
        if (text.isEmpty()) return null
        if (text.startsWith("{")) return SshConfig.fromJson(text)
        if (text.contains('\n') && text.contains('=')) parseKeyValue(text)?.let { return it }

        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val lower = line.lowercase()
        when {
            lower.startsWith("vless://") -> return parseVless(line)
            lower.startsWith("vmess://") -> return parseVmess(line)
            lower.startsWith("trojan://") -> return parseTrojan(line)
            lower.startsWith("ss://") -> return parseShadowsocks(line)
            lower.startsWith("ssh://") -> return parseSshUri(line.substring(6))
            lower.startsWith("ssh-direct://") -> return parseSshUri(line.substring(13))
            lower.startsWith("sshdirect://") -> return parseSshUri(line.substring(12))
        }
        if (allowBase64 && looksLikeBase64(line)) {
            decodeBase64(line)?.let { decoded -> parse(decoded, allowBase64 = false)?.let { return it } }
        }
        return parsePlainSsh(line)
    }

    private fun urlDecode(s: String) = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) { s }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isEmpty()) return emptyMap()
        val map = HashMap<String, String>()
        q.split('&').forEach { part ->
            val i = part.indexOf('=')
            if (i > 0) map[urlDecode(part.substring(0, i)).lowercase()] = urlDecode(part.substring(i + 1))
        }
        return map
    }

    private fun splitUri(body: String): Triple<String, String, Map<String, String>> {
        var rest = body
        var name = ""
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            name = urlDecode(rest.substring(hash + 1))
            rest = rest.substring(0, hash)
        }
        var query = emptyMap<String, String>()
        val qi = rest.indexOf('?')
        if (qi >= 0) {
            query = parseQuery(rest.substring(qi + 1))
            rest = rest.substring(0, qi)
        }
        return Triple(rest.trimEnd('/'), name, query)
    }

    private fun parseVless(line: String): SshConfig? {
        val (body, name, q) = splitUri(line.substring(8))
        val at = body.lastIndexOf('@')
        if (at < 0) return null
        val uuid = urlDecode(body.substring(0, at))
        val (host, port) = splitHostPort(body.substring(at + 1)) ?: return null
        if (uuid.isEmpty()) return null
        return SshConfig(
            host = host, port = port, name = name, protocol = "VLESS", uuid = uuid,
            encryption = q["encryption"] ?: "none",
            security = q["security"] ?: q["tls"] ?: "none",
            sni = q["sni"] ?: "",
            fingerprint = q["fp"] ?: q["fingerprint"] ?: "",
            publicKey = q["pbk"] ?: q["publickey"] ?: "",
            shortId = q["sid"] ?: q["shortid"] ?: "",
            spiderX = q["spx"] ?: "",
            network = q["type"] ?: q["network"] ?: "tcp",
            path = q["path"] ?: "",
            serviceName = q["servicename"] ?: "",
            hostHeader = q["host"] ?: "",
            alpn = q["alpn"] ?: "",
            flow = q["flow"] ?: "",
        )
    }

    private fun parseVmess(line: String): SshConfig? {
        val payload = line.substring(8)
        val jsonText = decodeBase64(payload) ?: return null
        return try {
            val o = JSONObject(jsonText)
            val host = o.optString("add", o.optString("host", "")).trim()
            val port = o.optInt("port", 443).let { if (it in 1..65535) it else 443 }
            val uuid = o.optString("id", "").trim()
            if (host.isEmpty() || uuid.isEmpty()) null
            else SshConfig(
                host = host, port = port,
                name = o.optString("ps", o.optString("remarks", "")),
                protocol = "VMESS", uuid = uuid,
                encryption = o.optString("scy", o.optString("security", "auto")),
                security = when {
                    o.optString("tls").equals("tls", true) -> "tls"
                    o.optString("tls").equals("reality", true) -> "reality"
                    else -> o.optString("tls", "none").ifBlank { "none" }
                },
                sni = o.optString("sni", ""),
                fingerprint = o.optString("fp", ""),
                network = o.optString("net", o.optString("type", "tcp")),
                path = o.optString("path", ""),
                hostHeader = o.optString("host", ""),
                alpn = o.optString("alpn", ""),
                alterId = o.optInt("aid", 0),
            )
        } catch (_: Exception) { null }
    }

    private fun parseTrojan(line: String): SshConfig? {
        val (body, name, q) = splitUri(line.substring(9))
        val at = body.lastIndexOf('@')
        if (at < 0) return null
        val password = urlDecode(body.substring(0, at))
        val (host, port) = splitHostPort(body.substring(at + 1)) ?: return null
        if (password.isEmpty()) return null
        return SshConfig(
            host = host, port = port, password = password, name = name, protocol = "TROJAN",
            security = q["security"] ?: "tls",
            sni = q["sni"] ?: "",
            fingerprint = q["fp"] ?: q["fingerprint"] ?: "",
            network = q["type"] ?: q["network"] ?: "tcp",
            path = q["path"] ?: "",
            serviceName = q["servicename"] ?: "",
            hostHeader = q["host"] ?: "",
            alpn = q["alpn"] ?: "",
        )
    }

    private fun parseShadowsocks(line: String): SshConfig? {
        val (body0, name, _) = splitUri(line.substring(5))
        var body = body0
        val at = body.lastIndexOf('@')
        if (at < 0) {
            val decoded = decodeBase64(body) ?: return null
            return parseShadowsocks("ss://$decoded${if (name.isNotEmpty()) "#$name" else ""}")
        }
        var userInfo = body.substring(0, at)
        val hostPart = body.substring(at + 1)
        if (!userInfo.contains(':')) decodeBase64(userInfo)?.let { userInfo = it }
        val colon = userInfo.indexOf(':')
        if (colon < 0) return null
        val method = urlDecode(userInfo.substring(0, colon))
        val password = urlDecode(userInfo.substring(colon + 1))
        val (host, port) = splitHostPort(hostPart) ?: return null
        if (method.isEmpty() || password.isEmpty()) return null
        return SshConfig(
            host = host, port = port, password = password, name = name,
            protocol = "SHADOWSOCKS", method = method,
        )
    }

    private fun parseSshUri(rest: String): SshConfig? {
        val (body, name, q) = splitUri(rest)
        val at = body.lastIndexOf('@')
        if (at < 0) return null
        val cred = body.substring(0, at)
        val hostPart = body.substring(at + 1)
        val colon = cred.indexOf(':')
        val user = urlDecode(if (colon >= 0) cred.substring(0, colon) else cred)
        val pass = if (colon >= 0) urlDecode(cred.substring(colon + 1)) else ""
        val (host, port) = splitHostPort(hostPart) ?: return null
        if (user.isEmpty()) return null
        val udpgw = q["udpgw"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 7300
        val protocol = if (q.containsKey("udpgw")) "SSH-UDPGW" else "SSH-Direct"
        return SshConfig(host = host, port = port, username = user, password = pass, name = name, udpgwPort = udpgw, protocol = protocol)
    }

    private fun parsePlainSsh(line: String): SshConfig? {
        var name = ""
        var body = line
        val hash = body.indexOf('#')
        if (hash >= 0) {
            name = body.substring(hash + 1).trim()
            body = body.substring(0, hash)
        }
        val at = body.indexOf('@')
        if (at > 0) {
            val hostPart = body.substring(0, at)
            val cred = body.substring(at + 1)
            val (host, port) = splitHostPort(hostPart) ?: return null
            val colon = cred.indexOf(':')
            val user = if (colon >= 0) cred.substring(0, colon) else cred
            val pass = if (colon >= 0) cred.substring(colon + 1) else ""
            if (user.isEmpty()) return null
            return SshConfig(host = host, port = port, username = user, password = pass, name = name)
        }
        val parts = body.split(':', limit = 4)
        if (parts.size == 4) {
            val port = parts[1].toIntOrNull()
            if (port != null && port in 1..65535 && parts[0].isNotEmpty() && parts[2].isNotEmpty()) {
                return SshConfig(host = parts[0], port = port, username = parts[2], password = parts[3], name = name)
            }
        }
        return null
    }

    private fun parseKeyValue(text: String): SshConfig? {
        val map = HashMap<String, String>()
        text.lineSequence().forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) map[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val host = map["host"] ?: map["server"] ?: map["ip"] ?: return null
        val protocol = map["protocol"] ?: "SSH-Direct"
        val user = map["user"] ?: map["username"] ?: ""
        val uuid = map["uuid"] ?: map["id"] ?: ""
        val isSshProto = protocol.uppercase().startsWith("SSH")
        if (isSshProto && user.isEmpty()) return null
        val port = map["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: if (isSshProto) 22 else 443
        return SshConfig(
            host = host, port = port, username = user,
            password = map["pass"] ?: map["password"] ?: "",
            name = map["name"] ?: map["remarks"] ?: "",
            udpgwPort = map["udpgw"]?.toIntOrNull()?.takeIf { it in 1..65535 }
                ?: map["udpgwport"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 7300,
            transparentDns = map["dns"]?.lowercase() !in listOf("off", "false", "0", "no"),
            protocol = protocol,
            maxChannels = map["maxchannels"]?.toIntOrNull()?.coerceIn(8, 128) ?: 48,
            uuid = uuid,
            encryption = map["encryption"] ?: "none",
            security = map["security"] ?: "none",
            sni = map["sni"] ?: "",
            network = map["network"] ?: map["type"] ?: "tcp",
            path = map["path"] ?: "",
            method = map["method"] ?: "aes-256-gcm",
        )
    }

    private fun splitHostPort(s: String): Pair<String, Int>? {
        val t = s.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("[")) {
            val end = t.indexOf(']')
            if (end < 0) return null
            val host = t.substring(1, end)
            val port = t.substring(end + 1).removePrefix(":").toIntOrNull() ?: 443
            return if (host.isEmpty() || port !in 1..65535) null else host to port
        }
        val colon = t.lastIndexOf(':')
        if (colon < 0) return t to 443
        val host = t.substring(0, colon)
        val port = t.substring(colon + 1).toIntOrNull() ?: return null
        return if (host.isEmpty() || port !in 1..65535) null else host to port
    }

    private fun looksLikeBase64(s: String): Boolean =
        s.length >= 12 && s.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }

    private fun decodeBase64(s: String): String? = try {
        String(Base64.decode(s, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
    } catch (_: Exception) {
        try { String(Base64.decode(s, Base64.URL_SAFE), Charsets.UTF_8) } catch (_: Exception) { null }
    }
}
