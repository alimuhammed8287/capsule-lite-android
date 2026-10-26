package com.example.capsule

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var diagText: TextView
    private lateinit var repairText: TextView
    private lateinit var saveMsg: TextView
    private lateinit var col: LinearLayout
    private var cat: String? = null
    private val prefs by lazy { getSharedPreferences("capsule", MODE_PRIVATE) }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Rounded, translucent buttons used across the settings screen
    private fun styledButton() = Button(this).apply {
        setTextColor(0xFFF2F6FC.toInt())
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        minHeight = dp(48)
        stateListAnimator = null
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF203244.toInt(), 0xFF121C28.toInt())
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x334DDDB0)
        }
        setPadding(dp(15), dp(11), dp(15), dp(11))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7) }
    }

    private val titles = mapOf(
        "setup" to "Setup and diagnostics",
        "pill" to "Pill",
        "music" to "Music",
        "notifications" to "Notifications",
        "quick" to "Quick controls",
        "charging" to "Charging",
        "look" to "Look",
        "privacy" to "Privacy",
        "tools" to "Tools"
    )

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
                setColor(0xFF172332.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x334EDDB0)
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
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
                    letterSpacing = -0.035f
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (cat == null) "FLOATING COMPANION  ·  V1.3" else "PERSONALISE YOUR EXPERIENCE"
                    textSize = 10f
                    letterSpacing = 0.08f
                    setTextColor(0xFF91A2B7.toInt())
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }.apply { 
            setPadding(dp(12), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        })
        if (cat != null) button(col, "Back") { finish() }
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFE7EDF5.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(12))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        when (cat) {
            null -> buildHome()
            "setup" -> buildSetup()
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
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0A111B.toInt(), 0xFF101824.toInt(), 0xFF090B10.toInt()))
            addView(col)
        })
    }

    private fun open(name: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("cat", name))
    }

    private fun showDiagnostics() {
        if (!::diagText.isInitialized) return
        val log = DiagLog.lastLines(this, 300)
        diagText.text = log.ifEmpty { "no service events yet" }
        if (::repairText.isInitialized) {
            repairText.text = "Accessibility keep-alive: " + (if (KeepAlive.isAccessibilityEnabled(this)) "on" else "OFF") +
                "\nAutomatic repair: " + when {
                    !prefs.getBoolean("autoRepairServices", false) -> "off (opt-in switch is off)"
                    !KeepAlive.canSelfRepair(this) -> "enabled, but optional ADB permission is missing"
                    else -> "enabled"
                } + "\nShowing the last 300 lines. Copy gives everything."
        }
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
        homeTile("⚙", "Setup and diagnostics", "Permissions, service status and troubleshooting", "setup")
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
                intArrayOf(0xFF1A2430.toInt(), 0xFF121A24.toInt())
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFF2A3948.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) }
            isClickable = true
            isFocusable = true
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
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(0xFF9AAABD.toInt())
                setPadding(0, dp(3), 0, 0)
            })
        })
        tile.addView(TextView(this).apply {
            text = "›"
            textSize = 26f
            setTextColor(0xFF8CA2B8.toInt())
            gravity = android.view.Gravity.CENTER
        })
        col.addView(tile)
    }

    private fun buildSetup() {
        val diag = card("App status and diagnostics")
        diag.addView(status)
        repairText = TextView(this).apply { textSize = 13f }
        diag.addView(repairText)
        diagText = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        diag.addView(diagText)
        button(diag, "Copy full diagnostics") { copyDiagnostics() }
        button(diag, "Refresh") { showDiagnostics() }
        button(diag, "Clear diagnostics") {
            DiagLog.clear(this)
            showDiagnostics()
        }
        toggle(diag, "Allow automatic service repair if Android disables it (optional)", "autoRepairServices", false)
        TextView(this).apply {
            text = "Automatic repair is off by default. If you enable it, the optional ADB permission is also required. Turning this off prevents Capsule from re-enabling services you deliberately disabled."
            textSize = 12f
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }.also { diag.addView(it) }
        button(diag, "Copy optional ADB permission command") { copyRepairCommand() }
        toggle(diag, "Keep-running notification (helps Android keep Capsule alive)", "keepNotif", true)

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

        val shown = card("Which notifications show")
        toggle(shown, "Other notifications", "showNotifs", true)
        toggle(shown, "Google Maps navigation", "showMaps", true)
        toggle(shown, "Navigation info in the pill (reads Maps' navigation notification, and the Maps screen while it is open)", "mapsInfo", false)
        toggle(shown, "Use Maps' own turn arrow picture (turn off to use the arrows Capsule draws)", "mapsOwnArrow", true)
        toggle(shown, "Phone events (unlock, headphones, Bluetooth)", "showEvents", true)
        toggle(shown, "Downloads and progress activities", "showDownloads", true)
        toggle(shown, "Live timer and stopwatch notifications", "liveActivities", true)
        val liveApps = EditText(this).apply {
            hint = "Optional live-activity allow-list (package names, comma separated; blank = all apps)"
            setText(prefs.getString("liveActivityApps", ""))
            textSize = 13f
        }
        shown.addView(liveApps)
        button(shown, "Save live-activity app list") {
            prefs.edit().putString("liveActivityApps", liveApps.text.toString().trim()).apply()
        }
        toggle(shown, "Driving mode (hide other notifications during Maps navigation)", "drivingMode", false)
        toggle(shown, "Colour the title from the sending app", "appAccent", true)
        toggle(shown, "Quick reply buttons (Yes, Later, Busy)", "replyChips", true)
        toggle(shown, "Debug: show raw Maps text", "debugMaps", false)

        val heights = card("Heights")
        slider(heights, "Height: notifications and calls", "expH_notif", 40, 120, 64)
        slider(heights, "Height: notification with buttons", "expH_stack", 40, 160, 104)
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
            setText(prefs.getString("replies", "Yes,Later,Busy"))
        }
        custom.addView(replyField)
        val pinField = EditText(this).apply {
            hint = "Pinned apps, package names, comma separated (up to 4)"
            setText(prefs.getString("pinned", ""))
        }
        custom.addView(pinField)
        button(custom, "Save") {
            prefs.edit()
                .putString("replies", replyField.text.toString())
                .putString("pinned", pinField.text.toString())
                .apply()
        }
    }

    private fun buildCharging() {
        val charge = card("Charging")
        toggle(charge, "Show charging status", "showCharging", true)
        toggle(charge, "Move charging into a separate pill while notifications, Maps, music or calls are active", "separateChargePill", true)
        toggle(charge, "Charging alerts (when the charge reaches a level, or is full)", "chargeAlert", true)
        slider(charge, "Alert at charge (%)", "chargeAlertAt", 50, 100, 80)
        slider(charge, "Height: charging", "expH_charge", 40, 120, 64)
        slider(charge, "Separate pill width", "chargeMiniW", 76, 150, 100)
        slider(charge, "Separate pill height", "chargeMiniH", 34, 64, 42)
        slider(charge, "Separate pill vertical offset", "chargeMiniYOffset", -80, 120, 0)
        chargingStyleButton(charge)
        chargingIconButton(charge)

        val gestures = card("Separate pill gestures")
        TextView(this).apply {
            text = "Choose actions for the charging pill independently from the main pill."
            textSize = 12f
            setTextColor(0xFF9DA6B5.toInt())
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }.also { gestures.addView(it) }
        chargeGestureButton(gestures, "chargePillTap", "Single tap")
        chargeGestureButton(gestures, "chargePillDoubleTap", "Double tap")
        chargeGestureButton(gestures, "chargePillLongPress", "Long press")
    }

    private fun chargeGestureButton(parent: LinearLayout, key: String, label: String) {
        val actions = listOf("battery" to "Open battery screen", "toggle" to "Expand/collapse main pill", "none" to "Do nothing")
        fun current() = (prefs.getString(key, if (key == "chargePillTap") "battery" else "none") ?: "none")
            .let { value -> actions.indexOfFirst { it.first == value }.coerceAtLeast(0) }
        val b = styledButton().apply {
            text = "$label: " + actions[current()].second
            contentDescription = "Configure separate charging pill $label"
        }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(label + " action")
                .setSingleChoiceItems(actions.map { it.second }.toTypedArray(), current()) { dialog, which ->
                    prefs.edit().putString(key, actions[which].first).apply()
                    b.text = "$label: " + actions[which].second
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun chargingStyleButton(parent: LinearLayout) {
        val styles = listOf("Liquid battery", "Segmented battery", "Pulse battery")
        fun current() = prefs.getInt("chargeIconStyle", 0).coerceIn(0, styles.lastIndex)
        val b = styledButton().apply {
            text = "Charging icon: " + styles[current()]
            contentDescription = "Choose charging animation"
        }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging animation")
                .setSingleChoiceItems(styles.toTypedArray(), current()) { dialog, which ->
                    prefs.edit().putInt("chargeIconStyle", which).apply()
                    b.text = "Charging icon: " + styles[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun chargingIconButton(parent: LinearLayout) {
        val icons = listOf("Battery", "Lightning bolt", "Power plug", "Leaf", "Spark")
        fun selected() = prefs.getInt("chargeIconShape", 0).coerceIn(0, icons.lastIndex)
        val b = styledButton().apply { text = "Charging symbol: " + icons[selected()] }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging symbol")
                .setSingleChoiceItems(icons.toTypedArray(), selected()) { dialog, which ->
                    prefs.edit().putInt("chargeIconShape", which).apply()
                    b.text = "Charging symbol: " + icons[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(b)
    }

    private fun buildLook() {
        val look = card("Appearance")
        themePreview(look)
        fontButton(look)
        themeButton(look)
        slider(look, "Text size (%)", "textScale", 70, 150, 100)

        val motion = card("Motion")
        motionPresetButton(motion)
        toggle(motion, "Enable liquid morph animation", "springMotion", true)
        toggle(motion, "Reduce motion (short, non-staggered transitions)", "reduceMotion", false)

        val blur = card("Frosted glass")
        toggle(blur, "Frosted translucent glass (safe mode)", "blur", false)
        blur.addView(TextView(this).apply {
            text = "Uses a layered, translucent pill surface without blurring the rest of your screen. True background blur clipped exactly to the pill is not enabled because some Xiaomi/HyperOS versions blur outside the pill shape."
            textSize = 12f
            setTextColor(0xFF9DA6B5.toInt())
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
            setText(prefs.getString("blockedApps", ""))
        }
        privacy.addView(blockField)
        button(privacy, "Save hidden apps") {
            prefs.edit().putString("blockedApps", blockField.text.toString()).apply()
        }
        button(privacy, "Open notification history") { startActivity(Intent(this, HistoryActivity::class.java)) }
    }

    private fun buildTools() {
        val tools = card("Tools")
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
        saveMsg = TextView(this).apply { textSize = 13f }
        tools.addView(saveMsg)
        button(tools, "Reset to defaults") {
            prefs.edit().clear().apply()
            recreate()
        }
    }

    // A rounded section with an accent title; controls are added inside it
    private fun card(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF192431.toInt(), 0xFF121A24.toInt())
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFF2B3B4B.toInt())
            }
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        box.addView(TextView(this).apply {
            text = title.uppercase()
            textSize = 11f
            letterSpacing = 0.09f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(0xFF76DDF4.toInt())
            setPadding(0, 0, 0, dp(8))
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
            setTextColor(0xFFE7EDF5.toInt())
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

    private fun themePreview(parent: LinearLayout) {
        val current = prefs.getString("theme", "black") ?: "black"
        val (bg, stroke, glow) = when (current) {
            "frosted" -> Triple(0xCC303641.toInt(), 0x99FFFFFF.toInt(), "FROSTED GLASS")
            "futuristic" -> Triple(0xFF071827.toInt(), 0xFF43D9FF.toInt(), "FUTURISTIC CYAN")
            "ios" -> Triple(0xFF34363D.toInt(), 0x66FFFFFF, "iOS INSPIRED")
            "accent" -> Triple(0xFF08090C.toInt(), accent, "ACCENT OUTLINE")
            else -> Triple(0xFF030405.toInt(), 0x443FFFFF.toInt(), "CLASSIC BLACK")
        }
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(30).toFloat()
                setStroke(dp(if (current == "futuristic" || current == "accent") 2 else 1), stroke)
            }
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply { bottomMargin = dp(8) }
        }
        preview.addView(TextView(this).apply {
            text = "●  82%   ·   ◉  ♫"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (current == "futuristic") 0xFF80E9FF.toInt() else Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        preview.addView(TextView(this).apply {
            text = glow
            textSize = 9f
            letterSpacing = 0.06f
            setTextColor(if (current == "futuristic") 0xFF43D9FF.toInt() else 0xFFB6C1D0.toInt())
        })
        parent.addView(preview)
        parent.addView(TextView(this).apply {
            text = "Live style preview · applies to the floating pill and expanded panel"
            textSize = 11f
            setTextColor(0xFF8E9AAA.toInt())
            setPadding(dp(2), 0, 0, dp(5))
        })
    }

    private fun themeButton(parent: LinearLayout) {
        val themes = listOf("black", "frosted", "futuristic", "ios", "accent")
        val names = mapOf(
            "black" to "Classic Black",
            "frosted" to "Frosted Glass",
            "futuristic" to "Futuristic Cyan",
            "ios" to "iOS Inspired",
            "accent" to "Accent Outline"
        )
        val labels = themes.map { names[it] ?: it }.toTypedArray()
        fun selectedTheme() = (prefs.getString("theme", "black") ?: "black")
            .takeIf { it in themes } ?: "black"
        fun label(t: String) = "Pill theme: ${names[t] ?: names["black"]}"
        val b = styledButton().apply {
            text = label(selectedTheme())
            contentDescription = "Choose pill theme"
        }
        b.setOnClickListener {
            val current = selectedTheme()
            val selectedIndex = themes.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Choose pill theme")
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
        val caption = TextView(this).apply { text = "$label: $start" }
        parent.addView(caption)
        parent.addView(SeekBar(this).apply {
            contentDescription = label
            max = hi - lo
            progress = start - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    caption.text = "$label: ${(p + lo).coerceIn(lo, hi)}"
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
                for ((k, v) in prefs.all) {
                    if (k == "diag" || k == "testPill") continue
                    json.put(k, v)
                }
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
                    ?: error("could not open the file")
            }.onSuccess {
                saveMsg.text = "Settings saved to the file."
            }.onFailure {
                saveMsg.text = "Could not save: ${it.message}"
            }
        }
        // Restore: read a saved file and apply every setting in it
        if (requestCode == 9 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
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
                val edit = prefs.edit()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "diag" || k == "testPill") continue
                    when (val v = json.get(k)) {
                        is Boolean -> edit.putBoolean(k, v)
                        is Int -> edit.putInt(k, v)
                        is Long -> edit.putLong(k, v)
                        is Double -> edit.putFloat(k, v.toFloat())
                        is String -> edit.putString(k, v)
                    }
                }
                edit.apply()
            }.onSuccess {
                recreate()
            }.onFailure {
                saveMsg.text = "Could not restore: ${it.message}"
            }
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

        status.text = "Overlay: ${if (overlay) "OK" else "missing"}\n" +
            "Notification access: ${if (listener) "OK" else "missing"}\n" +
            "Keep-alive: ${if (keepAlive) "OK" else "off"}\n" +
            "Brightness control: ${if (brightness) "OK" else "off"}\n" +
            "Do Not Disturb control: ${if (dnd) "OK" else "off"}\n" +
            "App usage access: ${if (usage) "OK" else "off"}\n" +
            "Microphone: ${if (mic) "OK" else "off"}"

        showDiagnostics()

        if (overlay && listener) {
            NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java))
        }
    }
}
