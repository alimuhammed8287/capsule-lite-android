package com.example.capsule

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
import androidx.activity.ComponentActivity
import java.io.File

class MainActivity : ComponentActivity() {

    private data class SliderHandle(
        val currentValue: () -> Int,
        val setMinimum: (Int) -> Unit,
        val writeTo: (SharedPreferences.Editor) -> Unit,
        val setValue: (Int) -> Unit
    )

    private data class SearchSetting(
        val label: String,
        val section: String,
        val key: String,
        val defaultValue: Any,
        val keywords: String = "",
        val advanced: Boolean = false,
        val resetKeys: Set<String> = setOf(key)
    )

    private lateinit var status: TextView
    private lateinit var diagText: TextView
    private lateinit var repairText: TextView
    private lateinit var saveMsg: TextView
    private lateinit var col: LinearLayout
    private var queueStateView: TextView? = null
    private var expansionPreviewSurface: View? = null
    private var expansionPreviewLabel: TextView? = null
    private var chargeWidthPreviewSample: View? = null
    private var chargeWidthPreviewValue: TextView? = null
    private var cat: String? = null
    private var notificationCornerPreviewBadge: TextView? = null
    private var notificationCornerPreviewPill: View? = null
    private var notificationCornerPreviewLabel: TextView? = null
    private val homeCustomizationBadges = mutableMapOf<String, TextView>()
    private val homeCustomizationTiles = mutableMapOf<String, View>()
    private val prefs by lazy { getSharedPreferences("capsule", MODE_PRIVATE) }
    private val queueStateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "queueStateSummary" -> refreshQueueState()
            "expandFromStatusBar" -> refreshExpansionAnchorPreview()
        }
    }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun settingTag(key: String) = "setting:$key"
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

    /** Palette used only by settings mockups; the live overlay keeps its own selected pill style. */
    private fun previewPillPalette(raw: String? = prefs.getString("theme", "capsule")): PillStylePalette {
        val style = PillStyles.palette(raw)
        return when {
            isLightAppTheme() -> {
                fun blend(base: Int, tint: Int, amount: Float) = Color.rgb(
                    (Color.red(base) * (1f - amount) + Color.red(tint) * amount).toInt(),
                    (Color.green(base) * (1f - amount) + Color.green(tint) * amount).toInt(),
                    (Color.blue(base) * (1f - amount) + Color.blue(tint) * amount).toInt()
                )
                val readableAccent = blend(style.accent, 0xFF111827.toInt(), 0.24f)
                style.copy(
                    top = blend(Color.WHITE, style.accent, 0.035f),
                    bottom = blend(Color.WHITE, style.accent, 0.085f),
                    outline = blend(0xFFE2E7ED.toInt(), style.accent, 0.28f),
                    accent = readableAccent,
                    primary = 0xFF18212B.toInt(),
                    secondary = 0xFF5D6B7B.toInt(),
                    control = 0xFFE5E9EF.toInt()
                )
            }
            appTheme() == "amoled" -> style.copy(
                top = Color.BLACK,
                bottom = 0xFF030303.toInt(),
                outline = 0xFF292929.toInt(),
                primary = 0xFFF5F5F7.toInt(),
                secondary = 0xFFB9BEC8.toInt(),
                control = 0xFF161616.toInt()
            )
            else -> style
        }
    }


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
            if (isLightAppTheme()) intArrayOf(0xFFF1F3F6.toInt(), 0xFFF1F3F6.toInt())
            else if (appTheme() == "amoled") intArrayOf(0xFF101010.toInt(), 0xFF101010.toInt())
            else intArrayOf(0xFF20262D.toInt(), 0xFF20262D.toInt())
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), if (isLightAppTheme()) 0xFFE1E5EA.toInt() else if (appTheme() == "amoled") 0xFF292929.toInt() else 0xFF343B44.toInt())
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
        "maps" to "Maps",
        "pinned" to "Pinned apps",
        "quick" to "Pinned apps",
        "apps_tasks" to "Apps & Tasks",
        "charging" to "Charging",
        "look" to "Look",
        "privacy" to "Privacy",
        "tools" to "Tools",
        "tasks" to "Tasks",
        "about" to "About & What's New"
    )


    private val sectionDescriptions = mapOf(
        "setup" to "Check permissions, service health, and recovery options.",
        "diagnostics" to "Review, copy, or share a diagnostic report.",
        "pill" to "Tune size, placement, gestures, and when the pill appears.",
        "music" to "Choose playback controls and visualizer behaviour.",
        "notifications" to "Control notification visibility, live activities, and replies.",
        "maps" to "Choose what navigation details appear and how the expanded route card is laid out.",
        "pinned" to "Choose apps to launch from the pill’s expanded view.",
        "quick" to "Choose apps to launch from the pill’s expanded view.",
        "apps_tasks" to "Manage pinned app shortcuts and your local task lists.",
        "charging" to "Customise charging status, animation, and session summaries.",
        "look" to "Choose colours, typography, glass styling, and motion.",
        "privacy" to "Manage hidden apps and on-device notification history.",
        "tools" to "Back up settings, restore a configuration, or reset preferences.",
        "tasks" to "Keep a private, local task list and choose what appears in the pill.",
        "about" to "See the installed app version, build number, and release notes."
    )

    private fun addSettingsIntro() {
        val title = TextView(this).apply {
            text = titles[cat] ?: "Settings"
            textSize = 30f
            letterSpacing = -0.035f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(primaryTextColor())
            setPadding(0, dp(22), 0, dp(5))
        }
        col.addView(title)
        col.addView(TextView(this).apply {
            text = sectionDescriptions[cat] ?: "Adjust the options below to suit your setup."
            textSize = 14f
            setTextColor(secondaryTextColor())
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
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
        applySettingsSystemBars()
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }
        if (cat == null) {
            status = TextView(this).apply {
                textSize = 13f
                setTextColor(primaryTextColor())
                setPadding(dp(8), dp(8), dp(8), dp(12))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            col.setPadding(dp(20), dp(4), dp(20), dp(28))
            buildHome()
            setContentView(buildOneUiHomeScreen())
            return
        } else {
            addSectionToolbar()
            addSettingsIntro()
            sectionPreview(cat!!)?.let { col.addView(it) }
        }
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
            "apps_tasks" -> buildAppsTasks()
            "notifications" -> buildNotifications()
            "maps" -> buildMaps()
            "pinned", "quick" -> buildPinnedApps()
            "charging" -> buildCharging()
            "look" -> buildLook()
            "privacy" -> buildPrivacy()
            "tools" -> buildTools()
            "tasks" -> buildTasks()
            "about" -> buildAbout()
        }

        val settingsScroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, screenColors())
            addView(col)
        }
        setContentView(settingsScroll)
        intent.getStringExtra("focusSetting")?.let { key ->
            settingsScroll.post {
                val target = col.findViewWithTag<View>(settingTag(key)) ?: return@post
                target.requestFocus()
                val bounds = android.graphics.Rect()
                target.getDrawingRect(bounds)
                settingsScroll.offsetDescendantRectToMyCoords(target, bounds)
                settingsScroll.smoothScrollTo(0, (bounds.top - dp(18)).coerceAtLeast(0))
            }
        }
    }

    private fun applySettingsSystemBars() {
        val surface = screenColors()[0]
        window.statusBarColor = surface
        window.navigationBarColor = surface
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            var flags = window.decorView.systemUiVisibility
            flags = if (isLightAppTheme()) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags = if (isLightAppTheme()) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun addSectionToolbar() {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }
        toolbar.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(primaryTextColor())
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0x22394455 else 0x22FFFFFF),
                null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF777777.toInt()) }
            )
            contentDescription = "Back to Capsule settings"
            scaleType = android.widget.ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { finish() }
        })
        toolbar.addView(TextView(this).apply {
            text = "Settings"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
            setPadding(dp(12), 0, 0, 0)
        })
        col.addView(toolbar)
    }

    private fun buildOneUiHomeScreen(): View {
        val background = screenColors()[0]
        val materialContext = android.view.ContextThemeWrapper(
            this,
            R.style.CapsuleMaterialComponentsTheme
        )
        val root = androidx.coordinatorlayout.widget.CoordinatorLayout(this).apply {
            setBackgroundColor(background)
        }
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // Keep the home header compact. The former 43% screen-height app bar left
        // most of the first screen empty before the settings sections began.
        val expandedHeight = dp(if (landscape) 96 else 112)
        val appBar = com.google.android.material.appbar.AppBarLayout(materialContext).apply {
            setBackgroundColor(background)
            minimumHeight = dp(64)
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT, expandedHeight
            )
        }
        val collapsing = com.google.android.material.appbar.CollapsingToolbarLayout(materialContext).apply {
            title = ""
            isTitleEnabled = false
            setExpandedTitleTextColor(android.content.res.ColorStateList.valueOf(primaryTextColor()))
            setCollapsedTitleTextColor(android.content.res.ColorStateList.valueOf(primaryTextColor()))
            setExpandedTitleTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            setCollapsedTitleTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            expandedTitleGravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            collapsedTitleGravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            setExpandedTitleMargin(dp(24), 0, dp(20), dp(8))
            setContentScrimColor(background)
            setStatusBarScrimColor(background)
            scrimAnimationDuration = 180L
            layoutParams = com.google.android.material.appbar.AppBarLayout.LayoutParams(
                com.google.android.material.appbar.AppBarLayout.LayoutParams.MATCH_PARENT,
                com.google.android.material.appbar.AppBarLayout.LayoutParams.MATCH_PARENT
            ).apply {
                scrollFlags = com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }
        }
        val introLine = TextView(this).apply {
            val overlayGranted = Settings.canDrawOverlays(this@MainActivity)
            val listenerGranted = KeepAlive.isListenerEnabled(this@MainActivity)
            val connected = KeepAlive.listenerConnected
            text = when {
                !overlayGranted || !listenerGranted -> "SETUP NEEDED"
                connected -> "SERVICE CONNECTED"
                else -> "ACCESS GRANTED · CHECK SERVICE STATUS"
            }
            textSize = 11f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (isLightAppTheme()) 0xFF587166.toInt() else 0xFF9BB8AC.toInt())
            contentDescription = text.toString().replace("·", "and", ignoreCase = false)
        }
        collapsing.addView(introLine, com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams(
            com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT,
            com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            leftMargin = dp(26)
            bottomMargin = dp(8)
            collapseMode = com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX
        })
        val toolbar = com.google.android.material.appbar.MaterialToolbar(materialContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            title = "Capsule Lite"
            setTitleTextColor(primaryTextColor())
            minimumHeight = dp(64)
            val search = menu.add(0, 9001, 0, "Search")
            search.setIcon(R.drawable.ic_search)
            search.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            search.icon?.setTint(primaryTextColor())
            val theme = menu.add(0, 9002, 1, homeThemeLabel())
            theme.setActionView(TextView(this@MainActivity).apply {
                text = homeThemeLabel()
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(primaryTextColor())
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                this.background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(if (isLightAppTheme()) 0xFFE9EDF1.toInt() else if (appTheme() == "amoled") 0xFF151515.toInt() else 0xFF242B33.toInt())
                }
                setPadding(dp(13), 0, dp(13), 0)
                contentDescription = "App appearance: ${homeThemeLabel()}. Change theme."
                isClickable = true
                isFocusable = true
                minimumHeight = dp(40)
                setOnClickListener { chooseHomeTheme() }
            })
            theme.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    9001 -> { showFeatureSearch(); true }
                    9002 -> { chooseHomeTheme(); true }
                    else -> false
                }
            }
        }
        collapsing.addView(toolbar, com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams(
            com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.MATCH_PARENT, dp(64)
        ).apply {
            gravity = android.view.Gravity.TOP
            collapseMode = com.google.android.material.appbar.CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
        })
        appBar.addView(collapsing)
        root.addView(appBar)
        val content = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(background)
            addView(col)
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT
            ).apply {
                behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
            }
        }
        root.addView(content)
        return root
    }

    private fun homeThemeLabel() = when (appTheme()) {
        "system" -> "Auto"
        "light" -> "Light"
        "amoled" -> "AMOLED"
        else -> "Dark"
    }

    private fun open(name: String, settingKey: String? = null, advanced: Boolean = false) {
        val destination = if (name == "quick") "pinned" else name
        val intent = Intent(this, MainActivity::class.java).putExtra("cat", destination)
        settingKey?.let { key ->
            intent.putExtra("focusSetting", key)
                .putExtra("focusSection", destination)
                .putExtra("focusAdvanced", advanced)
            val sizeSection = Regex("^size(?:Collapsed|Expanded)(?:Width|Height)_(.+)$")
                .matchEntire(key)?.groupValues?.getOrNull(1)
            if (sizeSection != null && !prefs.getBoolean("customSize_$sizeSection", false)) {
                // A direct request to edit a dimension should activate that custom layout,
                // otherwise the newly edited value would appear to do nothing.
                prefs.edit().putBoolean("customSize_$sizeSection", true).apply()
                intent.putExtra("searchEnabledCustomSize", true)
            }
        }
        startActivity(intent)
    }

    private fun chooseHomeTheme() {
        val modes = listOf("system", "light", "dark", "amoled")
        val labels = listOf("Auto · Follow device", "Light", "Dark", "AMOLED black")
        val selected = modes.indexOf(appTheme()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("App theme")
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                val next = modes[which]
                if (appTheme() != next) {
                    prefs.edit().putString("appTheme", next).apply()
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val settingSearchIndex: List<SearchSetting> by lazy {
        val entries = mutableListOf<SearchSetting>()
        fun add(
            section: String,
            label: String,
            key: String,
            defaultValue: Any,
            keywords: String = "",
            advanced: Boolean = false,
            resetKeys: Set<String> = setOf(key)
        ) {
            entries += SearchSetting(label, section, key, defaultValue, keywords, advanced, resetKeys)
        }
        fun addSizing(section: String, label: String, cw: Int, ch: Int, ew: Int, eh: Int, owner: String = section) {
            add(owner, "Use custom sizes for $label", "customSize_$section", false, "layout dimensions", true)
            add(owner, "$label collapsed width", "sizeCollapsedWidth_$section", cw, "size width", true)
            add(owner, "$label collapsed height", "sizeCollapsedHeight_$section", ch, "size height", true)
            add(owner, "$label expanded width", "sizeExpandedWidth_$section", ew, "size width", true)
            add(owner, "$label expanded height", "sizeExpandedHeight_$section", eh, "size height", true)
            add(owner, "$label icon size", "sectionIconScale_$section", 100, "scale", true)
            add(owner, "$label text size", "sectionTextScale_$section", 100, "scale typography", true)
        }

        add("setup", "Automatic service repair", "autoRepairServices", false, "keep alive recovery reconnect")
        add("setup", "Keep-running notification", "keepNotif", true, "ongoing notification service")

        add("pill", "Haptic feedback", "haptics", true, "vibration touch")
        add("pill", "Hide in full-screen apps", "hideFullscreen", false, "games video immersive")
        add("pill", "Night schedule", "nightHide", false, "hide schedule hours")
        add("pill", "Night schedule start", "nightFrom", 22, "hour start", true)
        add("pill", "Night schedule end", "nightTo", 7, "hour end", true)
        add("pill", "Concurrent activity bubble", "splitActivityOn", false, "second pill activity")
        CapsulePrefs.gestureKeys.forEach { key ->
            add("pill", "${CapsulePrefs.gestureLabels[key]} gesture", key, CapsulePrefs.defaultFor(key), "gesture action swipe tap")
        }
        add("pill", "Collapsed pill width", "miniW", 100, "size", true)
        add("pill", "Collapsed pill height", "miniH", 40, "size", true)
        add("pill", "Expanded pill width", "expW", 340, "size", true)
        add("pill", "Vertical pill position", "topY", 0, "placement offset", true)
        add("pill", "Landscape vertical pill position", "topYL", 0, "placement offset", true)
        add("pill", "Horizontal pill shift", "offX", 0, "placement offset", true)
        add("pill", "Landscape horizontal pill shift", "offXL", 0, "placement offset", true)
        add("pill", "Expand from status bar", "expandFromStatusBar", true, "top anchor alignment", true)

        add("music", "Show media player", "showMusic", true, "music playback")
        add("music", "Audio visualizer", "visualizer", false, "microphone sound")
        add("music", "Collapsed media indicator", "collapsedMediaIndicator", "waveform", "waveform bars dots pulse", false,
            setOf("collapsedMediaIndicator", "miniMediaWaveform"))
        add("music", "Expanded artwork and controls layout", "mediaReferenceLayout", true, "album art progress controls")
        add("music", "Visualizer reacts to notifications and calls", "vizNotif", true, "bars activity")
        add("music", "Collapsed media width", "mediaMiniW", 280, "size", true)
        add("music", "Expanded media width", "mediaExpandedW", 380, "size", true)
        add("music", "Expanded media height", "expH_music", 220, "size", true)
        add("music", "Visualizer style", "vizStyle", "bars", "bars mirror dots wave")
        addSizing("media", "Media", 280, 40, 380, 220)

        add("tasks", "Show tasks when the pill is idle", "taskPreviewIdleEnabled", true, "idle preview", false,
            setOf("taskPreviewIdleEnabled", "taskPreviewEnabled"))
        add("tasks", "Task preview list", "taskPreviewList", CapsuleTaskStore.ALL_LISTS, "upcoming today starred")
        add("tasks", "Swipe to browse task preview", "taskPreviewSwipe", true, "gesture")
        add("tasks", "Tasks shown in the pill", "taskPreviewLimit", 3, "count", true)
        add("tasks", "Show completed tasks", "taskShowCompleted", false, "done complete")
        add("tasks", "Task manager filter", "taskFilter", CapsuleTaskStore.ALL_LISTS, "list filter")
        addSizing("tasks", "Tasks and idle pill", 100, 40, 340, 160)

        add("notifications", "Expand for new notifications", "notifAutoExpand", true, "arrival")
        add("notifications", "Auto-collapse notifications", "notifAutoCollapse", true, "timeout")
        add("notifications", "Notification collapse delay", "notifSeconds", 4, "seconds duration", true)
        add("notifications", "Notification queue theme", "notificationQueueTheme", "blend", "transparent one ui iphone pixel nothing")
        add("notifications", "Notification count position", "notificationBadgeCorner", "top_end", "badge corner right left", false)
        add("notifications", "Notification count size", "notificationBadgeSize", CapsulePrefs.notificationBadgeSizeDefault, "badge count dot size", true)
        add("notifications", "Notification count color", "notificationBadgeColor", "theme", "badge color blue violet green amber red graphite", false)
        add("notifications", "Other notifications", "showNotifs", true, "show hide")
        add("notifications", "Phone events", "showEvents", true, "unlock bluetooth headphones")
        add("notifications", "Downloads and progress", "showDownloads", true, "transfer")
        add("notifications", "Live timers and stopwatch", "liveActivities", true, "timer live activity")
        add("notifications", "Activity priority", "activityPriorityOrder", "live,notifications,media,navigation,charging", "order ranking", true)
        add("notifications", "Live activity app allow-list", "liveActivityApps", "", "package filter", true)
        add("notifications", "Driving mode", "drivingMode", false, "maps navigation")
        add("notifications", "Use app accent for notification title", "appAccent", true, "color")
        add("notifications", "Quick reply buttons", "replyChips", true, "yes later busy actions")
        add("notifications", "Quick reply text", "replies", "Yes,Later,Busy", "reply messages", true)
        add("notifications", "Show raw Maps text", "debugMaps", false, "debug")
        add("notifications", "Expanded notification height", "expH_notif", 220, "size", true)
        addSizing("notifications", "Notifications", 260, 40, 340, 220)
        addSizing("calls", "Calls", 240, 40, 340, 144, "notifications")

        add("maps", "Show Maps navigation", "showMaps", true, "navigation route")
        add("maps", "Show Maps details", "mapsInfo", true, "route distance arrival")
        add("maps", "Tint ETA by traffic", "mapsTrafficColors", true, "green amber red traffic color")
        add("maps", "Expanded Maps layout", "mapsExpandedLayout", "direction_card", "direction card compact route", false,
            setOf("mapsExpandedLayout", "mapsReferenceLayout"))
        add("maps", "Use Maps turn-arrow artwork", "mapsOwnArrow", true, "direction icon")
        addSizing("maps", "Maps", 240, 40, 360, 112)

        add("pinned", "Enable pinned apps", "pinnedOn", true, "app shortcuts")
        add("pinned", "Pinned app icon size", "pinnedIconSize", 32, "shortcut dp", true)
        add("pinned", "Pinned app grid rows", "pinnedRows", 2, "shortcut grid", true)

        add("charging", "Show charging status", "showCharging", true, "battery")
        add("charging", "Charging alerts", "chargeAlert", true, "battery full")
        add("charging", "Charging alert threshold", "chargeAlertAt", 80, "battery percent", true)
        add("charging", "Charging expanded height", "expH_charge", 64, "size", true)
        add("charging", "Collapsed charging width", "chargeMiniW", 116, "size", true)
        add("charging", "Charging icon", "chargeIconShape", 9, "battery icon ring scallop")
        add("charging", "Charging icon style", "chargeIconStyle", 0, "liquid segmented pulse", true)
        add("charging", "Charging ring size", "chargeRingSize", 48, "diameter", true)
        add("charging", "Charging ring thickness", "chargeRingThickness", 3, "stroke", true)
        add("charging", "Uncharged arc color", "chargeRemainderColor", 0, "remainder gray", true)
        add("charging", "Uncharged arc thickness", "chargeRemainderThickness", 2, "remainder stroke", true)
        add("charging", "Collapsed charging text size", "chargePctTextSize", 13, "percentage watts sp", true)
        add("charging", "Collapsed charging readout", "chargeMiniReadout", "percent", "percentage watts both")
        add("charging", "Circular Wave spacing", "chargeWaveSpacingDp", 15, "peaks density", true, setOf("chargeWaveSpacingDp", "chargeRingDensity"))
        add("charging", "Other charging icon speed", "chargeRingSpeed", 5, "animation slow fast", true)
        add("charging", "Circular Wave speed", "chargeWaveSpeed", 29, "animation slow fast 24 seconds", true)
        add("charging", "Circular Wave height", "chargeWaveHeight", 9, "wave peaks", true)
        add("charging", "Circular Wave inner spacing", "chargeWaveInnerSpacingPercent", 28, "wave valleys", true)
        add("charging", "Circular Wave edge softness", "chargeWaveSoftness", 55, "rounded smooth", true)
        add("charging", "Charging animation", "chargeMotionEnabled", true, "animate motion", true)
        addSizing("charging", "Charging", 116, 40, 340, 64)

        add("look", "Settings app theme", "appTheme", "dark", "light dark amoled auto")
        add("look", "Pill theme", "theme", "capsule", "one ui iphone pixel nothing colors")
        add("look", "Font", "fontFamily", "default", "typography text")
        add("look", "Pill expansion animation", "pillAnimationStyle", "continuity", "liquid morph spring settling", false,
            setOf("pillAnimationStyle", "springMotion"))
        add("look", "Show pill on lock screen", "showOnLockScreen", true, "lock screen")
        add("look", "Hide details while locked", "lockScreenPrivacy", true, "privacy lock screen")
        add("look", "Pill text size", "textScale", 100, "typography scale", true)
        add("look", "Motion preset", "motionPreset", "fluid", "spring smooth fast", true,
            setOf("motionPreset", "springSpeed", "springMotion"))
        add("look", "Reduce motion", "reduceMotion", false, "accessibility animation", true)
        add("look", "Frosted glass", "blur", false, "translucent")
        add("look", "Custom image widget", "imageWidget", false, "wallpaper image")
        add("look", "Custom image height", "imageHeight", 120, "image size", true)

        add("privacy", "On-device notification history", "historyOn", false, "local history")
        add("privacy", "Hidden notification apps", "blockedApps", "", "hide package filter")
        entries
    }

    private fun sectionSearchTitle(section: String): String = titles[section] ?: section.replace('_', ' ')

    private fun settingValue(key: String, defaultValue: Any): Any = when (defaultValue) {
        is Boolean -> prefs.getBoolean(key, defaultValue)
        is Int -> prefs.getInt(key, defaultValue)
        is String -> prefs.getString(key, defaultValue) ?: defaultValue
        else -> defaultValue
    }

    private fun settingIsCustomized(setting: SearchSetting): Boolean {
        val value = runCatching {
            when (setting.key) {
                "collapsedMediaIndicator" -> prefs.getString("collapsedMediaIndicator", null)
                    ?: if (prefs.getBoolean("miniMediaWaveform", true)) "waveform" else "off"
                "taskPreviewIdleEnabled" -> prefs.getBoolean("taskPreviewIdleEnabled", prefs.getBoolean("taskPreviewEnabled", true))
                "mapsExpandedLayout" -> prefs.getString("mapsExpandedLayout", null)
                    ?: if (prefs.getBoolean("mapsReferenceLayout", true)) "direction_card" else "compact_row"
                "motionPreset" -> prefs.getString("motionPreset", null)?.takeIf { it in setOf("fluid", "smooth", "fast") }
                    ?: when (prefs.getString("springSpeed", "medium")) { "slow" -> "smooth"; "fast" -> "fast"; else -> "fluid" }
                else -> settingValue(setting.key, setting.defaultValue)
            }
        }.getOrElse { return true }
        return value != setting.defaultValue
    }

    private fun isSectionCustomized(section: String): Boolean {
        if (section == "apps_tasks") return isSectionCustomized("tasks") || isSectionCustomized("pinned")
        return settingSearchIndex.any { it.section == section && settingIsCustomized(it) }
    }

    private fun homeCustomizationSection(destination: String): String? = when (destination) {
        "more" -> null
        "apps_tasks" -> "apps_tasks"
        "quick" -> "pinned"
        "look" -> "look"
        else -> destination
    }

    private fun refreshHomeCustomizedBadges() {
        homeCustomizationBadges.forEach { (destination, badge) ->
            val section = homeCustomizationSection(destination) ?: return@forEach
            val customized = isSectionCustomized(section)
            badge.visibility = if (customized) View.VISIBLE else View.GONE
            homeCustomizationTiles[destination]?.contentDescription = buildString {
                append(homeCustomizationTiles[destination]?.tag as? String ?: destination)
                if (customized) append(". Customized")
            }
        }
    }

    private fun addSectionResetButton(parent: LinearLayout, section: String, label: String = "Reset this section") {
        button(parent, label) { resetSection(section) }
    }

    private fun resetSection(section: String) {
        val keys = settingSearchIndex.asSequence()
            .filter { it.section == section }
            .flatMap { it.resetKeys.asSequence() }
            .toMutableSet()
        keys += "advancedExpanded_$section"
        AlertDialog.Builder(this)
            .setTitle("Reset ${sectionSearchTitle(section)} settings?")
            .setMessage("This restores this section's options to their defaults. Pinned app choices, task lists and tasks, notification history, and charging session history are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset section") { _, _ ->
                prefs.edit().apply { keys.forEach { remove(it) } }.apply()
                recreate()
            }
            .show()
    }

    private fun showFeatureSearch() {
        val searchableSettings = settingSearchIndex.filterNot {
            it.key == "chargeIconStyle" && !supportsChargingStyle(prefs.getInt("chargeIconShape", 9).coerceIn(0, 11))
        }
        val sections = listOf(
            "Pill" to "pill", "Media" to "music", "Notifications" to "notifications",
            "Maps" to "maps", "Charging" to "charging", "Apps & Tasks" to "apps_tasks",
            "Tasks" to "tasks", "Pinned apps" to "pinned", "Setup and recovery" to "setup",
            "Diagnostic log" to "diagnostics", "Appearance and motion" to "look",
            "Privacy" to "privacy", "Tools and backup" to "tools",
            "About & What's New" to "about"
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(10))
        }
        val query = EditText(this).apply {
            hint = "Search settings and features"
            setSingleLine(true)
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
            contentDescription = "Search app settings"
        }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            addView(results)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(340)
            )
        }
        var searchDialog: AlertDialog? = null
        fun refreshResults(raw: String) {
            results.removeAllViews()
            val matchingSettings = if (raw.isBlank()) emptyList() else searchableSettings.filter { entry ->
                entry.label.contains(raw, true) || entry.keywords.contains(raw, true) ||
                    sectionSearchTitle(entry.section).contains(raw, true)
            }
            matchingSettings.forEach { entry ->
                results.addView(styledButton().apply {
                        val sizeMatch = Regex("^size(?:Collapsed|Expanded)(?:Width|Height)_(.+)$").matchEntire(entry.key)
                        val customSizeNeeded = sizeMatch?.groupValues?.getOrNull(1)?.let { !prefs.getBoolean("customSize_$it", false) } == true
                    text = buildString {
                        append(entry.label).append('\n').append(sectionSearchTitle(entry.section))
                        if (entry.advanced) append(" · Advanced opens")
                        if (customSizeNeeded) append(" · enables custom sizing")
                        if (isSectionCustomized(entry.section)) append(" · Customized")
                    }
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        searchDialog?.dismiss()
                        open(entry.section, entry.key, entry.advanced)
                    }
                })
            }
            val matchingSections = sections.filter { (label, id) ->
                raw.isBlank() || label.contains(raw, true) ||
                    (sectionDescriptions[id] ?: "").contains(raw, true)
            }
            matchingSections.forEach { (label, id) ->
                results.addView(styledButton().apply {
                    text = "$label\n${sectionDescriptions[id] ?: "Open $label settings"}"
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        searchDialog?.dismiss()
                        open(id)
                    }
                })
            }
            if (matchingSettings.isEmpty() && matchingSections.isEmpty()) results.addView(TextView(this).apply {
                text = "No matching settings"
                textSize = 14f
                setTextColor(secondaryTextColor())
                setPadding(dp(6), dp(12), dp(6), dp(12))
            })
        }
        query.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshResults(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        refreshResults("")
        root.addView(query)
        root.addView(scroll)
        searchDialog = AlertDialog.Builder(this)
            .setTitle("Search Capsule")
            .setView(root)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun sectionPreview(section: String): View? {
        if (section !in setOf("music", "maps", "charging", "notifications", "pinned", "quick", "tasks", "pill", "apps_tasks")) return null
        val palette = previewPillPalette()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isLightAppTheme()) intArrayOf(Color.WHITE, 0xFFF4F7FB.toInt())
                else if (appTheme() == "amoled") intArrayOf(Color.BLACK, 0xFF050505.toInt())
                else intArrayOf(0xFF1B2938.toInt(), 0xFF121B26.toInt())).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD6E0EA.toInt() else 0xFF2C4052.toInt())
            }
            setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            addView(TextView(this@MainActivity).apply {
                text = "SECTION PREVIEW"
                textSize = 10f
                letterSpacing = 0.1f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(if (isLightAppTheme()) 0xFF087F6A.toInt() else 0xFF6CE5B2.toInt())
            })
        }
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.top, palette.bottom)).apply {
                cornerRadius = dp(palette.cornerDp).toFloat()
                setStroke(dp(1), palette.outline)
            }
            minimumHeight = dp(if (section == "music") 82 else 72)
        }
        fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        when (section) {
            "music" -> {
                val art = TextView(this).apply {
                    text = "♪"
                    textSize = 21f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF657EA5.toInt(), 0xFF26384F.toInt())).apply { shape = GradientDrawable.OVAL }
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                }
                surface.addView(art)
                surface.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(11); marginEnd = dp(5)
                    }
                    addView(label("Midnight Drive", 13f, palette.primary, true))
                    addView(label("Artist name", 11f, palette.secondary))
                })
                val waveform = TextView(this).apply {
                    text = "▁▅▇▅▂"
                    textSize = 16f
                    setTextColor(palette.accent)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(43), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                surface.addView(waveform)
                surface.addView(label("Ⅱ", 18f, palette.primary, true).apply {
                    gravity = android.view.Gravity.CENTER
                    contentDescription = "Media preview with play and pause control"
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(40))
                })
            }
            "maps" -> {
                surface.addView(label("↱", 32f, palette.accent, true).apply {
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                })
                surface.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                    addView(label("Turn right in 250 m", 14f, palette.primary, true))
                    addView(label("Oak Street · 12 min · 4.8 km", 11f, palette.secondary))
                })
            }
            "charging" -> {
                surface.addView(label("◌ϟ", 31f, palette.accent, true).apply {
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(54), dp(48))
                })
                surface.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
                    addView(label("82%", 20f, palette.primary, true))
                    addView(label("Charging · about 24 min to full", 11f, palette.secondary))
                })
            }
            "notifications" -> {
                surface.addView(label("●", 27f, palette.accent, true).apply {
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                })
                surface.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
                    addView(label("Alex · now", 13f, palette.primary, true))
                    addView(label("New message preview", 11f, palette.secondary))
                })
            }
            "pinned", "quick" -> {
                listOf("Maps", "Music", "Tasks").forEach { appName ->
                    surface.addView(label(appName, 12f, palette.primary, true).apply {
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    })
                }
            }
            "tasks" -> {
                surface.orientation = LinearLayout.VERTICAL
                surface.addView(label("Today · 2 tasks", 13f, palette.primary, true))
                surface.addView(label("○  Review project\n○  Pick up parcel", 11f, palette.secondary).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            "apps_tasks" -> {
                surface.addView(label("▦", 28f, palette.accent, true).apply {
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                })
                surface.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                    addView(label("Pinned apps · Tasks", 14f, palette.primary, true))
                    addView(label("Shortcuts and local lists", 11f, palette.secondary))
                })
            }
            else -> {
                surface.addView(label("Capsule Lite", 13f, palette.secondary))
                surface.addView(label("Live activity preview", 14f, palette.primary, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
                })
            }
        }
        outer.addView(surface, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        return outer
    }

    private fun sectionSizing(
        section: String,
        label: String,
        collapsedWidth: Int,
        collapsedHeight: Int,
        expandedWidth: Int,
        expandedHeight: Int,
        parent: LinearLayout = col,
        onExpandedHeightCommitted: ((Int) -> Unit)? = null
    ) {
        val group = card("$label size and layout", parent)
        val customKey = "customSize_$section"
        val sizeDefaults = mapOf(
            "sizeCollapsedWidth_$section" to collapsedWidth,
            "sizeCollapsedHeight_$section" to collapsedHeight,
            "sizeExpandedWidth_$section" to expandedWidth,
            "sizeExpandedHeight_$section" to if (section == "charging") {
                prefs.getInt("expH_charge", expandedHeight)
            } else expandedHeight
        )
        val missingDefaults = sizeDefaults.filterKeys { !prefs.contains(it) }
        if (missingDefaults.isNotEmpty()) {
            prefs.edit().apply { missingDefaults.forEach { (key, value) -> putInt(key, value) } }.apply()
        }
        val customControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.getBoolean(customKey, false)) View.VISIBLE else View.GONE
        }
        toggle(group, "Use custom sizes for $label", customKey, false) { enabled ->
            customControls.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        customControls.addView(TextView(this).apply {
            text = "Sizes are in dp. Expanded width stays wider than collapsed when screen space allows. Content can keep a safe minimum height."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(6))
        })
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val availableWidthDp = (screenWidthDp - 24).coerceAtLeast(180)
        val collapsedWidthMax = minOf(440, (availableWidthDp - 16).coerceAtLeast(104))
        val expandedWidthMax = minOf(520, availableWidthDp)
        val collapsedWidthKey = "sizeCollapsedWidth_$section"
        val expandedWidthKey = "sizeExpandedWidth_$section"
        fun expandedWidthMinimum(collapsed: Int) =
            minOf(expandedWidthMax, maxOf(180, collapsed + 16))

        var expandedWidthControl: SliderHandle? = null
        val collapsedWidthControl = slider(
            customControls, "Collapsed width (dp)", collapsedWidthKey, 104, collapsedWidthMax, collapsedWidth,
            onValueChanged = { value -> expandedWidthControl?.setMinimum?.invoke(expandedWidthMinimum(value)) },
            onValueCommitted = { value ->
                val editor = prefs.edit().putInt(collapsedWidthKey, value)
                expandedWidthControl?.writeTo?.invoke(editor)
                editor.apply()
            }
        )
        slider(customControls, "Collapsed height (dp)", "sizeCollapsedHeight_$section", 28, 96, collapsedHeight)
        expandedWidthControl = slider(
            customControls, "Expanded width (dp)", expandedWidthKey,
            expandedWidthMinimum(collapsedWidthControl.currentValue()), expandedWidthMax, expandedWidth
        )
        if (onExpandedHeightCommitted == null) {
            slider(customControls, "Expanded height (dp)", "sizeExpandedHeight_$section", 64, 440, expandedHeight)
        } else {
            slider(
                customControls, "Expanded height (dp)", "sizeExpandedHeight_$section", 64, 440, expandedHeight,
                onValueCommitted = { value ->
                    prefs.edit()
                        .putInt("sizeExpandedHeight_$section", value)
                        .putInt("expH_$section", value)
                        .apply()
                    onExpandedHeightCommitted(value)
                }
            )
        }
        collapsedWidthControl.setMinimum(104)
        group.addView(customControls)
        slider(group, "Icon size (%)", "sectionIconScale_$section", 70, 160, 100)
        slider(group, "Text size (%)", "sectionTextScale_$section", 80, 160, 100)
    }

    /** Keeps uncommon, precise controls available without making the first view of a page dense. */
    private fun advancedGroup(sectionKey: String): LinearLayout {
        val group = card("Advanced")
        val expandedKey = "advancedExpanded_$sectionKey"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.getBoolean(expandedKey, false) ||
                (intent.getBooleanExtra("focusAdvanced", false) && intent.getStringExtra("focusSection") == sectionKey)
            ) View.VISIBLE else View.GONE
        }
        if (intent.getBooleanExtra("focusAdvanced", false) && intent.getStringExtra("focusSection") == sectionKey) {
            prefs.edit().putBoolean(expandedKey, true).apply()
        }
        val disclosure = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0x183C4855 else 0x20FFFFFF),
                null,
                GradientDrawable().apply { setColor(0xFF777777.toInt()); cornerRadius = dp(8).toFloat() }
            )
        }
        val label = TextView(this).apply {
            text = if (content.visibility == View.VISIBLE) "Hide fine-tuning controls" else "Show fine-tuning controls"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2), 0, 0, 0)
        }
        val indicator = TextView(this).apply {
            text = if (content.visibility == View.VISIBLE) "⌃" else "⌄"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(secondaryTextColor())
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(44))
        }
        disclosure.contentDescription = label.text.toString()
        disclosure.addView(label)
        disclosure.addView(indicator)
        disclosure.setOnClickListener {
            val expanded = content.visibility != View.VISIBLE
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(expandedKey, expanded).apply()
            label.text = if (expanded) "Hide fine-tuning controls" else "Show fine-tuning controls"
            indicator.text = if (expanded) "⌃" else "⌄"
            disclosure.contentDescription = label.text.toString()
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        headerRow.addView(disclosure, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(TextView(this).apply {
            text = "Reset"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (isLightAppTheme()) 0xFF536273.toInt() else 0xFFB6C2CE.toInt())
            gravity = android.view.Gravity.CENTER
            minWidth = dp(60)
            minHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = "Reset ${sectionSearchTitle(sectionKey)} settings"
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0x183C4855 else 0x20FFFFFF),
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isLightAppTheme()) 0xFFF1F3F6.toInt() else 0xFF20262D.toInt())
                },
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF777777.toInt()) }
            )
            setOnClickListener { resetSection(sectionKey) }
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(8) } })
        group.addView(headerRow)
        group.addView(content)
        return content
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
                    KeepAlive.repairPending(this@MainActivity) -> "reconnecting services"
                    else -> "on"
                }).append('\n')
                append("Next step: ").append(when {
                    !Settings.canDrawOverlays(this@MainActivity) -> "allow Display over other apps"
                    !listenerEnabled -> "enable Notification access"
                    prefs.getBoolean("autoRepairServices", false) && KeepAlive.canSelfRepair(this@MainActivity) &&
                        (!listenerConnected || !accessibilityConnected) -> "automatic reconnect is running; refresh this page shortly"
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

    @Suppress("DEPRECATION")
    private fun installedBuildLabel(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "Version ${info.versionName ?: "unknown"} · build $code · run ${BuildConfig.BUILD_ID}"
    }.getOrDefault("Version unavailable")

    @Suppress("DEPRECATION")
    private fun installedBuildShortLabel(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "v${info.versionName ?: "unknown"} · build $code"
    }.getOrDefault("Build details")

    private fun buildAbout() {
        val installed = card("Installed APK")
        installed.addView(TextView(this).apply {
            text = "Dynamic Island Project\n${installedBuildLabel()}\nPackage: $packageName"
            textSize = 14f
            setTextColor(primaryTextColor())
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(2), dp(2), dp(2), dp(6))
        })
        button(installed, "Copy build details") {
            getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                android.content.ClipData.newPlainText("Dynamic Island Project build", installedBuildLabel())
            )
            android.widget.Toast.makeText(this, "Build details copied", android.widget.Toast.LENGTH_SHORT).show()
        }

        val notes = card("Release notes")
        notes.addView(TextView(this).apply {
            text = getString(R.string.release_notes_1_4_20)
            textSize = 13f
            setTextColor(primaryTextColor())
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        })
    }

    // Keep the main screen focused on the six sections used most often.
    private fun buildHome() {
        col.addView(TextView(this).apply {
            text = "Settings"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
            setPadding(dp(4), dp(4), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        })
        homeTile("Pill", "Size, position, gestures and behaviour", "pill")
        homeTile("Media", "Playback controls, visualizer and layout", "music")
        homeTile("Notifications", "Events, display and quick replies", "notifications")
        homeTile("Maps", "Route details and expanded layout", "maps")
        homeTile("Charging", "Battery animation, alerts and layout", "charging")
        homeTile("Apps & Tasks", "Pinned shortcuts and your local task lists", "apps_tasks")

        val moreExpanded = prefs.getBoolean("homeMoreExpanded", false)
        val moreContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            visibility = if (moreExpanded) View.VISIBLE else View.GONE
        }
        homeTile(
            "More", "Setup, appearance, privacy and backup", "more",
            showDivider = false,
            onClick = { tile, chevron ->
                val next = moreContent.visibility != View.VISIBLE
                prefs.edit().putBoolean("homeMoreExpanded", next).apply()
                moreContent.visibility = if (next) View.VISIBLE else View.GONE
                chevron.text = if (next) "⌄" else "›"
                chevron.contentDescription = if (next) "Collapse more settings" else "Expand more settings"
                tile.contentDescription = if (next) "More settings. Expanded." else "More settings. Collapsed."
            }
        )
        col.addView(moreContent)
        homeTile("Setup and recovery", "Permissions, service status and troubleshooting", "setup", parent = moreContent)
        homeTile("Appearance", "App theme, pill style and motion", "look", parent = moreContent)
        homeTile("Privacy", "History and hidden apps", "privacy", parent = moreContent)
        homeTile("Tools and backup", "Utilities, import and export", "tools", parent = moreContent)
        homeTile("About & What's New", installedBuildShortLabel(), "about", parent = moreContent)
    }

    private fun homeTile(
        title: String,
        subtitle: String,
        destination: String,
        parent: LinearLayout = col,
        showDivider: Boolean = true,
        onClick: ((LinearLayout, TextView) -> Unit)? = null
    ) {
        val iconColor = when (destination) {
            "music" -> 0xFF7654C8.toInt()
            "maps" -> 0xFF1677D2.toInt()
            "charging" -> 0xFF168A58.toInt()
            "notifications" -> 0xFFD16C24.toInt()
            "tasks", "pinned", "apps_tasks" -> 0xFF5367B8.toInt()
            else -> if (isLightAppTheme()) 0xFF287A68.toInt() else 0xFF7ED4B5.toInt()
        }
        val iconResource = when (destination) {
            "setup" -> R.drawable.ic_cat_setup
            "pill" -> R.drawable.ic_cat_pill
            "music" -> R.drawable.ic_cat_music
            "apps_tasks" -> R.drawable.ic_cat_apps
            "tasks" -> R.drawable.ic_cat_tasks
            "notifications" -> R.drawable.ic_cat_notifications
            "maps" -> R.drawable.ic_cat_maps
            "pinned" -> R.drawable.ic_cat_apps
            "charging" -> R.drawable.ic_cat_charging
            "look" -> R.drawable.ic_cat_appearance
            "privacy" -> R.drawable.ic_cat_privacy
            else -> R.drawable.ic_cat_tools
        }
        val tintedSurface = Color.argb(24, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(11), dp(4), dp(11))
            minimumHeight = dp(70)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            contentDescription = if (destination == "more") {
                "More settings. ${if (prefs.getBoolean("homeMoreExpanded", false)) "Expanded" else "Collapsed"}."
            } else "$title. $subtitle${if (homeCustomizationSection(destination)?.let(::isSectionCustomized) == true) ". Customized" else ""}"
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0x183C4855 else 0x20FFFFFF),
                null,
                GradientDrawable().apply { setColor(0xFF777777.toInt()); cornerRadius = dp(8).toFloat() }
            )
        }
        tile.addView(android.widget.ImageView(this).apply {
            setImageResource(iconResource)
            imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tintedSurface)
            }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            contentDescription = null
        })
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(13)
                marginEnd = dp(6)
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(primaryTextColor())
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Customized"
                    textSize = 10f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(if (isLightAppTheme()) 0xFF287A68.toInt() else 0xFF9BB8AC.toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = dp(9).toFloat()
                        setColor(if (isLightAppTheme()) 0xFFE6F2EE.toInt() else 0xFF1B3029.toInt())
                    }
                    setPadding(dp(7), dp(3), dp(7), dp(3))
                    visibility = if (destination != "more" && homeCustomizationSection(destination)?.let(::isSectionCustomized) == true) View.VISIBLE else View.GONE
                    contentDescription = "Section has settings changed from defaults"
                    if (destination != "more") homeCustomizationBadges[destination] = this
                }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) } })
            })
            addView(TextView(this@MainActivity).apply {
                textSize = 12.5f
                setTextColor(secondaryTextColor())
                setPadding(0, dp(3), 0, 0)
                text = subtitle
            })
        }
        tile.addView(titleColumn)
        if (destination != "more") {
            tile.tag = "$title. $subtitle"
            homeCustomizationTiles[destination] = tile
            homeCustomizationBadges[destination]?.tag = title
        }
        val chevron = TextView(this).apply {
            text = if (destination == "more" && prefs.getBoolean("homeMoreExpanded", false)) "⌄" else "›"
            textSize = 24f
            setTextColor(if (isLightAppTheme()) 0xFF64748B.toInt() else 0xFF8CA2B8.toInt())
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(44))
        }
        tile.addView(chevron)
        tile.setOnClickListener { if (onClick != null) onClick(tile, chevron) else open(destination) }
        parent.addView(tile)
        if (showDivider) parent.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(if (isLightAppTheme()) 0xFFDDE2E8.toInt() else if (appTheme() == "amoled") 0xFF242424.toInt() else 0xFF2A3541.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                leftMargin = dp(52)
            }
        })
    }

    private fun buildAppsTasks() {
        val apps = card("Pinned apps")
        apps.addView(TextView(this).apply {
            text = "Choose installed apps and tune the shortcut grid shown from the pill."
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        button(apps, "Manage pinned apps") { open("pinned") }
        addSectionResetButton(apps, "pinned", "Reset pinned-app settings")

        val tasks = card("Tasks")
        tasks.addView(TextView(this).apply {
            text = "Create local task lists and choose what appears when the pill is idle."
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        button(tasks, "Open task manager") { open("tasks") }
        addSectionResetButton(tasks, "tasks", "Reset task settings")
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
        addSectionResetButton(health, "setup")

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

        val gestures = card("Gestures (tap a row to change it)")
        gestures.addView(TextView(this).apply {
            text = "Each row shows its current action. Tap it to choose another. The notification companion dismisses with a horizontal swipe; the activity companion switches when swiped toward the main pill."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(6))
        })
        for (key in CapsulePrefs.gestureKeys) gestureButton(gestures, key)

        val concurrent = card("Concurrent activity bubble")
        toggle(concurrent, "Show a second active activity beside the pill", "splitActivityOn", false)
        concurrent.addView(TextView(this).apply {
            text = "When two persistent activities are active, keep the higher-priority one in the main pill and show the other in a small connected bubble. Tap or swipe the bubble toward the pill to switch; swipe away to hide it for the current activity. Calls, timers, and notification counts keep their existing priority and placement."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(2))
        })
        combinedPillPreview()

        val advanced = advancedGroup("pill")
        val schedule = card("Night schedule", advanced)
        slider(schedule, "Night from (hour)", "nightFrom", 0, 23, 22)
        slider(schedule, "Night to (hour)", "nightTo", 0, 23, 7)
        val size = card("Size and position", advanced)
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val where = if (land) " (landscape)" else ""
        slider(size, "Collapsed width", "miniW", 40, 200, 100)
        slider(size, "Collapsed height", "miniH", 20, 60, 40)
        slider(size, "Expanded width", "expW", 150, 360, 340)
        slider(size, "Vertical position$where", if (land) "topYL" else "topY", 0, 40, 0)
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
        val style = previewPillPalette()
        val preview = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(156))
                .apply { topMargin = dp(4); bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(when {
                    isLightAppTheme() -> 0xFFF2F4F8.toInt()
                    appTheme() == "amoled" -> Color.BLACK
                    else -> 0xFF111116.toInt()
                })
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD6DDE6.toInt() else 0x33444455)
            }
            clipToPadding = true
            clipChildren = true
        }
        val status = TextView(this).apply {
            text = "9:41     ◇   5G   ▰"
            textSize = 10f
            setTextColor(if (isLightAppTheme()) 0xFF5D6B7B.toInt() else 0xFFB8B8C2.toInt())
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

    private fun combinedPillPreview(): View {
        val card = card("Combined three-pill preview")
        card.addView(TextView(this).apply {
            text = "Maps stays in the main pill, charging sits to its right, and the latest notification sits to its left. This preview uses your current pill style and Maps collapsed size; it does not change live activity settings."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(8))
        })

        val style = previewPillPalette()
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density)
            .toInt().coerceAtLeast(320)
        val minMainWidth = 104
        val maxMainWidth = (screenWidthDp - 24).coerceAtLeast(minMainWidth)
        val sideSlots = 112 + 80
        val mainSpace = (screenWidthDp - 20 - sideSlots).coerceAtLeast(minMainWidth)
        val customMapsSize = prefs.getBoolean("customSize_maps", false)
        val requestedMainWidth = if (customMapsSize) {
            prefs.getInt("sizeCollapsedWidth_maps", 240)
        } else 240
        val mainWidthDp = requestedMainWidth.coerceIn(minMainWidth, maxMainWidth).coerceAtMost(mainSpace)
        val requestedMainHeight = if (customMapsSize) {
            prefs.getInt("sizeCollapsedHeight_maps", prefs.getInt("miniH", 40))
        } else prefs.getInt("miniH", 40)
        val mainHeightDp = requestedMainHeight.coerceIn(28, 112)

        fun pillSurface(widthDp: Int, heightDp: Int, content: LinearLayout.() -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(style.top, style.bottom)).apply {
                cornerRadius = dp(style.cornerDp).toFloat()
                setStroke(dp(1), style.outline)
            }
            elevation = dp(3).toFloat()
            clipChildren = false
            clipToPadding = false
            content()
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
        }
        fun pillText(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val notification = pillSurface(112, 48) {
            addView(pillText("●", 15f, style.accent, true), LinearLayout.LayoutParams(dp(20), dp(28)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(pillText("New message", 9f, style.primary, true))
                addView(pillText("Latest · now", 8f, style.secondary))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            contentDescription = "Preview notification companion pill, 112 by 48 dp"
        }
        val main = pillSurface(mainWidthDp, mainHeightDp) {
            addView(pillText("↱", 19f, style.accent, true), LinearLayout.LayoutParams(dp(22), dp(30)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(pillText("Turn right", 10f, style.primary, true))
                addView(pillText("250 m", 9f, style.secondary))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            contentDescription = "Preview Maps main pill, $mainWidthDp by $mainHeightDp dp"
        }
        val charging = pillSurface(80, 48) {
            addView(pillText("◌ϟ", 17f, style.accent, true), LinearLayout.LayoutParams(dp(25), dp(30)))
            addView(pillText("82%", 10f, style.primary, true))
            contentDescription = "Preview charging companion pill, 80 by 48 dp"
        }
        val stage = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)).apply {
                marginStart = -dp(17)
                marginEnd = -dp(17)
            }
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, screenColors()).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFD6E0EA.toInt() else 0xFF2C4052.toInt())
            }
            clipChildren = false
            clipToPadding = true
        }
        stage.addView(TextView(this).apply {
            text = "9:41     ◇  5G  ▰"
            textSize = 10f
            setTextColor(if (isLightAppTheme()) 0xFF586779.toInt() else 0xFFB8C1CC.toInt())
            setPadding(dp(12), dp(5), dp(12), 0)
        }, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(22), android.view.Gravity.TOP
        ))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(notification, LinearLayout.LayoutParams(dp(112), dp(48)).apply { marginEnd = -dp(10) })
            addView(main, LinearLayout.LayoutParams(dp(mainWidthDp), dp(mainHeightDp)).apply { marginEnd = -dp(10) })
            addView(charging, LinearLayout.LayoutParams(dp(80), dp(48)))
        }
        stage.addView(row, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, dp(52), android.view.Gravity.CENTER
        ).apply { topMargin = dp(14) })
        card.addView(stage)
        card.addView(TextView(this).apply {
            text = "Maps $mainWidthDp × $mainHeightDp dp   ·   Charging 80 × 48 dp   ·   Notification 112 × 48 dp"
            textSize = 11f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(7), dp(2), 0)
        })
        return card
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
        collapsedMediaIndicatorButton(music)
        toggle(music, "Use expanded artwork, progress, and controls layout", "mediaReferenceLayout", true)
        toggle(music, "Bars react to notifications and calls", "vizNotif", true)
        val advanced = advancedGroup("music")
        val layout = card("Media sizing and visualizer", advanced)
        slider(layout, "Default collapsed media width (dp)", "mediaMiniW", 180, 420, 280)
        slider(layout, "Default expanded media width (dp)", "mediaExpandedW", 260, 520, 380)
        slider(layout, "Default expanded media height (dp)", "expH_music", 168, 320, 220)
        vizStyleButton(layout)
        sectionSizing("media", "Media", 280, 40, 380, 220, parent = advanced)
    }

    private fun collapsedMediaIndicatorButton(parent: LinearLayout) {
        val values = listOf("off", "waveform", "visualizer", "art_pulse")
        val labels = listOf("Off", "Mini waveform", "Audio visualizer", "Album-art pulse")
        fun current() = prefs.getString("collapsedMediaIndicator", null)
            ?.takeIf { it in values }
            ?: if (prefs.getBoolean("miniMediaWaveform", true)) "waveform" else "off"
        fun label(value: String) = "Collapsed media indicator: ${labels[values.indexOf(value).coerceAtLeast(0)]}"
        val button = styledButton().apply {
            text = label(current())
            tag = settingTag("collapsedMediaIndicator")
        }
        button.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Collapsed media indicator")
                .setSingleChoiceItems(labels.toTypedArray(), values.indexOf(current()).coerceAtLeast(0)) { dialog, which ->
                    prefs.edit().putString("collapsedMediaIndicator", values[which]).apply()
                    button.text = label(values[which])
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
        parent.addView(TextView(this).apply {
            text = "The mini waveform is Capsule's existing collapsed waveform. Audio visualizer uses the selected visualizer style; Album-art pulse adds a restrained scale and brightness breath only while music is playing. Reduced motion disables the pulse."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(3), dp(2), dp(7))
        })
    }

    private fun buildTasks() {
        val previewSettings = card("Pill task preview")
        toggle(previewSettings, "Show tasks when expanding an idle pill", "taskPreviewIdleEnabled",
            prefs.getBoolean("taskPreviewIdleEnabled", prefs.getBoolean("taskPreviewEnabled", true)))
        val listButton = styledButton().apply {
            text = "Preview list: ${prefs.getString("taskPreviewList", CapsuleTaskStore.ALL_LISTS) ?: CapsuleTaskStore.ALL_LISTS}"
            tag = settingTag("taskPreviewList")
        }
        listButton.setOnClickListener {
            val lists = (listOf(CapsuleTaskStore.ALL_LISTS, CapsuleTaskStore.TODAY, CapsuleTaskStore.UPCOMING, CapsuleTaskStore.STARRED) +
                CapsuleTaskStore.lists(prefs)).distinct()
            val current = prefs.getString("taskPreviewList", CapsuleTaskStore.ALL_LISTS) ?: CapsuleTaskStore.ALL_LISTS
            AlertDialog.Builder(this)
                .setTitle("Task preview list")
                .setItems(lists.toTypedArray()) { _, which ->
                    prefs.edit().putString("taskPreviewList", lists[which]).apply()
                    listButton.text = "Preview list: ${lists[which]}"
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        previewSettings.addView(listButton)
        toggle(previewSettings, "Swipe the pill preview to browse more tasks", "taskPreviewSwipe", true)

        val tools = card("Task lists")
        button(tools, "Create a task list") {
            val input = EditText(this).apply { hint = "List name"; setSingleLine(true) }
            AlertDialog.Builder(this).setTitle("New list").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create") { _, _ ->
                    if (!CapsuleTaskStore.addList(prefs, input.text.toString())) {
                        android.widget.Toast.makeText(this, "Use a unique list name", android.widget.Toast.LENGTH_SHORT).show()
                    } else recreate()
                }.show()
        }
        toggle(tools, "Show completed tasks", "taskShowCompleted", false)

        val tasksCard = card("Tasks")
        val filterButton = styledButton().apply { tag = settingTag("taskFilter") }
        fun currentFilter() = prefs.getString("taskFilter", CapsuleTaskStore.ALL_LISTS) ?: CapsuleTaskStore.ALL_LISTS
        fun updateFilterLabel() { filterButton.text = "List: ${currentFilter()}" }
        updateFilterLabel()
        var taskListView: LinearLayout? = null
        fun refresh() {
            val list = taskListView ?: return
            list.removeAllViews()
            val tasks = CapsuleTaskStore.filtered(prefs, currentFilter(), prefs.getBoolean("taskShowCompleted", false))
            if (tasks.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "Nothing here yet. Add a task or choose another list."
                    textSize = 13f
                    setTextColor(secondaryTextColor())
                    setPadding(dp(5), dp(8), dp(5), dp(8))
                })
            }
            tasks.forEach { task ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(2), dp(7), dp(2), dp(7))
                }
                row.addView(android.widget.CheckBox(this).apply {
                    isChecked = task.completed
                    contentDescription = "Mark ${task.title} ${if (task.completed) "active" else "complete"}"
                    setOnCheckedChangeListener { _, done ->
                        val all = CapsuleTaskStore.load(prefs).map { if (it.id == task.id) it.copy(completed = done) else it }
                        CapsuleTaskStore.save(prefs, all)
                        refresh()
                    }
                })
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showTaskEditor(task, ::refresh) }
                    addView(TextView(this@MainActivity).apply {
                        text = task.title + if (task.starred) "  ★" else ""
                        textSize = 14f
                        setTextColor(primaryTextColor())
                    })
                    val dueText = task.dueAtMillis?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
                    addView(TextView(this@MainActivity).apply {
                        text = listOfNotNull(task.listName, dueText, task.subtasks.takeIf { it.isNotEmpty() }?.let { "${it.count(CapsuleSubtask::completed)} / ${it.size} subtasks" }).joinToString(" · ")
                        textSize = 11f
                        setTextColor(secondaryTextColor())
                    })
                })
                val delete = TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    contentDescription = "Delete ${task.title}"
                    setTextColor(secondaryTextColor())
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    setOnClickListener {
                        CapsuleTaskStore.save(prefs, CapsuleTaskStore.load(prefs).filterNot { it.id == task.id })
                        refresh()
                    }
                }
                row.addView(delete)
                list.addView(row)
                list.addView(View(this).apply {
                    setBackgroundColor(if (isLightAppTheme()) 0xFFE2E8F0.toInt() else 0xFF263544.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }
        }
        filterButton.setOnClickListener {
            val lists = (listOf(CapsuleTaskStore.ALL_LISTS, CapsuleTaskStore.TODAY, CapsuleTaskStore.UPCOMING, CapsuleTaskStore.STARRED) +
                CapsuleTaskStore.lists(prefs)).distinct()
            AlertDialog.Builder(this).setTitle("Show tasks")
                .setItems(lists.toTypedArray()) { _, which ->
                    prefs.edit().putString("taskFilter", lists[which]).apply()
                    updateFilterLabel()
                    refresh()
                }.show()
        }
        tasksCard.addView(filterButton)
        button(tasksCard, "Add task") { showTaskEditor(null, ::refresh) }
        taskListView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tasksCard.addView(taskListView)
        refresh()

        val advanced = advancedGroup("tasks")
        val previewFine = card("Task preview tuning", advanced)
        slider(previewFine, "Tasks shown in the pill (1–8)", "taskPreviewLimit", 1, 8, 3)
        sectionSizing("tasks", "Tasks and idle pill", 100, 40, 340, 160, parent = advanced)
    }

    private fun showTaskEditor(existing: CapsuleTask?, onSaved: () -> Unit) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(2))
        }
        val title = EditText(this).apply { hint = "Task"; setSingleLine(true); setText(existing?.title.orEmpty()) }
        val details = EditText(this).apply { hint = "Details"; minLines = 2; setText(existing?.details.orEmpty()) }
        val listNames = CapsuleTaskStore.lists(prefs)
        val listSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listNames)
            setSelection(listNames.indexOf(existing?.listName ?: CapsuleTaskStore.DEFAULT_LIST).coerceAtLeast(0))
        }
        val starred = android.widget.CheckBox(this).apply { text = "Star this task"; isChecked = existing?.starred == true }
        val subtasks = EditText(this).apply {
            hint = "Subtasks (one per line)"
            minLines = 2
            setText(existing?.subtasks?.joinToString(System.lineSeparator()) { it.title }.orEmpty())
        }
        var dueAt = existing?.dueAtMillis
        val dueLabel = TextView(this).apply { textSize = 12f; setTextColor(secondaryTextColor()) }
        fun updateDueLabel() {
            dueLabel.text = dueAt?.let { "Due: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
                ?: "No due date"
        }
        updateDueLabel()
        val dueRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        button(dueRow, "Set date and time") {
            val initial = java.util.Calendar.getInstance().apply { timeInMillis = dueAt ?: System.currentTimeMillis() }
            android.app.DatePickerDialog(this, { _, year, month, day ->
                val selected = java.util.Calendar.getInstance().apply {
                    timeInMillis = dueAt ?: System.currentTimeMillis()
                    set(year, month, day)
                }
                android.app.TimePickerDialog(this, { _, hour, minute ->
                    selected.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    selected.set(java.util.Calendar.MINUTE, minute)
                    selected.set(java.util.Calendar.SECOND, 0)
                    dueAt = selected.timeInMillis
                    updateDueLabel()
                }, selected.get(java.util.Calendar.HOUR_OF_DAY), selected.get(java.util.Calendar.MINUTE), false).show()
            }, initial.get(java.util.Calendar.YEAR), initial.get(java.util.Calendar.MONTH), initial.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        dueRow.addView(dueLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        button(dueRow, "Clear") { dueAt = null; updateDueLabel() }
        form.addView(title)
        form.addView(details)
        form.addView(listSpinner)
        form.addView(starred)
        form.addView(dueRow)
        form.addView(subtasks)
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New task" else "Edit task")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        if (existing != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Delete", null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val taskTitle = title.text.toString().trim()
                if (taskTitle.isEmpty()) {
                    title.error = "Enter a task name"
                    return@setOnClickListener
                }
                val previousSubtasks = existing?.subtasks.orEmpty()
                val parsedSubtasks = subtasks.text.toString().lineSequence().map(String::trim).filter(String::isNotEmpty).map { label ->
                    CapsuleSubtask(label, previousSubtasks.firstOrNull { it.title == label }?.completed ?: false)
                }.toList()
                val newTask = CapsuleTask(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    title = taskTitle,
                    details = details.text.toString().trim(),
                    dueAtMillis = dueAt,
                    listName = listNames.getOrElse(listSpinner.selectedItemPosition) { CapsuleTaskStore.DEFAULT_LIST },
                    starred = starred.isChecked,
                    completed = existing?.completed ?: false,
                    subtasks = parsedSubtasks,
                    createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                )
                val all = CapsuleTaskStore.load(prefs).toMutableList()
                val index = all.indexOfFirst { it.id == newTask.id }
                if (index >= 0) all[index] = newTask else all.add(newTask)
                CapsuleTaskStore.save(prefs, all)
                dialog.dismiss()
                onSaved()
            }
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    CapsuleTaskStore.save(prefs, CapsuleTaskStore.load(prefs).filterNot { it.id == existing.id })
                    dialog.dismiss()
                    onSaved()
                }
            }
        }
        dialog.show()
    }

    private fun buildNotifications() {
        val auto = card("Auto expand")
        toggle(auto, "Expand the pill when a notification arrives", "notifAutoExpand", true)
        toggle(auto, "Collapse it automatically after a time", "notifAutoCollapse", true)

        val queueLook = card("Notification queue appearance")
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                val pill = previewPillPalette()
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
        notificationBadgeCornerButton(queueLook)
        notificationBadgeSizeSlider(queueLook, previewBadge)
        notificationBadgeColorButton(queueLook, previewCard, previewBadge)
        queueLook.addView(TextView(this).apply {
            text = "Style the count badge independently from the expanded queue card. Theme color follows the selected queue or pill accent; fixed colors stay the same across themes. Transparent blend removes the card fill so it reads as part of the pill."
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
        toggle(shown, "Phone events (unlock, headphones, Bluetooth)", "showEvents", true)
        toggle(shown, "Downloads and progress activities", "showDownloads", true)
        toggle(shown, "Live timer and stopwatch notifications", "liveActivities", true)
        activityPriorityButton(shown)
        val liveApps = EditText(this).apply {
            tag = settingTag("liveActivityApps")
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

        val quickReplies = card("Quick reply text")
        val replyField = EditText(this).apply {
            tag = settingTag("replies")
            hint = "Comma separated, up to 3 replies"
            contentDescription = "Quick reply messages, comma-separated, up to three"
            setText(prefs.getString("replies", "Yes,Later,Busy"))
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
        }
        quickReplies.addView(replyField)
        button(quickReplies, "Save replies") {
            val replies = replyField.text.toString().split(',').map { it.trim() }
                .filter { it.isNotBlank() }.take(3).joinToString(",")
            prefs.edit().putString("replies", replies).apply()
            android.widget.Toast.makeText(this, "Quick replies saved", android.widget.Toast.LENGTH_SHORT).show()
        }

        val advanced = advancedGroup("notifications")
        val timing = card("Notification timing", advanced)
        slider(timing, "Seconds before it collapses", "notifSeconds", 2, 15, 4)
        val heights = card("Expanded sizes", advanced)
        heights.addView(TextView(this).apply {
            text = "Short notifications fit their content. This value caps taller messages; text ends with an ellipsis instead of scrolling. Reply and action buttons keep their minimum space."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(6))
        })
        slider(heights, "Maximum expanded notification height (dp)", "expH_notif", 64, 360, 220)
        sectionSizing("notifications", "Notifications", 260, 40, 340, 220, parent = advanced)
        sectionSizing("calls", "Calls", 240, 40, 340, 144, parent = advanced)
    }

    private fun buildMaps() {
        val visibility = card("Navigation visibility")
        toggle(visibility, "Show Google Maps navigation in the pill", "showMaps", true)
        toggle(visibility, "Show navigation details from Maps notifications and the Maps screen", "mapsInfo", true)
        toggle(visibility, "Tint ETA by Maps traffic status when available", "mapsTrafficColors", true)
        visibility.addView(TextView(this).apply {
            text = "Green, amber, and red follow Maps’ ETA tint when it is exposed by the notification. If Maps provides no traffic tint, Capsule keeps the ETA neutral."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(5))
        })

        val layout = card("Expanded route layout")
        mapsLayoutButton(layout)
        toggle(layout, "Use Maps' turn-arrow artwork", "mapsOwnArrow", true)
        layout.addView(TextView(this).apply {
            text = "The direction-card option changes the expanded layout only. It keeps the current pill theme and icon choice."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(3), dp(2), dp(5))
        })
        val advanced = advancedGroup("maps")
        sectionSizing("maps", "Maps", 240, 40, 360, 112, parent = advanced)
    }

    private fun mapsLayoutButton(parent: LinearLayout) {
        val ids = listOf("direction_card", "compact_row")
        val labels = listOf("Direction card", "Compact route row")
        fun current(): String = (prefs.getString("mapsExpandedLayout", null)
            ?: if (prefs.getBoolean("mapsReferenceLayout", true)) ids[0] else ids[1])
            .takeIf { it in ids } ?: ids[0]
        fun label(value: String) = "Expanded Maps layout: ${labels[ids.indexOf(value).coerceAtLeast(0)]}"
        val button = styledButton().apply {
            text = label(current())
            tag = settingTag("mapsExpandedLayout")
        }
        button.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Expanded Maps layout")
                .setSingleChoiceItems(labels.toTypedArray(), ids.indexOf(current()).coerceAtLeast(0)) { dialog, which ->
                    val selected = ids[which]
                    prefs.edit()
                        .putString("mapsExpandedLayout", selected)
                        .putBoolean("mapsReferenceLayout", selected == "direction_card")
                        .apply()
                    button.text = label(selected)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
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
            tag = settingTag("notificationQueueTheme")
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

    private fun notificationBadgeCornerButton(parent: LinearLayout) {
        val corners = listOf("top_start", "top_end", "bottom_start", "bottom_end", "right_side")
        val labels = listOf("Top left", "Top right", "Bottom left", "Bottom right", "Beside pill · right")
        fun selected() = (prefs.getString("notificationBadgeCorner", "top_end") ?: "top_end")
            .takeIf { it in corners } ?: "top_end"
        fun label(corner: String) = "Count badge position: ${labels[corners.indexOf(corner).coerceAtLeast(0)]}"
        fun gravity(corner: String) = when (corner) {
            "top_start" -> android.view.Gravity.TOP or android.view.Gravity.LEFT
            "bottom_start" -> android.view.Gravity.BOTTOM or android.view.Gravity.LEFT
            "bottom_end" -> android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT
            "right_side" -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
            else -> android.view.Gravity.TOP or android.view.Gravity.RIGHT
        }
        val pillStyle = previewPillPalette()
        val queueTheme = prefs.getString("notificationQueueTheme", "blend") ?: "blend"
        val queueAccent = if (queueTheme == "blend") pillStyle.accent else previewPillPalette(queueTheme).accent
        val badgeColor = CapsulePrefs.notificationBadgeColor(prefs.getString("notificationBadgeColor", "theme"), queueAccent)
        val preview = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76))
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(when {
                    isLightAppTheme() -> 0xFFE9EEF4.toInt()
                    appTheme() == "amoled" -> Color.BLACK
                    else -> 0xFF10151D.toInt()
                })
            }
        }
        val samplePill = android.widget.FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp(pillStyle.cornerDp).toFloat()
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(pillStyle.top, pillStyle.bottom)
                setStroke(dp(1), pillStyle.outline)
            }
            val pillLabel = TextView(this@MainActivity).apply {
                text = "Capsule"
                textSize = 12f
                setTextColor(pillStyle.primary)
                gravity = android.view.Gravity.CENTER
            }
            addView(pillLabel, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            notificationCornerPreviewLabel = pillLabel
        }
        notificationCornerPreviewPill = samplePill
        preview.addView(samplePill, android.widget.FrameLayout.LayoutParams(dp(200), dp(44), android.view.Gravity.CENTER))
        val previewBadge = TextView(this).apply {
            text = "3"
            gravity = android.view.Gravity.CENTER
            val size = CapsulePrefs.notificationBadgeSize(prefs.getInt("notificationBadgeSize", CapsulePrefs.notificationBadgeSizeDefault))
            textSize = size * 0.46f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(CapsulePrefs.notificationBadgeTextColor(badgeColor))
            minWidth = dp(size)
            minHeight = dp(size)
            val horizontalPadding = dp((size / 8).coerceAtLeast(2))
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeColor)
            }
        }
        notificationCornerPreviewBadge = previewBadge
        samplePill.addView(previewBadge, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            gravity(selected())
        ).apply {
            when (selected()) {
                "right_side" -> { rightMargin = dp(1) }
                else -> setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        })
        if (selected() == "right_side") previewBadge.translationX = dp(35).toFloat()
        parent.addView(preview)
        parent.addView(TextView(this).apply {
            text = "Preview updates with your choice. Beside-pill placement reserves space to the right; corner placement moves away from visible controls when needed."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(4))
        })
        val button = styledButton().apply {
            text = label(selected())
            contentDescription = "Choose notification count badge corner"
            tag = settingTag("notificationBadgeCorner")
        }
        button.setOnClickListener {
            val current = selected()
            AlertDialog.Builder(this)
                .setTitle("Notification count badge corner")
                .setSingleChoiceItems(labels.toTypedArray(), corners.indexOf(current)) { dialog, which ->
                    val corner = corners[which]
                    prefs.edit().putString("notificationBadgeCorner", corner).apply()
                    button.text = label(corner)
                    (previewBadge.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { params ->
                        params.gravity = gravity(corner)
                        params.setMargins(0, 0, 0, 0)
                        if (corner == "right_side") {
                            params.rightMargin = dp(1)
                        } else {
                            params.setMargins(dp(3), dp(3), dp(3), dp(3))
                        }
                        previewBadge.layoutParams = params
                    }
                    previewBadge.translationX = if (corner == "right_side") dp(35).toFloat() else 0f
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
    }

    private fun notificationBadgeSizeSlider(parent: LinearLayout, badge: TextView) {
        slider(
            parent,
            "Count badge size (dp)",
            "notificationBadgeSize",
            16,
            36,
            CapsulePrefs.notificationBadgeSizeDefault,
            onValueChanged = {
                applyNotificationBadgePreviewSize(badge, it)
                notificationCornerPreviewBadge?.let { cornerBadge -> applyNotificationBadgePreviewSize(cornerBadge, it) }
            }
        )
    }

    private fun applyNotificationBadgePreviewSize(badge: TextView, rawSize: Int) {
        val size = CapsulePrefs.notificationBadgeSize(rawSize)
        badge.minWidth = dp(size)
        badge.minHeight = dp(size)
        badge.textSize = size * 0.46f
        val horizontalPadding = dp((size / 8).coerceAtLeast(2))
        badge.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        badge.requestLayout()
    }

    private fun notificationBadgeColorButton(parent: LinearLayout, preview: TextView, badge: TextView) {
        val options = CapsulePrefs.notificationBadgeColorOptions
        fun selected(): String = (prefs.getString("notificationBadgeColor", "theme") ?: "theme")
            .takeIf { value -> options.any { it.first == value } } ?: "theme"
        fun label(value: String) = "Count badge color: ${options.firstOrNull { it.first == value }?.second ?: options.first().second}"
        val button = styledButton().apply {
            text = label(selected())
            contentDescription = "Choose notification count badge color"
            tag = settingTag("notificationBadgeColor")
        }
        button.setOnClickListener {
            val current = selected()
            AlertDialog.Builder(this)
                .setTitle("Notification count badge color")
                .setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == current }) { dialog, which ->
                    val mode = options[which].first
                    prefs.edit().putString("notificationBadgeColor", mode).apply()
                    button.text = label(mode)
                    updateNotificationQueuePreview(
                        prefs.getString("notificationQueueTheme", "blend") ?: "blend",
                        preview,
                        badge
                    )
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
    }

    private fun updateNotificationQueuePreview(mode: String, preview: TextView, badge: TextView) {
        val blended = mode == "blend"
        val style = if (blended) previewPillPalette() else previewPillPalette(mode)
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
        applyNotificationBadgePreviewColor(badge, style.accent)
        applyNotificationBadgePreviewSize(
            badge,
            prefs.getInt("notificationBadgeSize", CapsulePrefs.notificationBadgeSizeDefault)
        )
        notificationCornerPreviewBadge?.let {
            applyNotificationBadgePreviewColor(it, style.accent)
            applyNotificationBadgePreviewSize(
                it,
                prefs.getInt("notificationBadgeSize", CapsulePrefs.notificationBadgeSizeDefault)
            )
        }
        notificationCornerPreviewPill?.background = GradientDrawable().apply {
            cornerRadius = dp(if (style.id == "nothing") 10 else style.cornerDp).toFloat()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(style.top, style.bottom)
            setStroke(dp(1), style.outline)
        }
        notificationCornerPreviewLabel?.setTextColor(style.primary)
    }

    private fun applyNotificationBadgePreviewColor(badge: TextView, themeAccent: Int) {
        val color = CapsulePrefs.notificationBadgeColor(prefs.getString("notificationBadgeColor", "theme"), themeAccent)
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        badge.setTextColor(CapsulePrefs.notificationBadgeTextColor(color))
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
            tag = settingTag("activityPriorityOrder")
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

    private fun buildPinnedApps() {
        val preferences = card("Pinned apps in the pill")
        toggle(preferences, "Enable pinned apps", "pinnedOn", true)
        preferences.addView(TextView(this).apply {
            text = "Choose up to 8 installed apps. Open the app grid using the Pinned apps action in Gesture settings."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(4), dp(2), dp(4), dp(7))
        })

        val selected = linkedSetOf<String>().apply {
            addAll((prefs.getString("pinned", "") ?: "").split(',').map(String::trim).filter(String::isNotBlank))
        }
        val selectedCard = card("Selected apps")
        val selectedCount = TextView(this).apply {
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        val selectedRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        selectedCard.addView(selectedCount)
        selectedCard.addView(selectedRows)

        val pickerCard = card("Installed apps")
        val search = EditText(this).apply {
            hint = "Search installed apps"
            contentDescription = "Search installed apps to pin"
            setSingleLine(true)
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
        }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appScroll = ScrollView(this).apply {
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380))
            addView(results)
        }
        pickerCard.addView(search)
        pickerCard.addView(appScroll)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installedApps = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolve ->
                val activity = resolve.activityInfo ?: return@mapNotNull null
                if (activity.packageName == packageName) return@mapNotNull null
                val label = resolve.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                Triple(activity.packageName, label, resolve.loadIcon(packageManager))
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(java.util.Locale.getDefault()) }
        selected.retainAll(installedApps.map { it.first }.toSet())

        lateinit var renderResults: (String) -> Unit
        fun saveSelection() {
            val saved = selected.take(8).joinToString(",")
            prefs.edit().putString("pinned", saved).apply()
            selectedCount.text = "${selected.size} of 8 apps selected"
            selectedRows.removeAllViews()
            selected.forEach { packageName ->
                val app = installedApps.firstOrNull { it.first == packageName } ?: return@forEach
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(5), dp(6), dp(4), dp(6))
                }
                row.addView(android.widget.ImageView(this).apply {
                    setImageDrawable(app.third.constantState?.newDrawable(resources)?.mutate() ?: app.third)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                })
                row.addView(TextView(this).apply {
                    text = app.second
                    textSize = 14f
                    setTextColor(primaryTextColor())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = dp(10) }
                })
                row.addView(TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(secondaryTextColor())
                    minWidth = dp(44)
                    minHeight = dp(44)
                    contentDescription = "Remove ${app.second} from pinned apps"
                    setOnClickListener { selected.remove(packageName); saveSelection(); renderResults(search.text.toString()) }
                })
                selectedRows.addView(row)
            }
            if (selected.isEmpty()) selectedRows.addView(TextView(this).apply {
                text = "No apps pinned yet"
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(dp(5), dp(5), dp(5), dp(8))
            })
        }

        fun toggleSelection(packageName: String) {
            if (packageName in selected) selected.remove(packageName)
            else if (selected.size < 8) selected.add(packageName)
            else {
                android.widget.Toast.makeText(this, "You can pin up to 8 apps", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            saveSelection()
            renderResults(search.text.toString())
        }

        renderResults = { query ->
            results.removeAllViews()
            val matching = installedApps.filter { it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true) }
            matching.take(60).forEach { app ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(dp(5), dp(7), dp(5), dp(7))
                    contentDescription = "${app.second}${if (app.first in selected) ", pinned" else ", not pinned"}"
                    setOnClickListener { toggleSelection(app.first) }
                }
                row.addView(android.widget.ImageView(this).apply {
                    setImageDrawable(app.third.constantState?.newDrawable(resources)?.mutate() ?: app.third)
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                })
                row.addView(TextView(this).apply {
                    text = app.second
                    textSize = 14f
                    setTextColor(primaryTextColor())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = dp(10) }
                })
                row.addView(Switch(this).apply {
                    isChecked = app.first in selected
                    isClickable = false
                    isFocusable = false
                    contentDescription = "Pin ${app.second}"
                })
                results.addView(row)
                results.addView(View(this).apply {
                    setBackgroundColor(if (isLightAppTheme()) 0xFFE6EAF0.toInt() else 0xFF273441.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }
            if (matching.isEmpty()) results.addView(TextView(this).apply {
                text = if (installedApps.isEmpty()) "No launchable apps were found." else "No matching apps."
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(dp(6), dp(14), dp(6), dp(14))
            })
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderResults(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        saveSelection()
        renderResults("")

        val advanced = advancedGroup("pinned")
        val layout = card("Shortcut layout", advanced)
        slider(layout, "App icon size (dp)", "pinnedIconSize", 20, 48, 32)
        slider(layout, "Grid rows", "pinnedRows", 1, 4, 2)
    }

    private fun buildCharging() {
        val charge = card("Charging")
        toggle(charge, "Show charging status in the main pill", "showCharging", true)
        toggle(charge, "Charging alerts (when the charge reaches a level, or is full)", "chargeAlert", true)

        var chargingHeightSlider: SliderHandle? = null
        val customChargingHeight = prefs.getBoolean("customSize_charging", false)
        val effectiveChargingHeight = if (customChargingHeight) {
            prefs.getInt("sizeExpandedHeight_charging", prefs.getInt("expH_charge", 64))
        } else {
            prefs.getInt("expH_charge", 64)
        }
        chargingHeightSlider = slider(
            charge, "Expanded charging height (dp)", "expH_charge", 40, 440, 64,
            onValueCommitted = { value ->
                prefs.edit()
                    .putInt("expH_charge", value)
                    .putInt("sizeExpandedHeight_charging", value)
                    .apply()
            },
            startValueOverride = effectiveChargingHeight
        )
        charge.addView(TextView(this).apply {
            text = "The pill keeps enough height for its content and any notification shown with charging."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(5))
        })

        val tuning = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        slider(tuning, "Alert at charge (%)", "chargeAlertAt", 50, 100, 80)
        val chargeWidthPreview = collapsedChargeWidthPreview()
        slider(tuning, "Collapsed charging pill width (dp)", "chargeMiniW", 104, 240, 116, onValueChanged = { value ->
            updateChargeWidthPreview(value)
        })
        tuning.addView(chargeWidthPreview)

        val previewPalette = previewPillPalette()
        val circularWavePreview = CircularWaveChargePreviewView(this).apply {
            setPreviewTint(previewPalette.primary)
            setPreviewSurface(previewPalette.top, previewPalette.bottom, previewPalette.outline)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(112)
            ).apply { topMargin = dp(8) }
        }
        tuning.addView(TextView(this).apply {
            text = "Circular Wave live preview"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
            setPadding(dp(2), dp(12), dp(2), dp(2))
        })
        tuning.addView(TextView(this).apply {
            text = "Compare expanded and compact sizes. Choose a charge level to see how the remaining arc changes."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(3))
        })
        val previewLabels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        listOf("Expanded", "Collapsed").forEach { label ->
            previewLabels.addView(TextView(this).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                textSize = 11f
                setTextColor(secondaryTextColor())
            }, LinearLayout.LayoutParams(0, dp(28), 1f))
        }
        tuning.addView(previewLabels)
        tuning.addView(circularWavePreview)

        var previewPercent = 50
        val previewLevelChips = mutableListOf<Pair<Int, TextView>>()
        val selectedChipText = if (
            (Color.red(previewPalette.primary) * 299 + Color.green(previewPalette.primary) * 587 +
                Color.blue(previewPalette.primary) * 114) / 1000 >= 150
        ) Color.BLACK else Color.WHITE
        fun refreshPreviewLevelChips() {
            previewLevelChips.forEach { (value, chip) ->
                val selected = value == previewPercent
                chip.background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (selected) previewPalette.primary else previewPalette.top)
                    setStroke(dp(1), if (selected) previewPalette.primary else previewPalette.outline)
                }
                chip.setTextColor(if (selected) selectedChipText else primaryTextColor())
                chip.contentDescription = "Preview charge level $value percent${if (selected) ", selected" else ""}"
            }
        }
        val previewLevels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        listOf(20, 50, 100).forEach { value ->
            val chip = TextView(this).apply {
                text = "$value%"
                gravity = android.view.Gravity.CENTER
                textSize = 13f
                minHeight = dp(44)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    previewPercent = value
                    circularWavePreview.setPreviewChargePercent(value)
                    refreshPreviewLevelChips()
                }
            }
            previewLevelChips += value to chip
            previewLevels.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (value != 100) rightMargin = dp(8)
            })
        }
        tuning.addView(previewLevels)
        sectionSizing(
            "charging", "Charging", 116, 40, 340, 64, parent = tuning,
            onExpandedHeightCommitted = { value ->
                prefs.edit().putInt("expH_charge", value).apply()
                chargingHeightSlider?.setValue(value)
            }
        )
        refreshPreviewLevelChips()
        circularWavePreview.setPreviewChargePercent(previewPercent)
        tuning.addView(TextView(this).apply {
            text = "Preview uses the Circular Wave ring. Choose it in Charging icon below to use it in the pill."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), 0, dp(2), dp(5))
        })

        var previewRingSize = prefs.getInt("chargeRingSize", 48).coerceIn(36, 80)
        var previewThickness = prefs.getInt("chargeRingThickness", 3).coerceIn(2, 10)
        var previewRemainderThickness = prefs.getInt("chargeRemainderThickness", 2).coerceIn(1, 6)
        val remainderColorLabels = listOf("Medium gray", "Dark gray")
        var previewRemainderColorIndex = prefs.getInt("chargeRemainderColor", 0).coerceIn(0, remainderColorLabels.lastIndex)
        fun previewRemainderColor(): Int = when (previewRemainderColorIndex) {
            1 -> 0xFF424242.toInt()
            else -> 0xFF777777.toInt()
        }
        val legacyWaveCount = prefs.getInt("chargeRingDensity", 7).coerceIn(3, 32)
        val ringSizeForLegacySpacing = prefs.getInt("chargeRingSize", 48).coerceIn(36, 80)
        val legacyWaveSpacingDp = (2.0 * Math.PI * ringSizeForLegacySpacing * 0.36 / legacyWaveCount)
            .toInt().coerceIn(3, 24)
        var previewSpacing = prefs.getInt("chargeWaveSpacingDp", legacyWaveSpacingDp).coerceIn(3, 24)
        fun durationFromLegacySpeed(speed: Int) =
            (8_000L - (speed.coerceIn(1, 30) - 1) * 255L).coerceIn(580L, 8_000L)
        fun waveSpeedFromLegacy(speed: Int): Int =
            (((24_000L - durationFromLegacySpeed(speed) + 300L) / 600L) + 1L).toInt().coerceIn(1, 40)
        var previewSpeed = prefs.getInt(
            "chargeWaveSpeed",
            waveSpeedFromLegacy(prefs.getInt("chargeRingSpeed", 5))
        ).coerceIn(1, 40)
        fun previewCycleDuration() = (24_000L - (previewSpeed - 1) * 600L).coerceIn(600L, 24_000L)
        var previewHeight = prefs.getInt("chargeWaveHeight", 9).coerceIn(2, 22)
        var previewInnerSpacing = prefs.getInt("chargeWaveInnerSpacingPercent", 28).coerceIn(0, 70)
        var previewSoftness = prefs.getInt("chargeWaveSoftness", 55).coerceIn(0, 100)
        var previewMotionEnabled = prefs.getBoolean("chargeMotionEnabled", true)
        fun refreshCircularWavePreview() {
            circularWavePreview.updateSettings(
                ringSizeDp = previewRingSize,
                waveSpacingDp = previewSpacing,
                thicknessDp = previewThickness,
                remainderThicknessDp = previewRemainderThickness,
                remainderColor = previewRemainderColor(),
                cycleDurationMs = previewCycleDuration(),
                waveHeightPercent = previewHeight,
                innerWaveSpacingPercent = previewInnerSpacing,
                edgeSoftnessPercent = previewSoftness,
                tint = previewPalette.primary,
                motionEnabled = previewMotionEnabled
            )
        }
        refreshCircularWavePreview()

        slider(tuning, "Charging ring size (dp)", "chargeRingSize", 36, 80, 48, onValueChanged = { value ->
            previewRingSize = value
            refreshCircularWavePreview()
        })
        slider(tuning, "Charging ring thickness (dp)", "chargeRingThickness", 2, 10, 3, onValueChanged = { value ->
            previewThickness = value
            refreshCircularWavePreview()
        })
        val remainderColorButton = styledButton().apply {
            text = "Circular Wave remainder color: ${remainderColorLabels[previewRemainderColorIndex]}"
            tag = settingTag("chargeRemainderColor")
        }
        tuning.addView(remainderColorButton)
        remainderColorButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Circular Wave remainder color")
                .setSingleChoiceItems(remainderColorLabels.toTypedArray(), previewRemainderColorIndex) { dialog, which ->
                    previewRemainderColorIndex = which.coerceIn(0, remainderColorLabels.lastIndex)
                    prefs.edit().putInt("chargeRemainderColor", previewRemainderColorIndex).apply()
                    remainderColorButton.text = "Circular Wave remainder color: ${remainderColorLabels[previewRemainderColorIndex]}"
                    refreshCircularWavePreview()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        slider(tuning, "Circular Wave remainder thickness (dp)", "chargeRemainderThickness", 1, 6, 2, onValueChanged = { value ->
            previewRemainderThickness = value
            refreshCircularWavePreview()
        })
        slider(tuning, "Collapsed charging percentage size (sp)", "chargePctTextSize", 10, 24, 13)
        val chargeReadoutModes = listOf("percent", "watts", "both")
        val chargeReadoutLabels = listOf("Percentage", "Watts", "Both")
        fun chargeReadoutLabel(): String {
            val mode = prefs.getString("chargeMiniReadout", "percent") ?: "percent"
            return chargeReadoutLabels[chargeReadoutModes.indexOf(mode).takeIf { it >= 0 } ?: 0]
        }
        val chargeReadoutButton = styledButton().apply {
            text = "Collapsed charging readout: ${chargeReadoutLabel()}"
            tag = settingTag("chargeMiniReadout")
        }
        tuning.addView(chargeReadoutButton)
        chargeReadoutButton.setOnClickListener {
            val selected = chargeReadoutModes.indexOf(prefs.getString("chargeMiniReadout", "percent")).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Collapsed charging readout")
                .setSingleChoiceItems(chargeReadoutLabels.toTypedArray(), selected) { dialog, which ->
                    prefs.edit().putString("chargeMiniReadout", chargeReadoutModes[which]).apply()
                    chargeReadoutButton.text = "Collapsed charging readout: ${chargeReadoutLabels[which]}"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        tuning.addView(TextView(this).apply {
            text = "Watts use the current and voltage reported by your phone; some devices may not provide a reading. Both may need a wider collapsed charging pill."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(1), dp(2), dp(5))
        })
        slider(tuning, "Wave spacing (dp between peaks)", "chargeWaveSpacingDp", 3, 24, legacyWaveSpacingDp, onValueChanged = { value ->
            previewSpacing = value
            refreshCircularWavePreview()
        })
        val otherIconSpeedGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        slider(otherIconSpeedGroup, "Other charging icon speed (1 slow · 30 fast)", "chargeRingSpeed", 1, 30, 5)
        tuning.addView(otherIconSpeedGroup)
        val circularWaveSpeedGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        slider(
            circularWaveSpeedGroup, "Circular Wave speed (1 slow · 40 fast)",
            "chargeWaveSpeed", 1, 40, previewSpeed,
            onValueChanged = { value ->
                previewSpeed = value
                refreshCircularWavePreview()
            }
        )
        tuning.addView(circularWaveSpeedGroup)
        slider(tuning, "Circular Wave height (%)", "chargeWaveHeight", 2, 22, 9, onValueChanged = { value ->
            previewHeight = value
            refreshCircularWavePreview()
        })
        slider(tuning, "Inner wave spacing (%)", "chargeWaveInnerSpacingPercent", 0, 70, 28, onValueChanged = { value ->
            previewInnerSpacing = value
            refreshCircularWavePreview()
        })
        slider(tuning, "Circular Wave edge softness", "chargeWaveSoftness", 0, 100, 55, onValueChanged = { value ->
            previewSoftness = value
            refreshCircularWavePreview()
        })
        tuning.addView(TextView(this).apply {
            text = "Wave spacing sets peak-to-peak distance. Inner wave spacing widens or tightens each inner valley; height sets wave depth, and softness rounds the edges. Only the charged contour animates; the uncharged arc stays fixed. Choose its gray tone and thickness above. At full charge, the complete contour moves. Circular Wave speed runs from 24 seconds down to 0.6 seconds per cycle."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(1), dp(2), dp(5))
        })
        val chargingStyleOption = chargingStyleButton(tuning)
        toggle(tuning, "Animate charging indicators", "chargeMotionEnabled", true) { enabled ->
            previewMotionEnabled = enabled
            refreshCircularWavePreview()
        }
        tuning.addView(TextView(this).apply {
            text = "When enabled, the wave contour moves only within the charged segment; the uncharged remainder stays still. At 100%, the complete contour animates. Turning this off keeps the ring still."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(1), dp(2), dp(5))
        })
        val selectedChargingShape = prefs.getInt("chargeIconShape", 9).coerceIn(0, 11)
        circularWaveSpeedGroup.visibility = if (selectedChargingShape == 11) View.VISIBLE else View.GONE
        otherIconSpeedGroup.visibility = if (selectedChargingShape == 11) View.GONE else View.VISIBLE
        chargingIconButton(charge) { shape ->
            chargingStyleOption.visibility = if (supportsChargingStyle(shape)) View.VISIBLE else View.GONE
            circularWaveSpeedGroup.visibility = if (shape == 11) View.VISIBLE else View.GONE
            otherIconSpeedGroup.visibility = if (shape == 11) View.GONE else View.VISIBLE
        }
        TextView(this).apply {
            text = "Choose a charging icon for both pill sizes. Circular Wave uses the same scalloped geometry at each size: the charged contour moves while the remaining-charge arc stays fixed. The size and wave sliders preview the selected geometry live."
            textSize = 12f
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(4), dp(2), dp(6))
        }.also { tuning.addView(it) }

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

        val advanced = advancedGroup("charging")
        advanced.addView(TextView(this).apply {
            text = "Animation and size controls"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(secondaryTextColor())
            setPadding(dp(2), dp(2), dp(2), dp(8))
        })
        advanced.addView(tuning)

    }

    private fun collapsedChargeWidthPreview(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(4))
        }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        labelRow.addView(TextView(this).apply {
            text = "Collapsed pill preview"
            textSize = 12f
            setTextColor(secondaryTextColor())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        chargeWidthPreviewValue = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
        }
        labelRow.addView(chargeWidthPreviewValue)
        shell.addView(labelRow)

        val previewStyle = previewPillPalette()
        val sample = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(previewStyle.top, previewStyle.bottom)
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), previewStyle.outline)
            }
            clipToPadding = true
        }
        sample.addView(TextView(this).apply {
            text = "ϟ"
            gravity = android.view.Gravity.CENTER
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(previewStyle.accent)
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(TextView(this).apply {
            text = "72%"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
        })
        textColumn.addView(TextView(this).apply {
            text = "Charging · 3.8 V"
            textSize = 9f
            setTextColor(secondaryTextColor())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        sample.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(7)
        })
        chargeWidthPreviewSample = sample
        shell.addView(sample, LinearLayout.LayoutParams(dp(116), dp(48)).apply { topMargin = dp(7) })
        updateChargeWidthPreview(prefs.getInt("chargeMiniW", 116))
        return shell
    }

    private fun updateChargeWidthPreview(widthDp: Int) {
        chargeWidthPreviewValue?.text = "$widthDp dp"
        val sample = chargeWidthPreviewSample ?: return
        val maxWidth = (resources.displayMetrics.widthPixels - dp(80)).coerceAtLeast(dp(104))
        val params = sample.layoutParams ?: return
        params.width = dp(widthDp).coerceAtMost(maxWidth)
        sample.layoutParams = params
        sample.requestLayout()
    }

    private fun supportsChargingStyle(shape: Int): Boolean = shape in 0..5 || shape == 8

    private fun chargingStyleButton(parent: LinearLayout): Button {
        val styles = listOf("Liquid battery", "Segmented battery", "Pulse battery")
        fun current() = prefs.getInt("chargeIconStyle", 0).coerceIn(0, styles.lastIndex)
        val button = styledButton().apply {
            text = "Charging style: ${styles[current()]}"
            contentDescription = "Choose charging style"
            tag = settingTag("chargeIconStyle")
            visibility = if (supportsChargingStyle(prefs.getInt("chargeIconShape", 9).coerceIn(0, 11))) View.VISIBLE else View.GONE
        }
        button.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging style")
                .setSingleChoiceItems(styles.toTypedArray(), current()) { dialog, which ->
                    prefs.edit().putInt("chargeIconStyle", which).apply()
                    button.text = "Charging style: ${styles[which]}"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
        return button
    }

    private fun chargingIconButton(parent: LinearLayout, onSelected: (Int) -> Unit) {
        val icons = listOf(
            "One UI battery", "Lightning bolt", "Power plug", "Leaf", "Spark", "Pixel battery",
            "Nothing glyph dots", "Pixel charge ring", "Irregular progress ring", "Crafted scallop ring",
            "Horologist wavy ring", "Circular Wave ring"
        )
        fun selected() = prefs.getInt("chargeIconShape", 9).coerceIn(0, icons.lastIndex)
        val button = styledButton().apply {
            text = "Charging icon: ${icons[selected()]}"
            contentDescription = "Choose charging icon design"
            tag = settingTag("chargeIconShape")
        }
        button.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Charging icon")
                .setSingleChoiceItems(icons.toTypedArray(), selected()) { dialog, which ->
                    prefs.edit().putInt("chargeIconShape", which).apply()
                    button.text = "Charging icon: ${icons[which]}"
                    onSelected(which)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        parent.addView(button)
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
        val advanced = advancedGroup("look")
        val type = card("Text sizing", advanced)
        slider(type, "Text size (%)", "textScale", 70, 150, 100)
        val imageSizing = card("Custom image sizing", advanced)
        slider(imageSizing, "Image height (dp)", "imageHeight", 40, 220, 120)
    }

    private fun buildPrivacy() {
        val privacy = card("History and hidden apps")
        toggle(privacy, "Keep notification history on this phone (off by default)", "historyOn", false)
        val blockField = EditText(this).apply {
            tag = settingTag("blockedApps")
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
        addSectionResetButton(privacy, "privacy")
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
        "Pill task preview" -> "Choose which local tasks appear in the expanded pill."
        "Task lists" -> "Create personal lists and decide whether completed tasks stay visible."
        "Tasks" -> "Create, organize, complete, and edit tasks stored only on this device."
        "Frosted glass" -> "Use a translucent layered look with a safe fallback on unsupported devices."
        "Custom image widget" -> "Choose an optional image to show in the pill."
        "Advanced" -> "Optional fine-tuning. Everyday controls stay visible above."
        "History and hidden apps" -> "Manage local notification history and apps to keep out of view."
        "Tools" -> "Back up settings, restore a configuration, and run utilities."
        "Combined three-pill preview" -> "See the main activity and both companion pills together."
        "Installed APK" -> "Identify the version, build number, and CI run installed on this phone."
        "Release notes" -> "Highlights and fixes included in this app version."
        else -> "Adjust how this part of Capsule Lite behaves."
    }

    // One UI-inspired preference group: quiet surface, clear title, short description, and controls.
    private fun card(title: String, parent: LinearLayout = col): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(if (isLightAppTheme()) Color.WHITE else if (appTheme() == "amoled") 0xFF080808.toInt() else 0xFF171C22.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (isLightAppTheme()) 0xFFE5E8EC.toInt() else if (appTheme() == "amoled") 0xFF252525.toInt() else 0xFF2A3038.toInt())
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
                text = title
                textSize = 16f
                letterSpacing = -0.01f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(primaryTextColor())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        })
        box.addView(TextView(this).apply {
            text = sectionDescription(title)
            textSize = 13f
            setTextColor(secondaryTextColor())
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(4), dp(2), dp(10))
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
        parent.addView(box)
        return box
    }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(styledButton().apply {
            text = label
            setOnClickListener { onClick() }
        })
    }

    private fun toggle(
        parent: LinearLayout,
        label: String,
        key: String,
        default: Boolean,
        onChanged: ((Boolean) -> Unit)? = null
    ) {
        parent.addView(Switch(this).apply {
            tag = settingTag(key)
            text = label
            textSize = 14f
            setTextColor(primaryTextColor())
            minHeight = dp(56)
            setPadding(dp(2), dp(9), dp(2), dp(9))
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(if (isLightAppTheme()) 0xFF168A72.toInt() else 0xFF7DE2BF.toInt(), 0xFFB9C0C8.toInt())
            )
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(if (isLightAppTheme()) 0x66168A72 else 0x667DE2BF, 0x553E4852)
            )
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(key, on).apply()
                onChanged?.invoke(on)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        })
    }

    // Show the available actions as a list so the current choice is visible and easy to change.
    private fun gestureButton(parent: LinearLayout, key: String) {
        fun label(action: String) =
            "${CapsulePrefs.gestureLabels[key]}: ${CapsulePrefs.actionLabels[action] ?: action}"

        fun currentAction() = (prefs.getString(key, CapsulePrefs.defaultFor(key)) ?: "none")
            .let { if (it == "quick_settings" || it == "control_panel") "open_pinned" else it }
        val b = styledButton().apply {
            minHeight = dp(56)
            text = label(currentAction())
            tag = settingTag(key)
            contentDescription = "${label(currentAction())}. Tap to choose another action."
        }
        b.setOnClickListener {
            val actions = CapsulePrefs.actions
            val labels = actions.map { CapsulePrefs.actionLabels[it] ?: it }.toTypedArray()
            val selected = actions.indexOf(currentAction()).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Choose ${CapsulePrefs.gestureLabels[key]} action")
                .setSingleChoiceItems(labels, selected) { dialog, which ->
                    val action = actions[which]
                    prefs.edit().putString(key, action).apply()
                    b.text = label(action)
                    b.contentDescription = "${label(action)}. Tap to choose another action."
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
            tag = settingTag("fontFamily")
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
        val b = styledButton().apply {
            text = "App theme: " + names[themes.indexOf(selectedTheme()).coerceAtLeast(0)]
            tag = settingTag("appTheme")
        }
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
        val style = previewPillPalette()
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
            tag = settingTag("theme")
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
            tag = settingTag("motionPreset")
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
        val styles = listOf("liquid_morph", "continuity", "settling_dock", "capsule_spring")
        val labels = mapOf(
            "liquid_morph" to "Liquid morph",
            "continuity" to "Icon continuity",
            "settling_dock" to "Settling dock",
            "capsule_spring" to "Capsule spring (current)"
        )
        val descriptions = arrayOf(
            "Liquid morph — elastic width and height movement",
            "Icon continuity — carry the charging icon between sizes",
            "Settling dock — calm expansion with details unfolding below",
            "Capsule spring — Capsule resize and content transition"
        )
        fun selected() = (prefs.getString("pillAnimationStyle", "continuity") ?: "continuity")
            .takeIf { it in styles } ?: "continuity"
        fun label(style: String) = "Expansion animation: ${labels[style] ?: labels["continuity"]}"
        val button = styledButton().apply {
            text = label(selected())
            tag = settingTag("pillAnimationStyle")
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
            text = "Choose between Capsule's liquid, continuity, settling, and spring transitions. This choice does not replace the charging icon setting."
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
            tag = settingTag("vizStyle")
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

    private fun slider(
        parent: LinearLayout,
        label: String,
        key: String,
        lo: Int,
        hi: Int,
        default: Int,
        onValueChanged: ((Int) -> Unit)? = null,
        onValueCommitted: ((Int) -> Unit)? = null,
        startValueOverride: Int? = null
    ): SliderHandle {
        var minimum = lo.coerceAtMost(hi)
        val stored = prefs.getInt(key, default)
        val start = (startValueOverride ?: stored).coerceIn(minimum, hi)
        // Normalize stale or imported settings so the caption and thumb always agree.
        if (startValueOverride == null && stored != start) prefs.edit().putInt(key, start).apply()
        val valueText = TextView(this).apply {
            text = start.toString()
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primaryTextColor())
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(1))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(primaryTextColor())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(valueText, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        val bar = SeekBar(this).apply {
            tag = settingTag(key)
            contentDescription = "$label: $start"
            max = hi - minimum
            progress = start - minimum
            minHeight = dp(44)
            setPadding(dp(2), dp(8), dp(2), dp(8))
            progressTintList = android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0xFF168A72.toInt() else 0xFF7DE2BF.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0xFFD8DEE5.toInt() else 0xFF3B434C.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(if (isLightAppTheme()) 0xFF168A72.toInt() else 0xFF7DE2BF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(5) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val value = (p + minimum).coerceIn(minimum, hi)
                    valueText.text = value.toString()
                    sb.contentDescription = "$label: $value"
                    onValueChanged?.invoke(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    val value = (sb.progress + minimum).coerceIn(minimum, hi)
                    if (onValueCommitted != null) onValueCommitted.invoke(value)
                    else prefs.edit().putInt(key, value).apply()
                }
            })
        }
        parent.addView(bar)
        onValueChanged?.invoke(start)
        return SliderHandle(
            currentValue = { (bar.progress + minimum).coerceIn(minimum, hi) },
            setMinimum = { requested ->
                val nextMinimum = requested.coerceIn(lo, hi)
                if (nextMinimum != minimum) {
                    val oldValue = (bar.progress + minimum).coerceIn(minimum, hi)
                    minimum = nextMinimum
                    bar.max = hi - minimum
                    bar.progress = (oldValue - minimum).coerceIn(0, hi - minimum)
                    val value = (bar.progress + minimum).coerceIn(minimum, hi)
                    valueText.text = value.toString()
                    bar.contentDescription = "$label: $value"
                    onValueChanged?.invoke(value)
                }
            },
            writeTo = { editor -> editor.putInt(key, (bar.progress + minimum).coerceIn(minimum, hi)) },
            setValue = { requested ->
                val safe = requested.coerceIn(minimum, hi)
                bar.progress = safe - minimum
            }
        )
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
        refreshHomeCustomizedBadges()
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

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
