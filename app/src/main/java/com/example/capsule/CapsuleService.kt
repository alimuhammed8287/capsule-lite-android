package com.example.capsule

import android.animation.ValueAnimator
import android.Manifest
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.provider.AlarmClock
import android.view.HapticFeedbackConstants
import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.widget.GridLayout
import android.widget.SeekBar
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.widget.ProgressBar
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs

class CapsuleService : NotificationListenerService() {

    private lateinit var uiCtx: Context
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams

    private var root: View? = null          // transparent window view (includes the tap margin)
    private var pill: LinearLayout? = null  // the black pill inside it

    private lateinit var miniText: TextView
    private lateinit var miniRight: TextView          // collapsed charging pill: the percentage, right of the camera hole
    private lateinit var miniSpacer: View
    private lateinit var chargeBox: LinearLayout      // expanded charging pill
    private lateinit var chargeRing: View
    private lateinit var chargeTime: TextView
    private lateinit var miniChargeRing: View
    private var chargeEta = "ETA —"
    private lateinit var chargeTop: List<TextView>
    private lateinit var chargeBottom: List<TextView>
    private var chargeVolts = 0.0
    private var chargeMa: Int? = null
    private var chargeWatts: Double? = null
    private val chargeRateSamples = mutableListOf<Pair<Long, Int>>()
    private var lastChargeSampleAt = 0L
    private lateinit var navBox: LinearLayout         // expanded navigation pill
    private lateinit var navArrow: ImageView
    private lateinit var navTurn: TextView
    private lateinit var navStreet: TextView
    private lateinit var navEta: TextView
    private lateinit var navArrive: TextView
    private lateinit var navLeft: TextView
    private var navArrowKey = "#"
    private var miniArrowKey = ""
    private var lastMapsPeek = 0L
    private var seenManeuver = 0
    private var lastMapsDump = ""
    private var lastMapsDumpAt = 0L
    private lateinit var icon: ImageView
    private lateinit var textCol: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var qsGrid: GridLayout
    private var qsButtons: Map<String, TextView> = emptyMap()
    private var imageOpen = false       // opened by the image widget gesture
    private var qsOpen = false          // opened by the quick settings gesture
    private var lastQs = false
    private var sizeAnim: ValueAnimator? = null
    // One easing curve for every size change: a quick start and a long, soft landing
    private val easeOut = PathInterpolator(0.2f, 0.9f, 0.2f, 1f)
    private val QS_COLS = 4
    private var stopwatchStart = 0L      // elapsedRealtime when running, 0 when paused
    private var stopwatchBase = 0L       // time already counted before the last pause
    private var lastTempC = 0.0
    private var hiddenByApp = false
    private var lastTickError = 0L
    private var lastFullscreenCheckAt = 0L
    private var lastCallReconcileAt = 0L
    private var lastHeartbeatAt = 0L
    @Volatile private var micRunning = false
    private var ambientRunning = false   // headphones: generated motion, since the mic can't hear them
    private var vizLoop = false
    private val smoothed = FloatArray(5)
    private val mirrorWeights = floatArrayOf(0.5f, 0.8f, 1f, 0.8f, 0.5f)
    private lateinit var widgetImage: ImageView
    private var widgetBitmap: Bitmap? = null
    private val barLevels = FloatArray(5)   // 0..1 level per visualizer bar, written by the mic thread
    private var lastInner = -1
    private var lastMapsWide = false
    private lateinit var artRow: LinearLayout
    private lateinit var miniVizSpacer: View
    private lateinit var replyRow: LinearLayout
    private lateinit var ctrlRow: LinearLayout
    private lateinit var timeRow: LinearLayout
    private lateinit var elapsedText: TextView
    private lateinit var remainText: TextView
    private var actionBtns: List<TextView> = emptyList()
    private var notifActions: List<Notification.Action> = emptyList()
    private var musicLayout = false
    private var pillSeeking = false     // true while a finger drags the pill's seek bar
    private var chargeAlerted = false
    private var fullAlerted = false
    private var boostAt = 0L             // when the last notification or event arrived
    private lateinit var barsRow: LinearLayout
    private var bars: List<View> = emptyList()
    private lateinit var btnAnswer: ImageButton
    private lateinit var btnDecline: ImageButton
    private var chips: List<TextView> = emptyList()

    private var miniW = 0
    private var miniH = 0
    private var expW = 0
    private var expH = 0

    private var expanded = false
    private var showingNotif = false
    private var notifIntent: PendingIntent? = null
    private var notifKey: String? = null
    private var notifLevel = 0   // 0 = none, 1 = title and text only, 2 = with reply and action buttons

    // The last app notification. It is kept after the pill collapses, until it is swiped away,
    // opened, replied to, or removed by the app.
    private class PendingNotif(
        val key: String,
        val pkg: String,
        val title: CharSequence,
        val body: CharSequence,
        val intent: PendingIntent?,
        val color: Int,
        val reply: Notification.Action?,
        val actions: List<Notification.Action>
    )
    private var pending: PendingNotif? = null
    private var downloadKey: String? = null
    private var liveChronometerKey: String? = null
    private var liveChronometerWhen = 0L
    private var liveChronometerCountdown = false
    private var liveChronometerLabel = ""
    private var chronometerExpiredKey: String? = null
    private val mapsNavKeys = mutableSetOf<String>()   // Maps' navigation notification(s) on the phone right now
    private val mapsKeys = mutableSetOf<String>()   // Maps notifications currently shown (navigation active when not empty)
    private var replyAction: Notification.Action? = null

    // Incoming or ongoing call (notification based)
    private var callActive = false
    private var callKey: String? = null
    private var callAnswer: Notification.Action? = null
    private var callHangup: Notification.Action? = null
    private var callState = 0            // 0 = no call, 1 = ringing, 2 = answered
    private var callStartedAt = 0L
    private var callEndedAt = 0L
    private var callDismissed = false    // the user swiped the call pill away (the call itself carries on)
    private var callByMode = false       // the answer was noticed from the phone's audio mode
    private var callQuietTicks = 0
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeBaseX = 0
    private var swiping = false
    private var swipeTracker: android.view.VelocityTracker? = null
    private var callTitle = ""
    private var callSubtitle = ""

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastMapsText = ""
    private var torchOn = false

    // Charging state, filled by the battery receiver
    private var charging = false
    private var chargePct = 0
    private var chargeDetail = ""

    private val prefs by lazy { getSharedPreferences("capsule", MODE_PRIVATE) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "diag") return@OnSharedPreferenceChangeListener
        if (key == "testPill") {
            uiPost { runTestPill() }
            return@OnSharedPreferenceChangeListener
        }
        uiPost {
            if (root == null) return@uiPost
            root?.let { v -> runCatching { wm.removeView(v) } }
            root = null
            pill = null
            expanded = callKeepsOpen()
            buildOverlay()
            msm?.let { pickController(it.getActiveSessions(ComponentName(this, CapsuleService::class.java))) }
            refreshMedia()
        }
    }

    private val collapse = Runnable {
        if (callKeepsOpen()) {
            refreshMedia()   // a ringing call keeps the pill open
        } else {
            showingNotif = false
            notifIntent = null
            notifKey = null
            notifLevel = 0
            replyAction = null
            qsOpen = false
            imageOpen = false
            notifActions = emptyList()
            applyNotifAccent(0)
            progress.visibility = View.GONE
            refreshMedia()
            setExpanded(false)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // A metadata event may carry new artwork even when title/artist stay the same.
            panelArtworkToken = null
            refreshMedia()
            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING && !showingNotif) {
                peek(3000)
            }
        }
        override fun onSessionDestroyed() = pickController(null)
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    // Gestures: tap, double tap, long press, and swipes. Each one runs the action chosen in settings.
    private val gestures by lazy { GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // A call: a tap on the small call indicator opens the call pill
            if (callActive && !expanded) {
                callDismissed = false
                if (callState == 2) peek(8000) else setExpanded(true)
                refreshMedia()
                return true
            }
            // Collapsed pill with a waiting notification: bring it back
            val waiting = pending
            if (waiting != null && !expanded && !showingNotif && !callActive) {
                presentNotif(waiting, 1)
                setExpanded(true)
                armNotifTimer()
                return true
            }
            // First tap on a notification that has replies or buttons: show them
            if (showingNotif && notifLevel == 1 && notifKey != null && notifHasMore() && !callActive) {
                notifLevel = 2
                applyVisibility()
                armNotifTimer()
                return true
            }
            // Expanded charging pill: a tap opens the phone's battery screen
            if (expanded && !showingNotif && !callActive && !qsActive() && !widgetActive() && chargeExpandedShown()) {
                openBatteryScreen()
                return true
            }
            // While music plays, a tap opens the music app
            val mc = controller
            if (mc != null && showMusic() && !showingNotif && !callActive) {
                launch(packageManager.getLaunchIntentForPackage(mc.packageName))
                return true
            }
            // A tap on a notification opens it, as before
            if (showingNotif && notifIntent != null && !callActive) {
                runCatching { notifIntent?.send() }
                if (pending?.key == notifKey) pending = null   // opened, so it is not brought back
                handler.removeCallbacks(collapse)
                collapse.run()
            } else {
                dispatchGesture("g_tap")
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            dispatchGesture("g_double")
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            dispatchGesture("g_long")
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            if (abs(dy) > dp(45) && dy > 0 && abs(dy) > abs(dx) * 1.15f &&
                (pending != null || showingNotif)) {
                // A downward swipe is an explicit expand gesture, not notification dismissal.
                val waiting = pending
                if (!showingNotif && waiting != null) presentNotif(waiting, 1)
                handler.removeCallbacks(collapse)
                setExpanded(true)
                armNotifTimer()
                return true
            }
            if (showingNotif && notifKey != null && abs(dx) > dp(60) && abs(dx) > abs(dy)) {
                if (pending?.key == notifKey) pending = null   // swiped away, so it is not brought back
                cancelNotification(notifKey)
                handler.removeCallbacks(collapse)
                collapse.run()
                return true
            }
            if (abs(dx) < dp(30) && abs(dy) < dp(30)) return false
            val key = if (abs(dx) > abs(dy)) {
                if (dx > 0) "g_right" else "g_left"
            } else {
                if (dy > 0) "g_down" else "g_up"
            }
            dispatchGesture(key)
            return true
        }
    }) }

    // ---------- diagnostics (shown in the app's settings screen) ----------

    private fun diag(msg: String) {
        DiagLog.add(this, msg)
    }

    // ---------- lifecycle ----------

    // Posts work to the main thread. A failure is written to the log instead of crashing the whole service.
    private fun uiPost(block: () -> Unit) {
        handler.post {
            try {
                block()
            } catch (t: Throwable) {
                diag("error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DiagLog.installCrashHandler(this)
        DiagLog.add(this, "service created (process ${android.os.Process.myPid()})")
        DiagLog.logExitReasons(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 40) diag("Android asked to free memory (level $level)")
    }

    override fun onListenerConnected() {
        KeepAlive.listenerConnected = true
        KeepAlive.schedule(this)
        diag("listener connected")
        if (root != null) return
        if (!Settings.canDrawOverlays(this)) {
            diag("overlay permission missing, not started")
            return
        }
        try {
            buildOverlay()
        } catch (e: Exception) {
            diag("overlay FAILED: ${e.javaClass.simpleName}: ${e.message}")
            return
        }
        diag("overlay added: ${lp.width}x${lp.height} at y=${lp.y}")
        startKeepRunning()
        powerSupplyCurrents().take(5).forEach { diag("psy $it") }
        registerBattery()
        registerEvents()
        handler.post(tickRunnable)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        val cn = ComponentName(this, CapsuleService::class.java)
        m.addOnActiveSessionsChangedListener(sessionsListener, cn)
        pickController(m.getActiveSessions(cn))
    }

    override fun onListenerDisconnected() {
        KeepAlive.listenerConnected = false
        diag("listener disconnected (Android disconnected the notification listener)")
        teardown()
        NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java))
    }

    override fun onDestroy() {
        diag("service destroyed")
        teardown()
        super.onDestroy()
    }

    // A small silent notification turns Capsule into a foreground service, which Android and HyperOS
    // are much less willing to stop in the background. It can be switched off in the settings.
    private fun startKeepRunning() {
        if (!prefs.getBoolean("keepNotif", true)) return
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel("keep", "Capsule is running", NotificationManager.IMPORTANCE_MIN)
                        .apply { setShowBadge(false) }
                )
            }
            val builder = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "keep") else Notification.Builder(this)
            val n = builder
                .setSmallIcon(R.drawable.ic_nav_pointer)
                .setContentTitle("Capsule is running")
                .setOngoing(true)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(7001, n, 0x40000000) else startForeground(7001, n)
            diag("keep-running notification on")
        }.onFailure { diag("keep-running notification failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    // Every 5 minutes: one line in the log, so a silent stop leaves a trace of exactly when it happened
    private fun heartbeat() {
        val acc = KeepAlive.ensureAccessibility(this)
        val info = android.app.ActivityManager.MemoryInfo()
        getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
        diag(
            "alive: overlay=${root != null}, accessibility=$acc, free memory ${info.availMem / 1048576} MB" +
                (if (info.lowMemory) " (LOW)" else "")
        )
    }

    private fun teardown() {
        stopSpring()
        clearSnapshot()
        root?.let { clearAnimationsRecursively(it) }
        runCatching { stopForeground(true) }
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(eventReceiver) }
        stopMic()
        closePanel(false)
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        root?.let { v -> runCatching { wm.removeView(v) } }
        root = null
        pill = null
        expanded = false
        showingNotif = false
        callActive = false
        callKey = null
        callState = 0
        callDismissed = false
    }

    // ---------- sizes and overlay ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Extra invisible touch margin around the pill, so taps near its edges register
    private fun touchPad() = dp(10)

    private fun buildOverlay() {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        uiCtx = createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        wm = uiCtx.getSystemService(WindowManager::class.java)

        val pad = touchPad()
        val screenW = uiCtx.resources.displayMetrics.widthPixels
        val cut = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }

        miniW = if (cut != null) cut.width() + dp(20) else dp(96)
        miniH = maxOf(if (cut != null) cut.height() + dp(8) else dp(34), dp(40))
        var topY = if (cut != null) (cut.top - dp(4)).coerceAtLeast(0) else dp(6)
        expW = minOf(screenW - dp(24), dp(340))
        expH = dp(64)

        // Saved settings override the automatic sizes
        if (prefs.contains("miniW")) miniW = dp(prefs.getInt("miniW", 100))
        if (prefs.contains("miniH")) miniH = dp(prefs.getInt("miniH", 40))
        if (prefs.contains("expW")) expW = minOf(screenW - dp(24), dp(prefs.getInt("expW", 340)))
        if (prefs.contains("expH")) expH = dp(prefs.getInt("expH", 64))
        val land = uiCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val topKey = if (land) "topYL" else "topY"
        val offKey = if (land) "offXL" else "offX"
        if (prefs.contains(topKey)) topY = dp(prefs.getInt(topKey, 6))
        val offX = dp(prefs.getInt(offKey, 0))

        val accent = uiCtx.getColor(android.R.color.system_accent1_200)

        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = themeBackground()
            setOnTouchListener { _, e -> onPillTouch(e) }
        }

        miniText = TextView(uiCtx).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
        }
        icon = ImageView(uiCtx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0x55FFFFFF)
            }
        }
        title = TextView(uiCtx).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        subtitle = TextView(uiCtx).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            isSingleLine = false
            maxLines = 15
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(1).toFloat(), 1.0f)
        }
        progress = SeekBar(uiCtx).apply {
            contentDescription = "Track progress"
            max = 1000
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar) {
                    pillSeeking = true
                }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    pillSeeking = false
                    val mc = controller ?: return
                    val dur = mc.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    if (dur > 0L) {
                        val target = (sb.progress.coerceIn(0, sb.max).toLong() * dur / sb.max.coerceAtLeast(1).toLong())
                            .coerceIn(0L, dur)
                        mc.transportControls.seekTo(target)
                    }
                }
            })
            progressBackgroundTintList = ColorStateList.valueOf(0x44FFFFFF)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
                .apply { topMargin = dp(4) }
        }
        textCol = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10); marginEnd = dp(6) }
            addView(title)
            addView(subtitle)
            addView(progress)
        }

        btnPrev = control(R.drawable.ic_prev, accent) {
            controller?.transportControls?.skipToPrevious()
        }
        btnPlay = control(R.drawable.ic_play, accent) { togglePlay() }
        btnNext = control(R.drawable.ic_next, accent) {
            controller?.transportControls?.skipToNext()
        }
        btnAnswer = control(R.drawable.ic_answer, Color.parseColor("#34C759")) {
            runCatching { callAnswer?.actionIntent?.send() }
        }
        btnDecline = control(R.drawable.ic_decline, Color.parseColor("#FF3B30")) {
            runCatching { callHangup?.actionIntent?.send() }
        }

        // Quick reply chips for message notifications that accept a reply
        val replies = (prefs.getString("replies", "Yes,Later,Busy") ?: "Yes,Later,Busy")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        chips = replies.map { text ->
            TextView(uiCtx).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 13f
                isSingleLine = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    setColor(0x33FFFFFF)
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) }
                setOnClickListener { sendReply(text) }
            }
        }

        widgetImage = ImageView(uiCtx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFF111111.toInt())
            }
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
        }
        widgetBitmap = if (prefs.getBoolean("imageWidget", false)) loadWidgetImage() else null
        widgetImage.setImageBitmap(widgetBitmap)
        qsGrid = GridLayout(uiCtx).apply {
            columnCount = QS_COLS
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .apply { setMargins(dp(10), dp(8), dp(10), dp(8)) }
        }
        qsButtons = CapsulePrefs.qsItems.associate { (id, label) ->
            val tv = TextView(uiCtx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                isSingleLine = true
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = GradientDrawable().apply {
                    setColor(0xFF333333.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = dp(40)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener { qsTap(id) }
                setOnLongClickListener {
                    if (id == "stopwatch") {
                        stopwatchStart = 0L
                        stopwatchBase = 0L
                        applyVisibility()
                    }
                    id == "stopwatch"
                }
            }
            qsGrid.addView(tv)
            id to tv
        }
        qsButtons = qsButtons + pinnedPackages().associate { pkg ->
            val id = "app:$pkg"
            id to makeTile(id, appLabel(pkg))
        }
        barsRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8); marginEnd = dp(10) }
            visibility = View.GONE
        }
        bars = List(5) {
            View(uiCtx).apply {
                background = GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dp(2).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(3), dp(6)).apply { marginStart = dp(2) }
            }.also { barsRow.addView(it) }
        }
        row.addView(miniText)
        row.addView(icon)
        row.addView(textCol)
        row.addView(btnPrev)
        row.addView(btnPlay)
        row.addView(btnNext)
        row.addView(barsRow)
        row.addView(qsGrid)
        row.addView(widgetImage)
        chips.forEach { row.addView(it) }
        row.addView(btnDecline)
        row.addView(btnAnswer)

        // Music layout: artwork and visualizer, then controls, then the seek bar with times
        musicLayout = false
        textCol.removeView(progress)
        elapsedText = TextView(uiCtx).apply { setTextColor(Color.WHITE); textSize = 11f; isSingleLine = true }
        remainText = TextView(uiCtx).apply { setTextColor(Color.WHITE); textSize = 11f; isSingleLine = true }
        progress.layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f).apply {
            marginStart = dp(6); marginEnd = dp(6)
        }
        artRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        ctrlRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        timeRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        miniSpacer = View(uiCtx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            visibility = View.GONE
        }
        miniRight = TextView(uiCtx).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            isSingleLine = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
        }
        chargeRing = ChargeRingView(uiCtx) { prefs.getInt("chargeIconStyle", 0) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(12) }
        }
        chargeTime = TextView(uiCtx).apply {
            text = chargeEta
            setTextColor(0xFFE6E6E6.toInt())
            textSize = 12.5f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        chargeTop = listOf(chargeTime, TextView(uiCtx), TextView(uiCtx))
        chargeBottom = listOf(TextView(uiCtx), TextView(uiCtx), TextView(uiCtx))
        (chargeTop + chargeBottom).forEach {
            it.setTextColor(0xFFE6E6E6.toInt())
            it.textSize = 12.5f
            it.isSingleLine = true
            it.ellipsize = TextUtils.TruncateAt.END
        }
        chargeBottom.forEach { it.setPadding(0, dp(3), 0, 0) }
        val chevron = TextView(uiCtx).apply {
            text = "\u203A"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0x44FFFFFF)
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(32)).apply {
                marginStart = dp(6)
                marginEnd = dp(12)
            }
        }
        chargeBox = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
            addView(chargeRing)
            for (i in 0 until 3) {
                addView(LinearLayout(uiCtx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = dp(12) }
                    addView(chargeTop[i])
                    addView(chargeBottom[i])
                })
            }
            addView(chevron)
        }
        navArrowKey = "#"
        miniArrowKey = ""
        navArrow = ImageView(uiCtx).apply {
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
        }
        val navTile = FrameLayout(uiCtx).apply {
            background = GradientDrawable().apply {
                setColor(0xFF0F6E5C.toInt())
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply { marginStart = dp(14) }
            addView(navArrow)
        }
        navTurn = TextView(uiCtx).apply {
            setTextColor(Color.WHITE)
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
        }
        navStreet = TextView(uiCtx).apply {
            setTextColor(0xFFD0D0D0.toInt())
            textSize = 16f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val navMid = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(14) }
            addView(navTurn)
            addView(navStreet)
        }
        navEta = TextView(uiCtx).apply {
            setTextColor(0xFF30D158.toInt())
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
            gravity = Gravity.END
        }
        navArrive = TextView(uiCtx).apply {
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 14f
            isSingleLine = true
            gravity = Gravity.END
        }
        navLeft = TextView(uiCtx).apply {
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 14f
            isSingleLine = true
            gravity = Gravity.END
        }
        val navRight = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
                marginEnd = dp(16)
            }
            addView(navEta)
            addView(navArrive)
            addView(navLeft)
        }
        navBox = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
            addView(navTile)
            addView(navMid)
            addView(navRight)
            addView(TextView(uiCtx).apply {
                text = "×"
                contentDescription = "Exit Maps navigation pill"
                setTextColor(0xFFDDE5F0.toInt())
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(40))
                setOnClickListener {
                    prefs.edit().putBoolean("mapsInfo", false).apply()
                    setExpanded(false)
                    applyVisibility()
                }
            })
        }
        row.removeAllViews()
        artRow.addView(icon)
        artRow.addView(textCol)
        miniVizSpacer = View(uiCtx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            visibility = View.GONE
        }
        artRow.addView(miniVizSpacer)
        artRow.addView(chargeBox)
        artRow.addView(navBox)
        artRow.addView(barsRow)
        ctrlRow.addView(btnPrev)
        ctrlRow.addView(btnPlay)
        ctrlRow.addView(btnNext)
        val exitMedia = TextView(uiCtx).apply {
            text = "×"
            contentDescription = "Exit media player"
            setTextColor(0xFFDDE5F0.toInt())
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(38)).apply { marginStart = dp(4) }
            setOnClickListener {
                controller?.unregisterCallback(controllerCallback)
                controller = null
                setExpanded(false)
                applyVisibility()
            }
        }
        ctrlRow.addView(exitMedia)
        timeRow.addView(elapsedText)
        timeRow.addView(progress)
        timeRow.addView(remainText)
        actionBtns = List(2) { idx ->
            TextView(uiCtx).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                isSingleLine = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    setColor(0x33FFFFFF)
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) }
                setOnClickListener { runNotifAction(idx) }
            }
        }
        miniChargeRing = ChargeRingView(uiCtx) { prefs.getInt("chargeIconStyle", 0) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8); marginEnd = dp(2) }
            visibility = View.GONE
        }
        row.removeAllViews()
        row.addView(miniChargeRing)
        row.addView(miniText)
        row.addView(miniSpacer)
        row.addView(miniRight)
        row.addView(artRow)
        row.addView(ctrlRow)
        row.addView(timeRow)
        row.addView(qsGrid)
        row.addView(widgetImage)
        replyRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        chips.forEach { replyRow.addView(it) }
        actionBtns.forEach { replyRow.addView(it) }
        row.addView(replyRow)
        row.addView(btnDecline)
        row.addView(btnAnswer)
        applyTextStyle()
        val wrap = FrameLayout(uiCtx).apply {
            setOnTouchListener { _, e -> onPillTouch(e) }
            addView(
                row,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(pad, pad, pad, pad) }
            )
        }

        lp = WindowManager.LayoutParams(
            miniW + 2 * pad, miniH + 2 * pad,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offX
            y = (topY - pad).coerceAtLeast(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (expanded) {
            lp.width = expW + 2 * pad
            lp.height = targetInnerH() + 2 * pad
        }

        if (prefs.getBoolean("blur", false)) applyBlur()
        stopSpring()
        pill = row
        root = wrap
        applyVisibility()
        wm.addView(wrap, lp)
    }

    private fun applyTextStyle() {
        val k = prefs.getInt("textScale", 100) / 100f
        val face = Typeface.create(prefs.getString("fontFamily", "default") ?: "default", Typeface.NORMAL)
        for (tv in listOf(miniText, title, subtitle) + chips) tv.typeface = face
        miniText.textSize = 13f * k
        title.textSize = 14f * k
        subtitle.textSize = 12f * k
        subtitle.alpha = 0.7f
        chips.forEach { it.textSize = 13f * k }
    }

    // "Glass" mode is deliberately local to the pill. Android's FLAG_BLUR_BEHIND
    // is a window-level effect and some OEM builds blur far beyond the rounded pill.
    // We therefore use a translucent, bordered surface instead of blurring the whole screen.
    private fun surfaceColor(rgb: Int, opaqueAlpha: Int, glass: Boolean): Int {
        val alpha = if (glass) 158 else opaqueAlpha
        return (alpha shl 24) or (rgb and 0x00FFFFFF)
    }

    // Keep all overlay surfaces on the same theme palette. Only the corner radius differs
    // between the compact pill and the expanded control panel.
    private fun themedSurface(cornerRadiusDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(cornerRadiusDp).toFloat()
        val glass = prefs.getBoolean("blur", false)
        when (prefs.getString("theme", "black")) {
            "frosted" -> {
                // Layered tint and a bright top edge create a glass-like pill without
                // asking Android to blur the entire window behind this overlay.
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    surfaceColor(0x343B49, 0xB8, glass),
                    surfaceColor(0x171C27, 0xC8, glass)
                )
                setStroke(dp(1), 0x88FFFFFF.toInt())
            }
            "futuristic" -> {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    surfaceColor(0x0B2B3B, 0xF7, glass),
                    surfaceColor(0x06111D, 0xF2, glass)
                )
                setStroke(dp(2), 0xFF43D9FF.toInt())
            }
            "ios" -> {
                // Soft graphite gradient gives the iOS-inspired surface more depth without
                // relying on expensive per-frame effects or a permanently running blur.
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    surfaceColor(0x3B3D45, 0xF2, glass),
                    surfaceColor(0x1D1E23, 0xF0, glass)
                )
                setStroke(dp(1), 0x88FFFFFF.toInt())
            }
            "accent" -> {
                setColor(surfaceColor(0x000000, 0xF0, glass))
                setStroke(dp(2), uiCtx.getColor(android.R.color.system_accent1_200))
            }
            else -> {
                setColor(surfaceColor(0x000000, 0xFF, glass))
                setStroke(dp(1), if (glass) 0x44FFFFFF else 0x22FFFFFF)
            }
        }
    }

    private fun themeBackground() = themedSurface(40)

    // Use Android's compositor blur only when the user opts in and the device reports
    // cross-window blur support. This API blurs content behind the overlay window; the
    // translucent themed drawable remains the graceful fallback on unsupported devices.
    private fun applyBlur() {
        // Never request FLAG_BLUR_BEHIND here: on some HyperOS/Xiaomi builds the
        // compositor effect spills outside the rounded overlay and blurs the screen.
        // Keep the system backdrop sharp; themedSurface() supplies the local glass look.
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        lp.dimAmount = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) lp.setBlurBehindRadius(0)
        diag("glass: local pill-only translucent glass enabled; system backdrop blur disabled")
    }

    private fun widgetActive(): Boolean {
        if (widgetBitmap == null || !expanded || callActive || showingNotif) return false
        if (imageOpen) return true
        val playing = showMusic() && controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        return prefs.getBoolean("imageWidget", false) && !playing
    }

    // Loads the chosen picture, scaled down so it stays light in memory
    private fun loadWidgetImage(): Bitmap? {
        val uri = prefs.getString("widgetImage", null) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    // Package names the user hid from the pill and from history
    private fun blockedApps(): Set<String> =
        (prefs.getString("blockedApps", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // An empty allow-list means all apps are eligible, subject to the existing hidden-app list.
    private fun liveActivityAppAllowed(pkg: String): Boolean {
        if (pkg in blockedApps()) return false
        val raw = prefs.getString("liveActivityApps", "").orEmpty()
        if (raw.isBlank()) return true
        return raw.split(',').asSequence().map { it.trim() }.filter { it.isNotEmpty() }.any { it == pkg }
    }

    private fun canPromoteLiveActivity(key: String, incomingPriority: Int): Boolean {
        if (callActive) return false
        val currentKey = when {
            showingNotif && liveChronometerKey != null && notifKey == liveChronometerKey -> liveChronometerKey
            showingNotif && downloadKey != null && notifKey == downloadKey -> downloadKey
            else -> null
        } ?: return true
        if (currentKey == key) return true
        val currentPriority = if (currentKey == liveChronometerKey) {
            LiveActivityPolicy.Kind.TIMER.priority
        } else {
            LiveActivityPolicy.Kind.PROGRESS.priority
        }
        return incomingPriority >= currentPriority
    }

    private fun pinnedPackages(): List<String> =
        if (!prefs.getBoolean("pinnedOn", true)) emptyList()
        else (prefs.getString("pinned", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(4)

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun makeTile(id: String, label: String): TextView {
        val tv = TextView(uiCtx).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFF333333.toInt())
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(40)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { qsTap(id) }
        }
        qsGrid.addView(tv)
        return tv
    }

    // Night hours (for example 22 to 7): true while the pill should be hidden
    private fun inNight(): Boolean {
        if (!prefs.getBoolean("nightHide", false)) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val from = prefs.getInt("nightFrom", 22)
        val to = prefs.getInt("nightTo", 7)
        return if (from <= to) h in from until to else h >= from || h < to
    }

    // The app that sent a notification sets the title colour; without one, the title stays white
    private fun applyNotifAccent(color: Int) {
        val useApp = prefs.getBoolean("appAccent", true) && color != 0
        title.setTextColor(if (useApp) color else Color.WHITE)
    }

    // Keeps the last ten notifications, shown in the History screen
    private fun recordHistory(pkg: String, t: String, body: String) {
        val h = getSharedPreferences("history", MODE_PRIVATE)
        val time = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val lines = (h.getString("list", "") ?: "").split("\n").filter { it.isNotBlank() }
        val entry = "$time|$pkg|${t.replace("\n", " ")}|${body.replace("\n", " ")}"
        h.edit().putString("list", (listOf(entry) + lines).take(10).joinToString("\n")).apply()
    }

    private fun haptic() {
        if (!prefs.getBoolean("haptics", true)) return
        root?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun startTimer() {
        launch(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, 300)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Capsule timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        })
    }

    private fun stopwatchElapsed(): Long =
        stopwatchBase + (if (stopwatchStart > 0) SystemClock.elapsedRealtime() - stopwatchStart else 0L)

    private fun toggleStopwatch() {
        if (stopwatchStart > 0) {
            stopwatchBase = stopwatchElapsed()
            stopwatchStart = 0L
        } else {
            stopwatchStart = SystemClock.elapsedRealtime()
        }
        applyVisibility()
    }

    // Hides the pill while a video or game app is in front (needs usage access). Calls still show.
    private fun checkFullscreen() {
        val want = (prefs.getBoolean("hideFullscreen", false) && !callActive && isFullscreenApp()) ||
            (!callActive && inNight())
        if (want != hiddenByApp) {
            hiddenByApp = want
            root?.visibility = if (want) View.GONE else View.VISIBLE
        }
    }

    private fun isFullscreenApp(): Boolean {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        val pkg = last ?: return false
        if (pkg == packageName) return false
        return runCatching {
            val cat = packageManager.getApplicationInfo(pkg, 0).category
            cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
    }

    // Rebuilds the pill, used when the phone rotates so the landscape position applies
    private fun rebuildOverlay() {
        if (root == null) return
        root?.let { v -> runCatching { wm.removeView(v) } }
        root = null
        pill = null
        expanded = callKeepsOpen()
        hiddenByApp = false
        buildOverlay()
        msm?.let { pickController(it.getActiveSessions(ComponentName(this, CapsuleService::class.java))) }
        refreshMedia()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiPost { rebuildOverlay() }
    }

    private fun control(res: Int, tint: Int, onClick: () -> Unit) = ImageButton(uiCtx).apply {
        setImageResource(res)
        setColorFilter(tint)
        background = null
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(5), dp(5), dp(5), dp(5))
        setOnClickListener {
            onClick()
            peek(4000)
        }
    }

    // ---------- touch and gestures ----------

    private fun onPillTouch(e: MotionEvent): Boolean {
        // Press feedback follows the same reduced-motion policy as pill transitions.
        // Keep the view at its neutral scale when either app or system animations are disabled.
        if (motionEnabled()) {
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    pill?.animate()?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(140)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())?.start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    pill?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(260)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())?.start()
            }
        } else if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            pill?.animate()?.cancel()
            pill?.scaleX = 1f
            pill?.scaleY = 1f
        }
        // Drag sideways to dismiss: a notification is removed, a call pill is closed (the call itself carries on)
        if (swipeTarget() || swiping) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownX = e.rawX
                    swipeDownY = e.rawY
                    swipeBaseX = lp.x
                    swiping = false
                    swipeTracker?.recycle()
                    swipeTracker = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    swipeTracker?.addMovement(e)
                    val dx = e.rawX - swipeDownX
                    val dy = e.rawY - swipeDownY
                    if (!swiping && abs(dx) > dp(12) && abs(dx) > abs(dy) * 1.5f) {
                        swiping = true
                        handler.removeCallbacks(collapse)   // no auto-collapse while the finger is down
                        pill?.animate()?.cancel()
                        pill?.scaleX = 1f
                        pill?.scaleY = 1f
                        // stop the tap and long-press detection for this touch
                        val cancel = MotionEvent.obtain(e)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        gestures.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                    if (swiping) {
                        dragPill(dx)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (swiping) {
                        swipeTracker?.addMovement(e)
                        swipeTracker?.computeCurrentVelocity(1000)
                        val vx = swipeTracker?.xVelocity ?: 0f
                        swipeTracker?.recycle()
                        swipeTracker = null
                        swiping = false
                        finishSwipe(e.rawX - swipeDownX, vx)
                        return true
                    }
                    swipeTracker?.recycle()
                    swipeTracker = null
                }
            }
        }
        if (e.action == MotionEvent.ACTION_OUTSIDE) {
            // Tap anywhere outside the window while expanded: collapse
            if (expanded && !callKeepsOpen()) {
                handler.removeCallbacks(collapse)
                collapse.run()
            }
            return false
        }
        return gestures.onTouchEvent(e)
    }

    // What a sideways swipe can dismiss right now: a notification, or the open call pill
    private fun swipeTarget() = (showingNotif && notifKey != null) || (callActive && expanded)

    // The whole pill window follows the finger, so it can slide right off the screen
    private fun dragPill(dx: Float) {
        val r = root ?: return
        lp.x = swipeBaseX + dx.toInt()
        runCatching { wm.updateViewLayout(r, lp) }
        pill?.alpha = (1f - abs(dx) / dp(220)).coerceIn(0.25f, 1f)
    }

    private fun finishSwipe(dx: Float, vx: Float) {
        val r = root ?: return
        val far = abs(dx) > dp(90) || (abs(vx) > dp(500) && vx * dx > 0)
        if (!motionEnabled()) {
            // Complete swipe handling synchronously for reduced-motion users; don't leave the
            // overlay partially transparent or off-screen while an animator is disabled.
            if (far) dismissSwiped()
            lp.x = swipeBaseX
            runCatching { wm.updateViewLayout(r, lp) }
            pill?.animate()?.cancel()
            pill?.alpha = 1f
            pill?.scaleX = 1f
            pill?.scaleY = 1f
            if (!far && showingNotif) armNotifTimer()
            return
        }
        if (!far) {
            // Not far enough: spring back
            ValueAnimator.ofFloat(dx, 0f).apply {
                duration = 220
                interpolator = easeOut
                addUpdateListener { dragPill(it.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        pill?.alpha = 1f
                        if (showingNotif) armNotifTimer()
                    }
                })
                start()
            }
            return
        }
        val target = (if (dx > 0) 1 else -1) * resources.displayMetrics.widthPixels.toFloat()
        ValueAnimator.ofFloat(dx, target).apply {
            duration = 180
            interpolator = android.view.animation.AccelerateInterpolator()
            addUpdateListener { dragPill(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dismissSwiped()
                    // The pill is smaller now: put it back in its place and fade it in
                    lp.x = swipeBaseX
                    runCatching { wm.updateViewLayout(r, lp) }
                    pill?.alpha = 0f
                    pill?.animate()?.alpha(1f)?.setDuration(220)?.start()
                }
            })
            start()
        }
    }

    private fun dismissSwiped() {
        if (showingNotif && notifKey != null) {
            if (pending?.key == notifKey) pending = null   // swiped away, so it is not brought back
            cancelNotification(notifKey)
            handler.removeCallbacks(collapse)
            collapse.run()
        } else if (callActive) {
            callDismissed = true
            handler.removeCallbacks(collapse)
            collapse.run()
        }
    }

    private fun dispatchGesture(key: String) {
        haptic()
        val action = prefs.getString(key, CapsulePrefs.defaultFor(key)) ?: "none"
        runAction(action)
    }

    private fun runAction(action: String) {
        when (action) {
            "expand" -> if (expanded) {
                handler.removeCallbacks(collapse)
                collapse.run()
            } else {
                peek(6000)
            }
            "play_pause" -> togglePlay()
            "next" -> controller?.transportControls?.skipToNext()
            "previous" -> controller?.transportControls?.skipToPrevious()
            "flashlight" -> toggleTorch()
            "volume_up" -> audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "volume_down" -> audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "brightness_up" -> adjustBrightness(25)
            "brightness_down" -> adjustBrightness(-25)
            "open_maps" -> launch(packageManager.getLaunchIntentForPackage(CapsulePrefs.MAPS_PKG))
            "open_camera" -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "open_dialer" -> launch(Intent(Intent.ACTION_DIAL))
            "image_widget" -> {
                if (widgetBitmap != null) {
                    imageOpen = !imageOpen
                    if (imageOpen) qsOpen = false
                    handler.removeCallbacks(collapse)
                    setExpanded(true)
                    applyVisibility()
                }
            }
            "open_codes" -> launch(Intent(this, CodeActivity::class.java))
            "clock" -> showClock()
            "control_panel" -> togglePanel()
            "quick_settings" -> togglePanel()   // old saved gesture: now opens the control panel
        }
    }

    private fun audio() = getSystemService(AudioManager::class.java)

    // True when the expanded pill should show the charging layout (same rule as the old charging text)
    private fun chargeExpandedShown() =
        charging && showCharging() && !mapsShown() && !(controller != null && showMusic())

    private fun updateChargeBox() {
        val ma = chargeMa
        val w = chargeWatts
        // Compact, consistent glyphs make each charging metric scannable without
        // adding extra rows that would make the floating pill taller.
        chargeTop[0].text = "◷  $chargeEta"
        chargeTop[1].text = "ϟ  %.1f V".format(chargeVolts)
        chargeTop[2].text = "↯  " + if (ma != null) "%.2f A".format(ma / 1000.0) else "— A"
        chargeBottom[0].text = "▰  $chargePct%"
        chargeBottom[1].text = "♨  %.1f\u00B0C".format(lastTempC)
        chargeBottom[1].setTextColor(if (lastTempC >= 42.0) 0xFFFF9F0A.toInt() else 0xFFE6E6E6.toInt())
        chargeBottom[2].text = "⚡  " + if (w != null) "%.1f W".format(w) else "\u2014 W"
    }

    // Opens the phone's battery screen. Tries the standard power-usage screen first, then battery saver settings.
    private fun openBatteryScreen() {
        val tries = listOf(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        for (i in tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(i) }.isSuccess) break
        }
        handler.removeCallbacks(collapse)
        collapse.run()
    }

    private fun launch(i: Intent?) {
        if (i == null) return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }
    }

    private fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    private fun toggleTorch() {
        val cam = getSystemService(CameraManager::class.java)
        runCatching {
            val id = cam.cameraIdList.firstOrNull {
                cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cam.setTorchMode(id, torchOn)
        }
    }

    private fun adjustBrightness(delta: Int) {
        if (!Settings.System.canWrite(this)) return   // needs the brightness permission in settings
        runCatching {
            val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (cur + delta).coerceIn(0, 255))
        }
    }

    // ---------- expand / collapse ----------

    private fun peek(ms: Long) {
        setExpanded(true)
        handler.removeCallbacks(collapse)
        if (!callKeepsOpen()) handler.postDelayed(collapse, ms)
    }

    // App notifications use their own timer: it can be switched off, and its length is a setting
    private fun armNotifTimer() {
        handler.removeCallbacks(collapse)
        if (!callKeepsOpen() && prefs.getBoolean("notifAutoCollapse", true)) {
            handler.postDelayed(collapse, prefs.getInt("notifSeconds", 4) * 1000L)
        }
    }

    // True when tapping the notification would reveal reply or action buttons
    private fun notifHasMore() =
        (replyAction != null && prefs.getBoolean("replyChips", true)) || notifActions.isNotEmpty()

    private fun presentNotif(p: PendingNotif, level: Int) {
        showingNotif = true
        notifIntent = p.intent
        notifKey = p.key
        replyAction = p.reply
        notifActions = p.actions
        notifLevel = level
        title.text = p.title
        applyNotifAccent(p.color)
        subtitle.text = p.body
        icon.setImageDrawable(appIcon(p.pkg))
        refreshMedia()
    }

    private fun setExpanded(e: Boolean) {
        val r = root ?: return
        if (e == expanded) {
            applyVisibility()
            return
        }
        snapshotOld()
        expanded = e
        applyVisibility()
        staggerIn()
        animateSize()
    }

    // Respect both the app's motion toggle and Android's system-wide animator setting.
    // ValueAnimator.areAnimatorsEnabled() is available from API 26; this app supports API 24+.
    private fun motionEnabled(): Boolean {
        if (!prefs.getBoolean("springMotion", true) || prefs.getBoolean("reduceMotion", false)) return false
        return android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()
    }

    // Each visible part of the pill grows in from slightly small and blurry, one after another
    private fun staggerIn() {
        val items = listOf(
            miniText, miniRight, icon, textCol, chargeBox, navBox,
            btnPrev, btnPlay, btnNext, barsRow, btnAnswer, btnDecline
        ) + chips
        if (!motionEnabled()) {
            // If motion is disabled while an entrance is queued, clear the old animation state too.
            for (v in items) {
                v.animate().cancel()
                blurAnimators.remove(v)?.cancel()
                if (android.os.Build.VERSION.SDK_INT >= 31) v.setRenderEffect(null)
                v.alpha = 1f
                v.scaleX = 1f
                v.scaleY = 1f
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastStaggerAt < 80) return
        lastStaggerAt = now
        var k = 0
        for (v in items) {
            // Cancel even hidden children: an earlier delayed animator must not wake up after the
            // content mode has changed and mutate a view that is no longer part of the presentation.
            v.animate().cancel()
            blurAnimators.remove(v)?.cancel()
            if (android.os.Build.VERSION.SDK_INT >= 31) v.setRenderEffect(null)
            v.alpha = 1f
            v.scaleX = 1f
            v.scaleY = 1f
            if (v.visibility != View.VISIBLE) continue
            // Content can change rapidly (for example, a notification arriving during music playback).
            // Reset before starting the new entrance so two transitions never compete.
            val delay = 40L + k * 25L
            k++
            v.alpha = 0f
            v.scaleX = 0.85f
            v.scaleY = 0.85f
            v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.05f))
                .start()
            blurIn(v, delay)
        }
    }

    // Expanded height depends on what the pill is showing: grid, music, notification or call, or charging
    private fun targetInnerH(): Int {
        if (showingNotif && !callActive) {
            val body = subtitle.text?.toString().orEmpty()
            val widthDp = (expW / resources.displayMetrics.density).toInt().coerceAtLeast(180)
            val charsPerLine = ((widthDp - 76) / 6.2f).toInt().coerceIn(18, 48)
            val lines = body.split('\n').sumOf { line ->
                maxOf(1, (line.length + charsPerLine - 1) / charsPerLine)
            }.coerceIn(1, 15)
            val textHeight = dp(20 + lines * 17)
            val actionHeight = if (notifLevel >= 2 && (replyAction != null || notifActions.isNotEmpty())) dp(44) else 0
            val desired = maxOf(dp(64), textHeight + dp(34) + actionHeight)
            val available = (resources.displayMetrics.heightPixels - lp.y - dp(48)).coerceAtLeast(dp(100))
            return minOf(desired, available)
        }
        if (qsActive()) {
            val rows = (CapsulePrefs.qsItems.size + pinnedPackages().size + QS_COLS - 1) / QS_COLS
            return rows * dp(46) + dp(16)
        }
        if (widgetActive()) return dp(prefs.getInt("imageHeight", 120))
        val key = when {
            callActive -> "expH_notif"
            showingNotif && notifLevel >= 2 && (replyAction != null || notifActions.isNotEmpty()) -> "expH_stack"
            showingNotif -> "expH_notif"
            mapsShown() -> "expH_nav"
            controller != null && showMusic() -> "expH_music"
            else -> "expH_charge"
        }
        return dp(prefs.getInt(key, if (key == "expH_music") 120 else if (key == "expH_nav") 112 else if (key == "expH_stack") 104 else 64))
    }

    // ---------- spring motion ----------
    // A damped spring, the way iOS animates the Dynamic Island: it speeds up, overshoots a little, and settles.
    // stiffness = (2*pi / response)^2, damping = 4*pi * ratio / response (mass 1). Width and height have their own
    // springs, and a new target keeps the speed it had, so a change in the middle of a move never jerks.
    private class Spring {
        var value = 0f
        var target = 0f
        var velocity = 0f

        fun step(dt: Float, response: Float, ratio: Float) {
            val w = (2.0 * Math.PI / response).toFloat()
            val k = w * w
            val c = (4.0 * Math.PI * ratio / response).toFloat()
            var left = dt
            while (left > 0f) {
                val h = minOf(left, 1f / 240f)
                val a = -k * (value - target) - c * velocity
                velocity += a * h
                value += velocity * h
                left -= h
            }
        }

        fun settled(eps: Float) = abs(value - target) < eps && abs(velocity) < eps * 8f
    }

    private val springW = Spring()
    private val springH = Spring()
    private var springRunning = false
    private var springLast = 0L

    private val springFrame = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val r = root
            if (r == null || !r.isAttachedToWindow) {
                // Do not keep scheduling display-frame callbacks for an overlay that has been removed.
                // A subsequent animateSize() call will initialise the spring from the current layout.
                springRunning = false
                springLast = 0L
                return
            }
            if (!motionEnabled()) {
                // The system animation scale can change while a spring is already running.
                // Finish at the latest target immediately instead of continuing to animate.
                val pad = touchPad()
                lp.width = (springW.target + 0.5f).toInt().coerceAtLeast(miniW + 2 * pad - dp(4))
                lp.height = (springH.target + 0.5f).toInt().coerceAtLeast(miniH + 2 * pad - dp(4))
                springW.value = springW.target
                springH.value = springH.target
                springW.velocity = 0f
                springH.velocity = 0f
                (pill?.background as? GradientDrawable)?.cornerRadius = minOf((lp.height - 2 * pad) / 2f, dp(40).toFloat())
                runCatching { wm.updateViewLayout(r, lp) }
                syncPanelY()
                springRunning = false
                springLast = 0L
                return
            }
            val dt = if (springLast == 0L) 1f / 60f
            else ((frameTimeNanos - springLast) / 1_000_000_000f).coerceIn(0f, 0.05f)
            springLast = frameTimeNanos
            // Width leads height to create a liquid stretch. Presets tune response time without changing the shape.
            val speed = prefs.getString("springSpeed", "medium") ?: "medium"
            val (widthResponse, heightResponse) = when (speed) {
                "fast" -> 0.31f to 0.38f
                "slow" -> 0.60f to 0.70f
                else -> 0.42f to 0.50f
            }
            springW.step(dt, widthResponse, 0.82f)
            springH.step(dt, heightResponse, 0.88f)
            val done = springW.settled(0.6f) && springH.settled(0.6f)
            if (done) {
                springW.value = springW.target
                springH.value = springH.target
                springW.velocity = 0f
                springH.velocity = 0f
            }
            val pad = touchPad()
            // Never smaller than the camera hole, even in the small bounce at the end of a collapse
            lp.width = (springW.value + 0.5f).toInt().coerceAtLeast(miniW + 2 * pad - dp(4))
            lp.height = (springH.value + 0.5f).toInt().coerceAtLeast(miniH + 2 * pad - dp(4))
            (pill?.background as? GradientDrawable)?.cornerRadius = minOf((lp.height - 2 * pad) / 2f, dp(40).toFloat())
            if (r.isAttachedToWindow) runCatching { wm.updateViewLayout(r, lp) }
            syncPanelY()
            if (done) {
                springRunning = false
                springLast = 0L
            } else {
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun stopSpring() {
        android.view.Choreographer.getInstance().removeFrameCallback(springFrame)
        springRunning = false
        springLast = 0L
    }

    private fun animateSize() {
        val r = root ?: return
        val pad = touchPad()
        val toW = (if (expanded) expW else if (mapsShown() || callCompact()) dp(240) else miniW) + 2 * pad
        val toH = (if (expanded) targetInnerH() else miniH) + 2 * pad
        if (!motionEnabled()) {
            // Motion disabled in app settings or system settings: jump straight to the new size
            stopSpring()
            lp.width = toW
            lp.height = toH
            (pill?.background as? GradientDrawable)?.cornerRadius = minOf((lp.height - 2 * pad) / 2f, dp(40).toFloat())
            if (r.isAttachedToWindow) runCatching { wm.updateViewLayout(r, lp) }
            syncPanelY()
            return
        }
        if (!springRunning) {
            springW.value = lp.width.toFloat()
            springW.velocity = 0f
            springH.value = lp.height.toFloat()
            springH.velocity = 0f
        }
        springW.target = toW.toFloat()
        springH.target = toH.toFloat()
        if (!springRunning) {
            springRunning = true
            springLast = 0L
            android.view.Choreographer.getInstance().postFrameCallback(springFrame)
        }
    }

    // ---------- content change ----------
    private var lastSnapAt = 0L
    private var lastStaggerAt = 0L
    private var snapView: ImageView? = null
    private var snapBitmap: Bitmap? = null
    private var snapBlurAnimator: ValueAnimator? = null

    // Clean up both snapshot animation systems together. ViewPropertyAnimator and the independent
    // RenderEffect ValueAnimator do not cancel each other, so a superseded snapshot must stop both.
    private fun clearSnapshot(expected: ImageView? = null) {
        val current = snapView ?: return
        if (expected != null && current !== expected) return
        snapBlurAnimator?.cancel()
        snapBlurAnimator = null
        current.animate().cancel()
        if (android.os.Build.VERSION.SDK_INT >= 31) current.setRenderEffect(null)
        current.setImageDrawable(null)
        (current.parent as? android.view.ViewGroup)?.removeView(current)
        if (snapView === current) snapView = null
        snapBitmap?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        snapBitmap = null
    }

    // RenderEffect blur uses independent ValueAnimators, so ViewPropertyAnimator.cancel() alone
    // cannot cancel them when a content transition is superseded.
    private val blurAnimators = java.util.WeakHashMap<View, ValueAnimator>()

    // Service teardown can happen while delayed entrance or blur animations are queued.
    // Cancel the whole view tree so callbacks cannot keep mutating a detached overlay.
    private fun clearAnimationsRecursively(view: View) {
        view.animate().cancel()
        blurAnimators.remove(view)?.cancel()
        if (android.os.Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) clearAnimationsRecursively(view.getChildAt(index))
        }
    }

    // A picture of what the pill shows now (without its black background). The old content blurs, shrinks
    // and fades out on top of the pill while the new content comes in underneath.
    private fun snapshotOld() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        if (!motionEnabled()) {
            // If motion was disabled while a prior snapshot was fading out, remove it immediately.
            clearSnapshot()
            return
        }
        val p = pill ?: run { clearSnapshot(); return }
        val r = root as? FrameLayout ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSnapAt < 80 || p.width <= 0 || p.height <= 0 || !r.isAttachedToWindow) return
        lastSnapAt = now
        runCatching {
            clearSnapshot()
            val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            for (i in 0 until p.childCount) {
                val v = p.getChildAt(i)
                if (v.visibility != View.VISIBLE) continue
                canvas.save()
                canvas.translate(v.left.toFloat(), v.top.toFloat())
                v.draw(canvas)
                canvas.restore()
            }
            val iv = ImageView(uiCtx).apply {
                setImageBitmap(bmp)
                layoutParams = FrameLayout.LayoutParams(p.width, p.height, Gravity.CENTER)
            }
            r.addView(iv)
            snapView = iv
            snapBitmap = bmp
            iv.animate()
                .alpha(0f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(140)
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.1f))
                .withEndAction { clearSnapshot(iv) }
                .start()
            snapBlurAnimator = ValueAnimator.ofFloat(0.1f, 8f).apply {
                duration = 140
                addUpdateListener {
                    if (snapView !== iv || !iv.isAttachedToWindow) return@addUpdateListener
                    val radius = it.animatedValue as Float
                    iv.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                    )
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (snapBlurAnimator === animation) snapBlurAnimator = null
                        if (snapView === iv && android.os.Build.VERSION.SDK_INT >= 31) iv.setRenderEffect(null)
                    }
                })
                start()
            }
        }
    }

    // The new content grows in from slightly small and blurry, one part after another, with a small overshoot
    private fun blurIn(v: View, delay: Long) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        blurAnimators.remove(v)?.cancel()
        val animator = ValueAnimator.ofFloat(14f, 0f).apply {
            startDelay = delay
            duration = 300
            addUpdateListener {
                val radius = it.animatedValue as Float
                v.setRenderEffect(
                    if (radius < 0.5f) null
                    else android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                )
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v.setRenderEffect(null)
                    if (blurAnimators[v] === animation) blurAnimators.remove(v)
                }
            })
        }
        blurAnimators[v] = animator
        animator.start()
    }

    // ---------- microphone visualizer ----------
    // Reads the microphone only to measure sound levels. Audio is never saved or sent anywhere.
    // It reacts to sound the microphone can hear, such as music from the speaker. Headphones won't drive it.

    private fun syncMic() {
        // The visualizer is only useful while the pill is visible. Stop microphone capture while the
        // display is off; the next foreground tick will restart it when appropriate.
        val screenInteractive = runCatching {
            getSystemService(android.os.PowerManager::class.java).isInteractive
        }.getOrDefault(true)
        val wants = screenInteractive && prefs.getBoolean("visualizer", false) && pill != null &&
            !callActive && !showingNotif && showMusic() &&
            controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (!wants) {
            if (micRunning || ambientRunning) {
                stopMic()
                applyVisibility()
            }
            return
        }
        if (headphonesConnected()) {
            if (micRunning) stopMic()
            if (!ambientRunning) {
                ambientRunning = true
                startViz()
                applyVisibility()
            }
        } else {
            ambientRunning = false
            if (!micRunning) {
                startMic()
                applyVisibility()
            }
        }
    }

    // A notification, call, or phone event makes the bars jump; the jump fades out over about a second
    private fun boostViz() {
        if (!prefs.getBoolean("vizNotif", true)) return
        boostAt = SystemClock.uptimeMillis()
    }

    private fun startViz() {
        if (vizLoop) return
        vizLoop = true
        handler.post(vizRunnable)
    }

    // Earphones, wired headsets, or Bluetooth audio: the microphone can't pick up their sound
    private fun headphonesConnected(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun stopMic() {
        micRunning = false
        ambientRunning = false
    }

    private fun startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        micRunning = true
        Thread {
            val rate = 44100
            val n = 1024
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), n * 2)
                )
            } catch (e: Exception) {
                micRunning = false
                return@Thread
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                micRunning = false
                rec.release()
                return@Thread
            }
            val buf = ShortArray(n)
            val freqs = doubleArrayOf(120.0, 300.0, 700.0, 1600.0, 3500.0)   // one per bar: bass to treble
            var peak = 1.0
            try {
                rec.startRecording()
                while (micRunning) {
                    val read = rec.read(buf, 0, n)
                    if (read <= 0) continue
                    val power = DoubleArray(5) { i -> Math.sqrt(maxOf(0.0, goertzel(buf, read, freqs[i], rate.toDouble()))) }
                    peak = maxOf(power.maxOrNull() ?: 0.0, peak * 0.98)
                    for (i in 0 until 5) barLevels[i] = (power[i] / peak).toFloat().coerceIn(0f, 1f)
                }
            } catch (e: Exception) {
                micRunning = false
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }.start()
        startViz()
    }

    // Power at one frequency (Goertzel algorithm): cheap enough to run on a phone many times a second
    private fun goertzel(buf: ShortArray, n: Int, freq: Double, rate: Double): Double {
        val k = Math.round(n * freq / rate).toDouble()
        val coeff = 2.0 * Math.cos(2.0 * Math.PI * k / n)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val s0 = buf[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    private val vizRunnable = object : Runnable {
        override fun run() {
            if ((!micRunning && !ambientRunning) || pill == null) {
                vizLoop = false
                return
            }
            if (!micRunning) {
                // Earphones: generated motion, not the real sound
                val t = SystemClock.uptimeMillis() / 1000.0
                for (i in 0 until 5) {
                    val v = 0.5 + 0.25 * Math.sin(t * (2.1 + i * 0.7) + i * 1.3) +
                        0.25 * Math.sin(t * (4.3 + i * 1.1) + i)
                    barLevels[i] = v.toFloat().coerceIn(0f, 1f)
                }
            }
            val style = prefs.getString("vizStyle", "bars") ?: "bars"
            val fade = (1f - (SystemClock.uptimeMillis() - boostAt) / 900f).coerceIn(0f, 1f)
            for ((i, b) in bars.withIndex()) {
                val lv = minOf(1f, barLevels[i] + fade * mirrorWeights[i])
                smoothed[i] += (lv - smoothed[i]) * 0.3f
                val (w, h) = when (style) {
                    "mirror" -> dp(3) to dp(3) + (lv * mirrorWeights[i] * dp(16)).toInt()
                    "dots" -> dp(6) to dp(6)
                    "wave" -> dp(3) to dp(3) + (smoothed[i] * dp(16)).toInt()
                    else -> dp(3) to dp(3) + (lv * dp(14)).toInt()
                }
                b.alpha = if (style == "dots") 0.25f + 0.75f * lv else 1f
                b.layoutParams = LinearLayout.LayoutParams(w, h).apply { marginStart = dp(2) }
                b.requestLayout()
            }
            handler.postDelayed(this, 80)
        }
    }

    // ---------- control panel (stage 2) ----------
    // A separate panel that opens from a gesture: music at the top, brightness and volume bars,
    // and a grid of tiles with Favourites and All pages.

    private var panelOpen = false
    private var panelRoot: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAnim: ValueAnimator? = null
    private var panelLeaving: FrameLayout? = null   // a panel that is still shrinking back into the pill
    private var panelDraggingMusic = false
    private var panelDraggingVolume = false
    private lateinit var pMusicCard: LinearLayout
    private lateinit var pArt: ImageView
    // Prevent reassigning the same artwork bitmap on every 500 ms panel refresh.
    private var panelArtworkToken: String? = null
    private var panelWasPlaying: Boolean? = null
    private lateinit var pTitle: TextView
    private lateinit var pArtist: TextView
    private lateinit var pPlayBtn: ImageButton
    private lateinit var pMusicSeek: SeekBar
    private lateinit var pElapsed: TextView
    private lateinit var pRemain: TextView
    private lateinit var pVol: SeekBar
    private lateinit var pTiles: GridLayout

    private val panelTick = object : Runnable {
        override fun run() {
            if (!panelOpen) return
            refreshPanel()
            handler.postDelayed(this, 500)
        }
    }

    private fun togglePanel() {
        if (panelOpen) closePanel() else openPanel()
    }

    // Charging indicator: an animated battery silhouette with a travelling fill and bolt.
    // This replaces the busy circular spinner in both compact and expanded pill layouts.
    private class ChargeRingView(ctx: Context, private val selectedStyle: () -> Int) : View(ctx) {
        private val outline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            color = 0xFFEAF7F0.toInt()
        }
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = 0xFF56E39A.toInt()
        }
        private val bolt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }
        private val body = android.graphics.RectF()
        private val inner = android.graphics.RectF()
        private val boltPath = android.graphics.Path()
        private var phase = 0f
        private val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                phase = it.animatedFraction
                invalidate()
            }
        }
        private fun updateRunning() {
            if (isAttachedToWindow && isShown && windowVisibility == View.VISIBLE) {
                if (!anim.isStarted) anim.start()
            } else anim.cancel()
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRunning() }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
        override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateRunning() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val unit = minOf(w, h)
            if (unit <= 0f) return
            val bw = unit * 0.62f
            val bh = unit * 0.76f
            val left = (w - bw) / 2f
            val top = (h - bh) / 2f
            val stroke = maxOf(1.5f, unit * 0.055f)
            body.set(left, top, left + bw, top + bh)
            outline.strokeWidth = stroke
            canvas.drawRoundRect(body, unit * 0.13f, unit * 0.13f, outline)
            val terminal = android.graphics.RectF(left + bw * 0.32f, top - unit * 0.10f, left + bw * 0.68f, top + unit * 0.015f)
            canvas.drawRoundRect(terminal, unit * 0.04f, unit * 0.04f, fill)
            val inset = stroke * 1.8f
            inner.set(left + inset, top + inset, left + bw - inset, top + bh - inset)
            val style = selectedStyle().coerceIn(0, 2)
            val wave = (0.5 + 0.5 * kotlin.math.sin(phase * Math.PI * 6.0)).toFloat()
            canvas.save()
            canvas.clipRect(inner)
            when (style) {
                0 -> {
                    // Smooth liquid fill.
                    val level = 0.28f + 0.62f * wave
                    val fillTop = inner.bottom - inner.height() * level
                    canvas.drawRoundRect(inner.left, fillTop, inner.right, inner.bottom, unit * 0.07f, unit * 0.07f, fill)
                }
                1 -> {
                    // Three clear charge segments light up in sequence.
                    val gap = maxOf(1f, unit * 0.035f)
                    val segmentH = (inner.height() - gap * 2f) / 3f
                    for (i in 0..2) {
                        val segment = android.graphics.RectF(
                            inner.left, inner.bottom - (i + 1) * segmentH - i * gap,
                            inner.right, inner.bottom - i * (segmentH + gap)
                        )
                        if (i <= ((phase * 9f).toInt() % 4)) {
                            canvas.drawRoundRect(segment, unit * 0.04f, unit * 0.04f, fill)
                        }
                    }
                }
                else -> {
                    // Gentle pulse mode: a steady base and a breathing bolt.
                    fill.alpha = (95 + 100 * wave).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(inner, unit * 0.07f, unit * 0.07f, fill)
                    fill.alpha = 255
                }
            }
            canvas.restore()
            val u = unit * 0.22f
            val boltScale = if (style == 2) 0.78f + 0.32f * wave else 1f
            canvas.save()
            canvas.translate(w / 2f, h / 2f)
            canvas.scale(boltScale, boltScale)
            boltPath.reset()
            boltPath.moveTo(u * 0.18f, -u)
            boltPath.lineTo(-u * 0.55f, u * 0.12f)
            boltPath.lineTo(-u * 0.05f, u * 0.12f)
            boltPath.lineTo(-u * 0.18f, u)
            boltPath.lineTo(u * 0.58f, -u * 0.16f)
            boltPath.lineTo(u * 0.08f, -u * 0.16f)
            boltPath.close()
            canvas.drawPath(boltPath, bolt)
            canvas.restore()
        }
    }

    // A scroll view that never grows taller than maxH, so a long panel scrolls instead of running off the screen
    private class CappedScroll(ctx: Context, private val maxH: Int) : android.widget.ScrollView(ctx) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    private fun openPanel() {
        if (pill == null || panelOpen) return
        panelLeaving?.let { old ->
            panelAnim?.cancel()
            runCatching { wm.removeView(old) }
            panelLeaving = null
        }
        val root = FrameLayout(uiCtx).apply {
            setPadding(dp(10), 0, dp(10), 0)
            setOnTouchListener { _, e ->
                // A touch outside the panel closes it
                if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
                false
            }
        }
        val card = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = panelBackground()
        }
        val content = LinearLayout(uiCtx).apply { orientation = LinearLayout.VERTICAL }
        buildPanel(content)
        val maxScrollH = (resources.displayMetrics.heightPixels * 0.72f).toInt() - dp(32)
        val scroll = CappedScroll(uiCtx, maxScrollH).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        card.addView(scroll)
        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = panelY()
            // Same coordinates as the pill window, so the panel lines up directly under it
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (prefs.getBoolean("blur", false)) blurParams(params, prefs.getInt("blurRadius", 40))
        card.alpha = 0f   // hidden until the grow animation starts, so nothing flashes
        panelRoot = root
        panelParams = params
        panelOpen = true
        wm.addView(root, params)
        // Once the panel has been measured, grow it out of the pill
        root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                growPanel(card)
                return true
            }
        })
        refreshPanel()
        handler.post(panelTick)
    }

    // The panel sits just under the pill (8dp gap) and follows it when the pill changes size
    private fun panelY(): Int = lp.y + lp.height - touchPad() + dp(8)

    private fun syncPanelY() {
        val v = panelRoot ?: return
        val pr = panelParams ?: return
        pr.y = panelY()
        if (v.isAttachedToWindow) runCatching { wm.updateViewLayout(v, pr) }
    }

    // f = 0: the panel is as wide as the pill and flat, hidden under it. f = 1: fully open.
    private fun applyPanelProgress(card: View, f: Float, fromScaleX: Float) {
        card.scaleX = fromScaleX + (1f - fromScaleX) * f
        card.scaleY = 0.12f + 0.88f * f
        card.alpha = minOf(1f, f * 2.5f)
        card.translationY = -dp(14) * (1f - f)
    }

    private fun panelStartScaleX(card: View): Float {
        val pillW = (lp.width - 2 * touchPad()).coerceAtLeast(dp(40))
        return (pillW.toFloat() / card.width.coerceAtLeast(1)).coerceIn(0.1f, 1f)
    }

    private fun growPanel(card: View) {
        val x0 = panelStartScaleX(card)
        card.pivotX = card.width / 2f
        card.pivotY = 0f
        applyPanelProgress(card, 0f, x0)
        panelAnim?.cancel()
        panelAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = android.view.animation.OvershootInterpolator(0.9f)
            addUpdateListener { applyPanelProgress(card, it.animatedValue as Float, x0) }
            start()
        }
    }

    private fun shrinkPanel(v: FrameLayout) {
        val card = v.getChildAt(0)
        val x0 = panelStartScaleX(card)
        card.pivotX = card.width / 2f
        card.pivotY = 0f
        panelAnim?.cancel()
        panelLeaving = v
        panelAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220
            interpolator = android.view.animation.AccelerateInterpolator(1.3f)
            addUpdateListener { applyPanelProgress(card, it.animatedValue as Float, x0) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    runCatching { wm.removeView(v) }
                    if (panelLeaving === v) panelLeaving = null
                }
            })
            start()
        }
    }

    private fun closePanel(animate: Boolean = true) {
        if (!panelOpen) return
        panelOpen = false
        handler.removeCallbacks(panelTick)
        val v = panelRoot
        panelRoot = null
        panelParams = null
        if (v == null) return
        if (animate && v.isAttachedToWindow && v.getChildAt(0).width > 0) {
            shrinkPanel(v)
        } else {
            runCatching { wm.removeView(v) }
        }
    }

    private fun panelBackground() = themedSurface(28)

    // Match the pill's opt-in compositor blur for the expanded panel. Android may disable
    // cross-window blur dynamically; the tinted panel background is always retained.
    private fun blurParams(params: WindowManager.LayoutParams, radius: Int) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        params.dimAmount = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val supported = runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
            if (supported) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.setBlurBehindRadius(dp(radius.coerceIn(10, 100)))
                diag("panel glass: compositor blur enabled")
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                params.setBlurBehindRadius(0)
                diag("panel glass: blur unavailable; tinted fallback")
            }
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        }
    }

    private fun panelCols() = prefs.getInt("panelCols", 4).coerceIn(4, 7)

    private fun buildPanel(card: LinearLayout) {
        val accent = uiCtx.getColor(android.R.color.system_accent1_200)

        // Now playing: artwork, title, transport buttons, then the seek bar and times
        pMusicCard = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val top = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pArt = ImageView(uiCtx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                // Rounded-square artwork reads like a modern media card rather than a circular crop.
                cornerRadius = dp(12).toFloat()
                setColor(0xFF222222.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val texts = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12); marginEnd = dp(6) }
        }
        pTitle = TextView(uiCtx).apply {
            setTextColor(Color.WHITE); textSize = 16f; isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        pArtist = TextView(uiCtx).apply {
            setTextColor(Color.WHITE); textSize = 13f; alpha = 0.7f; isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(pTitle)
        texts.addView(pArtist)
        pPlayBtn = panelButton(R.drawable.ic_play, accent) { togglePlay() }
        top.addView(pArt)
        top.addView(texts)
        top.addView(panelButton(R.drawable.ic_prev, accent) { controller?.transportControls?.skipToPrevious() })
        top.addView(pPlayBtn)
        top.addView(panelButton(R.drawable.ic_next, accent) { controller?.transportControls?.skipToNext() })

        pMusicSeek = SeekBar(uiCtx).apply {
            contentDescription = "Track position"
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar) { panelDraggingMusic = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    panelDraggingMusic = false
                    val dur = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    if (dur > 0L) {
                        val target = (sb.progress.coerceIn(0, sb.max).toLong() * dur / sb.max.coerceAtLeast(1).toLong())
                            .coerceIn(0L, dur)
                        controller?.transportControls?.seekTo(target)
                    }
                }
            })
        }
        val times = LinearLayout(uiCtx).apply { orientation = LinearLayout.HORIZONTAL }
        pElapsed = TextView(uiCtx).apply { setTextColor(Color.WHITE); textSize = 11f; alpha = 0.7f }
        pRemain = TextView(uiCtx).apply {
            setTextColor(Color.WHITE); textSize = 11f; alpha = 0.7f; gravity = Gravity.END
        }
        times.addView(pElapsed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(pRemain, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pMusicCard.addView(top)
        pMusicCard.addView(pMusicSeek)
        pMusicCard.addView(times)
        card.addView(pMusicCard)

        // Volume
        val sliders = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }
        sliders.addView(TextView(uiCtx).apply { text = "Volume"; setTextColor(Color.WHITE); textSize = 13f })
        pVol = SeekBar(uiCtx).apply {
            contentDescription = "Media volume"
            max = audio().getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) audio().setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
                }
                override fun onStartTrackingTouch(sb: SeekBar) { panelDraggingVolume = true }
                override fun onStopTrackingTouch(sb: SeekBar) { panelDraggingVolume = false }
            })
        }
        sliders.addView(pVol)
        card.addView(sliders)

        pTiles = GridLayout(uiCtx).apply { columnCount = panelCols() }
        card.addView(pTiles)
        buildTiles()
    }

    private fun panelButton(res: Int, tint: Int, onClick: () -> Unit) = ImageButton(uiCtx).apply {
        setImageResource(res)
        setColorFilter(tint)
        background = null
        // Keep media controls discoverable to TalkBack and other accessibility services.
        contentDescription = when (res) {
            R.drawable.ic_prev -> "Previous track"
            R.drawable.ic_play -> "Play or pause playback"
            R.drawable.ic_pause -> "Play or pause playback"
            R.drawable.ic_next -> "Next track"
            else -> "Media control"
        }
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun panelHeader(text: String) = TextView(uiCtx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        alpha = 0.7f
        setPadding(dp(6), dp(10), dp(6), dp(4))
        layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, panelCols()),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        )
    }

    // Wi-Fi: "On" when connected, the network name when location is allowed, "Off" otherwise
    private fun wifiStatus(): String = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching "Off"
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@runCatching "Off"
        val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')
        if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>") "On" else ssid
    }.getOrDefault("Off")

    // Bluetooth: the name of the connected audio device, read from the audio output list (no Bluetooth permission needed)
    private fun bluetoothStatus(): String = runCatching {
        val am = getSystemService(AudioManager::class.java)
        val dev = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        dev?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "None"
    }.getOrDefault("None")

    private fun tileLabel(id: String): String =
        if (id.startsWith("app:")) appLabel(id.removePrefix("app:"))
        else CapsulePrefs.qsItems.firstOrNull { it.first == id }?.second ?: id

    // A tile is shown unless its switch in the app is turned off
    private fun tileShown(id: String) = prefs.getBoolean("tile_$id", true)

    // Long press opens the app's settings; a tap runs the tile.
    private fun tileIconRes(id: String): Int? = when (id) {
        "torch" -> R.drawable.ic_tile_torch
        "vol_up" -> R.drawable.ic_tile_vol_up
        "vol_down" -> R.drawable.ic_tile_vol_down
        "bright_up" -> R.drawable.ic_tile_bright_up
        "bright_down" -> R.drawable.ic_tile_bright_down
        "rotate" -> R.drawable.ic_tile_rotate
        "dnd" -> R.drawable.ic_tile_dnd
        "wifi" -> R.drawable.ic_tile_wifi
        "bluetooth" -> R.drawable.ic_tile_bluetooth
        "data" -> R.drawable.ic_tile_data
        "airplane" -> R.drawable.ic_tile_airplane
        "hotspot" -> R.drawable.ic_tile_hotspot
        "location" -> R.drawable.ic_tile_location
        "timer" -> R.drawable.ic_tile_timer
        "stopwatch" -> R.drawable.ic_tile_stopwatch
        "clock" -> R.drawable.ic_tile_clock
        "date" -> R.drawable.ic_tile_date
        "battery" -> R.drawable.ic_tile_battery
        "codes" -> R.drawable.ic_tile_codes
        "history" -> R.drawable.ic_tile_history
        else -> null
    }

    // The icon above a tile's label. Pinned apps use their own app icon. A missing or broken icon shows nothing.
    private fun tileIcon(id: String): android.graphics.drawable.Drawable? {
        val d = if (id.startsWith("app:")) {
            appIcon(id.removePrefix("app:"))
        } else {
            val res = tileIconRes(id)
            if (res == null) null else runCatching { uiCtx.getDrawable(res)?.mutate() }.getOrNull()
        }
        d?.setBounds(0, 0, dp(22), dp(22))
        return d
    }

    private fun panelTile(id: String): TextView {
        val span = 1
        return TextView(uiCtx).apply {
            tag = id
            text = tileLabel(id)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            compoundDrawablePadding = dp(3)
            setCompoundDrawablesRelative(null, tileIcon(id), null, null)
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (prefs.getBoolean("blur", false)) 0x44FFFFFF else 0x00000000)
            }
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, span, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(64)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            setOnClickListener {
                haptic()
                qsTap(id)
            }
            setOnLongClickListener {
                launch(Intent(this@CapsuleService, MainActivity::class.java))
                true
            }
        }
    }

    private fun buildTiles() {
        pTiles.removeAllViews()
        pTiles.columnCount = panelCols()
        val conn = CapsulePrefs.connectivity.filter { tileShown(it) }
        val more = CapsulePrefs.allTileIds().filter { it !in CapsulePrefs.connectivity && tileShown(it) }
        val apps = pinnedPackages().map { "app:$it" }
        if (conn.isNotEmpty()) {
            pTiles.addView(panelHeader("Connectivity"))
            conn.forEach { pTiles.addView(panelTile(it)) }
        }
        if (more.isNotEmpty()) {
            pTiles.addView(panelHeader("More"))
            more.forEach { pTiles.addView(panelTile(it)) }
        }
        if (apps.isNotEmpty()) {
            pTiles.addView(panelHeader("Apps"))
            apps.forEach { pTiles.addView(panelTile(it)) }
        }
        refreshPanelTiles()
    }

    private fun refreshPanelTiles() {
        val accent = uiCtx.getColor(android.R.color.system_accent1_200)
        val rotateOn = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        }.getOrDefault(false)
        val dndOn = runCatching {
            getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
        for (i in 0 until pTiles.childCount) {
            val v = pTiles.getChildAt(i) as? TextView ?: continue
            val id = v.tag as? String ?: continue
            when (id) {
                "clock" -> v.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                "date" -> v.text = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date())
                "battery" -> v.text = "$chargePct%"
                "wifi" -> v.text = "Wi-Fi: " + wifiStatus()
                "bluetooth" -> v.text = "Bluetooth: " + bluetoothStatus()
            }
            val on = when (id) {
                "torch" -> torchOn
                "rotate" -> rotateOn
                "dnd" -> dndOn
                else -> false
            }
            (v.background as GradientDrawable).setColor(if (on) accent else 0x33FFFFFF)
            v.setTextColor(if (on) Color.BLACK else Color.WHITE)
            // The icon follows the label colour (pinned apps keep their own colours)
            if (!id.startsWith("app:")) v.compoundDrawablesRelative[1]?.setTint(if (on) Color.BLACK else Color.WHITE)
        }
    }

    private fun refreshPanel() {
        if (!panelOpen) return
        val c = controller
        val playing = c != null && showMusic()
        pMusicCard.visibility = vis(playing)
        if (playing && c != null) {
            val md = c.metadata
            val trackTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: "Unknown track"
            val trackArtist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            // The panel refreshes twice a second; avoid needless text/layout/accessibility updates.
            if (pTitle.text.toString() != trackTitle) pTitle.text = trackTitle
            if (pArtist.text.toString() != trackArtist) pArtist.text = trackArtist
            val artworkDescription = "Album artwork for $trackTitle"
            if (pArt.contentDescription?.toString() != artworkDescription) {
                pArt.contentDescription = artworkDescription
            }
            val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val artworkToken = buildString {
                append(c.packageName).append('|')
                append(md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()).append('|')
                append(md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()).append('|')
                append(md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()).append('|')
                append(md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()).append('|')
                append(if (art == null) "app-icon" else "${art.width}x${art.height}")
            }
            if (panelArtworkToken != artworkToken) {
                if (art != null) pArt.setImageBitmap(art)
                else pArt.setImageDrawable(appIcon(c.packageName))
                panelArtworkToken = artworkToken
            }
            val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            // Seeking is only meaningful when the session reports a usable duration.
            pMusicSeek.isEnabled = dur > 0L
            val seekDescription = if (dur > 0L) {
                "Track position, $trackTitle"
            } else {
                "Track position unavailable for this track"
            }
            if (pMusicSeek.contentDescription?.toString() != seekDescription) {
                pMusicSeek.contentDescription = seekDescription
            }
            val st = c.playbackState
            var pos = estimatedPlaybackPosition(st)
            val isPlaying = st?.state == PlaybackState.STATE_PLAYING
            if (panelWasPlaying != isPlaying) {
                pPlayBtn.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                panelWasPlaying = isPlaying
            }
            val playDescription = if (isPlaying) "Pause playback" else "Resume playback"
            if (pPlayBtn.contentDescription?.toString() != playDescription) {
                pPlayBtn.contentDescription = playDescription
            }
            if (!panelDraggingMusic) {
                if (dur > 0) {
                    pos = pos.coerceIn(0L, dur)
                    val targetProgress = ((pos.toDouble() / dur.toDouble()) * 1000.0)
                    .toInt().coerceIn(0, 1000)
                    if (pMusicSeek.progress != targetProgress) pMusicSeek.progress = targetProgress
                    val remainingText = "-" + fmt(dur - pos)
                    if (pRemain.text.toString() != remainingText) pRemain.text = remainingText
                } else {
                    // Do not leave the previous track's seek position visible when duration is unknown.
                    if (pMusicSeek.progress != 0) pMusicSeek.progress = 0
                    if (pRemain.text.isNotEmpty()) pRemain.text = ""
                }
                val elapsedText = fmt(pos)
                if (pElapsed.text.toString() != elapsedText) pElapsed.text = elapsedText
            }
        }

        if (!panelDraggingVolume) pVol.progress = audio().getStreamVolume(AudioManager.STREAM_MUSIC)
        refreshPanelTiles()
    }

    // ---------- quick settings grid ----------

    // The in-pill grid has been removed: it never shows. Its tiles now live in the control panel.
    private fun qsActive(): Boolean = false

    private fun qsTap(id: String) {
        haptic()
        if (id.startsWith("app:")) {
            launch(packageManager.getLaunchIntentForPackage(id.removePrefix("app:")))
            return
        }
        when (id) {
            "torch" -> toggleTorch()
            "vol_up" -> audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "bright_up" -> adjustBrightness(25)
            "bright_down" -> adjustBrightness(-25)
            "rotate" -> toggleRotate()
            "dnd" -> toggleDnd()
            "wifi" -> launch(Intent(Settings.Panel.ACTION_WIFI))
            "bluetooth" -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "data" -> launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            "airplane" -> launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            "hotspot" -> launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            "location" -> launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            "timer" -> startTimer()
            "stopwatch" -> toggleStopwatch()
            "codes" -> launch(Intent(this, CodeActivity::class.java))
            "history" -> launch(Intent(this, HistoryActivity::class.java))
        }
        refreshQsStates()
    }

    private fun toggleRotate() {
        if (!Settings.System.canWrite(this)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        runCatching {
            val cur = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (cur == 1) 0 else 1)
        }
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        runCatching {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE
            )
        }
    }

    // Colours the buttons that are on, using the system accent
    private fun refreshQsStates() {
        if (qsButtons.isEmpty()) return
        val rotateOn = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        }.getOrDefault(false)
        val dndOn = runCatching {
            getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
        // Widgets show live values inside their grid buttons
        val now = java.util.Date()
        qsButtons["clock"]?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
        qsButtons["date"]?.text = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(now)
        qsButtons["battery"]?.text = "$chargePct%"
        val on = mapOf("torch" to torchOn, "rotate" to rotateOn, "dnd" to dndOn)
        val accent = uiCtx.getColor(android.R.color.system_accent1_200)
        qsButtons.forEach { (id, tv) ->
            val active = on[id] == true
            (tv.background as GradientDrawable).setColor(if (active) accent else 0xFF333333.toInt())
            tv.setTextColor(if (active) Color.BLACK else Color.WHITE)
        }
    }

    // ---------- what the pill shows ----------

    private fun showCharging() = prefs.getBoolean("showCharging", true)
    private fun showMusic() = prefs.getBoolean("showMusic", true)

    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE

    private fun applyVisibility() {
        if (pill == null) return
        val qsOn = qsActive()
        val inner = targetInnerH()
        val mapsWide = !expanded && (mapsShown() || callCompact())
        var contentChanged = false
        if (qsOn != lastQs || inner != lastInner || mapsWide != lastMapsWide) {
            snapshotOld()
            lastQs = qsOn
            lastInner = inner
            lastMapsWide = mapsWide
            contentChanged = true
            animateSize()
        }
        val widgetOn = !qsOn && widgetActive()
        val hideAll = qsOn || widgetOn
        val playing = controller != null && showMusic()
        val musicOn = !hideAll && expanded && !callActive && !showingNotif && playing
        val miniMusic = !hideAll && !expanded && !callActive && !showingNotif && playing
        val swOn = stopwatchStart > 0
        val chargeMini = !hideAll && !expanded && !callActive && !showingNotif && controller == null && charging && showCharging()
        val replyOn = !hideAll && expanded && !callActive && showingNotif && notifLevel >= 2 && replyAction != null &&
            prefs.getBoolean("replyChips", true)
        val actionOn = !hideAll && expanded && !callActive && showingNotif && notifLevel >= 2 && notifActions.isNotEmpty()
        val callOn = !hideAll && expanded && callActive
        val chargeOn = !hideAll && expanded && !callActive && !showingNotif && chargeExpandedShown()
        val navOn = !hideAll && expanded && mapsShown()

        val notifStackOn = !hideAll && expanded && !callActive && showingNotif && (replyOn || actionOn)
        setMusicLayout(musicOn || notifStackOn)
        qsGrid.visibility = vis(qsOn)
        widgetImage.visibility = vis(widgetOn)
        if (qsOn) refreshQsStates()

        artRow.visibility = vis(!hideAll && (expanded || miniMusic))
        icon.visibility = vis(!hideAll && (expanded || miniMusic) && !chargeOn && !navOn)
        textCol.visibility = vis(!hideAll && expanded && !chargeOn && !navOn)
        chargeBox.visibility = vis(chargeOn)
        navBox.visibility = vis(navOn)
        if (chargeOn) updateChargeBox()
        if (navOn) updateNavBox()
        barsRow.visibility = vis(!hideAll && (musicOn || miniMusic) && (micRunning || ambientRunning))
        miniVizSpacer.visibility = vis(miniMusic && (micRunning || ambientRunning))
        ctrlRow.visibility = vis(musicOn)
        timeRow.visibility = vis(musicOn)
        btnPrev.visibility = vis(musicOn)
        btnPlay.visibility = vis(musicOn)
        btnNext.visibility = vis(musicOn)

        val mapsMini = !hideAll && !expanded && mapsShown()
        miniChargeRing.visibility = vis(chargeMini)
        miniText.visibility = vis(!hideAll && !expanded && (callActive || swOn || chargeMini || mapsMini))
        miniText.text = when {
            callActive -> if (callState == 2) "" else "Incoming"
            swOn -> "SW " + fmt(stopwatchElapsed())
            mapsMini -> mapsMiniText()
            chargeMini -> ""
            else -> "\u26A1"   // the percentage is shown on the right of the camera hole
        }
        val k = prefs.getInt("textScale", 100) / 100f
        miniText.textSize = if (mapsMini) 15f * k else 13f * k
        miniText.setTextColor(if (!callActive && !swOn && !mapsMini && lastTempC >= 42.0) 0xFFFF9F0A.toInt() else Color.WHITE)
        val chargeSplit = chargeMini && !swOn && !mapsMini
        val mapsSplit = mapsMini && !swOn
        val callSplit = !hideAll && !expanded && callActive && callState == 2
        miniSpacer.visibility = vis(chargeSplit || mapsSplit || callSplit)
        miniRight.visibility = vis(chargeSplit || mapsSplit || callSplit)
        if (callSplit) {
            miniRight.text = callTimerText()
            miniRight.textSize = 15f * k
            miniRight.setTextColor(0xFF30D158.toInt())
        } else if (mapsSplit) {
            miniRight.text = MapsNav.eta.ifEmpty { MapsNav.distanceLeft }
            miniRight.textSize = 15f * k
            miniRight.setTextColor(0xFF30D158.toInt())
        } else {
            miniRight.text = "⚡ $chargePct%"
            miniRight.textSize = 13f * k
            miniRight.setTextColor(if (lastTempC >= 42.0) 0xFFFF9F0A.toInt() else Color.WHITE)
        }
        val arrowBmp = mapsArrow()
        when {
            callSplit -> setMiniIcon("call", R.drawable.ic_answer, 0xFF30D158.toInt())
            mapsSplit && arrowBmp != null -> setMiniIcon("bmp:${MapsNav.arrowVersion}", bmp = arrowBmp, tintBmp = MapsNav.arrowShape)
            mapsSplit -> setMiniIcon("d:${MapsNav.direction}", navArrowRes(MapsNav.direction))
            else -> setMiniIcon(null)
        }

        chips.forEach { it.visibility = vis(replyOn) }
        replyRow.visibility = vis(replyOn || actionOn)
        actionBtns.forEachIndexed { i, b ->
            b.text = notifActions.getOrNull(i)?.title?.toString() ?: ""
            b.visibility = vis(actionOn && i < notifActions.size)
        }
        btnAnswer.visibility = vis(callOn && callAnswer != null && callState == 1)
        btnDecline.visibility = vis(callOn && callHangup != null)
        if (contentChanged) staggerIn()
    }

    // Vertical stack for music (art, controls, seek bar), horizontal row for everything else
    private fun setMusicLayout(music: Boolean) {
        val p = pill ?: return
        if (music == musicLayout) return
        musicLayout = music
        p.orientation = if (music) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        p.gravity = if (music) Gravity.CENTER else Gravity.CENTER_VERTICAL
        val full = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        artRow.layoutParams = if (music) full else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        ctrlRow.layoutParams = if (music) full else
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        timeRow.layoutParams = full
    }

    private fun runNotifAction(i: Int) {
        val a = notifActions.getOrNull(i) ?: return
        runCatching { a.actionIntent.send() }
        handler.removeCallbacks(collapse)
        collapse.run()
    }

    // A call ends when the phone no longer shows any call notification
    private fun reconcileCall() {
        if (!callActive) return
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        if (active.none { it.notification.category == Notification.CATEGORY_CALL }) endCall()
    }

    private fun endCall() {
        callActive = false
        callKey = null
        callAnswer = null
        callHangup = null
        callState = 0
        callDismissed = false
        callByMode = false
        callEndedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(collapse)
        collapse.run()
    }

    // A ringing call keeps the pill open, unless the user swiped it away
    private fun callKeepsOpen() = callActive && callState == 1 && !callDismissed

    // The small green call indicator is shown while a call is answered and the pill is closed
    private fun callCompact() = callActive && callState == 2

    private fun callTimerText(): String {
        val sec = ((SystemClock.elapsedRealtime() - callStartedAt) / 1000).coerceAtLeast(0)
        return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
        else "%d:%02d".format(sec / 60, sec % 60)
    }

    private fun callBecameOngoing(byMode: Boolean) {
        if (callState == 2) return
        callState = 2
        callStartedAt = SystemClock.elapsedRealtime()
        callByMode = byMode
        callQuietTicks = 0
        callDismissed = true   // the pill shrinks to the small green call indicator
        diag("call answered")
        handler.removeCallbacks(collapse)
        setExpanded(false)
        refreshMedia()
    }

    // Called twice a second: notices when a ringing call has been answered, and when an answered call has stopped
    private fun updateCallState() {
        if (!callActive) return
        val mode = audio().mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (callState == 1 && inCall) callBecameOngoing(true)
        if (callState == 2) {
            if (callByMode && !inCall) {
                if (++callQuietTicks >= 6) {
                    diag("call ended (the phone's audio mode went back to normal)")
                    endCall()
                    return
                }
            } else {
                callQuietTicks = 0
            }
            if (expanded) refreshMedia()   // keeps the timer in the open pill moving
        }
    }

    private fun fmt(ms: Long): String {
        val sec = ms / 1000
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    // Estimate the current playback position using the full playback speed (including fractions).
    // MediaController positions are snapshots; extrapolate only while the session is playing.
    private fun estimatedPlaybackPosition(state: PlaybackState?): Long {
        if (state == null) return 0L
        val base = state.position.coerceAtLeast(0L)
        if (state.state != PlaybackState.STATE_PLAYING) return base
        val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        val speed = state.playbackSpeed.toDouble()
        if (!speed.isFinite() || speed <= 0.0) return base
        val rawAdvance = elapsed.toDouble() * speed
        // Saturate before converting/adding so malformed session values cannot wrap Long arithmetic.
        val advanced = if (!rawAdvance.isFinite() || rawAdvance >= Long.MAX_VALUE.toDouble()) {
            Long.MAX_VALUE
        } else {
            rawAdvance.toLong().coerceAtLeast(0L)
        }
        return if (advanced > Long.MAX_VALUE - base) Long.MAX_VALUE else base + advanced
    }

    // Moves the seek bar, and the elapsed and remaining times, while music plays
    private fun updateProgress() {
        if (showingNotif || callActive) return
        val c = controller ?: return
        val md = c.metadata
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        var pos = estimatedPlaybackPosition(st)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        if (subtitle.text.toString() != artist) subtitle.text = artist
        val trackTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        progress.isEnabled = dur > 0L
        val progressDescription = if (dur > 0L) "Track progress, $trackTitle" else "Track progress unavailable for this track"
        if (progress.contentDescription?.toString() != progressDescription) {
            progress.contentDescription = progressDescription
        }
        if (dur > 0) {
            pos = pos.coerceIn(0L, dur)
            if (progress.isIndeterminate) progress.isIndeterminate = false
            if (!pillSeeking) {
                val targetProgress = ((pos.toDouble() / dur.toDouble()) * 1000.0)
                    .toInt().coerceIn(0, 1000)
                if (progress.progress != targetProgress) progress.progress = targetProgress
            }
            val elapsed = fmt(pos)
            val remaining = "-" + fmt(dur - pos)
            if (elapsedText.text.toString() != elapsed) elapsedText.text = elapsed
            if (remainText.text.toString() != remaining) remainText.text = remaining
        } else {
            if (!progress.isIndeterminate) progress.isIndeterminate = true
            val elapsed = fmt(pos)
            if (elapsedText.text.toString() != elapsed) elapsedText.text = elapsed
            if (remainText.text.isNotEmpty()) remainText.text = ""
        }
        if (progress.visibility != View.VISIBLE) progress.visibility = View.VISIBLE
    }

    // Adaptive polling keeps live content responsive without doing UI work at a fixed high rate
    // while the pill is idle. Visualizer frames and the open control panel have their own loops.
    private fun nextTickDelayMs(): Long {
        if (panelOpen) return 500L
        if (callActive || callState == 2 || MapsNav.active || stopwatchStart > 0 || lastQs) return 500L
        if (expanded && (controller != null && showMusic() || showingNotif || widgetActive() || qsActive())) return 500L
        if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING && showMusic()) return 750L
        if (micRunning || ambientRunning) return 750L
        return 1800L
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (pill == null) return
            try {
                val now = SystemClock.uptimeMillis()
                updateProgress()
                updateLiveChronometer()
                if (MapsNav.active && !MapsNav.notifAlive && now - MapsNav.lastSeen > 10000) MapsNav.active = false
                checkMapsManeuver()
                updateCallState()
                if (stopwatchStart > 0 || lastQs || MapsNav.active || callState == 2) applyVisibility()
                syncMic()

                // Use elapsed time rather than tick counts: the scheduler is intentionally adaptive.
                if (now - lastFullscreenCheckAt >= 2000L) {
                    lastFullscreenCheckAt = now
                    checkFullscreen()
                }
                if (now - lastCallReconcileAt >= 2000L) {
                    lastCallReconcileAt = now
                    reconcileCall()
                }
                if (now - lastHeartbeatAt >= 5 * 60 * 1000L) {
                    lastHeartbeatAt = now
                    heartbeat()
                }
            } catch (t: Throwable) {
                // One failing step must never stop the ticker: that would freeze the whole pill.
                val now = SystemClock.uptimeMillis()
                if (now - lastTickError > 30000) {
                    lastTickError = now
                    diag("tick error: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            handler.postDelayed(this, nextTickDelayMs())
        }
    }

    private fun runTestPill() {
        if (pill == null) return
        title.text = "Test song"
        subtitle.text = "Test artist"
        icon.setImageDrawable(appIcon(packageName))
        showingNotif = true
        notifIntent = null
        replyAction = null
        setExpanded(true)
        handler.removeCallbacks(collapse)
        if (!callActive) handler.postDelayed(collapse, 6000)
    }

    private fun refreshMedia() {
        if (pill == null) return
        if (callActive) {
            title.text = callTitle
            subtitle.text = if (callState == 2) callTimerText() + " \u00B7 Ongoing call" else callSubtitle
        } else if (!showingNotif) {
            renderMedia()
        }
        applyVisibility()
    }

    // Collapsed navigation text: the distance to the next turn. The ETA sits on the other side of the camera hole.
    private fun mapsMiniText(): String = MapsNav.turnDistance.ifEmpty { MapsNav.distanceLeft }

    private fun navArrowRes(dir: String): Int = when (dir) {
        "left" -> R.drawable.ic_nav_left
        "right" -> R.drawable.ic_nav_right
        "slight_left" -> R.drawable.ic_nav_slight_left
        "slight_right" -> R.drawable.ic_nav_slight_right
        "straight" -> R.drawable.ic_nav_straight
        "uturn" -> R.drawable.ic_nav_uturn
        "roundabout" -> R.drawable.ic_nav_roundabout
        "arrive" -> R.drawable.ic_nav_arrive
        else -> R.drawable.ic_nav_pointer   // direction not known: a neutral navigation arrow, never a wrong turn
    }

    private fun updateNavBox() {
        val bmp = mapsArrow()
        val key = if (bmp != null) "bmp:${MapsNav.arrowVersion}" else "d:${MapsNav.direction}"
        if (key != navArrowKey) {
            navArrowKey = key
            if (bmp != null) {
                navArrow.setImageBitmap(bmp)
                if (MapsNav.arrowShape) navArrow.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                else navArrow.clearColorFilter()
            } else {
                navArrow.clearColorFilter()
                navArrow.setImageResource(navArrowRes(MapsNav.direction))
            }
        }
        navTurn.text = MapsNav.turnDistance.ifEmpty { MapsNav.distanceLeft.ifEmpty { "\u2014" } }
        navStreet.text = MapsNav.street
        navEta.text = MapsNav.eta.ifEmpty { "\u2014" }
        navArrive.text = MapsNav.arrival
        navLeft.text = if (MapsNav.distanceLeft.isEmpty()) "" else "${MapsNav.distanceLeft} left"
    }

    // The small icon left of the camera hole in the collapsed pill (a turn arrow, or the green phone). key = null: no icon.
    private fun setMiniIcon(
        key: String?, res: Int = 0, tint: Int = Color.WHITE,
        bmp: android.graphics.Bitmap? = null, tintBmp: Boolean = false
    ) {
        val k = key ?: ""
        if (k == miniArrowKey) return
        miniArrowKey = k
        if (key == null) {
            miniText.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val d: android.graphics.drawable.Drawable? = if (bmp != null) {
            android.graphics.drawable.BitmapDrawable(resources, bmp).also { if (tintBmp) it.setTint(tint) }
        } else {
            runCatching { uiCtx.getDrawable(res)?.mutate() }.getOrNull()?.also { it.setTint(tint) }
        }
        d?.setBounds(0, 0, dp(20), dp(20))
        miniText.compoundDrawablePadding = dp(6)
        miniText.setCompoundDrawablesRelative(d, null, null, null)
    }

    // A new turn opens the navigation pill for a few seconds. Distance updates never do.
    private fun checkMapsManeuver() {
        val seq = MapsNav.maneuverSeq
        if (seq != seenManeuver) {
            seenManeuver = seq
            val now = SystemClock.uptimeMillis()
            if (prefs.getBoolean("mapsInfo", false) && mapsShown() && !expanded && now - lastMapsPeek > 8000) {
                lastMapsPeek = now
                peek(5000L)
            }
        }
        if (prefs.getBoolean("debugMaps", false) && MapsNav.rawDump != lastMapsDump) {
            val now = SystemClock.uptimeMillis()
            if (now - lastMapsDumpAt > 4000) {
                lastMapsDump = MapsNav.rawDump
                lastMapsDumpAt = now
                diag("maps text: " + MapsNav.rawDump.take(500))
            }
        }
    }

    // Distances, times and clock values change all the time. Only a change in the instruction itself is a new turn.
    private val mapsSpaceRe = Regex("""[\u00A0\u2007\u2009\u202F]""")
    private val mapsNumberRe = Regex("""\d+([.,:]\d+)*""")
    private val mapsUnitRe = Regex("""\b(km|mi|ft|yd|m|metres|meters|miles|min|mins|hr|hrs|h|am|pm)\b""", RegexOption.IGNORE_CASE)
    private val mapsSeparatorRe = Regex("""[\s|\u00B7\u2022()\-]+""")
    private fun mapsManeuverKey(text: String): String =
        text.replace(mapsSpaceRe, " ").replace(mapsNumberRe, "").replace(mapsUnitRe, "")
            .replace(mapsSeparatorRe, " ").trim().lowercase()

    // The Maps arrow picture, unless the user chose Capsule's own drawn arrows
    private fun mapsArrow(): Bitmap? = if (prefs.getBoolean("mapsOwnArrow", true)) MapsNav.arrow else null

    // Reads what Maps' navigation notification shows: its texts, and the turn arrow picture.
    // Returns true when navigation details were found.
    private fun feedMaps(n: Notification): Boolean {
        val texts = mutableListOf<String>()
        var arrow: Bitmap? = null
        val ex = n.extras
        for (k in listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT
        )) {
            ex.getCharSequence(k)?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        }
        // A custom notification layout holds the real texts and the arrow picture
        @Suppress("DEPRECATION")
        val views = listOfNotNull(n.bigContentView, n.contentView, n.headsUpContentView)
        for (rv in views) {
            val v = runCatching { rv.apply(this, FrameLayout(this)) }.getOrNull() ?: continue
            val found = scrapeViews(v, texts)
            if (arrow == null) arrow = found
        }
        if (arrow == null) {
            arrow = runCatching { n.getLargeIcon()?.loadDrawable(this)?.let { drawableToBitmap(it) } }.getOrNull()
        }
        if (prefs.getBoolean("debugMaps", false)) diag("maps notification: texts=${texts.take(14)}, arrow picture found=${arrow != null}")
        MapsNav.updateFromNotification(texts, arrow)
        return MapsNav.active
    }

    // Collects the texts and the first real picture of a notification layout. Android's own icons
    // (the small app icon, the expand button) are skipped.
    private fun scrapeViews(root: View, texts: MutableList<String>): Bitmap? {
        var found: Bitmap? = null
        fun walk(v: View, depth: Int) {
            if (depth > 25) return
            if (v is TextView) v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
            if (v is ImageView) {
                v.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                val systemIcon = v.id != View.NO_ID &&
                    (runCatching { resources.getResourceName(v.id) }.getOrNull()?.startsWith("android:id/") == true)
                if (found == null && !systemIcon) found = drawableToBitmap(v.drawable)
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable?): Bitmap? {
        if (d == null) return null
        return runCatching {
            val size = 96
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(android.graphics.Canvas(bmp))
            if (MapsNav.isBlank(bmp)) null else bmp
        }.getOrNull()
    }

    // Maps info replaces the music text while navigating and no music is playing
    private fun mapsShown(): Boolean =
        prefs.getBoolean("mapsInfo", false) && MapsNav.active && !callActive && !showingNotif &&
            !(showMusic() && controller?.playbackState?.state == PlaybackState.STATE_PLAYING)

    private fun renderMedia() {
        if (mapsShown()) {
            title.text = listOf(MapsNav.eta, MapsNav.arrival).filter { it.isNotEmpty() }.joinToString(" \u00B7 ")
            val where = listOf(
                MapsNav.distanceLeft.let { if (it.isEmpty()) "" else "$it left" },
                MapsNav.street.ifEmpty { if (MapsNav.turnDistance.isEmpty()) "" else "Turn in ${MapsNav.turnDistance}" }
            ).filter { it.isNotEmpty() }
            subtitle.text = where.joinToString(" \u00B7 ")
            icon.setImageDrawable(appIcon(CapsulePrefs.MAPS_PKG))
            return
        }
        val c = if (showMusic()) controller else null
        if (c == null) {
            if (charging && showCharging()) {
                title.text = "$chargePct% \u00B7 Charging"
                title.setTextColor(if (lastTempC >= 42.0) 0xFFFF9F0A.toInt() else Color.WHITE)
                subtitle.text = chargeDetail
            } else {
                title.text = ""
                subtitle.text = ""
            }
            icon.setImageDrawable(null)
        } else {
            val md = c.metadata
            title.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            subtitle.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (art != null) {
                icon.setImageBitmap(art)
            } else {
                icon.setImageDrawable(appIcon(c.packageName))
            }
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            btnPlay.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
            val playDescription = if (playing) "Pause playback" else "Resume playback"
            if (btnPlay.contentDescription?.toString() != playDescription) {
                btnPlay.contentDescription = playDescription
            }
        }
    }

    private fun pickController(list: List<MediaController>?) {
        controller?.unregisterCallback(controllerCallback)
        val next = if (!showMusic()) null else (
            list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list?.firstOrNull()
            )
        controller = next
        next?.registerCallback(controllerCallback)
        refreshMedia()
        if (next == null && !showingNotif && !callActive) setExpanded(false)
    }

    private fun appIcon(pkg: String) =
        runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    // ---------- calls ----------

    private fun showCall(sbn: StatusBarNotification) {
        // Phones often post the call notification once more just after the call ended: ignore that
        if (!callActive && SystemClock.elapsedRealtime() - callEndedAt < 4000 && audio().mode == AudioManager.MODE_NORMAL) return
        boostViz()
        val n = sbn.notification
        val actions: Array<Notification.Action> = n.actions ?: emptyArray()
        val type = n.extras.getInt("android.callType", 0)   // call-style notifications: 1 incoming, 2 ongoing, 3 screening
        val mode = audio().mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        val ongoing = type == 2 || inCall
        val wasActive = callActive
        callKey = sbn.key
        callActive = true

        // Find the buttons by their names first, then by their position
        fun titleOf(a: Notification.Action) = a.title?.toString() ?: ""
        val answerRe = Regex("answer|accept|pick", RegexOption.IGNORE_CASE)
        val hangRe = Regex("decline|reject|hang|end|disconnect|dismiss|cancel", RegexOption.IGNORE_CASE)
        var answer = actions.firstOrNull { answerRe.containsMatchIn(titleOf(it)) }
        var hang = actions.firstOrNull { it !== answer && hangRe.containsMatchIn(titleOf(it)) }
        if (answer == null && hang == null) {
            if (!ongoing && actions.size >= 2) {
                answer = actions[0]
                hang = actions[1]
            } else {
                hang = actions.firstOrNull()
            }
        }
        if (ongoing) answer = null
        callAnswer = answer
        callHangup = hang

        callTitle = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Call"
        callSubtitle = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Incoming call"
        notifIntent = n.contentIntent
        showingNotif = false
        icon.setImageDrawable(appIcon(sbn.packageName))
        if (!wasActive) {
            callState = 1
            callDismissed = false
            diag("call started: ${sbn.packageName}, type=$type, audio mode=$mode, buttons=${actions.size}")
        }
        if (ongoing) callBecameOngoing(inCall)
        handler.removeCallbacks(collapse)
        if (callState == 1 && !callDismissed) setExpanded(true)
        refreshMedia()
    }

    // ---------- notifications ----------

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handlePosted(sbn)
        } catch (t: Throwable) {
            diag("notification error: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        if (root == null) {
            diag("notif from ${sbn.packageName} ignored: overlay not running")
            return
        }
        if (sbn.packageName == packageName) return
        if (sbn.packageName in blockedApps()) return
        val n = sbn.notification
        boostViz()

        // Calls come first and always show
        if (n.category == Notification.CATEGORY_CALL) {
            uiPost { showCall(sbn) }
            return
        }

        val isMaps = sbn.packageName == CapsulePrefs.MAPS_PKG
        if (isMaps) {
            mapsKeys.add(sbn.key)
            // Maps' navigation notification: with navigation info on, it feeds the pill and is not shown as a notification
            if (sbn.isOngoing || n.category == Notification.CATEGORY_NAVIGATION) {
                mapsNavKeys.add(sbn.key)
                MapsNav.notifAlive = true
                if (prefs.getBoolean("mapsInfo", false) && feedMaps(n)) {
                    uiPost { applyVisibility() }
                    return
                }
            }
        }
        // Driving mode: while Maps is navigating, other notifications stay hidden
        if (!isMaps && mapsKeys.isNotEmpty() && prefs.getBoolean("drivingMode", false)) return

        // Live progress activity: support both correctly categorized progress notifications and
        // ongoing notifications that expose Android's standard progress extras without a category.
        // Preserve the notification tap action so the expanded activity can open its source app.
        val progressMax = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressCurrent = n.extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressIndeterminate = n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasProgress = progressMax > 0 || progressIndeterminate
        if ((n.category == Notification.CATEGORY_PROGRESS || (sbn.isOngoing && hasProgress)) &&
            prefs.getBoolean("showDownloads", true) && liveActivityAppAllowed(sbn.packageName)) {
            val name = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: "In progress"
            val pct = if (progressMax > 0) {
                (progressCurrent.toLong() * 100L / progressMax).toInt().coerceIn(0, 100)
            } else -1
            uiPost {
                if (canPromoteLiveActivity(sbn.key, LiveActivityPolicy.Kind.PROGRESS.priority)) {
                    showDownload(sbn.key, name, pct, progressIndeterminate, n.contentIntent)
                }
            }
            return
        }

        // Timer/stopwatch live activity: Android chronometer notifications expose their base time
        // and countdown flag in public extras. Keep the pill updated locally instead of requiring
        // the source app to repost every second.
        if (sbn.isOngoing && n.`when` > 0L && prefs.getBoolean("liveActivities", true) &&
            liveActivityAppAllowed(sbn.packageName) &&
            n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)) {
            val whenMs = n.`when`
            chronometerExpiredKey = chronometerExpiredKey?.takeUnless { it == sbn.key }
            val countdown = n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
            val name = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Timer"
            val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            uiPost {
                if (canPromoteLiveActivity(sbn.key, LiveActivityPolicy.Kind.TIMER.priority)) {
                    showChronometerActivity(sbn.key, name, body, whenMs, countdown, n.contentIntent, sbn.packageName)
                }
            }
            return
        }

        if (sbn.isOngoing && !isMaps) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (isMaps && !prefs.getBoolean("showMaps", true)) return
        if (!isMaps && !prefs.getBoolean("showNotifs", true)) return

        val t = n.extras.getCharSequence(Notification.EXTRA_TITLE) ?: return
        val body = n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""

        val debugMaps = prefs.getBoolean("debugMaps", false)
        if (isMaps) {
            // Maps updates this notification every ~100 m. Only a changed instruction (not the distance or time) is a new turn.
            val key = mapsManeuverKey("$t | $body")
            val newTurn = key.isNotEmpty() && key != lastMapsText
            if (key.isNotEmpty()) lastMapsText = key
            MapsNav.noteDirection(listOf(t.toString(), body.toString()))
            if (debugMaps) {
                android.util.Log.d("CapsuleMaps", "title=[$t] body=[$body]")
                diag("maps notif: title=[$t] body=[$body] largeIcon=${n.getLargeIcon() != null}")
            }
            if (!newTurn && !debugMaps) return
        }

        if (prefs.getBoolean("historyOn", false)) recordHistory(sbn.packageName, t.toString(), body.toString())
        val reply = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

        val others = n.actions?.filter { it.remoteInputs.isNullOrEmpty() }?.take(2) ?: emptyList()
        uiPost {
            if (callActive) return@uiPost   // a call always stays on top
            // Keep ordinary alerts queued behind an active timer/download instead of interrupting it.
            if (!isMaps && showingNotif &&
                ((liveChronometerKey != null && notifKey == liveChronometerKey) ||
                    (downloadKey != null && notifKey == downloadKey))) {
                pending = PendingNotif(sbn.key, sbn.packageName, t, body, n.contentIntent, n.color, reply, others)
                return@uiPost
            }
            if (isMaps) {
                val nowMs = SystemClock.uptimeMillis()
                if (!debugMaps && prefs.getBoolean("mapsInfo", false) && MapsNav.active && mapsShown()) {
                    // The navigation pill already shows the details: just open it for a moment at a new turn
                    if (nowMs - lastMapsPeek > 8000) {
                        lastMapsPeek = nowMs
                        peek(5000L)
                    }
                    return@uiPost
                }
                lastMapsPeek = nowMs
                // Maps keeps its old behaviour: everything at once, fixed time
                showingNotif = true
                notifIntent = n.contentIntent
                notifKey = sbn.key
                replyAction = reply
                notifActions = others
                notifLevel = 2
                title.text = if (debugMaps) "DEBUG: $t" else t
                applyNotifAccent(n.color)
                subtitle.text = body
                icon.setImageDrawable(appIcon(sbn.packageName))
                refreshMedia()
                peek(if (debugMaps) 10000L else if (reply != null) 8000L else 4000L)
                return@uiPost
            }
            val p = PendingNotif(sbn.key, sbn.packageName, t, body, n.contentIntent, n.color, reply, others)
            pending = p
            if (!prefs.getBoolean("notifAutoExpand", true)) return@uiPost   // kept for a tap on the pill
            presentNotif(p, 1)
            setExpanded(true)
            armNotifTimer()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        try {
            handleRemoved(sbn)
        } catch (t: Throwable) {
            diag("notification removal error: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun handleRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == CapsulePrefs.MAPS_PKG) {
            mapsKeys.remove(sbn.key)
            if (mapsNavKeys.remove(sbn.key) && mapsNavKeys.isEmpty()) {
                // Navigation ended: forget the notification's data and the arrow picture
                MapsNav.notifAlive = false
                MapsNav.active = false
                MapsNav.arrow = null
                MapsNav.arrowVersion++
            }
        }
        uiPost {
            if (pending?.key == sbn.key) pending = null
            reconcileCall()
            val removedLiveActivity = sbn.key == downloadKey || sbn.key == liveChronometerKey
            if (sbn.key == downloadKey) downloadKey = null
            if (sbn.key == liveChronometerKey) {
                liveChronometerKey = null
                liveChronometerWhen = 0L
                liveChronometerLabel = ""
                if (chronometerExpiredKey == sbn.key) chronometerExpiredKey = null
            }
            if (removedLiveActivity && !callActive && showingNotif && notifKey == sbn.key) {
                handler.removeCallbacks(collapse)
                collapse.run()
                // If another ongoing activity remains, promote it instead of leaving the pill idle.
                handler.postDelayed({ restoreNextLiveActivity(sbn.key) }, 180L)
            }
            if (sbn.key == callKey) endCall()
            if (sbn.key == notifKey) {
                notifKey = null
                replyAction = null
                if (!callActive && showingNotif) {
                    handler.removeCallbacks(collapse)
                    collapse.run()
                }
            }
        }
    }

    /** Restore another active download/timer after the currently displayed activity disappears. */
    private fun restoreNextLiveActivity(excludedKey: String) {
        if (pill == null || callActive || showingNotif) return
        val candidates = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrElse {
                diag("live activity restore failed: ${it.javaClass.simpleName}")
                emptyList()
            }
            .filter { sbn ->
                sbn.key != excludedKey && sbn.packageName != packageName &&
                    liveActivityAppAllowed(sbn.packageName) && LiveActivityPolicy.classify(sbn) != null &&
                    when (LiveActivityPolicy.classify(sbn)) {
                        LiveActivityPolicy.Kind.TIMER -> prefs.getBoolean("liveActivities", true)
                        LiveActivityPolicy.Kind.PROGRESS -> prefs.getBoolean("showDownloads", true)
                        null -> false
                    }
            }
        val orderedCandidates = LiveActivityPolicy.ordered(candidates)
        val next = orderedCandidates.firstOrNull()
        if (next != null) {
            runCatching { handlePosted(next) }
                .onFailure { diag("live activity handoff failed: ${it.javaClass.simpleName}") }
        } else {
            val waiting = pending
            if (waiting != null && prefs.getBoolean("notifAutoExpand", true)) {
                presentNotif(waiting, 1)
                setExpanded(true)
                armNotifTimer()
            }
        }
    }

    private fun sendReply(text: String) {
        val action = replyAction ?: return
        val ri = action.remoteInputs?.firstOrNull() ?: return
        val intent = Intent()
        val bundle = Bundle().apply { putCharSequence(ri.resultKey, text) }
        RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
        runCatching { action.actionIntent.send(this, 0, intent) }
        if (pending?.key == notifKey) pending = null   // replied, so it is not brought back
        handler.removeCallbacks(collapse)
        collapse.run()
    }

    // ---------- phone events (unlock, headphones, Bluetooth) ----------

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (!prefs.getBoolean("showEvents", true)) return
            // Headset state is sticky: ignore the copy Android sends when we first register
            if (Build.VERSION.SDK_INT >= 33 && isInitialStickyBroadcast) return
            val msg = when (intent.action) {
                Intent.ACTION_USER_PRESENT -> "Unlocked"
                Intent.ACTION_HEADSET_PLUG ->
                    if (intent.getIntExtra("state", 0) == 1) "Headphones connected" else "Headphones disconnected"
                BluetoothDevice.ACTION_ACL_CONNECTED -> "Bluetooth connected"
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> "Bluetooth disconnected"
                else -> return
            }
            uiPost { showEvent(msg) }
        }
    }

    private fun registerEvents() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, eventReceiver, f, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun showChronometerActivity(
        key: String,
        name: String,
        body: String,
        whenMs: Long,
        countdown: Boolean,
        contentIntent: PendingIntent?,
        sourcePackage: String
    ) {
        if (pill == null || callActive) return
        liveChronometerKey = key
        liveChronometerWhen = whenMs
        liveChronometerCountdown = countdown
        liveChronometerLabel = body
        notifKey = key
        downloadKey = null
        title.text = name
        subtitle.text = body
        icon.setImageDrawable(appIcon(sourcePackage))
        showingNotif = true
        notifIntent = contentIntent
        replyAction = null
        notifActions = emptyList()
        progress.visibility = View.GONE
        handler.removeCallbacks(collapse)
        updateLiveChronometer()
        setExpanded(true)
        applyVisibility()
    }

    private fun finishLiveActivity(key: String?) {
        if (key == null || notifKey != key || !showingNotif || callActive) return
        collapse.run()
        handler.postDelayed({ restoreNextLiveActivity(key) }, 180L)
    }

    private fun updateLiveChronometer() {
        if (liveChronometerKey == null || notifKey != liveChronometerKey || !showingNotif || pill == null) return
        val now = System.currentTimeMillis()
        val delta = if (liveChronometerCountdown) liveChronometerWhen - now else now - liveChronometerWhen
        if (liveChronometerCountdown && delta <= 0L) {
            subtitle.text = "Timer complete"
            if (chronometerExpiredKey != liveChronometerKey) {
                chronometerExpiredKey = liveChronometerKey
                handler.removeCallbacks(collapse)
                val expiredKey = liveChronometerKey
                handler.postDelayed({ finishLiveActivity(expiredKey) }, 2500L)
            }
            return
        }
        val seconds = (delta / 1000L).coerceAtLeast(0L)
        val clock = if (seconds >= 3600L) {
            "%02d:%02d:%02d".format(seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
        } else {
            "%02d:%02d".format(seconds / 60L, seconds % 60L)
        }
        val next = if (liveChronometerLabel.isBlank()) clock else "$clock · $liveChronometerLabel"
        if (subtitle.text.toString() != next) subtitle.text = next
    }

    // Shows a download or other ongoing progress activity in the pill until its notification is removed.
    private fun showDownload(
        key: String,
        name: String,
        pct: Int,
        indet: Boolean,
        contentIntent: PendingIntent? = null
    ) {
        if (pill == null || callActive) return
        downloadKey = key
        liveChronometerKey = null
        notifKey = key
        title.text = name
        subtitle.text = if (indet || pct < 0) "In progress" else "$pct%"
        icon.setImageDrawable(null)
        showingNotif = true
        notifIntent = contentIntent
        replyAction = null
        notifActions = emptyList()
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = indet || pct < 0
        if (!indet && pct >= 0) progress.progress = pct * 10
        handler.removeCallbacks(collapse)
        if (!indet && pct >= 100) {
            subtitle.text = "Complete"
            handler.postDelayed({ finishLiveActivity(key) }, 2200L)
        }
        setExpanded(true)
        applyVisibility()
    }

    // Shows the time and date in the pill for a few seconds
    private fun showClock() {
        if (pill == null || callActive) return
        val now = java.util.Date()
        title.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
        subtitle.text = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(now)
        icon.setImageDrawable(null)
        showingNotif = true
        notifIntent = null
        replyAction = null
        setExpanded(true)
        handler.removeCallbacks(collapse)
        handler.postDelayed(collapse, 4000)
    }

    // Shows a short message in the pill, then returns to the normal state
    private fun showEvent(msg: String) {
        boostViz()
        if (pill == null || callActive) return
        title.text = msg
        subtitle.text = ""
        icon.setImageDrawable(null)
        showingNotif = true
        notifIntent = null
        replyAction = null
        setExpanded(true)
        handler.removeCallbacks(collapse)
        handler.postDelayed(collapse, 2500)
    }

    // ---------- charging ----------

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            try {
                handleBattery(intent)
            } catch (t: Throwable) {
                diag("battery error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun registerBattery() {
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        sticky?.let { handleBattery(it) }
    }

    // Lists every entry under /sys/class/power_supply with its type and raw current (for Diagnostics)
    private fun powerSupplyCurrents(): List<String> {
        val dir = File("/sys/class/power_supply")
        val names = dir.list()?.toList() ?: return emptyList()
        return names.mapNotNull { name ->
            val base = File(dir, name)
            val type = runCatching { File(base, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            val cur = runCatching { File(base, "current_now").readText().trim() }.getOrNull()
            "$name type=$type current=${cur ?: "unreadable"}"
        }
    }

    // Current is normalized to microamps. Some Xiaomi builds return CURRENT_NOW in
    // milliamps despite Android's documented microamp unit; detect that implausibly
    // small reading during active charging and normalize it before calculating watts.
    private fun readCurrentUa(intent: Intent? = null, isCharging: Boolean = charging, pct: Int = chargePct): Long? {
        val bm = getSystemService(BatteryManager::class.java)
        val fromApi = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (fromApi != Int.MIN_VALUE && fromApi != 0) {
            val raw = fromApi.toLong()
            return if (isCharging && pct < 95 && abs(raw) in 1L..9999L) raw * 1000L else raw
        }
        // Some OEMs publish current on ACTION_BATTERY_CHANGED even when the API property is empty.
        val fromIntent = intent?.getIntExtra("current_now", 0)?.toLong() ?: 0L
        if (fromIntent != 0L) return fromIntent

        // Vendors expose live current under different power-supply nodes.
        val dir = File("/sys/class/power_supply")
        val names = dir.list()?.toList().orEmpty().sortedBy { if (it.equals("battery", true)) 0 else 1 }
        for (name in names) {
            val base = File(dir, name)
            val type = runCatching { File(base, "type").readText().trim() }.getOrNull()
            if (type !in setOf("Battery", "USB", "Mains", "Wireless", "USB_PD")) continue
            val v = runCatching { File(base, "current_now").readText().trim().toLong() }.getOrNull()
            if (v != null && v != 0L) return v
            val avg = runCatching { File(base, "current_avg").readText().trim().toLong() }.getOrNull()
            if (avg != null && avg != 0L) return avg
        }
        return null
    }

    private fun updateChargeEta(now: Long, platformEtaMs: Long) {
        if (!charging) {
            chargeRateSamples.clear()
            lastChargeSampleAt = 0L
            chargeEta = "ETA —"
            return
        }
        if (lastChargeSampleAt == 0L || now - lastChargeSampleAt >= 30_000L) {
            chargeRateSamples.add(now to chargePct)
            lastChargeSampleAt = now
        }
        while (chargeRateSamples.isNotEmpty() && now - chargeRateSamples.first().first > 30 * 60_000L) {
            chargeRateSamples.removeAt(0)
        }

        if (platformEtaMs > 0L) {
            chargeEta = formatChargeEta(platformEtaMs / 60_000L)
            return
        }
        val first = chargeRateSamples.firstOrNull()
        val last = chargeRateSamples.lastOrNull()
        if (first != null && last != null) {
            val elapsedMs = last.first - first.first
            val gainedPct = last.second - first.second
            if (elapsedMs >= 90_000L && gainedPct > 0 && chargePct < 100) {
                val pctPerHour = gainedPct * 3_600_000.0 / elapsedMs
                val minutes = (((100 - chargePct) / pctPerHour) * 60.0).toLong()
                if (minutes in 1L..(48L * 60L)) {
                    chargeEta = formatChargeEta(minutes)
                    return
                }
            }
        }
        chargeEta = "ETA —"
    }

    private fun formatChargeEta(minutes: Long): String =
        if (minutes >= 60L) "ETA ${minutes / 60}h ${minutes % 60}m" else "ETA ${minutes}m"

    private fun handleBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        lastTempC = tempC

        val wasCharging = charging
        charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        chargePct = level * 100 / scale
        if (!charging) {
            chargeAlerted = false
            fullAlerted = false
        } else if (prefs.getBoolean("chargeAlert", true)) {
            if (chargePct >= prefs.getInt("chargeAlertAt", 80) && !chargeAlerted) {
                chargeAlerted = true
                uiPost { showEvent("Reached $chargePct%") }
            }
            if (status == BatteryManager.BATTERY_STATUS_FULL && !fullAlerted) {
                fullAlerted = true
                uiPost { showEvent("Fully charged") }
            }
        }

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Charging"
        }

        val parts = mutableListOf(source)
        val currentUa = readCurrentUa(i, charging, chargePct)
        chargeVolts = voltageMv / 1000.0
        chargeMa = currentUa?.let { (abs(it) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        chargeWatts = if (currentUa != null && voltageMv > 0) abs(voltageMv.toDouble() * currentUa.toDouble()) / 1e9 else null
        if (currentUa != null) {
            val mA = abs(currentUa) / 1000L
            val watts = abs(voltageMv.toDouble() * currentUa.toDouble()) / 1e9
            parts.add("$mA mA")
            parts.add("%.1f W".format(watts))
        }
        parts.add("%.2f V".format(voltageMv / 1000.0))
        parts.add("%.1f\u00B0C".format(tempC))
        val ttf = getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()
        updateChargeEta(SystemClock.elapsedRealtime(), ttf)
        if (ttf > 0) parts.add("full in ${ttf / 60000} min")
        chargeDetail = parts.joinToString(" \u00B7 ")
        diag("battery: charging=$charging pct=$chargePct current=${currentUa ?: "unavailable"}uA voltage=${voltageMv}mV")

        refreshMedia()
    }
}
