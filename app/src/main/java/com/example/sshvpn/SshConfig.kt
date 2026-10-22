package com.example.sshvpn

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

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
    // Xray-family fields
    val uuid: String = "",
    val encryption: String = "none",
    val security: String = "none", // none | tls | reality
    val sni: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val network: String = "tcp", // tcp | ws | grpc | h2
    val path: String = "",
    val serviceName: String = "",
    val hostHeader: String = "",
    val alpn: String = "",
    val flow: String = "",
    val alterId: Int = 0,
    val method: String = "aes-256-gcm", // Shadowsocks
    val packetEncoding: String = "",
) {
    val isSsh: Boolean
        get() = protocol.startsWith("SSH", ignoreCase = true) ||
            protocol.equals("ssh-direct", true) ||
            protocol.equals("ssh-udpgw", true)

    val isXrayFamily: Boolean
        get() = !isSsh

    fun displayProtocol(): String = when (protocol.uppercase()) {
        "SSH-DIRECT", "SSH" -> "SSH-Direct"
        "SSH-UDPGW" -> "SSH+UDPGW"
        "VLESS" ->