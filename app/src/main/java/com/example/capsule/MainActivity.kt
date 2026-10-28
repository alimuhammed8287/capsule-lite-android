package com.example.capsule

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.ComponentName
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var diagText: TextView
    private lateinit var repairText: TextView
    private lateinit var saveMsg: TextView
    private lateinit var col: LinearLayout
    private var queueStateView: TextView? = null
    private var expansionPreviewSurface: View? = null
    private var expansionPreviewLabel: TextView? = null
    private var cat: String? = null
    private val prefs by lazy { getSharedPreferences("capsule", MODE_PRIVATE) }
    private val queueStateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "queueStateSummary" -> refreshQueueState()
            "expandFromStatusBar" -> refreshExpansionAnchorPreview()
        }
    }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun appTheme() = (prefs.getString("appTheme", "dark") ?: "dark").takeIf { it in listOf("system", "light", "dark", "amoled") } ?: "dark"
    private fun isLightAppTheme() = when (appTheme()) {
        "light" -> true
        "system" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_NO
        else -> false
    }
    private fun screenColors(): IntArray = when (appTheme()) {
        "light" -> intArrayOf(0xFFF4F6F9.toInt(), 0xFFEAF0F5.toInt(), 0xFFF8FAFC.toInt())
        "amoled" -> intArrayOf(Color.BLACK, Color.BLACK, 0xFF050505.toInt())
        "system" -> if (isLightAppTheme()) intArrayOf(0xFFF4F6F9.toInt(), 0xFFEAF0F5.toInt(), 0xFFF8FAFC.toInt()) else intArrayOf(0xFF0A111B.toInt(), 0xFF101824.toInt(), 0xFF090B10.toInt())
        else -> intArrayOf(0xFF0A111B.toInt(), 0xFF101824.toInt(), 0xFF090B10.toInt())
    }
    private fun surfaceColors(): IntArray = when (appTheme()) {
        "light" -> intArrayOf(Color.WHITE, 0xFFF7F9FC.toInt())
        "amoled" -> intArrayOf(Color.BLACK, 0xFF050505.toInt())
        "system" -> if (isLightAppTheme()) intArrayOf(Color.WHITE, 0xFFF7F9FC.toInt()) else intArrayOf(0xFF192431.toInt(), 0xFF121A24.toInt())
        else -> intArrayOf(0xFF192431.toInt(), 0xFF121A24.toInt())
    }
    private fun primaryTextColor() = if (isLightAppTheme()) 0xFF17202B.toInt() else 0xFFE7EDF5.toInt()
    private fun secondaryTextColor() = if (isLightAppTheme()) 0xFF5D6B7B.toInt() else 0xFF9AAABD.toInt()


    // Rounded, translucent buttons used across the settings screen
    private fun styledButton() = Button(this).apply {
        setTextColor(primaryTextColor())
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        minHeight = dp(48)
        stateListAnimator = null
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (isLightAppTheme()) intArrayOf(0xFFE9EEF4.toInt(), 0xFFDDE5EE.toInt())
            else if (appTheme() == "amoled") intArrayOf(0xFF080808.toInt(), Color.BLACK)
            else intArrayOf(0xFF203244.toInt(), 0xFF121C28.toInt())
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (isLightAppTheme()) 0xFFCBD5E1.toInt() else if (appTheme() == "amoled") 0xFF303030.toInt() else 0x334DDDB0)
        }
        setPadding(dp(15), dp(11), dp(15), dp(11))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7) }
    }

    private val titles = mapOf(
        "setup" to "Setup and recovery",
        "diagnostics" to "Diagnostic log",
        "pill" to "Pill",
        "music" to "Music",
        "notifications" to "Notifications",
        "quick" to "Quick controls",
        "charging" to "Charging",
        "look" to "Look",
        "privacy" to "Privacy",
        "tools" to "Tools"
    )


    private val sectionDescriptions = mapOf(
        "setup" to "Check permissions, service health, and recovery options.",
        "diagnostics" to "Review, copy, or share a diagnostic report.",
        "pill" to "Tune size, placement, gestures, and when the pill appears.",
        "music" to "Choose playback controls and visualizer behaviour.",
        "notifications" to "Control notification visibility, live activities, and replies.",
        "quick" to "Personalise quick controls, tiles, and pinned apps.",
        "charging" to "Customise charging status, animation, and session summaries.",
        "look" to "Choose colours, typography, glass styling, and motion.",
        "privacy" to "Manage hidden apps and on-device notification history.",
        "tools" to "Back up settings, restore a configuration, or reset preferences."
    )

    private fun addSettingsIntro() {
        val isHome = cat == null
        val accentTint = if (isLightAppTheme()) 0xFF087F6A.toInt() else 0xFF6CE5B2.toInt()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(19), dp(19), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isLightAppTheme()) intArrayOf(0xFFFFFFFF.toInt(), 0xFFE8F7F1.toInt())
                else if (appTheme() == "amoled") intArrayOf(0xFF080B0A.toInt(), Color.BLACK)
                else intArrayOf(0xFF1C3440.toInt(), 0xFF14231F.toInt())
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFBBDDCF.toInt() else 0x446CE5B2)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        hero.addView(TextView(this).apply {
            text = if (isHome) "CAPSULE LITE  /  PERSONALISE" else "SETTINGS  /  " + (titles[cat] ?: "PREFERENCES").uppercase()
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(accentTint)
        })
        hero.addView(TextView(this).apply {
            text = if (isHome) "Make it yours." else (titles[cat] ?: "Settings")
            textSize = if (isHome) 25f else 22f
            letterSpacing = -0.025f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(primaryTextColor())
            setPadding(0, dp(5), 0, dp(4))
        })
        hero.addView(TextView(this).apply {
            text = if (isHome) "Fine-tune your floating companion, from gestures and charging to privacy and motion."
                else sectionDescriptions[cat] ?: "Adjust the options below to suit your setup."
            textSize = 13f
            setTextColor(secondaryTextColor())
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        if (isHome) {
            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(13), 0, 0)
            }
            listOf("9 SETTINGS AREAS", "BACKUP & RESTORE").forEach { label ->
                chips.addView(TextView(this).apply {
                    text = label
                    textSize = 9f
                    letterSpacing = 0.04f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(if (isLightAppTheme()) 0xFF176A59.toInt() else 0xFFB7F3D9.toInt())
                    background = GradientDrawable().apply {
                        setColor(if (isLightAppTheme()) 0xFFD9F1E7.toInt() else 0x223FE0B0)
                        cornerRadius = dp(20).toFloat()
                        setStroke(dp(1), if (isLightAppTheme()) 0xFFB9DFD1.toInt() else 0x336CE5B2)
                    }
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(7) }
                })
            }
            hero.addView(chips)
        }
        col.addView(hero)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagLog.installCrashHandler(this)
        // Android 13 and newer ask before showing any notification, including the small "running" one
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        cat = intent.getStringExtra("cat")
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(if (isLightAppTheme()) 0xFFFDFEFF.toInt() else if (appTheme() == "amoled") Color.BLACK else 0xFF172332.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD7E0E9.toInt() else if (appTheme() == "amoled") 0xFF303030.toInt() else 0x334EDDB0)
            }
            addView(TextView(this@MainActivity).apply {
                text = "C"
                textSize = 21f
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF08130F.toInt())
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
                background = GradientDrawable().apply {
                    setColor(0xFF6CE5B2.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
                addView(TextView(this@MainActivity).apply {
                    text = if (cat == null) "Capsule Lite" else titles[cat] ?: "Settings"
                    textSize = 26f
                    setTextColor(primaryTextColor())
                    setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
                    letterSpacing = -0.035f
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (cat == null) "FLOATING COMPANION  ·  V1.4.2" else "SETTINGS  ·  PERSONALISE YOUR EXPERIENCE"
                    textSize = 10f
                    letterSpacing = 0.08f
                    setTextColor(secondaryTextColor())
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }.apply { 
            setPadding(dp(12), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        })
        addSettingsIntro()
        if (cat != null) button(col, "‹  All settings") { finish() }
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(primaryTextColor())
            setPadding(dp(8), dp(8), dp(8), dp(12))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        when (cat) {
            null -> buildHome()
            "setup" -> buildSetup()
            "diagnostics" -> buildDiagnostics()
            "pill" -> buildPill()
            "music" -> buildMusic()
            "notifications" -> buildNotifications()
            "quick" -> buildQuick()
            "charging" -> buildCharging()
            "look" -> buildLook()
            "privacy" -> buildPrivacy()
            "tools" -> buildTools()
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, screenColors())
            addView(col)
        })
    }

    private fun open(name: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("cat", name))
    }

    private fun showDiagnostics() {
        if (::diagText.isInitialized) {
            val log = DiagLog.lastLines(this, 300)
            diagText.text = log.ifEmpty { "No service events recorded yet." }
        }
        if (::repairText.isInitialized) {
            val listenerEnabled = KeepAlive.isListenerEnabled(this)
            val listenerConnected = KeepAlive.listenerConnected
            val accessibilityEnabled = KeepAlive.isAccessibilityEnabled(this)
            val accessibilityConnected = KeepAlive.accessibilityConnected
            repairText.text = buildString {
                append("Notification listener: ")
                    .append(if (listenerEnabled && listenerConnected) "Connected" else if (listenerEnabled) "Enabled, not connected" else "Permission needed")
                    .append('\n')
                append("Accessibility keep-alive: ")
                    .append(if (accessibilityEnabled && accessibilityConnected) "Connected" else if (accessibilityEnabled) "Enabled, not connected" else "Permission needed")
                    .append('\n')
                append("Overlay: ").append(if (Settings.canDrawOverlays(this@MainActivity)) "Allowed" else "Permission needed")
                    .append('\n')
                append("Automatic repair: ").append(when {
                    !prefs.getBoolean("autoRepairServices", false) -> "off"
                    !KeepAlive.canSelfRepair(this@MainActivity) -> "on, but optional ADB permission is missing"
                    else -> "on"
                }).append('\n')
                append("Next step: ").append(when {
                    !Settings.canDrawOverlays(this@MainActivity) -> "allow Display over other apps"
                    !listenerEnabled -> "enable Notification access"
                    !listenerConnected -> "tap Try reconnect, then reopen Capsule if it stays disconnected"
                    !accessibilityEnabled -> "enable the optional keep-alive in Accessibility settings"
                    else -> "refresh diagnostics if the pill is still missing"
                })
            }
        }
    }

    private fun reconnectServices() {
        when {
            !Settings.canDrawOverlays(this) -> {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            !KeepAlive.isListenerEnabled(this) -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            else -> {
                runCatching { NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java)) }
                KeepAlive.schedule(this, 1_000L)
                android.widget.Toast.makeText(this, "Reconnect requested. Refresh in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                showDiagnostics()
            }
        }
    }

    private fun buildDiagnostics() {
        val health = card("Service health")
        health.addView(status)
        repairText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(6), dp(2), dp(6), dp(8))
        }
        health.addView(repairText)
        button(health, "Try reconnect") { reconnectServices() }
        button(health, "Refresh status and log") { showDiagnostics() }

        val logCard = card("Recent diagnostic events")
        diagText = TextView(this).apply {
            textSize = 11f
            setTextColor(secondaryTextColor())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(6), dp(4), dp(6), dp(8))
        }
        logCard.addView(diagText)
        button(logCard, "Copy full diagnostics") { copyDiagnostics() }
        button(logCard, "Share diagnostics as .txt") { shareDiagnosticsFile() }
        button(logCard, "Clear diagnostics") {
            AlertDialog.Builder(this)
                .setTitle("Clear diagnostic log?")
                .setMessage("This clears saved event history. It does not change permissions or service settings.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> DiagLog.clear(this); showDiagnostics() }
                .show()
        }
        TextView(this).apply {
            text = "The report contains device details, selected settings, and recent service events. Review it before sharing."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(6), dp(2), dp(6), dp(8))
        }.also { logCard.addView(it) }
    }

    private fun shareDiagnosticsFile() {
        AlertDialog.Builder(this)
            .setTitle("Share diagnostics?")
            .setMessage("A .txt file will include device details, saved settings, and recent service events. Only continue if you are comfortable sharing those details.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                runCatching {
                    val directory = File(cacheDir, "shared")
                    directory.mkdirs()
                    directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
                    val file = File(directory, "capsule-lite-diagnostics-${System.currentTimeMillis()}.txt")
                    file.writeText(DiagLog.report(this), Charsets.UTF_8)
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Capsule Lite diagnostics")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(contentResolver, "Capsule Lite diagnostics", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "Share diagnostic file"))
                }.onFailure {
                    android.widget.Toast.makeText(this, "Could not share diagnostics: ${it.message ?: "file error"}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun copyDiagnostics() {
        val text = DiagLog.report(this)
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Capsule diagnostics", text))
        android.widget.Toast.makeText(this, "Copied ${text.length} characters", android.widget.Toast.LENGTH_LONG).show()
    }

    // Optional elevated permission for the user's explicitly enabled automatic service-repair setting.
    // It is run once from a computer with the phone connected by USB; the in-app opt-in remains required.
    private fun copyRepairCommand() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("adb command", KeepAlive.adbCommand(this)))
        android.widget.Toast.makeText(this, "Copied: " + KeepAlive.adbCommand(this), android.widget.Toast.LENGTH_LONG).show()
    }

    // The main screen: a short list of categories
    private fun buildHome() {
        homeTile("⚙", "Setup and recovery", "Permissions, service status and troubleshooting", "setup")
        homeTile("◉", "Pill appearance", "Size, position, gestures and behaviour", "pill")
        homeTile("♫", "Media player", "Playback controls, visualizer and layout", "music")
        homeTile("▤", "Notifications", "Events, auto-collapse and quick replies", "notifications")
        homeTile("⊞", "Quick controls", "Control panel, tiles and pinned apps", "quick")
        homeTile("ϟ", "Charging", "Battery animation, alerts and layout", "charging")
        homeTile("✧", "Appearance and motion", "Themes, typography, glass and animation", "look")
        homeTile("◈", "Privacy", "History and hidden apps", "privacy")
        homeTile("⌘", "Tools and backup", "Code generator, import, export and tests", "tools")
    }

    private fun homeTile(symbol: String, title: String, subtitle: String, destination: String) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                surfaceColors()
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD7E0E9.toInt() else 0xFF2A3948.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) }
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setOnClickListener { open(destination) }
        }
        tile.addView(TextView(this).apply {
            text = symbol
            textSize = 23f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF76E5C0.toInt())
            background = GradientDrawable().apply {
                setColor(0x223FE0B0)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        tile.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(13)
                marginEnd = dp(6)
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 15.5f
                setTextColor(primaryTextColor())
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(secondaryTextColor())
                setPadding(0, dp(3), 0, 0)
            })
        })
        tile.addView(TextView(this).apply {
            text = "›"
            textSize = 26f
            setTextColor(if (isLightAppTheme()) 0xFF64748B.toInt() else 0xFF8CA2B8.toInt())
            gravity = android.view.Gravity.CENTER
        })
        col.addView(tile)
    }

    private fun buildSetup() {
        val health = card("Service health and recovery")
        health.addView(status)
        repairText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(6), dp(2), dp(6), dp(8))
        }
        health.addView(repairText)
        button(health, "Try reconnect") { reconnectServices() }
        button(health, "Open diagnostic details") { open("diagnostics") }
        toggle(health, "Allow automatic service repair if Android disables it (optional)", "autoRepairServices", false)
        TextView(this).apply {
            text = "Repair is opt-in. Android still requires you to grant or restore permissions in Settings."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }.also { health.addView(it) }
        button(health, "Copy optional ADB permission command") { copyRepairCommand() }
        toggle(health, "Keep-running notification (helps Android keep Capsule alive)", "keepNotif", true)

        val perms = card("Permissions")
        button(perms, "1. Display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button(perms, "2. Notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        button(perms, "3. Keep-alive (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(perms, "4. App info (battery: No restrictions, Autostart)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button(perms, "5. Brightness control (for brightness gestures)") {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        button(perms, "6. Do Not Disturb control (for quick settings)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        button(perms, "7. App usage access (for hiding in video and game apps)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        button(perms, "8. Microphone (for the music visualizer)") {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 42)
        }
        button(perms, "9. Location (shows the Wi-Fi network name)") {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 43)
        }
    }

    private fun buildPill() {
        val pillCard = card("Show in the pill")
        toggle(pillCard, "Haptic feedback", "haptics", true)
        toggle(pillCard, "Hide in video and game apps (needs app usage access)", "hideFullscreen", false)
        toggle(pillCard, "Night: hide the pill during these hours", "nightHide", false)
        slider(pillCard, "Night from (hour)", "nightFrom", 0, 23, 22)
        slider(pillCard, "Night to (hour)", "nightTo", 0, 23, 7)

        val gestures = card("Gestures (tap a row to change it)")
        for (key in CapsulePrefs.gestureKeys) gestureButton(gestures, key)

        val size = card("Size and position")
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val where = if (land) " (landscape)" else ""
        slider(size, "Collapsed width", "miniW", 40, 200, 100)
        slider(size, "Collapsed height", "miniH", 20, 60, 40)
        slider(size, "Expanded width", "expW", 150, 360, 340)
        slider(size, "Vertical position$where", if (land) "topYL" else "topY", 0, 40, 6)
        slider(size, "Horizontal shift$where", if (land) "offXL" else "offX", -100, 100, 0)
        toggle(size, "Keep expanded pill aligned with the status-bar capsule", "expandFromStatusBar", true)
        size.addView(expansionAnchorPreview())
        expansionPreviewLabel = TextView(this).apply {
            textSize = 11f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(6))
        }.also { size.addView(it) }
        refreshExpansionAnchorPreview()
    }

    private fun expansionAnchorPreview(): View {
        val style = PillStyles.palette(prefs.getString("theme", "capsule"))
        val preview = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(156))
                .apply { topMargin = dp(4); bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF111116.toInt())
                setStroke(dp(1), 0x33444455)
            }
            clipToPadding = true
            clipChildren = true
        }
        val status = TextView(this).apply {
            text = "9:41     ◇   5G   ▰"
            textSize = 10f
            setTextColor(0xFFB8B8C2.toInt())
            setPadding(dp(10), dp(5), dp(10), 0)
        }
        preview.addView(status, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(22), android.view.Gravity.TOP
        ))
        val expandedSurface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.TOP
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(style.cornerDp.coerceAtLeast(18)).toFloat()
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(style.top, style.bottom)
                setStroke(dp(1), style.outline)
            }
        }
        expandedSurface.addView(TextView(this).apply {
            text = "◉     Now playing · Notifications"
            textSize = 11f
            setTextColor(style.primary)
        })
        expandedSurface.addView(TextView(this).apply {
            text = "Expanded content grows downward from this line"
            textSize = 9f
            setTextColor(style.secondary)
            setPadding(0, dp(3), 0, 0)
        })
        preview.addView(expandedSurface, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(68), android.view.Gravity.TOP
        ).apply { leftMargin = dp(8); rightMargin = dp(8) })
        expansionPreviewSurface = expandedSurface
        return preview
    }

    private fun refreshExpansionAnchorPreview() {
        val fixedTop = prefs.getBoolean("expandFromStatusBar", true)
        expansionPreviewSurface?.let { surface ->
            (surface.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = dp(if (fixedTop) 22 else 44)
                surface.layoutParams = params
            }
        }
        expansionPreviewLabel?.text = if (fixedTop) {
            "Preview: the capsule keeps its top edge and opens downward."
        } else {
            "Preview: expanded content stays vertically centered."
        }
    }

    private fun buildMusic() {
        val music = card("Music player")
        toggle(music, "Show the music player", "showMusic", true)
        toggle(music, "Music visualizer (uses the microphone, reacts to sound it hears)", "visualizer", false)
        toggle(music, "Bars react to notifications and calls", "vizNotif", true)
        vizStyleButton(music)
        slider(music, "Height: music player", "expH_music", 40, 160, 120)
    }

    private fun buildNotifications() {
        val auto = card("Auto expand")
        toggle(auto, "Expand the pill when a notification arrives", "notifAutoExpand", true)
        toggle(auto, "Collapse it automatically after a time", "notifAutoCollapse", true)
        slider(auto, "Seconds before it collapses", "notifSeconds", 2, 15, 4)

        val queueLook = card("Notification queue appearance")
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                val pill = PillStyles.palette(prefs.getString("theme", "capsule"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(pill.top, pill.bottom)
                setStroke(dp(1), pill.outline)
            }
        }
        val previewHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        previewHeader.addView(TextView(this).apply {
            text = "2 waiting · notification preview"
            textSize = 12f
            setTextColor(primaryTextColor())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val previewBadge = TextView(this).apply {
            text = "2"
            gravity = android.view.Gravity.CENTER
            minWidth = dp(24)
            minHeight = dp(24)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        }
        previewHeader.addView(previewBadge)
        preview.addView(previewHeader)
        val previewCard = TextView(this).apply {
            text = "Delivery update\nYour order is nearby · Dismiss"
            textSize = 12f
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        }
        preview.addView(previewCard)
        queueLook.addView(preview)
        notificationQueueThemeButton(queueLook, previewCard, previewBadge)
        queueLook.addView(TextView(this).apply {
            text = "Style the notification count and expanded queue card independently. Transparent blend removes the card fill so it reads as part of the pill."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(2))
        })
        queueStateView = TextView(this).apply {
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(8), dp(2), dp(2))
        }.also { queueLook.addView(it) }
        button(queueLook, "Refresh queue status") { refreshQueueState() }
        updateNotificationQueuePreview(
            prefs.getString("notificationQueueTheme", "blend") ?: "blend",
            previewCard,
            previewBadge
        )

        val shown = card("Which notifications show")
        toggle(shown, "Other notifications", "showNotifs", true)
        toggle(shown, "Google Maps navigation", "showMaps", true)
        toggle(shown, "Navigation info in the pill (reads Maps' navigation notification, and the Maps screen while it is open)", "mapsInfo", true)
        toggle(shown, "Use Maps' own turn arrow picture (turn off to use the arrows Capsule draws)", "mapsOwnArrow", true)
        toggle(shown, "Phone events (unlock, headphones, Bluetooth)", "showEvents", true)
        toggle(shown, "Downloads and progress activities", "showDownloads", true)
        toggle(shown, "Live timer and stopwatch notifications", "liveActivities", true)
        activityPriorityButton(shown)
        val liveApps = EditText(this).apply {
            hint = "Optional live-activity allow-list (package names, comma separated; blank = all apps)"
            contentDescription = "Live-activity app allow-list, comma-separated package names"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(prefs.getString("liveActivityApps", ""))
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
            textSize = 13f
        }
        shown.addView(liveApps)
        button(shown, "Save live-activity app list") {
            prefs.edit().putString("liveActivityApps", liveApps.text.toString().trim()).apply()
            android.widget.Toast.makeText(this, "Live-activity app list saved", android.widget.Toast.LENGTH_SHORT).show()
        }
        toggle(shown, "Driving mode (hide other notifications during Maps navigation)", "drivingMode", false)
        toggle(shown, "Colour the title from the sending app", "appAccent", true)
        toggle(shown, "Quick reply buttons (Yes, Later, Busy)", "replyChips", true)
        toggle(shown, "Debug: show raw Maps text", "debugMaps", false)

        val heights = card("Heights")
        slider(heights, "Height: notifications and calls", "expH_notif", 40, 120, 64)
        slider(heights, "Height: notification with buttons", "expH_stack", 40, 160, 104)
    }

    private fun notificationQueueThemeButton(parent: LinearLayout, preview: TextView, badge: TextView) {
        val modes = listOf("blend", "oneui", "iphone", "pixel", "nothing", "capsule")
        val names = mapOf(
            "blend" to "Transparent blend with pill",
            "oneui" to "One UI inspired",
            "iphone" to "iPhone inspired",
            "pixel" to "Pixel inspired",
            "nothing" to "Nothing inspired",
            "capsule" to "Capsule original"
        )
        fun selected() = (prefs.getString("notificationQueueTheme", "blend") ?: "blend")
            .takeIf { it in modes } ?: "blend"
        fun label(mode: String) = "Queue theme: ${names[mode] ?: names["blend"]}"
        val button = styledButton().apply {
            text = label(selected())
            contentDescription = "Choose notification queue appearance"
        }
        button.setOnClickListener {
            val current = selected()
            AlertDialog.Builder(this)
                .setTitle("Notification queue appearance")
                .setSingleChoiceItems(modes.map { names[it] ?: it }.toTypedArray(), modes.indexOf(current)) { dialog, which ->
                    val mode = modes[which]
                    prefs.edit().putString("notificationQueueTheme", mode).apply()
                    button.text = label(mode)
                    updateNotificationQueuePreview(mode, preview, badge)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
    }

    private fun updateNotificationQueuePreview(mode: String, preview: TextView, badge: TextView) {
        val blended = mode == "blend"
        val style = if (blended) PillStyles.palette(prefs.getString("theme", "capsule")) else PillStyles.palette(mode)
        preview.setTextColor(style.primary)
        preview.background = GradientDrawable().apply {
            cornerRadius = dp(if (style.id == "nothing") 10 else style.cornerDp).toFloat()
            if (blended) {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), (0x66 shl 24) or (style.accent and 0x00FFFFFF))
            } else {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(style.top, style.bottom)
                setStroke(dp(1), style.outline)
            }
        }
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(style.accent)
        }
    }

    private fun refreshQueueState() {
        queueStateView?.text = "Live queue state\n" +
            (prefs.getString("queueStateSummary", null) ?: "Waiting for Capsule to report the current queue state.")
    }

    private fun activityPriorityButton(parent: LinearLayout) {
        val ids = listOf("live", "notifications", "media", "navigation", "charging")
        val names = mapOf(
            "live" to "Live timers and downloads",
            "notifications" to "Other notifications",
            "media" to "Media",
            "navigation" to "Navigation",
            "charging" to "Charging"
        )
        fun order(): MutableList<String> {
            val saved = (prefs.getString("activityPriorityOrder", "") ?: "")
                .split(",").map { it.trim() }.filter { it in ids }.distinct()
            return (saved + ids.filter { it !in saved }).toMutableList()
        }
        fun label(values: List<String>) = "Activity priority: " +
            (listOf("Calls (fixed)") + values.mapNotNull { names[it] }).joinToString("  ›  ")
        val button = styledButton().apply {
            text = label(order())
            contentDescription = "Arrange activity priority"
        }
        button.setOnClickListener {
            val values = order()
            val rows = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(6), dp(18), dp(4))
            }
            lateinit var dialog: AlertDialog
            fun redraw() {
                rows.removeAllViews()
                values.forEachIndexed { index, id ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, dp(3), 0, dp(3))
                    }
                    row.addView(TextView(this).apply {
                        text = "${index + 2}. ${names[id]}"
                        textSize = 14f
                        setTextColor(primaryTextColor())
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }, LinearLayout.LayoutParams(0, dp(48), 1f))
                    val up = styledButton().apply {
                        text = "↑"
                        contentDescription = "Move ${names[id]} up"
                        isEnabled = index > 0
                        setOnClickListener {
                            if (index > 0) {
                                val previous = values[index - 1]
                                values[index - 1] = values[index]
                                values[index] = previous
                                redraw()
                            }
                        }
                    }
                    val down = styledButton().apply {
                        text = "↓"
                        contentDescription = "Move ${names[id]} down"
                        isEnabled = index < values.lastIndex
                        setOnClickListener {
                            if (index < values.lastIndex) {
                                val next = values[index + 1]
                                values[index + 1] = values[index]
                                values[index] = next
                                redraw()
                            }
                        }
                    }
                    row.addView(up, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
                    row.addView(down, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
                    rows.addView(row)
                }
            }
            dialog = AlertDialog.Builder(this)
                .setTitle("Activity priority")
                .setMessage("Calls and controls you open directly stay on top. Move other activities to choose which one wins when several are active.")
                .setView(rows)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit().putString("activityPriorityOrder", values.joinToString(",")).apply()
                    button.text = label(values)
                }
                .create()
            redraw()
            dialog.show()
        }
        parent.addView(button)
        parent.addView(TextView(this).apply {
            text = "When activities overlap, Capsule shows the highest item in this order. Calls and manually opened controls remain fixed above it."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(8))
        })
    }

    private fun buildQuick() {
        val panel = card("Control panel")
        slider(panel, "Columns (4 to 7)", "panelCols", 4, 7, 4)
        toggle(panel, "Pinned apps in the control panel", "pinnedOn", true)

        val tiles = card("Tiles in the control panel (switch off to remove a tile)")
        for (id in CapsulePrefs.allTileIds()) {
            val label = CapsulePrefs.qsItems.firstOrNull { it.first == id }?.second ?: id
            toggle(tiles, label, "tile_$id", true)
        }

        val custom = card("Pinned apps and quick replies")
        val replyField = EditText(this).apply {
            hint = "Quick replies, comma separated (up to 3)"
            contentDescription = "Quick reply messages, comma-separated, up to three"
            setText(prefs.getString("replies", "Yes,Later,Busy"))
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
        }
        custom.addView(replyField)
        val pinField = EditText(this).apply {
            hint = "Pinned apps, package names, comma separated (up to 4)"
            contentDescription = "Pinned app package names, comma-separated, up to four"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(prefs.getString("pinned", ""))
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
        }
        custom.addView(pinField)
        button(custom, "Save") {
            prefs.edit()
                .putString("replies", replyField.text.toString())
                .putString("pinned", pinField.text.toString())
                .apply()
            android.widget.Toast.makeText(this, "Quick replies and pinned apps saved", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildCharging() {
        val charge = card("Charging")
        toggle(charge, "Show charging status in the main pill", "showCharging", true)
        toggle(charge, "Charging alerts (when the charge reaches a level, or is full)", "chargeAlert", true)
        slider(charge, "Alert at charge (%)", "chargeAlertAt", 50, 100, 80)
        slider(charge, "Height: charging", "expH_charge", 40, 120, 64)
        slider(charge, "Charging ring size (dp)", "chargeRingSize", 36, 64, 48)
        slider(charge, "Collapsed charging percentage size (sp)", "chargePctTextSize", 10, 24, 13)
        slider(charge, "Irregular ring wave density", "chargeRingDensity", 3, 16, 7)
        slider(charge, "Irregular ring animation speed", "chargeRingSpeed", 1, 10, 5)
        charge.addView(TextView(this).apply {
            text = "Tune how many waves form around the irregular ring and how quickly they move. Lower density makes broader waves; higher density makes finer ripples. The remaining-charge track stays visible, and the centered bolt follows the selected animation."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(1), dp(2), dp(5))
        })
        chargingStyleButton(charge)
        chargingIconButton(charge)
        TextView(this).apply {
            text = "Choose a charging icon for both pill sizes. The irregular progress ring keeps the bolt still, animates its outline, and leaves a gap for the remaining charge. Charging stat icons follow the selected pill theme."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(6))
        }.also { charge.addView(it) }

        val sessions = card("Charging sessions")
        val historyText = TextView(this).apply {
            text = ChargingSessionHistory.summary(this@MainActivity)
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        sessions.addView(historyText)
        button(sessions, "Refresh session summary") {
            historyText.text = ChargingSessionHistory.summary(this)
        }
        button(sessions, "Clear completed sessions") {
            AlertDialog.Builder(this)
                .setTitle("Clear charging history?")
                .setMessage("This removes completed session summaries stored on this device. An active session will continue to be recorded.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    ChargingSessionHistory.clear(this)
                    historyText.text = ChargingSessionHistory.summary(this)
                }
                .show()
        }
        TextView(this).apply {
            text = "Summaries stay on this device and keep only the latest 20 sessions. Power and temperature are device readings, not battery-health measurements."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }.also { sessions.addView(it) }

    }

    private fun chargingStyleButton(parent: LinearLayout) {
        val styles = listOf("Liquid battery", "Segmented battery", "Pulse battery")
        fun current() = prefs.getInt("chargeIconStyle", 0).coerceIn(0, styles.lastIndex)
        val b = styledButton().apply {
            text = "Charging animation: " + styles[current()]
            contentDescription = "Choose charging animation"
        }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging animation")
                .setSingleChoiceItems(styles.toTypedArray(), current()) { dialog, which ->
                    prefs.edit().putInt("chargeIconStyle", which).apply()
                    b.text = "Charging animation: " + styles[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun chargingIconButton(parent: LinearLayout) {
        val icons = listOf("One UI battery", "Lightning bolt", "Power plug", "Leaf", "Spark", "Pixel battery", "Nothing glyph dots", "Pixel charge ring", "Irregular progress ring", "Crafted live charge ring")
        fun selected() = prefs.getInt("chargeIconShape", 9).coerceIn(0, icons.lastIndex)
        val b = styledButton().apply {
            text = "Charging icon: " + icons[selected()]
            contentDescription = "Choose charging icon design"
        }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging icon")
                .setSingleChoiceItems(icons.toTypedArray(), selected()) { dialog, which ->
                    prefs.edit().putInt("chargeIconShape", which).apply()
                    b.text = "Charging icon: " + icons[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun buildLook() {
        val appTheme = card("App theme")
        appThemeButton(appTheme)
        appTheme.addView(TextView(this).apply {
            text = "Light, Dark, AMOLED black, or follow your phone system theme."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(5), dp(2), dp(2))
        })
        val look = card("Pill appearance")
        themePreview(look)
        fontButton(look)
        themeButton(look)
        pillAnimationStyleButton(look)
        look.addView(TextView(this).apply {
            text = "Style-inspired presets change the pill's surface, accent, shape, and playback controls."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(5), dp(2), dp(2))
        })
        val lock = card("Lock screen")
        toggle(lock, "Show the pill on the lock screen", "showOnLockScreen", true)
        toggle(lock, "Hide notification and call details while locked", "lockScreenPrivacy", true)
        lock.addView(TextView(this).apply {
            text = "Android and HyperOS may restrict overlays on a secure lock screen. Capsule keeps notification text hidden until unlock; the system may still choose not to display the overlay."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(2))
        })
        slider(look, "Text size (%)", "textScale", 70, 150, 100)

        val motion = card("Motion")
        motionPresetButton(motion)
        toggle(motion, "Reduce motion (short, non-staggered transitions)", "reduceMotion", false)

        val blur = card("Frosted glass")
        toggle(blur, "Frosted translucent glass (safe mode)", "blur", false)
        blur.addView(TextView(this).apply {
            text = "Uses a layered, translucent pill surface without blurring the rest of your screen. True background blur clipped exactly to the pill is not enabled because some Xiaomi/HyperOS versions blur outside the pill shape."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(6), dp(2), dp(2))
        })

        val image = card("Custom image widget")
        toggle(image, "Show the image in the pill (when nothing else is playing)", "imageWidget", false)
        button(image, "Choose image") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 7)
        }
        slider(image, "Image height (dp)", "imageHeight", 40, 220, 120)
    }

    private fun buildPrivacy() {
        val privacy = card("History and hidden apps")
        toggle(privacy, "Keep notification history on this phone (off by default)", "historyOn", false)
        val blockField = EditText(this).apply {
            hint = "Hide notifications from these apps (package names, comma separated)"
            contentDescription = "Apps whose notifications should be hidden, comma-separated package names"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(prefs.getString("blockedApps", ""))
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
        }
        privacy.addView(blockField)
        button(privacy, "Save hidden apps") {
            prefs.edit().putString("blockedApps", blockField.text.toString()).apply()
            android.widget.Toast.makeText(this, "Hidden-app list saved", android.widget.Toast.LENGTH_SHORT).show()
        }
        button(privacy, "Open notification history") { startActivity(Intent(this, HistoryActivity::class.java)) }
    }

    private fun buildTools() {
        val tools = card("Tools")
        val backupNote = TextView(this).apply {
            text = "Settings backups use a versioned JSON format. Older backups are supported; restore asks before changing your current settings."
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(10))
        }
        tools.addView(backupNote)
        button(tools, "Open code generator") { startActivity(Intent(this, CodeActivity::class.java)) }
        button(tools, "Test pill (preview a music card)") {
            prefs.edit().putLong("testPill", System.currentTimeMillis()).apply()
        }
        button(tools, "Save settings to a file") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "capsule-settings.json")
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 8)
        }
        button(tools, "Restore settings from a file") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 9)
        }
        saveMsg = TextView(this).apply { textSize = 13f; setTextColor(primaryTextColor()) }
        tools.addView(saveMsg)
        button(tools, "Reset to defaults") {
            AlertDialog.Builder(this)
                .setTitle("Reset all settings?")
                .setMessage("This clears your saved Capsule Lite preferences on this phone. Export your settings first if you may want them later.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().clear().apply()
                    recreate()
                }
                .show()
        }
    }

    // Section descriptions and card hierarchy keep settings easy to scan.
    private fun sectionDescription(title: String): String = when (title) {
        "App status and diagnostics" -> "Service health, recent events, and troubleshooting details."
        "Permissions" -> "Grant only the access needed for the features you want to use."
        "Show in the pill" -> "Choose which live events can appear in the floating pill."
        "Gestures (tap a row to change it)" -> "Tap an action row to cycle through its available behavior."
        "Size and position" -> "Tune the pill footprint and placement for your display."
        "Music player" -> "Choose how playback information and visual effects are shown."
        "Auto expand" -> "Control when the pill opens automatically for new events."
        "Which notifications show" -> "Filter notification types and supported live activities."
        "Heights" -> "Adjust expanded layouts for different event types."
        "Control panel" -> "Choose how the quick-control panel opens and behaves."
        "Tiles in the control panel (switch off to remove a tile)" -> "Keep only the shortcuts you use."
        "Pinned apps and quick replies" -> "Personalize app shortcuts and supported reply suggestions."
        "Charging" -> "Configure charging status, alerts, and the main pill battery icon."
        "App theme" -> "Choose the appearance of the Capsule Lite settings screens."
        "Pill appearance" -> "Customize the floating pill's style, color, and typography."
        "Motion" -> "Balance smooth transitions with reduced-motion preferences."
        "Frosted glass" -> "Use a translucent layered look with a safe fallback on unsupported devices."
        "Custom image widget" -> "Choose an optional image to show in the pill."
        "History and hidden apps" -> "Manage local notification history and apps to keep out of view."
        "Tools" -> "Back up settings, restore a configuration, and run utilities."
        else -> "Adjust how this part of Capsule Lite behaves."
    }

    // Theme-aware section card: title, purpose, divider, then the existing controls.
    private fun card(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isLightAppTheme()) intArrayOf(0xFFFFFFFF.toInt(), 0xFFF4F7FB.toInt())
                else if (appTheme() == "amoled") intArrayOf(Color.BLACK, 0xFF050505.toInt())
                else intArrayOf(0xFF1B2938.toInt(), 0xFF121B26.toInt())
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD6E0EA.toInt() else if (appTheme() == "amoled") 0xFF292929.toInt() else 0xFF2C4052.toInt())
            }
            elevation = if (appTheme() == "amoled") 0f else dp(1).toFloat()
            setPadding(dp(17), dp(16), dp(17), dp(17))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "●"
                textSize = 10f
                setTextColor(if (isLightAppTheme()) 0xFF138A78.toInt() else 0xFF76DDF4.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(24))
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 15.5f
                letterSpacing = -0.015f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(primaryTextColor())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })
        box.addView(TextView(this).apply {
            text = sectionDescription(title)
            textSize = 12f
            setTextColor(secondaryTextColor())
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(22), dp(2), dp(2), dp(10))
        })
        box.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(if (isLightAppTheme()) 0xFFE2E8F0.toInt() else if (appTheme() == "amoled") 0xFF242424.toInt() else 0xFF304254.toInt())
                cornerRadius = dp(1).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(7)
            }
        })
        col.addView(box)
        return box
    }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(styledButton().apply {
            text = label
            setOnClickListener { onClick() }
        })
    }

    private fun toggle(parent: LinearLayout, label: String, key: String, default: Boolean) {
        parent.addView(Switch(this).apply {
            text = label
            textSize = 14f
            setTextColor(primaryTextColor())
            minHeight = dp(52)
            setPadding(dp(2), dp(8), dp(2), dp(8))
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        })
    }

    // Each tap moves the gesture to the next action in the list
    private fun gestureButton(parent: LinearLayout, key: String) {
        fun label(action: String) =
            "${CapsulePrefs.gestureLabels[key]}: ${CapsulePrefs.actionLabels[action] ?: action}"

        val current = (prefs.getString(key, CapsulePrefs.defaultFor(key)) ?: "none")
            .let { if (it == "quick_settings") "control_panel" else it }
        val b = styledButton().apply { text = label(current) }
        b.setOnClickListener {
            val cur = (prefs.getString(key, CapsulePrefs.defaultFor(key)) ?: "none")
                .let { if (it == "quick_settings") "control_panel" else it }
            val idx = CapsulePrefs.actions.indexOf(cur)
            val next = CapsulePrefs.actions[(idx + 1) % CapsulePrefs.actions.size]
            prefs.edit().putString(key, next).apply()
            b.text = label(next)
        }
        parent.addView(b)
    }

    private fun fontButton(parent: LinearLayout) {
        val fonts = listOf("default", "sans-serif-medium", "serif", "sans-serif-condensed", "monospace")
        val names = mapOf(
            "default" to "Default", "sans-serif-medium" to "Medium sans", "serif" to "Serif",
            "sans-serif-condensed" to "Condensed", "monospace" to "Monospace"
        )
        val labels = fonts.map { names[it] ?: it }.toTypedArray()
        fun selectedFont() = (prefs.getString("fontFamily", "default") ?: "default")
            .takeIf { it in fonts } ?: "default"
        fun label(f: String) = "Font: ${names[f] ?: names["default"]}"
        val b = styledButton().apply {
            text = label(selectedFont())
            contentDescription = "Choose pill font"
        }
        b.setOnClickListener {
            val current = selectedFont()
            AlertDialog.Builder(this)
                .setTitle("Choose pill font")
                .setSingleChoiceItems(labels, fonts.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                    val next = fonts[which]
                    prefs.edit().putString("fontFamily", next).apply()
                    b.text = label(next)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun appThemeButton(parent: LinearLayout) {
        val themes = listOf("system", "light", "dark", "amoled")
        val names = listOf("Follow system", "Light", "Dark", "AMOLED black")
        fun selectedTheme() = (prefs.getString("appTheme", "dark") ?: "dark").takeIf { it in themes } ?: "dark"
        val b = styledButton().apply { text = "App theme: " + names[themes.indexOf(selectedTheme()).coerceAtLeast(0)] }
        b.setOnClickListener {
            AlertDialog.Builder(this).setTitle("App theme")
                .setSingleChoiceItems(names.toTypedArray(), themes.indexOf(selectedTheme()).coerceAtLeast(0)) { dialog, which ->
                    prefs.edit().putString("appTheme", themes[which]).apply()
                    dialog.dismiss()
                    recreate()
                }.setNegativeButton("Cancel", null).show()
        }
        parent.addView(b)
    }
    private fun themePreview(parent: LinearLayout) {
        val style = PillStyles.palette(prefs.getString("theme", "capsule"))
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(style.top, style.bottom)
                cornerRadius = dp(style.cornerDp + 12).toFloat()
                setStroke(dp(1), style.outline)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)).apply { bottomMargin = dp(8) }
        }
        preview.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(style.accent)
                cornerRadius = dp(style.artworkCornerDp).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
        val trackTitle = TextView(this).apply {
            text = "Midnight Drive"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(style.primary)
        }
        val artist = TextView(this).apply {
            text = "Artist  ·  Playing"
            textSize = 11f
            setTextColor(style.secondary)
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(trackTitle)
            addView(artist)
        }
        preview.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        preview.addView(TextView(this).apply {
            text = "◁   ●   ▷"
            textSize = 12f
            setTextColor(style.accent)
            contentDescription = "Preview playback controls"
        })
        parent.addView(preview)
        parent.addView(TextView(this).apply {
            text = "${style.label} · preview of the expanded media pill"
            textSize = 11f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, 0, dp(5))
        })
    }

    private fun themeButton(parent: LinearLayout) {
        val themes = PillStyles.ids
        val labels = themes.map { PillStyles.palette(it).label }.toTypedArray()
        fun selectedTheme() = PillStyles.normalize(prefs.getString("theme", "capsule"))
        fun label(t: String) = "Pill style: ${PillStyles.palette(t).label}"
        val b = styledButton().apply {
            text = label(selectedTheme())
            contentDescription = "Choose pill style"
        }
        b.setOnClickListener {
            val current = selectedTheme()
            val selectedIndex = themes.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Choose pill style")
                .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                    val next = themes[which]
                    prefs.edit().putString("theme", next).apply()
                    b.text = label(next)
                    dialog.dismiss()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun motionPresetButton(parent: LinearLayout) {
        val presets = listOf("fluid", "smooth", "fast")
        val names = mapOf(
            "fluid" to "Fluid Spring",
            "smooth" to "Smooth & Subtle",
            "fast" to "Fast & Responsive"
        )
        val descriptions = arrayOf(
            "Fluid Spring — balanced movement",
            "Smooth & Subtle — slower transitions",
            "Fast & Responsive — quicker transitions"
        )
        fun currentPreset(): String {
            prefs.getString("motionPreset", null)?.let { if (it in presets) return it }
            return when (prefs.getString("springSpeed", "medium")) {
                "slow" -> "smooth"
                "fast" -> "fast"
                else -> "fluid"
            }
        }
        fun label(value: String) = "Motion preset: ${names[value] ?: names["fluid"]}"
        val button = styledButton().apply {
            text = label(currentPreset())
            contentDescription = "Choose animation preset"
        }
        button.setOnClickListener {
            val current = currentPreset()
            val selectedIndex = presets.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Choose animation preset")
                .setSingleChoiceItems(descriptions, selectedIndex) { dialog, which ->
                    val next = presets[which]
                    val speed = when (next) { "smooth" -> "slow"; "fast" -> "fast"; else -> "medium" }
                    prefs.edit().putString("motionPreset", next)
                        .putString("springSpeed", speed)
                        .putBoolean("springMotion", true)
                        .apply()
                    button.text = label(next)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
    }

    private fun pillAnimationStyleButton(parent: LinearLayout) {
        val styles = listOf("liquid_morph", "continuity", "settling_dock")
        val labels = mapOf(
            "liquid_morph" to "Liquid morph",
            "continuity" to "Icon continuity",
            "settling_dock" to "Settling dock"
        )
        val descriptions = arrayOf(
            "Liquid morph — elastic width and height movement",
            "Icon continuity — carry the charging icon between sizes",
            "Settling dock — calm expansion with details unfolding below"
        )
        fun selected() = (prefs.getString("pillAnimationStyle", "continuity") ?: "continuity")
            .takeIf { it in styles } ?: "continuity"
        fun label(style: String) = "Expansion animation: ${labels[style] ?: labels["continuity"]}"
        val button = styledButton().apply {
            text = label(selected())
            contentDescription = "Choose pill expansion animation style"
        }
        button.setOnClickListener {
            val current = selected()
            AlertDialog.Builder(this)
                .setTitle("Pill expansion animation")
                .setSingleChoiceItems(descriptions, styles.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                    val next = styles[which]
                    prefs.edit().putString("pillAnimationStyle", next)
                        .putBoolean("springMotion", true)
                        .apply()
                    button.text = label(next)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
        parent.addView(TextView(this).apply {
            text = "Choose how the pill expands: Liquid morph, Icon continuity, or Settling dock. This changes the pill transition, not the charging icon."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(8))
        })
    }

    private fun vizStyleButton(parent: LinearLayout) {
        val styles = listOf("bars", "mirror", "dots", "wave")
        val names = mapOf("bars" to "Bars", "mirror" to "Mirror", "dots" to "Dots", "wave" to "Wave")
        val labels = styles.map { names[it] ?: it }.toTypedArray()
        fun selectedStyle() = (prefs.getString("vizStyle", "bars") ?: "bars")
            .takeIf { it in styles } ?: "bars"
        fun label(v: String) = "Visualizer style: ${names[v] ?: names["bars"]}"
        val b = styledButton().apply {
            text = label(selectedStyle())
            contentDescription = "Choose visualizer style"
        }
        b.setOnClickListener {
            val current = selectedStyle()
            AlertDialog.Builder(this)
                .setTitle("Choose visualizer style")
                .setSingleChoiceItems(labels, styles.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                    val next = styles[which]
                    prefs.edit().putString("vizStyle", next).apply()
                    b.text = label(next)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun slider(parent: LinearLayout, label: String, key: String, lo: Int, hi: Int, default: Int) {
        val stored = prefs.getInt(key, default)
        val start = stored.coerceIn(lo, hi)
        // Normalize stale or imported settings so the caption and thumb always agree.
        if (stored != start) prefs.edit().putInt(key, start).apply()
        val caption = TextView(this).apply { text = "$label: $start"; setTextColor(primaryTextColor()) }
        parent.addView(caption)
        parent.addView(SeekBar(this).apply {
            contentDescription = "$label: $start"
            max = hi - lo
            progress = start - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val value = (p + lo).coerceIn(lo, hi)
                    caption.text = "$label: $value"
                    sb.contentDescription = "$label: $value"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    prefs.edit().putInt(key, (sb.progress + lo).coerceIn(lo, hi)).apply()
                }
            })
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Save: write every setting to the chosen file (diagnostics and the test flag are left out)
        if (requestCode == 8 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                val json = JSONObject()
                json.put("_capsuleLiteBackupVersion", 1)
                json.put("_capsuleLiteExportedAt", System.currentTimeMillis())
                for ((k, v) in prefs.all) {
                    if (k == "diag" || k == "testPill") continue
                    json.put(k, v)
                }
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("could not open the selected file for writing")
                output.use { stream ->
                    stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }.onSuccess {
                saveMsg.text = "Settings saved to the file."
                android.widget.Toast.makeText(this, "Settings backup saved", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                saveMsg.text = "Could not save: ${it.message ?: "file access error"}"
                android.widget.Toast.makeText(this, "Settings backup failed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Restore: ask before applying a settings file over the current configuration.
        if (requestCode == 9 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            AlertDialog.Builder(this)
                .setTitle("Restore settings?")
                .setMessage("Settings in this file will overwrite matching Capsule Lite choices. Settings not included in the file will remain unchanged. Export your current settings first if you want a backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore") { _, _ ->
                    runCatching {
                        val text = contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader().use { reader ->
                                val out = StringBuilder()
                                val buffer = CharArray(8192)
                                while (true) {
                                    val count = reader.read(buffer)
                                    if (count < 0) break
                                    if (out.length + count > 1_000_000) {
                                        error("settings file is too large (maximum 1 MB)")
                                    }
                                    out.append(buffer, 0, count)
                                }
                                out.toString()
                            }
                        } ?: error("could not open the file")
                        val json = JSONObject(text)
                        if (json.has("_capsuleLiteBackupVersion")) {
                            val backupVersion = json.optInt("_capsuleLiteBackupVersion", -1)
                            if (backupVersion != 1) error("unsupported backup version: $backupVersion")
                        }
                        val edit = prefs.edit()
                        val keys = json.keys()
                        var restoredCount = 0
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k == "diag" || k == "testPill" ||
                                k == "_capsuleLiteBackupVersion" || k == "_capsuleLiteExportedAt") continue
                            when (val v = json.get(k)) {
                                is Boolean -> { edit.putBoolean(k, v); restoredCount++ }
                                is Int -> { edit.putInt(k, v); restoredCount++ }
                                is Long -> { edit.putLong(k, v); restoredCount++ }
                                is Double -> {
                                    if (!v.isFinite()) error("invalid number for $k")
                                    val floatValue = v.toFloat()
                                    if (!floatValue.isFinite()) error("number is out of range for $k")
                                    edit.putFloat(k, floatValue)
                                    restoredCount++
                                }
                                is String -> { edit.putString(k, v); restoredCount++ }
                                else -> error("unsupported value for setting $k")
                            }
                        }
                        if (restoredCount == 0) error("no supported settings found in this file")
                        edit.apply()
                    }.onSuccess {
                        saveMsg.text = "Settings restored from the file."
                        android.widget.Toast.makeText(this, "Settings restored", android.widget.Toast.LENGTH_SHORT).show()
                        recreate()
                    }.onFailure {
                        saveMsg.text = "Could not restore: ${it.message ?: "invalid settings file"}"
                        android.widget.Toast.makeText(this, "Settings restore failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
        if (requestCode == 7 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            // Keep read access to the picture after the app restarts
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit()
                .putString("widgetImage", uri.toString())
                .putBoolean("imageWidget", true)
                .apply()
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(queueStateListener)
        refreshQueueState()
        val overlay = Settings.canDrawOverlays(this)
        val listener = Settings.Secure
            .getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val keepAlive = Settings.Secure
            .getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        val brightness = Settings.System.canWrite(this)
        val dnd = getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
        val usage = getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
        val mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        status.text = "Overlay: ${if (overlay) "Allowed" else "permission needed"}\n" +
            "Notification listener: ${if (listener) "enabled" else "permission needed"} · ${if (KeepAlive.listenerConnected) "connected" else "disconnected"}\n" +
            "Accessibility keep-alive: ${if (keepAlive) "enabled" else "off"} · ${if (KeepAlive.accessibilityConnected) "connected" else "disconnected"}\n" +
            "Brightness control: ${if (brightness) "OK" else "off"}\n" +
            "Do Not Disturb control: ${if (dnd) "OK" else "off"}\n" +
            "App usage access: ${if (usage) "OK" else "off"}\n" +
            "Microphone: ${if (mic) "OK" else "off"}"

        showDiagnostics()

        if (overlay && listener) {
            NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java))
        }
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(queueStateListener)
        super.onPause()
    }
}
