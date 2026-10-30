package com.example.sshvpn

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    private companion object {
        const val REQ_VPN = 1
        const val REQ_NOTIF = 4
        const val REQ_LOG_EXPORT = 5
        const val SUPPORT_EMAIL = "ghobadi.karoon@gmail.com"
        val BG = Color.parseColor("#0B1220")
        val CARD = Color.parseColor("#1A2332")
        val ACCENT = Color.parseColor("#3B82F6")
        val TEXT = Color.parseColor("#F1F5F9")
        val MUTED = Color.parseColor("#94A3B8")
        val SUCCESS = Color.parseColor("#22C55E")
        val ERROR = Color.parseColor("#EF4444")
        val WARNING = Color.parseColor("#F59E0B")
        val INPUT_BG = Color.parseColor("#243044")
        val NAV_BG = Color.parseColor("#111827")
    }

    private lateinit var profiles: ProfileStore
    private var editingId: String? = null
    private lateinit var contentHost: FrameLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabConfigs: LinearLayout
    private lateinit var tabSpeed: LinearLayout
    private lateinit var tabLogs: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var tvHomeStatus: TextView
    private lateinit var tvActiveProfile: TextView
    private lateinit var btnMain: Button
    private lateinit var configsList: LinearLayout
    private lateinit var tvLogs: TextView
    private lateinit var chartUp: TrafficChartView
    private lateinit var chartDown: TrafficChartView
    private lateinit var tvUplinkTotal: TextView
    private lateinit var tvDownlinkTotal: TextView
    private lateinit var etMaxChannels: EditText
    private val navButtons = ArrayList<TextView>()
    private var currentTab = 0
    private var disconnectRequested = false
    private var pendingLogExport: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speedRunning = false
    private val upSamples = ArrayDeque<Float>(300)
    private val downSamples = ArrayDeque<Float>(300)
    private var lastUpBytes = 0L
    private var lastDownBytes = 0L
    private val speedRunnable = Runnable { tickStats() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = BG
            window.navigationBarColor = NAV_BG
            LogStore.init(applicationContext)
            profiles = ProfileStore(this)
            editingId = profiles.getActiveId()
            buildUi()
            VpnState.listener = { runOnUiThread { try {
                if (disconnectRequested && VpnState.status != Status.CONNECTED && VpnState.status != Status.CONNECTING && VpnState.status != Status.RECONNECTING) stopDisconnectPulse()
                renderHome(); if (currentTab == 3) refreshLogs()
            } catch (_: Throwable) {} } }
            LogStore.listener = { runOnUiThread { try { if (currentTab == 3) refreshLogs() } catch (_: Throwable) {} } }
            renderHome()
            showTab(0)
        } catch (t: Throwable) {
            val msg = "${t.javaClass.simpleName}: ${t.message}"
            try { android.util.Log.e("KaroonTunnel", "onCreate crashed", t) } catch (_: Throwable) {}
            val tv = TextView(this).apply {
                text = "Startup error:\n$msg\n\nPlease reinstall or report this message."
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLACK)
                setPadding(48, 48, 48, 48)
                textSize = 14f
            }
            setContentView(tv)
            try { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        }
    }
    override fun onDestroy() { stopSpeedMonitor(); stopChartAnimation(); stopDisconnectPulse(); VpnState.listener = null; LogStore.listener = null; super.onDestroy() }
    override fun onPause() { stopSpeedMonitor(); stopChartAnimation(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (currentTab == 2) {
            startSpeedMonitor()
            startChartAnimation()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        contentHost = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        tabHome = buildHomeTab(); tabConfigs = buildConfigsTab(); tabSpeed = buildSpeedTab(); tabLogs = buildLogsTab(); tabSettings = buildSettingsTab()
        listOf(tabHome, tabConfigs, tabSpeed, tabLogs, tabSettings).forEach {
            it.visibility = View.GONE
            contentHost.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(NAV_BG); setPadding(0, dp(6), 0, dp(10)); elevation = 12f }
        listOf("Home", "Configs", "Stats", "Logs", "More").forEachIndexed { i, label ->
            val tv = TextView(this).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(MUTED); setPadding(0, dp(8), 0, dp(8))
                background = ripple(NAV_BG, 12)
                setOnClickListener { animatePress(this); showTab(i) }
            }
            navButtons.add(tv)
            nav.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(contentHost); root.addView(nav); setContentView(root)
    }

    private fun showTab(index: Int) {
        currentTab = index
        listOf(tabHome, tabConfigs, tabSpeed, tabLogs, tabSettings).forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        navButtons.forEachIndexed { i, tv -> tv.setTextColor(if (i == index) ACCENT else MUTED); tv.typeface = if (i == index) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        when (index) {
            0 -> renderHome()
            1 -> refreshConfigsList()
            2 -> { startSpeedMonitor(); startChartAnimation() }
            3 -> refreshLogs()
        }
        if (index != 2) stopChartAnimation()
    }

    private fun buildHomeTab(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(24)); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(TextView(this).apply { text = "Karoon Tunnel"; textSize = 24f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
        col.addView(TextView(this).apply { text = "SSH · VLESS · VMess · Trojan · Shadowsocks"; textSize = 12f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(20)) })
        tvActiveProfile = TextView(this).apply { textSize = 13f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) }
        col.addView(tvActiveProfile)
        val statusCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, 16); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        tvHomeStatus = TextView(this).apply { textSize = 15f; setTextColor(TEXT); gravity = Gravity.CENTER }
        statusCard.addView(tvHomeStatus)
        col.addView(statusCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) })
        btnMain = Button(this).apply {
            text = "Connect"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = ripple(ACCENT, 28); setPadding(0, dp(18), 0, dp(18)); minHeight = dp(64); isAllCaps = false
            elevation = dp(4).toFloat(); setOnClickListener { animatePress(this); onMainButton() }
        }
        col.addView(btnMain, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(TextView(this).apply {
            text = "Configs tab → Import link or Add new\nSupports vless:// vmess:// trojan:// ss:// ssh://"
            textSize = 12f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0)
        })
        scroll.addView(col)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)) }
    }

    private fun renderHome() {
        val st = VpnState.status
        val profile = profiles.getActive()
        tvActiveProfile.text = profile?.let { "Profile: ${it.displayName}\n${it.config.displayProtocol()}" } ?: "No profile selected"
        if (disconnectRequested && st in listOf(Status.CONNECTED, Status.CONNECTING, Status.RECONNECTING)) {
            tvHomeStatus.setTextColor(WARNING)
            tvHomeStatus.text = "Disconnecting..."
            btnMain.text = "Disconnecting..."
            btnMain.isEnabled = false
            btnMain.background = ripple(WARNING, 28)
            return
        }
        val (label, color) = when (st) {
            Status.DISCONNECTED -> "Disconnected" to MUTED
            Status.CONNECTING -> "Connecting..." to WARNING
            Status.RECONNECTING -> "Reconnecting..." to WARNING
            Status.CONNECTED -> "Connected" to SUCCESS
            Status.ERROR -> "Error" to ERROR
        }
        tvHomeStatus.setTextColor(color)
        tvHomeStatus.text = if (VpnState.message.isBlank()) label else "$label\n${VpnState.message}"
        when (st) {
            Status.DISCONNECTED, Status.ERROR -> { btnMain.text = "Connect"; btnMain.isEnabled = true; btnMain.background = ripple(ACCENT, 28) }
            Status.CONNECTING, Status.RECONNECTING -> { btnMain.text = if (st == Status.RECONNECTING) "Reconnecting..." else "Connecting..."; btnMain.isEnabled = true; btnMain.background = ripple(WARNING, 28) }
            Status.CONNECTED -> { btnMain.text = "Disconnect"; btnMain.isEnabled = true; btnMain.background = ripple(ERROR, 28) }
        }
    }

    private fun onMainButton() {
        when (VpnState.status) {
            Status.CONNECTED, Status.CONNECTING, Status.RECONNECTING -> onDisconnect()
            Status.DISCONNECTED, Status.ERROR -> onConnect()
        }
    }

    private fun buildConfigsTab(): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(12), dp(16), dp(12), dp(8)) }
        outer.addView(TextView(this).apply { text = "Saved configs"; textSize = 18f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(8)) })
        outer.addView(row(smallBtn("Import link") { showImportDialog() }, smallBtn("Add new") { showEditDialog(null) }))
        val scroll = ScrollView(this)
        configsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(configsList)
        outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun refreshConfigsList() {
        configsList.removeAllViews()
        val list = profiles.list()
        if (list.isEmpty()) {
            configsList.addView(TextView(this).apply {
                text = "No configs yet.\nImport vless:// vmess:// trojan:// ss:// ssh://"; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0)
            }); return
        }
        val activeId = profiles.getActiveId()
        list.forEach { p ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, 12); setPadding(dp(14), dp(12), dp(14), dp(12)) }
            card.addView(TextView(this).apply {
                text = p.displayName + if (p.id == activeId) "  ✓" else ""; textSize = 15f
                setTextColor(if (p.id == activeId) ACCENT else TEXT); typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(TextView(this).apply {
                val who = when { p.config.isSsh -> p.config.username; p.config.uuid.isNotBlank() -> p.config.uuid.take(8) + "…"; else -> p.config.method.ifBlank { "—" } }
                text = "${p.config.displayProtocol()} · ${p.config.host}:${p.config.port} · $who"
                textSize = 12f; setTextColor(MUTED); setPadding(0, dp(2), 0, dp(8))
            })
            card.addView(row(
                smallBtn("Select") { profiles.setActiveId(p.id); editingId = p.id; toast("Selected"); showTab(0) },
                smallBtn("Edit") { showEditDialog(p) },
                smallBtn("Delete") {
                    AlertDialog.Builder(this).setTitle("Delete?").setMessage(p.displayName)
                        .setPositiveButton("Delete") { _, _ -> profiles.delete(p.id); if (editingId == p.id) editingId = profiles.getActiveId(); refreshConfigsList(); renderHome() }
                        .setNegativeButton("Cancel", null).show()
                },
            ))
            configsList.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "vless://  vmess://  trojan://  ss://  ssh://"; setTextColor(TEXT); setHintTextColor(MUTED); setBackgroundColor(INPUT_BG)
            setPadding(dp(12), dp(12), dp(12), dp(12)); minLines = 4; gravity = Gravity.TOP or Gravity.START
        }
        AlertDialog.Builder(this).setTitle("Import config").setView(input)
            .setPositiveButton("Import") { _, _ ->
                val list = ConfigParser.parseMany(input.text.toString())
                if (list.isEmpty()) { toast("Could not parse"); LogStore.append("Import failed") }
                else {
                    var lastId: String? = null
                    list.forEach { cfg -> val id = ProfileStore.newId(); profiles.save(Profile(id, cfg)); lastId = id; LogStore.append("Imported ${cfg.displayProtocol()} ${cfg.host}") }
                    editingId = lastId; toast("Imported ${list.size}"); refreshConfigsList(); renderHome()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showEditDialog(existing: Profile?) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)) }
        fun field(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
            this.hint = hint; setText(value); setTextColor(TEXT); setHintTextColor(MUTED); setBackgroundColor(INPUT_BG)
            setPadding(dp(10), dp(10), dp(10), dp(10)); inputType = type
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
        val protocols = arrayOf("SSH-Direct", "SSH-UDPGW", "VLESS", "VMESS", "TROJAN", "SHADOWSOCKS")
        val c = existing?.config
        var selectedProto = c?.protocol?.uppercase()?.let { p ->
            when {
                p.contains("UDPGW") -> "SSH-UDPGW"
                p.startsWith("SSH") -> "SSH-Direct"
                p == "SS" -> "SHADOWSOCKS"
                else -> protocols.firstOrNull { it.equals(p, true) } ?: "SSH-Direct"
            }
        } ?: "SSH-Direct"
        val tvProto = TextView(this).apply {
            text = "Protocol: $selectedProto (tap to change)"
            textSize = 14f; setTextColor(ACCENT); setPadding(dp(4), dp(8), dp(4), dp(10))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Protocol")
                    .setItems(protocols) { _, which -> selectedProto = protocols[which]; text = "Protocol: $selectedProto (tap to change)" }.show()
            }
        }
        form.addView(tvProto)
        val etName = field("Name", c?.name ?: "")
        val etHost = field("Host", c?.host ?: "")
        val etPort = field("Port", (c?.port ?: 22).toString(), InputType.TYPE_CLASS_NUMBER)
        val etUser = field("Username (SSH)", c?.username ?: "")
        val etPass = field("Password / Trojan / SS", c?.password ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val etUuid = field("UUID (VLESS/VMess)", c?.uuid ?: "")
        val etSecurity = field("Security none/tls/reality", c?.security ?: "none")
        val etNetwork = field("Network tcp/ws/grpc", c?.network ?: "tcp")
        val etSni = field("SNI", c?.sni ?: "")
        val etPath = field("Path", c?.path ?: "")
        val etMethod = field("SS method", c?.method ?: "aes-256-gcm")
        val etUdpgw = field("UDPGW (SSH)", (c?.udpgwPort ?: 7300).toString(), InputType.TYPE_CLASS_NUMBER)
        listOf(etName, etHost, etPort, etUser, etPass, etUuid, etSecurity, etNetwork, etSni, etPath, etMethod, etUdpgw).forEach { form.addView(it) }
        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "New config" else "Edit config").setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val host = etHost.text.toString().trim()
                val user = etUser.text.toString().trim()
                val port = etPort.text.toString().toIntOrNull() ?: if (selectedProto.startsWith("SSH")) 22 else 443
                val pass = etPass.text.toString()
                val uuid = etUuid.text.toString().trim()
                if (host.isEmpty() || port !in 1..65535) { toast("Host and port required"); return@setPositiveButton }
                val isSsh = selectedProto.startsWith("SSH")
                if (isSsh && user.isEmpty()) { toast("Username required"); return@setPositiveButton }
                if (isSsh && pass.isEmpty() && existing?.config?.privateKey.isNullOrBlank()) { toast("Password required"); return@setPositiveButton }
                if (!isSsh && selectedProto !in listOf("TROJAN", "SHADOWSOCKS") && uuid.isEmpty()) { toast("UUID required"); return@setPositiveButton }
                if (selectedProto in listOf("TROJAN", "SHADOWSOCKS") && pass.isEmpty()) { toast("Password required"); return@setPositiveButton }
                val prev = existing?.config
                val cfg = SshConfig(
                    host = host, port = port, username = user, password = pass,
                    privateKey = prev?.privateKey ?: "", passphrase = prev?.passphrase ?: "",
                    name = etName.text.toString().trim(),
                    udpgwPort = etUdpgw.text.toString().toIntOrNull()?.takeIf { it in 1..65535 } ?: 7300,
                    transparentDns = prev?.transparentDns ?: true,
                    protocol = selectedProto, maxChannels = etMaxChannelsSafe(),
                    uuid = uuid, encryption = prev?.encryption ?: if (selectedProto == "VMESS") "auto" else "none",
                    security = etSecurity.text.toString().trim().ifBlank { "none" },
                    sni = etSni.text.toString().trim(), network = etNetwork.text.toString().trim().ifBlank { "tcp" },
                    path = etPath.text.toString().trim(), hostHeader = etSni.text.toString().trim(),
                    method = etMethod.text.toString().trim().ifBlank { "aes-256-gcm" },
                    fingerprint = prev?.fingerprint ?: "", publicKey = prev?.publicKey ?: "",
                    shortId = prev?.shortId ?: "", flow = prev?.flow ?: "", alterId = prev?.alterId ?: 0,
                )
                val id = existing?.id ?: ProfileStore.newId()
                profiles.save(Profile(id, cfg)); editingId = id; toast("Saved ${cfg.displayProtocol()}"); refreshConfigsList(); renderHome()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun buildSpeedTab(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(16)) }
        col.addView(TextView(this).apply { text = "Stats"; textSize = 20f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(2)) })
        col.addView(TextView(this).apply {
            text = "LIVE  ·  LAST 5 MINUTES"; textSize = 10f; setTextColor(MUTED)
            letterSpacing = 0.08f; setPadding(0, 0, 0, dp(14))
        })
        val upHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        upHeader.addView(TextView(this).apply { text = "Uplink"; textSize = 14f; setTextColor(MUTED); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        tvUplinkTotal = TextView(this).apply { text = "0 B"; textSize = 14f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD }
        upHeader.addView(tvUplinkTotal); col.addView(upHeader)
        chartUp = TrafficChartView(this, Color.parseColor("#F59E0B"))
        col.addView(chartUp, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)).apply { topMargin = dp(6); bottomMargin = dp(20) })
        val downHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        downHeader.addView(TextView(this).apply { text = "Downlink"; textSize = 14f; setTextColor(MUTED); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        tvDownlinkTotal = TextView(this).apply { text = "0 B"; textSize = 14f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD }
        downHeader.addView(tvDownlinkTotal); col.addView(downHeader)
        chartDown = TrafficChartView(this, Color.parseColor("#22C55E"))
        col.addView(chartDown, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)).apply { topMargin = dp(6); bottomMargin = dp(12) })
        scroll.addView(col)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)) }
    }

    private fun startSpeedMonitor() {
        if (speedRunning) return
        speedRunning = true
        lastUpBytes = TunnelStats.uplink.get()
        lastDownBytes = TunnelStats.downlink.get()
        if (upSamples.isEmpty()) repeat(300) { upSamples.addLast(0f); downSamples.addLast(0f) }
        mainHandler.post(speedRunnable)
    }
    private fun stopSpeedMonitor() { speedRunning = false; mainHandler.removeCallbacks(speedRunnable) }
    private fun startChartAnimation() { if (::chartUp.isInitialized) { chartUp.startAnimation(); chartDown.startAnimation() } }
    private fun stopChartAnimation() { if (::chartUp.isInitialized) { chartUp.stopAnimation(); chartDown.stopAnimation() } }
    private fun tickStats() {
        if (!speedRunning) return
        val up = TunnelStats.uplink.get(); val down = TunnelStats.downlink.get()
        val upRate = max(0f, (up - lastUpBytes).toFloat()); val downRate = max(0f, (down - lastDownBytes).toFloat())
        lastUpBytes = up; lastDownBytes = down
        if (upSamples.size >= 300) upSamples.removeFirst()
        upSamples.addLast(upRate)
        if (downSamples.size >= 300) downSamples.removeFirst()
        downSamples.addLast(downRate)
        if (currentTab == 2) {
            chartUp.setSamples(upSamples.toList())
            chartDown.setSamples(downSamples.toList())
        }
        tvUplinkTotal.text = formatBytes(up); tvDownlinkTotal.text = formatBytes(down)
        mainHandler.postDelayed(speedRunnable, 1000)
    }
    private fun formatBytes(v: Long): String {
        val d = v.toDouble()
        return when {
            d < 1024 -> "$v B"
            d < 1024 * 1024 -> String.format("%.1f KB", d / 1024)
            d < 1024.0 * 1024 * 1024 -> String.format("%.2f MB", d / (1024 * 1024))
            else -> String.format("%.2f GB", d / (1024 * 1024 * 1024))
        }
    }

    private fun buildLogsTab(): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(12), dp(16), dp(12), dp(8)) }
        outer.addView(TextView(this).apply { text = "Logs"; textSize = 18f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(8)) })
        outer.addView(row(
            smallBtn("Copy") { copyLogs() },
            smallBtn("Save TXT") { saveLogsToFile() },
        ))
        outer.addView(row(
            smallBtn("Send Log") { confirmShareLogs() },
            smallBtn("Clear") { LogStore.clear(); refreshLogs() },
        ))
        val scroll = ScrollView(this)
        tvLogs = TextView(this).apply { setTextColor(MUTED); textSize = 11f; setTextIsSelectable(true); setPadding(dp(8), dp(8), dp(8), dp(8)); typeface = Typeface.MONOSPACE }
        scroll.addView(tvLogs)
        outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }
    private fun refreshLogs() {
        val entries = LogStore.entries()
        if (entries.isEmpty()) {
            tvLogs.text = "(empty)"
            return
        }
        val styled = SpannableStringBuilder()
        entries.forEach { entry ->
            val line = LogStore.format(entry) + "\n"
            val start = styled.length
            styled.append(line)
            val color = when {
                entry.isContext -> MUTED
                entry.severity == LogSeverity.ERROR -> ERROR
                entry.severity == LogSeverity.WARNING -> WARNING
                else -> MUTED
            }
            styled.setSpan(ForegroundColorSpan(color), start, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvLogs.text = styled
    }
    private fun copyLogs() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("logs", LogStore.text()))
        toast("Logs copied")
    }

    private fun saveLogsToFile() {
        pendingLogExport = LogStore.text()
        if (pendingLogExport.isBlank()) {
            toast("No logs to save")
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "karoon-vpn-diagnostics.txt")
        }
        try {
            startActivityForResult(intent, REQ_LOG_EXPORT)
        } catch (e: Exception) {
            toast("Cannot open file picker: ${e.message}")
        }
    }

    private fun confirmShareLogs() {
        val logText = LogStore.text()
        if (logText.isBlank()) {
            toast("No logs to send")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Send diagnostic log?")
            .setMessage("The log may contain server addresses, IP addresses, timestamps, and network details. Review the file before sending it to anyone.")
            .setPositiveButton("Continue") { _, _ -> chooseLogDestination(logText) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseLogDestination(logText: String) {
        val destinations = arrayOf("Email", "Telegram", "WhatsApp")
        AlertDialog.Builder(this)
            .setTitle("Choose how to send the log")
            .setItems(destinations) { _, which ->
                val destination = when (which) {
                    0 -> "email"
                    1 -> "telegram"
                    else -> "whatsapp"
                }
                shareLogsFile(logText, destination)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareLogsFile(logText: String, destination: String) {
        try {
            val shareDir = File(cacheDir, "shared-logs")
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                throw IllegalStateException("Could not create temporary log folder")
            }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val logFile = File(shareDir, "karoon-vpn-diagnostics-$timestamp.txt")
            logFile.writeText(logText, Charsets.UTF_8)

            val contentUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile,
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Karoon Tunnel diagnostic log")
                putExtra(Intent.EXTRA_TEXT, "Karoon Tunnel diagnostic log is attached.")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(contentResolver, logFile.name, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            when (destination) {
                "email" -> {
                    sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                    startActivity(Intent.createChooser(sendIntent, "Send log by email"))
                }
                "telegram" -> {
                    sendIntent.setPackage("org.telegram.messenger")
                    startActivity(sendIntent)
                }
                "whatsapp" -> {
                    sendIntent.setPackage("com.whatsapp")
                    startActivity(sendIntent)
                }
            }
        } catch (e: android.content.ActivityNotFoundException) {
            val appName = if (destination == "telegram") "Telegram" else "WhatsApp"
            toast("$appName is not installed")
            LogStore.append("$appName sharing unavailable: app not installed")
        } catch (e: Exception) {
            toast("Could not share log: ${e.message}")
            LogStore.append("Log sharing failed: ${e.javaClass.simpleName}")
        }
    }
    private fun buildSettingsTab(): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(16)) }
        col.addView(TextView(this).apply { text = "More"; textSize = 20f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(12)) })
        col.addView(TextView(this).apply { text = "Max SOCKS channels (SSH)"; textSize = 13f; setTextColor(MUTED) })
        etMaxChannels = EditText(this).apply {
            setText("48"); inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(TEXT); setHintTextColor(MUTED); setBackgroundColor(INPUT_BG); setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        col.addView(etMaxChannels, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        col.addView(TextView(this).apply {
            text = "\nProtocols: SSH, VLESS, VMess, Trojan, Shadowsocks\nVersion 2.1.3\nSigning: keystore/SIGNING.md"
            textSize = 12f; setTextColor(MUTED); setPadding(0, dp(16), 0, 0)
        })
        scroll.addView(col)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)) }
    }

    private fun etMaxChannelsSafe(): Int =
        if (::etMaxChannels.isInitialized) etMaxChannels.text.toString().toIntOrNull()?.coerceIn(8, 128) ?: 48 else 48

    private fun onConnect() {
        val p = profiles.getActive() ?: run { toast("Select a config first"); showTab(1); return }
        editingId = p.id
        val prep = VpnService.prepare(this)
        if (prep != null) { startActivityForResult(prep, REQ_VPN); return }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF); return
            }
        }
        startVpn()
    }
    private fun startVpn() {
        val p = profiles.getActive() ?: return
        var cfg = p.config
        if (::etMaxChannels.isInitialized) {
            val maxCh = etMaxChannels.text.toString().toIntOrNull()?.coerceIn(8, 128) ?: 48
            if (maxCh != cfg.maxChannels) {
                cfg = cfg.copy(maxChannels = maxCh)
                profiles.save(Profile(p.id, cfg))
            }
        }
        LogStore.append("Starting ${cfg.displayProtocol()} to ${cfg.host}:${cfg.port}")
        val i = Intent(this, SshVpnService::class.java)
            .setAction(SshVpnService.ACTION_START)
            .putExtra(SshVpnService.EXTRA_CONFIG, cfg.toJson())
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
    private fun onDisconnect() {
        if (disconnectRequested) return
        disconnectRequested = true
        LogStore.append("Disconnect requested")
        renderHome()
        animateDisconnectPulse()
        startService(Intent(this, SshVpnService::class.java).setAction(SshVpnService.ACTION_STOP))
    }

    private fun animateDisconnectPulse() {
        if (!disconnectRequested || !::btnMain.isInitialized) return
        btnMain.animate().cancel()
        btnMain.animate().alpha(0.68f).scaleX(0.97f).scaleY(0.97f).setDuration(420L)
            .withEndAction {
                if (disconnectRequested) {
                    btnMain.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L)
                        .withEndAction { animateDisconnectPulse() }.start()
                }
            }.start()
    }

    private fun stopDisconnectPulse() {
        disconnectRequested = false
        if (!::btnMain.isInitialized) return
        btnMain.animate().cancel()
        btnMain.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120L).start()
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_VPN -> if (resultCode == RESULT_OK) onConnect()
            REQ_LOG_EXPORT -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(pendingLogExport.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("Could not open selected file")
                        toast("Diagnostic log saved")
                    } catch (e: Exception) {
                        toast("Save failed: ${e.message}")
                    }
                }
                pendingLogExport = ""
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) onConnect()
    }

    private fun row(vararg views: View): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        views.forEachIndexed { index, view ->
            r.addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                val halfGap = dp(4)
                leftMargin = if (index == 0) 0 else halfGap
                rightMargin = if (index == views.lastIndex) 0 else halfGap
                topMargin = dp(4); bottomMargin = dp(4)
            })
        }
        return r
    }
    private fun smallBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); isAllCaps = false
        minHeight = dp(44); minWidth = 0; elevation = dp(2).toFloat()
        background = ripple(ACCENT, 10)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { animatePress(this); onClick() }
    }
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = resources.displayMetrics.density * radiusDp
    }
    private fun ripple(color: Int, radiusDp: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.parseColor("#55FFFFFF")),
        rounded(color, radiusDp),
        rounded(Color.WHITE, radiusDp),
    )
    private fun animatePress(view: View) {
        view.animate().cancel()
        view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
        view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(85L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(190L).start()
            }.start()
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Smooth five-minute rolling traffic chart with a restrained comet-like live tip. */
class TrafficChartView(ctx: Context, private val lineColor: Int) : View(ctx) {
    private data class Point(val x: Float, val y: Float)

    private val samples = ArrayList<Float>(300)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 2.8f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        alpha = 75
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#243044")
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private var shimmer = 0f
    private var shimmerAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null

    fun setSamples(list: List<Float>) {
        val target = list.takeLast(300)
        if (samples.isEmpty() || samples.size != target.size) {
            samples.clear()
            samples.addAll(target)
            invalidate()
            return
        }
        transitionAnimator?.cancel()
        val start = samples.toList()
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 850L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                samples.indices.forEach { index ->
                    samples[index] = start[index] + (target[index] - start[index]) * fraction
                }
                invalidate()
            }
            start()
        }
    }

    fun startAnimation() {
        if (shimmerAnimator?.isRunning == true) return
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmer = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        transitionAnimator?.cancel()
        transitionAnimator = null
    }

    private fun smoothPath(points: List<Point>): android.graphics.Path {
        val path = android.graphics.Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        if (points.size == 1) return path
        for (i in 1 until points.lastIndex) {
            val current = points[i]
            val next = points[i + 1]
            path.quadTo(current.x, current.y, (current.x + next.x) * 0.5f, (current.y + next.y) * 0.5f)
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, gridPaint)
        if (samples.size < 2) return

        val maxV = samples.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val inset = 6f * resources.displayMetrics.density
        val plotWidth = (w - inset * 2f).coerceAtLeast(1f)
        val points = samples.mapIndexed { i, value ->
            val x = inset + i * plotWidth / 299f
            val y = h - (value / maxV) * (h * 0.88f) - h * 0.04f
            Point(x, y)
        }

        canvas.drawPath(smoothPath(points), linePaint)

        // Short, low-opacity trail: a subtle comet effect without visual clutter.
        val tail = points.subList((points.size - 9).coerceAtLeast(0), points.size)
        if (tail.size > 1) canvas.drawPath(smoothPath(tail), tailPaint)

        val head = points.last()
        val pulse = 0.75f + shimmer * 0.25f
        glowPaint.alpha = (34 + shimmer * 18).toInt()
        canvas.drawCircle(head.x, head.y, 7f * resources.displayMetrics.density * pulse, glowPaint)
        glowPaint.alpha = 235
        canvas.drawCircle(head.x, head.y, 2.2f * resources.displayMetrics.density, glowPaint)
    }
}
