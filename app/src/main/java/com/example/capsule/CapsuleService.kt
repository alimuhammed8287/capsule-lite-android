package com.example.capsule

import android.animation.ValueAnimator
import android.Manifest
import android.app.NotificationManager
import android.app.KeyguardManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
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
import android.text.Layout
import android.text.StaticLayout
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
    private var chargeFlightView: ImageView? = null
    private var chargeFlightAnimator: ValueAnimator? = null
    private var chargeFlightBitmap: Bitmap? = null
    private var chargeFlightRect = RectF()
    private var chargeFlightPending = false
    // Keep the media artwork as one continuous shared element between compact and expanded layouts.
    private var mediaArtFlightAnimator: ValueAnimator? = null
    private var mediaArtFlightBitmap: Bitmap? = null
    private var mediaArtFlightRect = RectF()
    private var mediaArtFlightPending = false
    private var mediaArtFlightView: ImageView? = null
    private var pillEdgeAnimator: ValueAnimator? = null
    private var chargeEta = "ETA —"
    private lateinit var chargeTop: List<TextView>
    private lateinit var chargeBottom: List<TextView>
    private lateinit var chargeMetricIcons: List<ChargeMetricIconView>
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
    private lateinit var notificationBodyPanel: NotificationBodyPanel
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var miniPlayButton: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnBack10: ImageButton
    private lateinit var btnForward10: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var mediaWaveProgress: SeekBar
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
    private var lockScreenWasActive = false
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
    private var lastChargeMini = false
    private var lastNotificationMini = false
    private lateinit var artRow: LinearLayout
    private lateinit var miniMediaWaveform: MediaWaveformView
    private lateinit var miniVizSpacer: View
    private lateinit var replyRow: LinearLayout
    private lateinit var notificationActionsRow: LinearLayout
    private lateinit var notificationActionsScroll: android.widget.HorizontalScrollView
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
    private lateinit var callAnswerGroup: LinearLayout
    private lateinit var callDeclineGroup: LinearLayout
    private lateinit var callAnswerLabel: TextView
    private lateinit var callDeclineLabel: TextView
    private lateinit var callControlsRow: LinearLayout
    private lateinit var notificationBadge: TextView
    private lateinit var notificationBadgeTarget: FrameLayout
    private lateinit var activityBubble: LinearLayout
    private lateinit var activityBubbleIcon: ImageView
    private lateinit var activityBubbleChargeRing: ChargeRingView
    private lateinit var activityBubbleLabel: TextView
    private lateinit var notificationBubble: LinearLayout
    private lateinit var notificationBubbleIcon: ImageView
    private lateinit var notificationBubbleTitle: TextView
    private lateinit var notificationBubbleBody: TextView
    private var activityBubbleTouchDownX = 0f
    private var activityBubbleTouchDownY = 0f
    private var activityBubbleTouchDownKind: CapsuleActivityKind? = null
    private var tapGestureOwnerCaptured = false
    private var tapGestureOwnerId: String? = null
    private var companionMotionExpansionOverride: Boolean? = null
    private var activityBubbleMotionExpanding = false
    private var notificationBubbleMotionExpanding = false
    private var activityBubbleSwiping = false
    private var activityBubbleSlotActive = false
    private var activityBubbleLastSlotWidth = 0
    private var notificationBubbleSlotActive = false
    private var notificationBubbleTargetVisible = false
    private var notificationBubbleHidePending = false
    private var notificationBubbleLastSlotWidth = 0
    private var notificationBubbleKey: String? = null
    private var notificationBubbleDownX = 0f
    private var notificationBubbleDownY = 0f
    private var notificationBubbleSwiping = false
    private var activityBubbleTargetVisible = false
    private var activityBubbleHidePending = false
    private var activityBubbleKey: String? = null
    private var suppressedActivityBubbleKey: String? = null
    private var focusedActivityId: String? = null
    private var lastActivityBubbleVisible = false
    private lateinit var notificationPrevious: TextView
    private lateinit var notificationPosition: TextView
    private lateinit var notificationNext: TextView
    private lateinit var mediaNotificationCard: LinearLayout
    private lateinit var notificationQueueStack: android.widget.ScrollView
    private lateinit var notificationQueueStackRows: LinearLayout
    private lateinit var taskPreviewPanel: LinearLayout
    private lateinit var taskPreviewTitle: TextView
    private lateinit var taskPreviewItems: LinearLayout
    private var taskPreviewPage = 0
    private var taskPreviewDownX = 0f

    private fun renderTaskPreview() {
        if (!::taskPreviewItems.isInitialized) return
        val textScale = prefs.getInt("sectionTextScale_tasks", 100).coerceIn(80, 160) / 100f
        val filter = prefs.getString("taskPreviewList", CapsuleTaskStore.ALL_LISTS) ?: CapsuleTaskStore.ALL_LISTS
        val tasks = CapsuleTaskStore.filtered(prefs, filter)
        val limit = prefs.getInt("taskPreviewLimit", 3).coerceIn(1, 8)
        val pageCount = ((tasks.size + limit - 1) / limit).coerceAtLeast(1)
        taskPreviewPage = taskPreviewPage.coerceIn(0, pageCount - 1)
        val start = taskPreviewPage * limit
        val visible = tasks.drop(start).take(limit)
        taskPreviewTitle.text = if (tasks.isEmpty()) "No upcoming tasks" else
            "${if (filter == CapsuleTaskStore.ALL_LISTS) "All tasks" else filter} · ${taskPreviewPage + 1}/$pageCount"
        taskPreviewTitle.textSize = 13f * textScale
        taskPreviewItems.removeAllViews()
        visible.forEach { task ->
            val line = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(4), dp(3), dp(4))
            }
            line.addView(TextView(uiCtx).apply {
                text = if (task.starred) "★" else "○"
                textSize = 15f * textScale
                setTextColor(if (task.starred) pillStyle().accent else pillStyle().secondary)
                layoutParams = LinearLayout.LayoutParams(dp(25), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            line.addView(TextView(uiCtx).apply {
                text = task.title + (task.dueAtMillis?.let { " · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "")
                textSize = 12f * textScale
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(pillStyle().primary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            taskPreviewItems.addView(line)
        }
        if (visible.isEmpty()) {
            taskPreviewItems.addView(TextView(uiCtx).apply {
                text = "Add a task from the full Task Manager."
                textSize = 11f
                setTextColor(pillStyle().secondary)
                setPadding(dp(3), dp(5), dp(3), dp(5))
            })
        }
        val divider = TextView(uiCtx).apply {
            text = if (tasks.size > limit) "Swipe to browse · ${tasks.size} tasks" else "View only · changes are made in Task Manager"
            textSize = 10f
            setTextColor(pillStyle().secondary)
            setPadding(dp(3), dp(4), dp(3), 0)
        }
        taskPreviewItems.addView(divider)
    }
    private lateinit var mediaNotificationHeader: LinearLayout
    private lateinit var mediaNotificationContent: LinearLayout
    private lateinit var mediaNotificationActionsRow: LinearLayout
    private lateinit var mediaNotificationActionButtons: List<TextView>
    private lateinit var mediaNotificationIcon: ImageView
    private lateinit var mediaNotificationTitle: TextView
    private lateinit var mediaNotificationBody: TextView
    private lateinit var mediaNotificationPosition: TextView
    private var mediaNotificationKey: String? = null
    private var mediaNotificationRenderedKey: String? = null
    private var mediaNotificationDownX = 0f
    private var mediaNotificationDownY = 0f
    private var mediaNotificationExpanded = false
    private var notificationBodyDownX = 0f
    private var notificationBodyDownY = 0f
    private var notificationBodyHorizontalSwipe = false
    private var chips: List<TextView> = emptyList()

    private var miniW = 0
    private var miniH = 0
    private var expW = 0
    private var expH = 0
    // Persist the resolved overlay offsets so secondary overlays can follow the main pill.
    private var resolvedTopY = 0
    private var resolvedOffX = 0

    private var expanded = false
    private var showingNotif = false
    // User-initiated queue browsing may temporarily surface a notification above media/navigation.
    // Calls, stopwatch, and live timer activities keep their normal priority.
    private var userFocusedNotification = false
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
        val actions: List<Notification.Action>,
        val isMissedCall: Boolean = false,
        val receivedAt: Long = System.currentTimeMillis()
    )
    private var pending: PendingNotif? = null
    private val notificationQueue = LinkedHashMap<String, PendingNotif>()
    private var notificationOverflow = 0
    private val maxQueuedNotifications = 20
    private var lastLockscreenSummaryCount = -1
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
    private var expandSwipeDownX = 0f
    private var expandSwipeDownY = 0f
    private var expandGestureActivityId: String? = null
    private var expandingBySwipe = false
    private var expandSwipeCaptured = false
    private var interceptingPillDownSwipe = false
    private var expandSwipeDelegatedToChild = false
    private var swipeBaseX = 0
    private var swiping = false
    private var swipeTracker: android.view.VelocityTracker? = null
    private var callTitle = ""
    private var callSubtitle = ""

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pausedMediaCompactGraceMs = 2 * 60 * 1000L
    private var lastMediaPlayingAtElapsed = 0L
    private var mediaWasPlaying = false
    private var mediaCompactGraceUntilElapsed = 0L
    private val mediaCompactGraceExpiry = object : Runnable {
        override fun run() {
            val remaining = mediaCompactGraceUntilElapsed - SystemClock.elapsedRealtime()
            if (remaining > 0L) {
                handler.postDelayed(this, remaining)
            } else {
                mediaCompactGraceUntilElapsed = 0L
                refreshMedia()
            }
        }
    }
    private var lastMapsText = ""
    private var torchOn = false

    private fun mediaPlaybackState() = controller?.playbackState?.state

    private fun mediaIsPlaying() = mediaPlaybackState() == PlaybackState.STATE_PLAYING

    private fun mediaSessionCanResume(): Boolean = controller != null && showMusic() && when (mediaPlaybackState()) {
        PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> true
        else -> false
    }

    private fun mediaPauseGraceActive(): Boolean {
        val state = mediaPlaybackState()
        val pauseOrBuffering = state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
        return mediaCompactGraceUntilElapsed > SystemClock.elapsedRealtime() && pauseOrBuffering
    }

    private fun mediaCompactActivityActive(): Boolean =
        showMusic() && controller != null && (mediaIsPlaying() || mediaPauseGraceActive())

    /** Keep a resumable session in the collapsed pill briefly, then free the compact surface. */
    private fun updateMediaPauseGrace(state: PlaybackState?) {
        val now = SystemClock.elapsedRealtime()
        when (state?.state) {
            PlaybackState.STATE_PLAYING -> {
                lastMediaPlayingAtElapsed = now
                mediaWasPlaying = true
                mediaCompactGraceUntilElapsed = 0L
                handler.removeCallbacks(mediaCompactGraceExpiry)
            }
            PlaybackState.STATE_PAUSED -> {
                if (mediaWasPlaying) lastMediaPlayingAtElapsed = now
                mediaWasPlaying = false
                val until = lastMediaPlayingAtElapsed + pausedMediaCompactGraceMs
                if (lastMediaPlayingAtElapsed > 0L && now < until) {
                    mediaCompactGraceUntilElapsed = until
                    handler.removeCallbacks(mediaCompactGraceExpiry)
                    handler.postDelayed(mediaCompactGraceExpiry, until - now)
                } else {
                    mediaCompactGraceUntilElapsed = 0L
                    handler.removeCallbacks(mediaCompactGraceExpiry)
                }
            }
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> {
                lastMediaPlayingAtElapsed = 0L
                mediaWasPlaying = false
                mediaCompactGraceUntilElapsed = 0L
                handler.removeCallbacks(mediaCompactGraceExpiry)
            }
        }
    }

    private fun chargeWaveSpacingDp(): Int {
        val configuredSpacing = prefs.getInt("chargeWaveSpacingDp", -1)
        if (configuredSpacing >= 3) return configuredSpacing.coerceIn(3, 24)
        // Convert the old lobe-count preference into equivalent spacing without rewriting
        // preferences from a service callback (which would trigger an overlay rebuild).
        val oldLobes = prefs.getInt("chargeRingDensity", 7).coerceIn(3, 32)
        val ringSize = prefs.getInt("chargeRingSize", 48).coerceIn(36, 80)
        return (2.0 * Math.PI * ringSize * 0.36 / oldLobes).toInt().coerceIn(3, 24)
    }

    /** Circular Wave has its own slower range; old speed values retain their old timing. */
    private fun chargeWaveCycleDurationMs(): Long {
        val selectedWaveSpeed = prefs.getInt("chargeWaveSpeed", -1)
        if (selectedWaveSpeed in 1..40) {
            return (24_000L - (selectedWaveSpeed - 1) * 600L).coerceIn(600L, 24_000L)
        }
        val legacySpeed = prefs.getInt("chargeRingSpeed", 5).coerceIn(1, 30)
        return (8_000L - (legacySpeed - 1) * 255L).coerceIn(580L, 8_000L)
    }

    private val chargeWaveMotionEpochMs = android.os.SystemClock.uptimeMillis()

    /** All live ring sizes sample the same clock so their charged contour stays in phase. */
    private fun chargeWavePhase(): Float {
        val duration = chargeWaveCycleDurationMs()
        val elapsed = (android.os.SystemClock.uptimeMillis() - chargeWaveMotionEpochMs).coerceAtLeast(0L)
        return (elapsed % duration).toFloat() / duration
    }

    private fun chargeRemainderThicknessDp(): Int =
        prefs.getInt("chargeRemainderThickness", 2).coerceIn(1, 6)

    private fun chargeRemainderColor(): Int = when (prefs.getInt("chargeRemainderColor", 0)) {
        1 -> 0xFF424242.toInt()
        else -> 0xFF777777.toInt()
    }

    // Charging state, filled by the battery receiver
    private var charging = false
    private var chargePct = 0
    private var chargeDetail = ""
    private var chargePlugged = 0

    private val prefs by lazy { getSharedPreferences("capsule", MODE_PRIVATE) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "diag" || key == "queueStateSummary") return@OnSharedPreferenceChangeListener
        if (key == "notificationBadgeSize" || key == "notificationBadgeColor") {
            uiPost { applyNotificationBadgeAppearance() }
            return@OnSharedPreferenceChangeListener
        }
        if (key == "testPill") {
            uiPost { runTestPill() }
            return@OnSharedPreferenceChangeListener
        }
        uiPost {
            if (root == null) return@uiPost
            cancelChargeIconFlight()
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
            userFocusedNotification = false
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
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaPauseGrace(state)
            refreshMedia()
        }
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
            // Keep the identity of the surface that received ACTION_DOWN. A queued
            // notification arriving during the tap must not redirect it to another pill.
            val tapOwner = if (tapGestureOwnerCaptured) tapGestureOwnerId else activityWinner()
            tapGestureOwnerCaptured = false
            tapGestureOwnerId = null
            // A call: a tap on the small call indicator opens the call pill
            if (callActive && !expanded) {
                callDismissed = false
                if (callState == 2) peek(8000) else setExpanded(true)
                refreshMedia()
                return true
            }
            // Collapsed pill with a waiting notification: bring it back
            val waiting = pending
            if (waiting != null && !expanded && !showingNotif && !callActive &&
                tapOwner in setOf(null, "notifications")) {
                mediaNotificationKey = waiting.key
                openMediaNotification()
                return true
            }
            // First tap on a notification that has replies or buttons: show them
            if (showingNotif && tapOwner in setOf(null, "notifications", "live") &&
                notifLevel == 1 && notifKey != null && notifHasMore() && !callActive) {
                notifLevel = 2
                applyVisibility()
                armNotifTimer()
                return true
            }
            // Expanded charging pill: a tap opens the phone's battery screen
            if (tapOwner == "charging" && expanded && !showingNotif && !callActive &&
                !qsActive() && !widgetActive() && chargeExpandedShown()) {
                openBatteryScreen()
                return true
            }
            // A collapsed media tap follows the configured gesture (normally expand). Opening the
            // source player is a deliberate tap from the expanded media surface only.
            val mc = controller
            if (tapOwner == "media" && mc != null && showMusic() && !showingNotif && !callActive) {
                if (!expanded) dispatchGesture("g_tap")
                else launch(packageManager.getLaunchIntentForPackage(mc.packageName))
                return true
            }
            // A tap on a notification opens it, as before
            if (showingNotif && tapOwner in setOf(null, "notifications", "live") &&
                notifIntent != null && !callActive) {
                runCatching { notifIntent?.send() }
                removeQueuedNotification(notifKey)
                handler.removeCallbacks(collapse)
                collapse.run()
            } else if (tapOwner == null || tapOwner == activityWinner()) {
                dispatchGesture("g_tap")
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            tapGestureOwnerCaptured = false
            tapGestureOwnerId = null
            dispatchGesture("g_double")
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            tapGestureOwnerCaptured = false
            tapGestureOwnerId = null
            dispatchGesture("g_long")
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            tapGestureOwnerCaptured = false
            tapGestureOwnerId = null
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            val owner = mainPillSwipeOwner()
            if (abs(dy) > dp(45) && dy > 0 && abs(dy) > abs(dx) * 1.15f &&
                mainPillDownSwipeExpands(owner)) {
                // Expand the activity under the touch. A queued notification cannot steal a
                // charging/media/Maps gesture from the main pill.
                openMainPillOwnerAfterDownSwipe(owner)
                handler.removeCallbacks(collapse)
                if (!expanded) setExpanded(true)
                armNotifTimer()
                return true
            }
            if (mainPillNotificationSwipeTarget() && abs(dx) > dp(36) && abs(dx) > abs(dy)) {
                finishSwipe(dx, vx)
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
        KeepAlive.markListenerConnected(this)
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
        restoreQueuedNotifications()
    }

    override fun onListenerDisconnected() {
        KeepAlive.listenerConnected = false
        diag("listener disconnected (Android disconnected the notification listener)")
        teardown()
        NotificationListenerService.requestRebind(ComponentName(this, CapsuleService::class.java))
    }

    override fun onDestroy() {
        KeepAlive.listenerConnected = false
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
                        .apply {
                            setShowBadge(false)
                            setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                        }
                )
            }
            val builder = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "keep") else Notification.Builder(this)
            val n = builder
                .setSmallIcon(R.drawable.ic_nav_pointer)
                .setContentTitle("Capsule is running")
                .setContentText("Pill is ready")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(7001, n, 0x40000000) else startForeground(7001, n)
            diag("keep-running notification on")
        }.onFailure { diag("keep-running notification failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    // Every 5 minutes: one line in the log, so a silent stop leaves a trace of exactly when it happened
    private fun heartbeat() {
        val acc = KeepAlive.ensureAccessibility(this)
        val listener = KeepAlive.ensureListener(this)
        if (KeepAlive.repairPending(this)) KeepAlive.schedule(this, 2_000L)
        val info = android.app.ActivityManager.MemoryInfo()
        getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
        diag(
            "alive: overlay=${root != null}, listener=$listener, accessibility=$acc, free memory ${info.availMem / 1048576} MB" +
                (if (info.lowMemory) " (LOW)" else "")
        )
    }

    private fun teardown() {
        stopSpring()
        cancelChargeIconFlight()
        cancelMediaArtFlight()
        pillEdgeAnimator?.cancel()
        pillEdgeAnimator = null
        clearSnapshot()
        root?.let { clearAnimationsRecursively(it) }
        runCatching { stopForeground(true) }
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(eventReceiver) }
        albumArtPulseAnimator?.cancel()
        albumArtPulseAnimator = null
        stopMic()
        closePanel(false)
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        lastMediaPlayingAtElapsed = 0L
        mediaWasPlaying = false
        mediaCompactGraceUntilElapsed = 0L
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
        var topY = if (cut != null) (cut.top - dp(4)).coerceAtLeast(0) else 0
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
        if (prefs.contains(topKey)) topY = dp(prefs.getInt(topKey, 0))
        val offX = dp(prefs.getInt(offKey, 0))
        resolvedTopY = topY
        resolvedOffX = offX

        val accent = pillAccent()

        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (expanded && prefs.getBoolean("expandFromStatusBar", true)) {
                Gravity.TOP or Gravity.START
            } else {
                Gravity.CENTER_VERTICAL
            }
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
        mediaWaveProgress = SeekBar(uiCtx).apply {
            contentDescription = "Track progress"
            max = 1000
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(0x44FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    pillSeeking = true
                    val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    if (duration > 0L) {
                        val position = duration * value.coerceIn(0, sb.max) / sb.max.coerceAtLeast(1)
                        elapsedText.text = fmt(position)
                        remainText.text = "-" + fmt(duration - position)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) { pillSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    pillSeeking = false
                    val mediaController = controller ?: return
                    val duration = mediaController.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    if (duration > 0L) {
                        val target = duration * sb.progress.coerceIn(0, sb.max) / sb.max.coerceAtLeast(1)
                        mediaController.transportControls.seekTo(target.coerceIn(0L, duration))
                    }
                }
            })
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20))
                .apply { topMargin = dp(3) }
        }
        miniMediaWaveform = MediaWaveformView(uiCtx).apply {
            contentDescription = "Animated audio visualizer"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(24)).apply { marginStart = dp(8); marginEnd = dp(8) }
        }
        textCol = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10); marginEnd = dp(6) }
            addView(title)
            notificationBodyPanel = NotificationBodyPanel(uiCtx).apply {
                clipChildren = true
                clipToPadding = true
                addView(subtitle, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ))
                horizontalGestureHandler = { event -> onNotificationBodyTouch(event) }
            }
            addView(notificationBodyPanel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(mediaWaveProgress)
            addView(progress)
        }

        btnPrev = control(R.drawable.ic_prev, accent) {
            controller?.transportControls?.skipToPrevious()
        }
        btnBack10 = control(R.drawable.ic_rewind_10, pillStyle().primary) {
            seekBy(-10_000L)
        }.apply { contentDescription = "Rewind 10 seconds" }
        btnPlay = control(R.drawable.ic_play, accent) { togglePlay() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            setColorFilter(Color.WHITE)
            contentDescription = "Play or pause playback"
        }
        miniPlayButton = ImageButton(uiCtx).apply {
            setImageResource(R.drawable.ic_play)
            background = mediaControlBackground()
            setColorFilter(pillStyle().primary)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isFocusable = true
            contentDescription = "Play or pause playback"
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(6) }
            setOnClickListener { togglePlay(); peek(4000) }
        }
        btnForward10 = control(R.drawable.ic_forward_10, pillStyle().primary) {
            seekBy(10_000L)
        }.apply { contentDescription = "Forward 10 seconds" }
        btnNext = control(R.drawable.ic_next, pillStyle().primary) {
            controller?.transportControls?.skipToNext()
        }
        btnAnswer = callActionControl(R.drawable.ic_answer, "Answer call", 0xFF2FBF71.toInt()) {
            runCatching { callAnswer?.actionIntent?.send() }
        }
        btnDecline = callActionControl(R.drawable.ic_decline, "Decline call", 0xFFE75252.toInt()) {
            runCatching { callHangup?.actionIntent?.send() }
        }
        callControlsRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(4), dp(6), dp(4), dp(2))
            visibility = View.GONE
        }
        callDeclineLabel = TextView(uiCtx).apply {
            text = "Decline"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(pillStyle().secondary)
        }
        callAnswerLabel = TextView(uiCtx).apply {
            text = "Answer"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(pillStyle().secondary)
        }
        callDeclineGroup = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(btnDecline)
            addView(callDeclineLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) })
        }
        callAnswerGroup = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(btnAnswer)
            addView(callAnswerLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) })
        }
        callControlsRow.addView(callDeclineGroup)
        callControlsRow.addView(callAnswerGroup)

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

        // Music layout: artwork and visualizer, then controls, then the seek bar with times
        musicLayout = false
        textCol.removeView(mediaWaveProgress)
        elapsedText = TextView(uiCtx).apply { setTextColor(Color.WHITE); textSize = 11f; isSingleLine = true }
        remainText = TextView(uiCtx).apply { setTextColor(Color.WHITE); textSize = 11f; isSingleLine = true }
        mediaWaveProgress.layoutParams = LinearLayout.LayoutParams(0, dp(24), 1f).apply {
            marginStart = dp(6); marginEnd = dp(6)
        }
        artRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { _, event -> onNotificationBodyTouch(event) }
            setOnClickListener {
                if (shouldShowCollapsedNotificationPreview()) openMediaNotification()
            }
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
        chargeRing = ChargeRingView(uiCtx, { prefs.getInt("chargeIconStyle", 0) }, { prefs.getInt("chargeIconShape", 9) },
            { chargePct }, { pillStyle().primary }, { chargeWaveSpacingDp() }, { prefs.getInt("chargeRingSpeed", 5) },
            { prefs.getInt("chargeRingThickness", 3) }, { prefs.getInt("chargeWaveHeight", 9) },
            { prefs.getInt("chargeWaveSoftness", 55) }, { prefs.getInt("chargeWaveInnerSpacingPercent", 28) },
            { prefs.getBoolean("chargeMotionEnabled", true) }, { chargeRemainderThicknessDp() },
            { chargeRemainderColor() }, { chargeWaveCycleDurationMs() }, { prefs.getInt("chargeRingSize", 48) },
            { chargeWavePhase() }).apply {
            val size = prefs.getInt("chargeRingSize", 48).coerceIn(36, 80)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginStart = dp(12) }
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
        chargeMetricIcons = List(6) { index ->
            ChargeMetricIconView(uiCtx, index, { pillStyle() }, {
                if (index == 4 && lastTempC >= 42.0) 0xFFFF9F0A.toInt() else pillStyle().accent
            }).apply {
                layoutParams = LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(5) }
                contentDescription = listOf("Charging time", "Voltage", "Current", "Battery level", "Temperature", "Power")[index]
            }
        }
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
                    addView(LinearLayout(uiCtx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(chargeMetricIcons[i])
                        addView(chargeTop[i], LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    })
                    addView(LinearLayout(uiCtx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(chargeMetricIcons[i + 3])
                        addView(chargeBottom[i], LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    })
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
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        navStreet = TextView(uiCtx).apply {
            setTextColor(0xFFD0D0D0.toInt())
            textSize = 16f
            maxLines = 2
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
            setTextColor(pillStyle().primary)
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
        artRow.addView(miniMediaWaveform)
        artRow.addView(barsRow)
        artRow.addView(miniPlayButton)
        artRow.addView(chargeBox)
        artRow.addView(navBox)
        ctrlRow.addView(btnPrev)
        ctrlRow.addView(btnBack10)
        ctrlRow.addView(btnPlay)
        ctrlRow.addView(btnForward10)
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
        timeRow.addView(mediaWaveProgress)
        timeRow.addView(remainText)
        mediaNotificationCard = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = notificationQueueSurface()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
            visibility = View.GONE
        }
        mediaNotificationIcon = ImageView(uiCtx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
        }
        mediaNotificationTitle = TextView(uiCtx).apply {
            setTextColor(notificationQueueStyle().primary)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaNotificationBody = TextView(uiCtx).apply {
            setTextColor(notificationQueueStyle().secondary)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        mediaNotificationPosition = TextView(uiCtx).apply {
            setTextColor(notificationQueueStyle().secondary)
            textSize = 10f
            maxLines = 1
        }
        mediaNotificationContent = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(mediaNotificationIcon)
            addView(LinearLayout(uiCtx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(mediaNotificationTitle)
                addView(mediaNotificationBody)
                addView(mediaNotificationPosition)
            })
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        mediaNotificationDownX = event.x
                        mediaNotificationDownY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - mediaNotificationDownX
                        val dy = event.y - mediaNotificationDownY
                        when {
                            abs(dx) >= dp(32) && abs(dx) > abs(dy) -> {
                                if (dx < 0f) dismissMediaNotification()
                                else browseQueuedNotification(-1)
                            }
                            abs(dy) >= dp(32) -> {
                                if (dy > 0f) openMediaNotification()
                            }
                            else -> toggleMediaNotificationDetails()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }
        mediaNotificationHeader = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(mediaNotificationContent, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        mediaNotificationCard.addView(mediaNotificationHeader)
        mediaNotificationActionsRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        mediaNotificationActionButtons = List(2) { index ->
            TextView(uiCtx).apply {
                setTextColor(notificationQueueStyle().primary)
                textSize = 12f
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                minHeight = dp(44)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = notificationQueueControlBackground()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (index > 0) marginStart = dp(8) }
                setOnClickListener { runMediaNotificationAction(index) }
            }
        }
        mediaNotificationActionButtons.forEach { mediaNotificationActionsRow.addView(it) }
        mediaNotificationCard.addView(mediaNotificationActionsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        notificationQueueStackRows = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
        }
        notificationQueueStack = CappedScroll(uiCtx, dp(132)).apply {
            isFillViewport = false
            clipToPadding = false
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
            addView(notificationQueueStackRows)
        }
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
        fun queueButton(text: String, description: String, action: () -> Unit) = TextView(uiCtx).apply {
            this.text = text
            contentDescription = description
            setTextColor(notificationQueueStyle().primary)
            textSize = 17f
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            background = notificationQueueControlBackground()
            setOnClickListener { action() }
        }
        notificationPrevious = queueButton("‹", "Previous notification") { browseQueuedNotification(-1) }
        notificationPosition = TextView(uiCtx).apply {
            setTextColor(notificationQueueStyle().secondary)
            textSize = 12f
            gravity = Gravity.CENTER
            minHeight = dp(44)
        }
        notificationNext = queueButton("›", "Next notification") { browseQueuedNotification(1) }
        miniChargeRing = ChargeRingView(uiCtx, { prefs.getInt("chargeIconStyle", 0) }, { prefs.getInt("chargeIconShape", 9) },
            { chargePct }, { pillStyle().primary }, { chargeWaveSpacingDp() }, { prefs.getInt("chargeRingSpeed", 5) },
            { prefs.getInt("chargeRingThickness", 3) }, { prefs.getInt("chargeWaveHeight", 9) },
            { prefs.getInt("chargeWaveSoftness", 55) }, { prefs.getInt("chargeWaveInnerSpacingPercent", 28) },
            { prefs.getBoolean("chargeMotionEnabled", true) }, { chargeRemainderThicknessDp() },
            { chargeRemainderColor() }, { chargeWaveCycleDurationMs() }, { prefs.getInt("chargeRingSize", 48) },
            { chargeWavePhase() }).apply {
            val size = (prefs.getInt("chargeRingSize", 48).coerceIn(36, 80) * 0.72f).toInt().coerceIn(26, 46)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginStart = dp(8); marginEnd = dp(2) }
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
        taskPreviewTitle = TextView(uiCtx).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(pillStyle().primary)
            setPadding(dp(4), dp(4), dp(4), dp(5))
        }
        taskPreviewItems = LinearLayout(uiCtx).apply { orientation = LinearLayout.VERTICAL }
        taskPreviewPanel = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = notificationQueueSurface()
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(5) }
            addView(taskPreviewTitle)
            addView(taskPreviewItems)
            addView(TextView(uiCtx).apply {
                text = "Open Task Manager"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(pillStyle().accent)
                setPadding(dp(8), dp(9), dp(8), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    setExpanded(false)
                    startActivity(Intent(this@CapsuleService, MainActivity::class.java)
                        .putExtra("cat", "tasks")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
            setOnTouchListener { _, event ->
                if (!prefs.getBoolean("taskPreviewSwipe", true)) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { taskPreviewDownX = event.rawX; false }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - taskPreviewDownX
                        if (abs(dx) > dp(36)) {
                            taskPreviewPage = (taskPreviewPage + if (dx < 0) 1 else -1).coerceAtLeast(0)
                            renderTaskPreview()
                            true
                        } else false
                    }
                    else -> false
                }
            }
        }
        row.addView(mediaNotificationCard)
        row.addView(notificationQueueStack)
        row.addView(taskPreviewPanel)
        row.addView(qsGrid)
        row.addView(widgetImage)
        replyRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        notificationActionsRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chips.forEach { notificationActionsRow.addView(it) }
        actionBtns.forEach { notificationActionsRow.addView(it) }
        notificationActionsRow.addView(notificationPrevious)
        notificationActionsRow.addView(notificationPosition)
        notificationActionsRow.addView(notificationNext)
        notificationActionsScroll = android.widget.HorizontalScrollView(uiCtx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            visibility = View.GONE
            addView(notificationActionsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        replyRow.addView(notificationActionsScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        row.addView(replyRow)
        row.addView(callControlsRow)
        applyTextStyle()
        notificationBubbleSlotActive = shouldShowNotificationBubble()
        notificationBubbleLastSlotWidth = if (notificationBubbleSlotActive) dp(112) else 0
        activityBubbleSlotActive = shouldShowActivityBubble()
        activityBubbleLastSlotWidth = if (activityBubbleSlotActive) dp(80) + activityBubbleBadgeReserve() else 0
        val wrap = object : FrameLayout(uiCtx) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        expandSwipeDownX = event.rawX
                        expandSwipeDownY = event.rawY
                        expandSwipeDelegatedToChild = dedicatedGestureSurfaceAt(event.rawX, event.rawY)
                        // Latch the main surface owner before children can consume this
                        // gesture. A newly arriving notification must not retarget a tap
                        // that began on media, Maps, or charging.
                        tapGestureOwnerCaptured = !expandSwipeDelegatedToChild
                        tapGestureOwnerId = if (expandSwipeDelegatedToChild) null else activityWinner()
                        expandGestureActivityId = tapGestureOwnerId
                        expandingBySwipe = false
                        expandSwipeCaptured = false
                        interceptingPillDownSwipe = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - expandSwipeDownX
                        val dy = event.rawY - expandSwipeDownY
                        if (!expandSwipeDelegatedToChild && shouldCapturePillDownSwipe(dx, dy)) {
                            interceptingPillDownSwipe = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!interceptingPillDownSwipe) expandGestureActivityId = null
                        expandSwipeDelegatedToChild = false
                        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            interceptingPillDownSwipe = false
                        }
                    }
                }
                return super.onInterceptTouchEvent(event)
            }
        }.apply {
            setOnTouchListener { _, e -> onPillTouch(e) }
            val badgeCorner = prefs.getString("notificationBadgeCorner", "top_end")
                ?.takeIf { it in setOf("top_start", "top_end", "bottom_start", "bottom_end", "right_side") } ?: "top_end"
            val sideBadgeSpace = if (badgeCorner == "right_side") dp(56) else 0
            val leftNotificationSpace = notificationBubbleSlot()
            val rightBubbleSpace = activityBubbleSlot()
            addView(
                row,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(pad + leftNotificationSpace, pad, pad + sideBadgeSpace + rightBubbleSpace, pad) }
            )
            val badgeGravity = when (badgeCorner) {
                "top_start" -> Gravity.TOP or Gravity.LEFT
                "bottom_start" -> Gravity.BOTTOM or Gravity.LEFT
                "bottom_end" -> Gravity.BOTTOM or Gravity.RIGHT
                "right_side" -> Gravity.CENTER_VERTICAL or Gravity.RIGHT
                else -> Gravity.TOP or Gravity.RIGHT
            }
            val visibleBadgeGravity = if (badgeCorner == "right_side") Gravity.CENTER_VERTICAL or Gravity.LEFT else badgeGravity
            notificationBadge = TextView(uiCtx).apply {
                gravity = Gravity.CENTER
                val badgeSize = notificationBadgeSizeDp()
                textSize = badgeSize * 0.46f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(notificationBadgeTextColor())
                minWidth = dp(badgeSize)
                minHeight = dp(badgeSize)
                val horizontalPadding = dp((badgeSize / 8).coerceAtLeast(2))
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(notificationBadgeFillColor())
                }
                elevation = dp(3).toFloat()
                visibility = View.GONE
                contentDescription = null
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            notificationBadgeTarget = FrameLayout(uiCtx).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Unread notifications"
                var badgeDownX = 0f
                var badgeDownY = 0f
                var badgeSwipeCaptured = false
                var badgeExpandedFromSwipe = false
                // The badge is a clickable child of the pill, so pill-level swipe handling
                // never sees a gesture that starts here. Preserve taps, but let a deliberate
                // vertical drag open the latest queued notification in the same pill.
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            badgeDownX = event.rawX
                            badgeDownY = event.rawY
                            badgeSwipeCaptured = false
                            badgeExpandedFromSwipe = false
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = event.rawY - badgeDownY
                            val dx = event.rawX - badgeDownX
                            val hasNotification = notificationQueue.isNotEmpty() || pending != null || showingNotif
                            if (!badgeSwipeCaptured && !expanded && hasNotification && dy >= dp(10) &&
                                abs(dy) > abs(dx) * 1.15f) {
                                badgeSwipeCaptured = true
                                badgeExpandedFromSwipe = true
                                focusNotifications()
                                val waiting = notificationQueue.values.lastOrNull() ?: pending
                                if (!showingNotif && waiting != null && !callActive &&
                                    stopwatchStart <= 0L && !liveActivityIsShowing()) {
                                    mediaNotificationKey = waiting.key
                                    openMediaNotification()
                                }
                                handler.removeCallbacks(collapse)
                                if (!expanded) setExpanded(true)
                                if (showingNotif) armNotifTimer()
                                true
                            } else if (badgeExpandedFromSwipe && expanded && dy <= dp(3)) {
                                badgeExpandedFromSwipe = false
                                setExpanded(false)
                                true
                            } else {
                                badgeSwipeCaptured
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val wasCaptured = badgeSwipeCaptured
                            badgeSwipeCaptured = false
                            badgeExpandedFromSwipe = false
                            wasCaptured
                        }
                        else -> badgeSwipeCaptured
                    }
                }
                setOnClickListener {
                    val waiting = notificationQueue.values.lastOrNull() ?: pending ?: return@setOnClickListener
                    if (callActive || stopwatchStart > 0L || liveActivityIsShowing()) return@setOnClickListener
                    mediaNotificationKey = waiting.key
                    openMediaNotification()
                }
                addView(notificationBadge, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    visibleBadgeGravity
                ).apply {
                    when (badgeCorner) {
                        "top_start" -> { topMargin = dp(1); leftMargin = dp(1) }
                        "bottom_start" -> { bottomMargin = dp(1); leftMargin = dp(1) }
                        "bottom_end" -> { bottomMargin = dp(1); rightMargin = dp(1) }
                        "right_side" -> { leftMargin = dp(1); rightMargin = dp(1) }
                        else -> { topMargin = dp(1); rightMargin = dp(1) }
                    }
                })
            }
            addView(notificationBadgeTarget, FrameLayout.LayoutParams(
                dp(44), dp(44), badgeGravity
            ).apply {
                if (badgeCorner == "right_side") rightMargin = pad
                if (badgeCorner == "top_start" || badgeCorner == "bottom_start") leftMargin = pad + leftNotificationSpace
            })

            activityBubbleIcon = ImageView(uiCtx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(7) }
            }
            activityBubbleChargeRing = ChargeRingView(uiCtx,
                { prefs.getInt("chargeIconStyle", 0) }, { prefs.getInt("chargeIconShape", 9) },
                { chargePct }, { pillStyle().primary }, { chargeWaveSpacingDp() },
                { prefs.getInt("chargeRingSpeed", 5) }, { prefs.getInt("chargeRingThickness", 3) },
                { prefs.getInt("chargeWaveHeight", 9) }, { prefs.getInt("chargeWaveSoftness", 55) },
                { prefs.getInt("chargeWaveInnerSpacingPercent", 28) },
                { prefs.getBoolean("chargeMotionEnabled", true) }, { chargeRemainderThicknessDp() },
                { chargeRemainderColor() }, { chargeWaveCycleDurationMs() }, { prefs.getInt("chargeRingSize", 48) },
                { chargeWavePhase() }).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(6) }
                visibility = View.GONE
                contentDescription = "Charging progress"
            }
            activityBubbleLabel = TextView(uiCtx).apply {
                setTextColor(pillStyle().primary)
                textSize = 10f
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(5); marginEnd = dp(7) }
            }
            activityBubble = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    colors = intArrayOf(pillStyle().top, pillStyle().bottom)
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(1), pillStyle().outline)
                }
                elevation = dp(4).toFloat()
                isClickable = true
                isFocusable = true
                visibility = if (activityBubbleSlotActive) View.VISIBLE else View.GONE
                alpha = if (activityBubbleSlotActive) 1f else 0f
                contentDescription = "Second active activity. Tap to focus and expand; swipe toward the main pill to switch; swipe away to hide."
                addView(activityBubbleIcon)
                addView(activityBubbleChargeRing)
                addView(activityBubbleLabel)
                setOnClickListener {
                    val touchedKind = activityBubbleTouchDownKind
                    activityBubbleTouchDownKind = null
                    val renderedId = activityBubbleKey
                    val kind = touchedKind ?: CapsuleActivityKind.entries.firstOrNull { it.id == renderedId }
                    kind?.let { focusActivity(it, expand = true) }
                }
                setOnTouchListener { _, event -> onActivityBubbleTouch(event) }
            }
            addView(activityBubble, FrameLayout.LayoutParams(
                dp(80), dp(48), Gravity.CENTER_VERTICAL or Gravity.RIGHT
            ).apply {
                val reserve = if (badgeCorner == "top_end" || badgeCorner == "bottom_end") activityBubbleBadgeReserve() else 0
                rightMargin = pad + sideBadgeSpace + reserve
            })

            notificationBubbleIcon = ImageView(uiCtx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pillStyle().accent) }
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8); marginEnd = dp(6) }
            }
            notificationBubbleTitle = TextView(uiCtx).apply {
                setTextColor(pillStyle().primary)
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            notificationBubbleBody = TextView(uiCtx).apply {
                setTextColor(pillStyle().secondary)
                textSize = 9f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            notificationBubble = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = themeBackground()
                elevation = dp(4).toFloat()
                isClickable = true
                isFocusable = true
                visibility = if (notificationBubbleSlotActive) View.VISIBLE else View.GONE
                alpha = if (notificationBubbleSlotActive) 1f else 0f
                contentDescription = "Latest notification. Tap or swipe down to expand; swipe left or right to dismiss."
                addView(notificationBubbleIcon)
                addView(LinearLayout(uiCtx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginEnd = dp(8) }
                    addView(notificationBubbleTitle)
                    addView(notificationBubbleBody)
                })
                setOnClickListener {
                    notificationQueue.values.lastOrNull()?.let { latest -> mediaNotificationKey = latest.key }
                    openMediaNotification()
                }
                setOnTouchListener { _, event -> onNotificationBubbleTouch(event) }
            }
            addView(notificationBubble, FrameLayout.LayoutParams(
                dp(112), dp(48), Gravity.CENTER_VERTICAL or Gravity.LEFT
            ).apply { leftMargin = pad + dp(4) })
            lastActivityBubbleVisible = activityBubbleSlotActive
        }

        lp = WindowManager.LayoutParams(
            miniW + 2 * pad + overlaySideSlots(), collapsedTargetHeight() + 2 * pad,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                (if (prefs.getBoolean("showOnLockScreen", true))
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offX + (overlayRightSlots() - overlayLeftSlots()) / 2
            y = topY - pad
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (expanded) {
            lp.width = expandedTargetWidth() + 2 * pad + sideBadgeSlot()
            lp.height = targetInnerH() + 2 * pad
        } else {
            lp.width = compactTargetWidth() + 2 * pad + overlaySideSlots()
        }

        if (prefs.getBoolean("blur", false)) applyBlur()
        stopSpring()
        pill = row
        root = wrap
        syncOverlayRowInsets()
        renderActivityBubble(presentationState().secondary)
        updateNotificationBubble(presentationState())
        applyVisibility()
        wm.addView(wrap, lp)
    }

    private fun applyTextStyle() {
        val k = (prefs.getInt("textScale", 100) / 100f) *
            (prefs.getInt("sectionTextScale_${activePillSizingSection()}", 100).coerceIn(80, 160) / 100f)
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
    private fun pillStyle() = PillStyles.palette(prefs.getString("theme", "capsule"))

    private fun notificationQueueThemeId(): String =
        (prefs.getString("notificationQueueTheme", "blend") ?: "blend")
            .takeIf { it in setOf("blend", "oneui", "iphone", "pixel", "nothing", "capsule") } ?: "blend"

    private fun notificationQueueStyle(): PillStylePalette =
        if (notificationQueueThemeId() == "blend") pillStyle() else PillStyles.palette(notificationQueueThemeId())

    /** Transparent mode lets the notification layer read as part of the main pill surface. */
    private fun notificationQueueSurface(): GradientDrawable {
        val style = notificationQueueStyle()
        val blended = notificationQueueThemeId() == "blend"
        return GradientDrawable().apply {
            cornerRadius = dp(if (style.id == "nothing") 10 else style.cornerDp).toFloat()
            if (blended) {
                setColor(Color.TRANSPARENT)
                val edge = (0x66 shl 24) or (style.accent and 0x00FFFFFF)
                setStroke(dp(1), edge)
            } else {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(style.top, style.bottom)
                setStroke(dp(1), style.outline)
            }
        }
    }

    private fun notificationQueueControlBackground(): GradientDrawable {
        val style = notificationQueueStyle()
        val blended = notificationQueueThemeId() == "blend"
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (style.id == "nothing") 8 else style.cornerDp).toFloat()
            setColor(if (blended) Color.TRANSPARENT else style.control)
            setStroke(dp(1), if (blended) style.outline else style.accent)
        }
    }

    private fun pillAccent(): Int = if (pillStyle().id == "pixel") {
        runCatching { uiCtx.getColor(android.R.color.system_accent1_200) }.getOrDefault(pillStyle().accent)
    } else pillStyle().accent

    private fun themedSurface(cornerRadiusDp: Int): GradientDrawable = GradientDrawable().apply {
        val style = pillStyle()
        val glass = prefs.getBoolean("blur", false)
        cornerRadius = dp(if (cornerRadiusDp >= 36) 40 else style.cornerDp).toFloat()
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(
            surfaceColor(style.top, 0xF4, glass),
            surfaceColor(style.bottom, 0xF8, glass)
        )
        setStroke(dp(1), if (glass && style.id == "capsule") 0x66FFFFFF else style.outline)
    }

    private fun themeBackground() = themedSurface(pillStyle().cornerDp)

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
        val mediaOwnsExpandedSurface = showMusic() && controller != null &&
            (mediaIsPlaying() || mediaSessionCanResume())
        return prefs.getBoolean("imageWidget", false) && !mediaOwnsExpandedSurface
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

    private val defaultActivityPriority = listOf("live", "notifications", "media", "navigation", "charging")

    private fun activityPriorityOrder(): List<String> {
        val saved = (prefs.getString("activityPriorityOrder", "") ?: "")
            .split(",").map { it.trim() }.filter { it in defaultActivityPriority }.distinct()
        return saved + defaultActivityPriority.filter { it !in saved }
    }

    private fun liveActivityIsShowing(): Boolean =
        showingNotif && notifKey != null && (notifKey == liveChronometerKey || notifKey == downloadKey)

    private fun notificationMayInterrupt(winner: String?): Boolean {
        if (winner == null || winner == "notifications") return true
        if (winner in setOf("calls", "stopwatch", "live", "navigation")) return false
        val order = activityPriorityOrder()
        val notificationRank = order.indexOf("notifications")
        val currentRank = order.indexOf(winner)
        return notificationRank >= 0 && currentRank >= 0 && notificationRank < currentRank
    }

    /**
     * Translate service-owned source state into a pure schedule. Queue contents remain owned by
     * notificationQueue (their actions, dismissal and history are service concerns); this schedule
     * decides which source owns the pill and whether exactly one notification companion can fit.
     */
    private fun presentationState(): CapsulePresentationState {
        val live = liveActivityIsShowing()
        val events = listOf(
            CapsuleActivityEvent(CapsuleActivityKind.CALL, callActive, persistent = true),
            CapsuleActivityEvent(CapsuleActivityKind.STOPWATCH, stopwatchStart > 0L, persistent = true),
            CapsuleActivityEvent(CapsuleActivityKind.LIVE, live, persistent = true),
            CapsuleActivityEvent(
                CapsuleActivityKind.NOTIFICATION,
                showingNotif && !live,
                persistent = false
            ),
            CapsuleActivityEvent(
                CapsuleActivityKind.MEDIA,
                mediaCompactActivityActive() || (expanded && mediaSessionCanResume()),
                persistent = true
            ),
            CapsuleActivityEvent(
                CapsuleActivityKind.NAVIGATION,
                prefs.getBoolean("mapsInfo", true) && MapsNav.active,
                persistent = true
            ),
            CapsuleActivityEvent(CapsuleActivityKind.CHARGING, showCharging() && charging, persistent = true)
        )
        if (focusedActivityId != null && events.none { it.active && it.kind.id == focusedActivityId }) {
            focusedActivityId = null
        }
        val schedule = CapsuleActivityScheduler.schedule(
            CapsuleActivitySnapshot(
                events = events,
                priorityOrder = activityPriorityOrder(),
                queuedNotificationCount = notificationQueue.size + notificationOverflow,
                notificationFocusedByUser = userFocusedNotification,
                userFocusedActivity = focusedActivityId
            )
        )
        return CapsulePresentationStateMachine.reduce(schedule)
    }

    private fun activityWinner(): String? = presentationState().foreground?.id

    /** A deliberate notification selection supersedes any activity previously focused from its bubble. */
    private fun focusNotifications() {
        focusedActivityId = null
        userFocusedNotification = true
    }

    private fun tasksPreviewEnabled(): Boolean =
        prefs.getBoolean("taskPreviewIdleEnabled", prefs.getBoolean("taskPreviewEnabled", true))

    private fun pillIsIdleForTasks(): Boolean = tasksPreviewEnabled() && activityWinner() == null &&
        !callActive && stopwatchStart <= 0L && !showingNotif && notificationQueue.isEmpty() &&
        !liveActivityIsShowing() && !qsActive() && !widgetActive() &&
        !(lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true))

    private fun idleTasksPreviewVisible(): Boolean = expanded && pillIsIdleForTasks()

    private fun companionHiddenScale(): Float = when (pillAnimationStyle()) {
        "liquid_morph" -> 0.72f
        "continuity" -> 0.80f
        "settling_dock" -> 0.90f
        else -> 0.66f
    }

    private fun companionOffset(): Float = dp(when (pillAnimationStyle()) {
        "liquid_morph" -> 16
        "continuity" -> 14
        "settling_dock" -> 22
        else -> 18
    }).toFloat()

    /** Side pills borrow the selected main-pill motion profile, then add a style-specific tether. */
    private fun companionMotionProfile(expanding: Boolean): CapsuleMotionProfile = CapsuleMotionProfile.from(
        prefs.getString("springSpeed", "medium") ?: "medium",
        pillAnimationStyle(),
        expanding = expanding
    )

    private fun companionDamping(profile: CapsuleMotionProfile): Float = when (pillAnimationStyle()) {
        "liquid_morph" -> (profile.dampingRatio * 0.91f).coerceAtLeast(0.58f)
        "continuity" -> profile.dampingRatio
        "settling_dock" -> (profile.dampingRatio * 1.04f).coerceAtMost(1.25f)
        else -> (profile.dampingRatio * 0.84f).coerceAtLeast(0.58f)
    }

    private fun renderActivityBubble(kind: CapsuleActivityKind?) {
        if (!::activityBubble.isInitialized || kind == null) return
        val nextId = kind.id
        val changed = activityBubbleKey != null && activityBubbleKey != nextId
        activityBubbleKey = nextId
        val packageLabel = controller?.packageName?.let(::appLabel)
        val text = when (kind) {
            CapsuleActivityKind.MEDIA -> controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: packageLabel ?: "Media"
            CapsuleActivityKind.NAVIGATION -> MapsNav.turnDistance.ifBlank {
                MapsNav.distanceLeft.ifBlank { MapsNav.eta.ifBlank { "Maps" } }
            }
            CapsuleActivityKind.CHARGING -> "$chargePct%"
            CapsuleActivityKind.LIVE -> liveChronometerLabel.ifBlank { title.text?.toString().orEmpty().ifBlank { "Activity" } }
            else -> return
        }
        val drawable = when (kind) {
            CapsuleActivityKind.MEDIA -> {
                val artwork = controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                if (artwork != null) android.graphics.drawable.BitmapDrawable(resources, artwork)
                else controller?.packageName?.let(::appIcon)
            }
            CapsuleActivityKind.NAVIGATION -> MapsNav.arrow?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                ?: ContextCompat.getDrawable(this, navArrowRes(MapsNav.direction))
            CapsuleActivityKind.CHARGING -> null
            CapsuleActivityKind.LIVE -> ContextCompat.getDrawable(this, R.drawable.ic_tile_timer)
            else -> null
        }
        activityBubbleLabel.text = text
        activityBubbleLabel.contentDescription = text
        activityBubbleIcon.setImageDrawable(drawable)
        activityBubbleIcon.visibility = vis(kind != CapsuleActivityKind.CHARGING)
        activityBubbleChargeRing.visibility = vis(kind == CapsuleActivityKind.CHARGING)
        val bubbleChargeScale = prefs.getInt("sectionIconScale_charging", 100).coerceIn(70, 160) / 100f
        val bubbleRingDp = (prefs.getInt("chargeRingSize", 48).coerceIn(36, 80) * 0.48f * bubbleChargeScale)
            .toInt().coerceIn(18, 34)
        (activityBubbleChargeRing.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = dp(bubbleRingDp)
            params.height = dp(bubbleRingDp)
            activityBubbleChargeRing.layoutParams = params
        }
        activityBubbleChargeRing.refreshAnimationState()
        activityBubbleIcon.clearColorFilter()
        activityBubble.contentDescription = "$text. Tap to expand this activity; swipe toward the main pill to switch; swipe away to hide this bubble."

        val params = activityBubble.layoutParams as? FrameLayout.LayoutParams
        if (params != null) {
            val corner = prefs.getString("notificationBadgeCorner", "top_end")
            val badgeSafe = if (corner == "top_end" || corner == "bottom_end") activityBubbleBadgeReserve() else 0
            params.rightMargin = touchPad() + sideBadgeSlot() + badgeSafe
            activityBubble.layoutParams = params
        }
        if (changed && activityBubbleTargetVisible && motionEnabled()) {
            if (!bubbleSpringActive) {
                springBubbleX.snapTo(-companionOffset())
                springBubbleScale.snapTo(companionHiddenScale())
                springBubbleAlpha.snapTo(0f)
            }
            springBubbleX.retarget(0f)
            springBubbleScale.retarget(1f)
            springBubbleAlpha.retarget(1f)
            bubbleSpringActive = true
            postSpringFrame()
        }
    }

    private fun renderNotificationBubble(state: CapsulePresentationState = presentationState()) {
        if (!::notificationBubble.isInitialized) return
        val item = notificationQueue.values.lastOrNull()
        if (item == null) {
            notificationBubbleKey = null
            return
        }
        notificationBubbleKey = item.key
        notificationBubbleIcon.setImageDrawable(appIcon(item.pkg))
        notificationBubbleTitle.text = item.title.ifBlank { appLabel(item.pkg) }
        notificationBubbleBody.text = item.body.ifBlank { appLabel(item.pkg) }
        notificationBubble.contentDescription =
            "Latest notification from ${appLabel(item.pkg)}. ${item.title}. Tap to open, swipe left or right to dismiss."
        val style = pillStyle()
        notificationBubble.background = themeBackground()
        notificationBubbleTitle.setTextColor(style.primary)
        notificationBubbleBody.setTextColor(style.secondary)
        notificationBubbleIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(style.accent)
        }
        notificationBubble.pivotX = notificationBubble.width.toFloat()
        notificationBubble.pivotY = notificationBubble.height / 2f
        (notificationBubble.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.leftMargin = touchPad() + dp(4)
            notificationBubble.layoutParams = params
        }
        notificationBubble.contentDescription =
            "Latest notification from ${appLabel(item.pkg)}. ${item.title}. Tap or swipe down to expand; swipe left or right to dismiss."
    }

    private fun updateNotificationBubble(state: CapsulePresentationState = presentationState()) {
        if (!::notificationBubble.isInitialized) return
        val desired = shouldShowNotificationBubble(state)
        val previousKey = notificationBubbleKey
        renderNotificationBubble(state)
        val displayedNotificationChanged = previousKey != notificationBubbleKey
        if (desired != notificationBubbleTargetVisible) {
            notificationBubbleMotionExpanding = companionMotionExpansionOverride ?: desired
        }
        if (desired) {
            val entering = !notificationBubbleTargetVisible
            notificationBubbleTargetVisible = true
            notificationBubbleHidePending = false
            val requiredSlot = dp(112)
            if (!notificationBubbleSlotActive || notificationBubbleLastSlotWidth != requiredSlot) {
                notificationBubbleSlotActive = true
                notificationBubbleLastSlotWidth = requiredSlot
                syncOverlayRowInsets()
                animateSize()
            }
            notificationBubble.visibility = View.VISIBLE
            notificationBubble.pivotX = notificationBubble.width.toFloat()
            notificationBubble.pivotY = notificationBubble.height / 2f
            if (entering && motionEnabled()) {
                if (!notificationSpringActive) {
                    notificationSpringX.snapTo(companionOffset())
                    notificationSpringScale.snapTo(companionHiddenScale())
                    notificationSpringAlpha.snapTo(0f)
                }
                notificationSpringX.retarget(0f)
                notificationSpringScale.retarget(1f)
                notificationSpringAlpha.retarget(1f)
                notificationSpringActive = true
                postSpringFrame()
            } else if (displayedNotificationChanged &&
                (abs(notificationBubble.translationX) > 1f || notificationBubble.scaleX < 0.999f ||
                    notificationBubble.alpha < 0.999f)) {
                // Restore a replacement notification to its slot if the previous one was swiped.
                if (motionEnabled()) {
                    notificationSpringX.snapTo(notificationBubble.translationX)
                    notificationSpringScale.snapTo(notificationBubble.scaleX)
                    notificationSpringAlpha.snapTo(notificationBubble.alpha)
                    notificationSpringX.retarget(0f)
                    notificationSpringScale.retarget(1f)
                    notificationSpringAlpha.retarget(1f)
                    notificationSpringActive = true
                    postSpringFrame()
                } else {
                    notificationSpringX.snapTo(0f)
                    notificationSpringScale.snapTo(1f)
                    notificationSpringAlpha.snapTo(1f)
                    notificationBubble.translationX = 0f
                    notificationBubble.scaleX = 1f
                    notificationBubble.scaleY = 1f
                    notificationBubble.alpha = 1f
                }
            } else if (!motionEnabled()) {
                notificationSpringX.snapTo(0f)
                notificationSpringScale.snapTo(1f)
                notificationSpringAlpha.snapTo(1f)
                notificationBubble.translationX = 0f
                notificationBubble.scaleX = 1f
                notificationBubble.scaleY = 1f
                notificationBubble.alpha = 1f
            }
            return
        }

        if (!notificationBubbleSlotActive || notificationBubbleHidePending) return
        notificationBubbleTargetVisible = false
        if (!motionEnabled()) {
            val previousSideSlots = overlaySideSlots()
            notificationBubble.visibility = View.GONE
            notificationBubbleSlotActive = false
            notificationBubbleLastSlotWidth = 0
            notificationBubble.translationX = companionOffset()
            notificationBubble.scaleX = companionHiddenScale()
            notificationBubble.scaleY = companionHiddenScale()
            notificationBubble.alpha = 0f
            releaseCompanionSlotWithoutMainMotion(previousSideSlots)
            return
        }
        notificationBubbleHidePending = true
        notificationSpringX.retarget(companionOffset())
        notificationSpringScale.retarget(companionHiddenScale())
        notificationSpringAlpha.retarget(0f)
        notificationSpringActive = true
        postSpringFrame()
    }

    private fun onNotificationBubbleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                notificationBubbleDownX = event.rawX
                notificationBubbleDownY = event.rawY
                notificationBubbleSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - notificationBubbleDownX
                val dy = event.rawY - notificationBubbleDownY
                if (maxOf(abs(dx), abs(dy)) > dp(12)) notificationBubbleSwiping = true
                if (notificationBubbleSwiping) {
                    notificationBubble.translationX = dx.coerceIn(-dp(40).toFloat(), dp(40).toFloat())
                    val pull = (maxOf(abs(dx), abs(dy)) / dp(90).toFloat()).coerceIn(0f, 1f)
                    notificationBubble.scaleX = 1f - pull * 0.04f
                    notificationBubble.scaleY = 1f - pull * 0.04f
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - notificationBubbleDownX
                val dy = event.rawY - notificationBubbleDownY
                notificationSpringX.snapTo(notificationBubble.translationX)
                notificationSpringScale.snapTo(notificationBubble.scaleX)
                notificationSpringAlpha.snapTo(notificationBubble.alpha)
                if (!notificationBubbleSwiping) {
                    notificationBubble.performClick()
                } else if (abs(dx) >= dp(36) && abs(dx) > abs(dy)) {
                    dismissMediaNotification(notificationBubbleKey)
                } else if (dy >= dp(28)) {
                    notificationQueue.values.lastOrNull()?.let { latest ->
                        mediaNotificationKey = latest.key
                        openMediaNotification()
                    }
                } else {
                    notificationSpringX.retarget(0f)
                    notificationSpringScale.retarget(1f)
                    notificationSpringAlpha.retarget(1f)
                    notificationSpringActive = motionEnabled()
                    if (notificationSpringActive) postSpringFrame()
                }
                notificationBubbleSwiping = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                notificationBubbleSwiping = false
                notificationSpringX.snapTo(notificationBubble.translationX)
                notificationSpringScale.snapTo(notificationBubble.scaleX)
                notificationSpringAlpha.snapTo(notificationBubble.alpha)
                notificationSpringX.retarget(0f)
                notificationSpringScale.retarget(1f)
                notificationSpringAlpha.retarget(1f)
                notificationSpringActive = motionEnabled()
                if (notificationSpringActive) postSpringFrame()
                return true
            }
        }
        return true
    }

    private fun updateActivityBubble(state: CapsulePresentationState = presentationState()) {
        if (!::activityBubble.isInitialized) return
        if (suppressedActivityBubbleKey != null && suppressedActivityBubbleKey != state.secondary?.id) {
            suppressedActivityBubbleKey = null
        }
        val desired = shouldShowActivityBubble(state)
        val activity = state.secondary
        if (desired != activityBubbleTargetVisible) {
            activityBubbleMotionExpanding = companionMotionExpansionOverride ?: desired
        }
        if (desired) {
            val entering = !activityBubbleTargetVisible
            activityBubbleTargetVisible = true
            activityBubbleHidePending = false
            val requiredSlot = dp(80) + activityBubbleBadgeReserve()
            if (!activityBubbleSlotActive) {
                activityBubbleSlotActive = true
                activityBubbleLastSlotWidth = requiredSlot
                syncOverlayRowInsets()
                animateSize()
            } else if (activityBubbleLastSlotWidth != requiredSlot) {
                activityBubbleLastSlotWidth = requiredSlot
                syncOverlayRowInsets()
                animateSize()
            }
            activityBubble.visibility = View.VISIBLE
            renderActivityBubble(activity)
            if (entering && motionEnabled()) {
                if (!bubbleSpringActive) {
                    springBubbleX.snapTo(-companionOffset())
                    springBubbleScale.snapTo(companionHiddenScale())
                    springBubbleAlpha.snapTo(0f)
                }
                springBubbleX.retarget(0f)
                springBubbleScale.retarget(1f)
                springBubbleAlpha.retarget(1f)
                bubbleSpringActive = true
                postSpringFrame()
            } else if (!motionEnabled()) {
                springBubbleX.snapTo(0f)
                springBubbleScale.snapTo(1f)
                springBubbleAlpha.snapTo(1f)
                activityBubble.translationX = 0f
                activityBubble.scaleX = 1f
                activityBubble.scaleY = 1f
                activityBubble.alpha = 1f
            }
            lastActivityBubbleVisible = true
            return
        }

        if (!activityBubbleSlotActive || activityBubbleHidePending) return
        activityBubbleTargetVisible = false
        if (!motionEnabled()) {
            val previousSideSlots = overlaySideSlots()
            activityBubble.visibility = View.GONE
            activityBubbleSlotActive = false
            activityBubbleLastSlotWidth = 0
            activityBubble.translationX = -companionOffset()
            activityBubble.scaleX = companionHiddenScale()
            activityBubble.scaleY = companionHiddenScale()
            activityBubble.alpha = 0f
            lastActivityBubbleVisible = false
            releaseCompanionSlotWithoutMainMotion(previousSideSlots)
            return
        }
        activityBubbleHidePending = true
        springBubbleX.retarget(-companionOffset())
        springBubbleScale.retarget(companionHiddenScale())
        springBubbleAlpha.retarget(0f)
        bubbleSpringActive = true
        postSpringFrame()
        lastActivityBubbleVisible = false
    }

    private fun focusActivity(kind: CapsuleActivityKind, expand: Boolean) {
        val state = presentationState()
        if (kind != state.secondary && kind.id != focusedActivityId) return
        focusedActivityId = kind.id
        userFocusedNotification = false
        suppressedActivityBubbleKey = null
        handler.removeCallbacks(collapse)
        if (expand) setExpanded(true) else applyVisibility()
    }

    private fun hideActivityBubbleForCurrentEvent() {
        suppressedActivityBubbleKey = presentationState().secondary?.id
        if (suppressedActivityBubbleKey == null) return
        uiPost { applyVisibility() }
    }

    private fun onActivityBubbleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activityBubbleTouchDownX = event.rawX
                activityBubbleTouchDownY = event.rawY
                activityBubbleTouchDownKind = presentationState().secondary
                activityBubbleSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - activityBubbleTouchDownX
                val dy = event.rawY - activityBubbleTouchDownY
                if (abs(dx) > dp(12) || abs(dy) > dp(12)) activityBubbleSwiping = true
                if (activityBubbleSwiping) {
                    // Let the side surface follow the finger slightly. The main pill gives a
                    // restrained counter-pressure so the two surfaces read as one system.
                    val pull = (abs(dx) / dp(96).toFloat()).coerceIn(0f, 1f)
                    activityBubble.translationX = dx.coerceIn(-dp(22).toFloat(), dp(18).toFloat())
                    activityBubble.scaleX = 1f - pull * 0.035f
                    activityBubble.scaleY = 1f - pull * 0.035f
                    animatePress(1f - pull * 0.018f)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - activityBubbleTouchDownX
                val dy = event.rawY - activityBubbleTouchDownY
                val kind = activityBubbleTouchDownKind ?: presentationState().secondary
                springBubbleX.snapTo(activityBubble.translationX)
                springBubbleScale.snapTo(activityBubble.scaleX)
                springBubbleAlpha.snapTo(activityBubble.alpha)
                animatePress(1f)
                if (!activityBubbleSwiping) {
                    activityBubble.performClick()
                } else if (abs(dx) > abs(dy) && abs(dx) >= dp(28)) {
                    activityBubbleTouchDownKind = null
                    if (dx < 0f) kind?.let { focusActivity(it, expand = false) }
                    else hideActivityBubbleForCurrentEvent()
                } else if (dy >= dp(28)) {
                    activityBubbleTouchDownKind = null
                    kind?.let { focusActivity(it, expand = true) }
                } else if (dy <= -dp(28)) {
                    activityBubbleTouchDownKind = null
                    kind?.let { focusActivity(it, expand = false) }
                } else {
                    activityBubbleTouchDownKind = null
                    springBubbleX.retarget(0f)
                    springBubbleScale.retarget(1f)
                    springBubbleAlpha.retarget(1f)
                    bubbleSpringActive = true
                    postSpringFrame()
                }
                activityBubbleSwiping = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activityBubbleSwiping = false
                activityBubbleTouchDownKind = null
                springBubbleX.snapTo(activityBubble.translationX)
                springBubbleScale.snapTo(activityBubble.scaleX)
                springBubbleAlpha.snapTo(activityBubble.alpha)
                springBubbleX.retarget(0f)
                springBubbleScale.retarget(1f)
                springBubbleAlpha.retarget(1f)
                bubbleSpringActive = true
                animatePress(1f)
                postSpringFrame()
                return true
            }
        }
        return true
    }

    private fun canPromoteLiveActivity(key: String, incomingPriority: Int): Boolean {
        if (callActive) return false
        val winner = activityWinner()
        if (winner == "calls" || winner == "stopwatch") return false
        if (winner != null && winner != "live") {
            val order = activityPriorityOrder()
            return order.indexOf("live") < order.indexOf(winner)
        }
        val currentKey = when {
            liveChronometerKey != null && notifKey == liveChronometerKey -> liveChronometerKey
            downloadKey != null && notifKey == downloadKey -> downloadKey
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
        else (prefs.getString("pinned", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)

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
        val lockActive = lockScreenActive()
        val want = (lockActive && !prefs.getBoolean("showOnLockScreen", true)) ||
            (prefs.getBoolean("hideFullscreen", false) && !callActive && isFullscreenApp()) ||
            (!callActive && inNight())
        if (want != hiddenByApp) {
            hiddenByApp = want
            root?.visibility = if (want) View.GONE else View.VISIBLE
        }
    }

    private fun lockScreenActive(): Boolean = runCatching {
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }.getOrDefault(false)

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

    private fun mediaControlBackground(): GradientDrawable {
        val style = pillStyle()
        return GradientDrawable().apply {
            shape = if (style.id == "nothing") GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            cornerRadius = dp(if (style.id == "nothing") 9 else style.cornerDp).toFloat()
            setColor((0x20 shl 24) or (style.accent and 0x00FFFFFF))
            setStroke(dp(1), style.outline)
        }
    }

    private fun refreshMediaControlStyle() {
        if (!::btnPrev.isInitialized) return
        val style = pillStyle()
        listOf(btnPrev, btnBack10, btnForward10, btnNext).forEach { button ->
            button.background = mediaControlBackground()
            button.setColorFilter(style.primary)
        }
        btnPlay.background = GradientDrawable().apply {
            shape = if (style.id == "nothing") GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            cornerRadius = dp(if (style.id == "nothing") 14 else 40).toFloat()
            setColor(pillAccent())
        }
        btnPlay.setColorFilter(style.primary)
        if (::miniPlayButton.isInitialized) {
            miniPlayButton.background = mediaControlBackground()
            miniPlayButton.setColorFilter(style.primary)
        }
    }

    private fun control(res: Int, tint: Int, onClick: () -> Unit) = ImageButton(uiCtx).apply {
        setImageResource(res)
        setColorFilter(tint)
        background = mediaControlBackground()
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(11), dp(11), dp(11), dp(11))
        isFocusable = true
        contentDescription = when (res) {
            R.drawable.ic_prev -> "Previous track"
            R.drawable.ic_next -> "Next track"
            R.drawable.ic_play, R.drawable.ic_pause -> "Play or pause playback"
            else -> "Media control"
        }
        setOnClickListener {
            onClick()
            peek(4000)
        }
    }

    private fun callActionControl(res: Int, label: String, color: Int, onClick: () -> Unit) =
        ImageButton(uiCtx).apply {
            setImageResource(res)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(15), dp(15), dp(15), dp(15))
            contentDescription = label
            isFocusable = true
            setOnClickListener {
                onClick()
                peek(4000)
            }
        }

    // ---------- touch and gestures ----------

    private fun touchInsideVisibleView(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null || !view.isShown || view.width <= 0 || view.height <= 0) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val scaledWidth = view.width * view.scaleX.coerceAtLeast(0f)
        val scaledHeight = view.height * view.scaleY.coerceAtLeast(0f)
        return rawX >= location[0] && rawX <= location[0] + scaledWidth &&
            rawY >= location[1] && rawY <= location[1] + scaledHeight
    }

    /** Companion pills and the count badge own their gestures; never route them as main-pill drags. */
    private fun dedicatedGestureSurfaceAt(rawX: Float, rawY: Float): Boolean =
        (::activityBubble.isInitialized && activityBubbleSlotActive &&
            touchInsideVisibleView(activityBubble, rawX, rawY)) ||
            (::notificationBubble.isInitialized && notificationBubbleSlotActive &&
                touchInsideVisibleView(notificationBubble, rawX, rawY)) ||
            (::notificationBadgeTarget.isInitialized && ::notificationBadge.isInitialized &&
                notificationBadge.visibility == View.VISIBLE &&
                touchInsideVisibleView(notificationBadgeTarget, rawX, rawY))

    private fun mainPillSwipeOwner() = expandGestureActivityId ?: activityWinner()

    /** A queued notification may expand only when the gesture began on its own main surface. */
    private fun mainPillDownSwipeExpands(owner: String?): Boolean {
        val configured = prefs.getString("g_down", CapsulePrefs.defaultFor("g_down")) == "expand"
        val activityOwnsSurface = owner == "media" || owner == "navigation" || owner == "charging" ||
            owner == "calls" || owner == "stopwatch" || owner == "live"
        return if (activityOwnsSurface) {
            configured
        } else {
            // Idle notification expansion follows the explicit swipe-down action too;
            // don't let a queued message hijack a different configured gesture.
            configured || ((owner == null || owner == "notifications") && (pending != null || showingNotif))
        }
    }

    private fun shouldCapturePillDownSwipe(dx: Float, dy: Float, owner: String? = mainPillSwipeOwner()): Boolean =
        !expanded && !expandSwipeCaptured && mainPillDownSwipeExpands(owner) &&
            dy >= dp(10) && abs(dy) > abs(dx) * 1.15f

    private fun openMainPillOwnerAfterDownSwipe(owner: String?) {
        if (owner != "notifications" || showingNotif || callActive || stopwatchStart > 0L || liveActivityIsShowing()) return
        val waiting = notificationQueue.values.lastOrNull() ?: pending ?: return
        mediaNotificationKey = waiting.key
        openMediaNotification()
    }

    private fun onPillTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animatePress(0.94f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(1f)
        }
        // Expand as soon as a deliberate downward drag is recognized, and let the same
        // still-held gesture reverse back to collapsed if the finger returns upward.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                expandSwipeDownX = e.rawX
                expandSwipeDownY = e.rawY
                expandGestureActivityId = activityWinner()
                tapGestureOwnerId = expandGestureActivityId
                tapGestureOwnerCaptured = true
                expandingBySwipe = false
                expandSwipeCaptured = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.rawY - expandSwipeDownY
                val dx = e.rawX - expandSwipeDownX
                val returnedUp = dy <= dp(3)
                val gestureOwner = mainPillSwipeOwner()
                if (shouldCapturePillDownSwipe(dx, dy, gestureOwner)) {
                    expandingBySwipe = true
                    expandSwipeCaptured = true
                    openMainPillOwnerAfterDownSwipe(gestureOwner)
                    handler.removeCallbacks(collapse)
                    if (!expanded) setExpanded(true)
                    if (showingNotif) armNotifTimer()
                    val cancel = MotionEvent.obtain(e)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    gestures.onTouchEvent(cancel)
                    cancel.recycle()
                    return true
                } else if (expandingBySwipe && expanded && returnedUp) {
                    expandingBySwipe = false
                    setExpanded(false)
                    return true
                } else if (expandSwipeCaptured) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (expandSwipeCaptured) {
                    tapGestureOwnerCaptured = false
                    tapGestureOwnerId = null
                    expandingBySwipe = false
                    expandSwipeCaptured = false
                    expandGestureActivityId = null
                    interceptingPillDownSwipe = false
                    expandSwipeDelegatedToChild = false
                    return true
                }
            }
        }
        // Horizontal swipes browse expanded notifications; only calls can be swiped away.
        if (swipeTarget() || swiping) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownX = e.rawX
                    swipeDownY = e.rawY
                    swipeBaseX = lp.x
                    swiping = false
                    alphaSpringActive = false
                    springAlpha.snapTo(1f)
                    pill?.alpha = 1f
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
                        animatePress(1f)
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
        if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
            tapGestureOwnerCaptured = false
            tapGestureOwnerId = null
        }
        val handled = gestures.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP ||
            (e.actionMasked == MotionEvent.ACTION_CANCEL && !interceptingPillDownSwipe)) {
            expandGestureActivityId = null
            expandSwipeDelegatedToChild = false
        }
        if (e.actionMasked == MotionEvent.ACTION_UP) interceptingPillDownSwipe = false
        return handled
    }

    /** Notification text is clipped to its compact layout; horizontal drags dismiss it. */
    private fun onNotificationBodyTouch(event: MotionEvent): Boolean {
        val canHandle = mainPillNotificationSwipeTarget() && !shouldShowActivityNotificationCard() &&
            !(lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true))
        if (!canHandle) {
            notificationBodyHorizontalSwipe = false
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                notificationBodyDownX = event.rawX
                notificationBodyDownY = event.rawY
                swipeBaseX = lp.x
                notificationBodyHorizontalSwipe = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - notificationBodyDownX
                val dy = event.rawY - notificationBodyDownY
                if (!notificationBodyHorizontalSwipe && abs(dx) >= dp(14) && abs(dx) > abs(dy) * 1.25f) {
                    notificationBodyHorizontalSwipe = true
                }
                if (notificationBodyHorizontalSwipe) dragPill(dx)
                return notificationBodyHorizontalSwipe
            }
            MotionEvent.ACTION_UP -> {
                if (!notificationBodyHorizontalSwipe) return false
                val dx = event.rawX - notificationBodyDownX
                val elapsedMs = (event.eventTime - event.downTime).coerceAtLeast(1L)
                val velocityX = dx * 1000f / elapsedMs
                notificationBodyHorizontalSwipe = false
                finishSwipe(dx, velocityX)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasHorizontal = notificationBodyHorizontalSwipe
                notificationBodyHorizontalSwipe = false
                if (wasHorizontal) finishSwipe(0f, 0f)
                return wasHorizontal
            }
        }
        return notificationBodyHorizontalSwipe
    }

    // The main pill owns the notification only when it is actually showing that notification.
    // A queued item alongside media, Maps, or charging is dismissed through its own companion pill.
    private fun mainPillNotificationSwipeTarget(): Boolean {
        if (callActive || qsActive() || widgetActive()) return false
        return if (expanded) {
            notifKey != null && activityWinner() in setOf("live", "notifications")
        } else {
            shouldShowCollapsedNotificationPreview()
        }
    }

    // This also covers title/icon regions outside the notification text body.
    private fun swipeTarget() = mainPillNotificationSwipeTarget() || (callActive && expanded)

    // The whole pill window follows the finger, so it can slide right off the screen
    private fun dragPill(dx: Float) {
        val r = root ?: return
        lp.x = swipeBaseX + dx.toInt()
        runCatching { wm.updateViewLayout(r, lp) }
        pill?.alpha = (1f - abs(dx) / dp(220)).coerceIn(0.25f, 1f)
    }

    private fun finishSwipe(dx: Float, vx: Float) {
        val r = root ?: return
        val notificationSwipe = mainPillNotificationSwipeTarget()
        val far = if (notificationSwipe) {
            abs(dx) >= dp(36) || abs(vx) >= dp(350)
        } else {
            abs(dx) > dp(90) || (abs(vx) > dp(500) && vx * dx > 0)
        }
        if (notificationSwipe) {
            val direction = if (abs(dx) > dp(1)) dx else vx
            if (far && abs(direction) > 0f) {
                if (!motionEnabled()) {
                    dismissSwiped()
                    lp.x = swipeBaseX
                    runCatching { wm.updateViewLayout(r, lp) }
                    pill?.alpha = 1f
                    pill?.scaleX = 1f
                    pill?.scaleY = 1f
                    return
                }
                val target = (if (direction > 0f) 1f else -1f) * resources.displayMetrics.widthPixels.toFloat()
                springX.snapTo(lp.x.toFloat())
                springX.retarget(target)
                springX.setVelocity(vx.coerceIn(-dp(1200).toFloat(), dp(1200).toFloat()))
                swipeSpringActive = true
                swipeDismissPending = true
                postSpringFrame()
                return
            }
            if (!motionEnabled()) {
                lp.x = swipeBaseX
                runCatching { wm.updateViewLayout(r, lp) }
                pill?.alpha = 1f
                pill?.scaleX = 1f
                pill?.scaleY = 1f
                if (showingNotif) armNotifTimer()
                return
            }
            val releaseVelocity = vx.coerceIn(-dp(1200).toFloat(), dp(1200).toFloat())
            springX.snapTo(lp.x.toFloat())
            springAlpha.snapTo(pill?.alpha ?: 1f)
            springAlpha.retarget(1f)
            alphaSpringActive = true
            springX.retarget(swipeBaseX.toFloat())
            springX.setVelocity(releaseVelocity)
            swipeSpringActive = true
            swipeReturnPending = true
            postSpringFrame()
            return
        }
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
        val velocityLimit = dp(1200).toFloat()
        val releaseVelocity = vx.coerceIn(-velocityLimit, velocityLimit)
        springX.snapTo(lp.x.toFloat())
        springAlpha.snapTo(pill?.alpha ?: 1f)
        springAlpha.retarget(1f)
        alphaSpringActive = true
        if (!far) {
            springX.retarget(swipeBaseX.toFloat())
            springX.setVelocity(releaseVelocity)
            swipeSpringActive = true
            swipeReturnPending = true
            postSpringFrame()
            return
        }
        val target = (if (dx > 0) 1 else -1) * resources.displayMetrics.widthPixels.toFloat()
        springX.retarget(target)
        springX.setVelocity(releaseVelocity)
        swipeSpringActive = true
        swipeDismissPending = true
        postSpringFrame()
    }

    private fun dismissSwiped() {
        if (mainPillNotificationSwipeTarget()) {
            if (expanded) dismissExpandedNotification()
            else dismissMediaNotification(notificationQueue.values.lastOrNull()?.key ?: notifKey)
            return
        }
        if (callActive) {
            callDismissed = true
            handler.removeCallbacks(collapse)
            collapse.run()
        }
    }

    private fun dismissExpandedNotification() {
        val key = notifKey ?: return
        removeQueuedNotification(key)
        runCatching { cancelNotification(key) }
        mediaNotificationExpanded = false
        val next = notificationQueue.values.lastOrNull()
        mediaNotificationKey = next?.key
        if (next != null) {
            presentNotif(next, 2)
            focusNotifications()
            if (!expanded) setExpanded(true) else applyVisibility()
            armNotifTimer()
        } else {
            showingNotif = false
            notifIntent = null
            notifKey = null
            notifLevel = 0
            replyAction = null
            notifActions = emptyList()
            userFocusedNotification = false
            handler.removeCallbacks(collapse)
            setExpanded(false)
            applyVisibility()
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
            "open_tasks" -> {
                handler.removeCallbacks(collapse)
                setExpanded(false)
                startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("cat", "tasks")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "clock" -> showClock()
            "open_pinned", "control_panel", "quick_settings" -> togglePanel()
        }
    }

    private fun audio() = getSystemService(AudioManager::class.java)

    // True when the expanded pill should show the charging layout (same rule as the old charging text)
    private fun chargeExpandedShown() =
        charging && showCharging() && (activityWinner() == "charging" || shouldShowChargingWithNotification())

    private fun updateChargeBox() {
        val ma = chargeMa
        val w = chargeWatts
        val textScale = prefs.getInt("sectionTextScale_charging", 100).coerceIn(80, 160) / 100f
        val iconScale = prefs.getInt("sectionIconScale_charging", 100).coerceIn(70, 160) / 100f
        val expandedRingDp = (prefs.getInt("chargeRingSize", 48).coerceIn(36, 80) * iconScale).toInt().coerceIn(32, 96)
        (chargeRing.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = dp(expandedRingDp)
            params.height = dp(expandedRingDp)
            chargeRing.layoutParams = params
        }
        chargeMetricIcons.forEach { metric ->
            (metric.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                val iconDp = (15 * iconScale).toInt().coerceIn(12, 24)
                params.width = dp(iconDp)
                params.height = dp(iconDp)
                metric.layoutParams = params
            }
        }
        (chargeTop + chargeBottom).forEach { it.textSize = 12.5f * textScale }
        if (::chargeMetricIcons.isInitialized) chargeMetricIcons.forEach { it.invalidate() }
        // Compact, consistent glyphs make each charging metric scannable without
        // adding extra rows that would make the floating pill taller.
        chargeTop[0].text = chargeEta
        chargeTop[1].text = "%.1f V".format(chargeVolts)
        chargeTop[2].text = if (ma != null) "%.2f A".format(ma / 1000.0) else "— A"
        chargeBottom[0].text = "$chargePct%"
        chargeBottom[1].text = "%.1f\u00B0C".format(lastTempC)
        chargeBottom[1].setTextColor(if (lastTempC >= 42.0) 0xFFFF9F0A.toInt() else 0xFFE6E6E6.toInt())
        chargeBottom[2].text = if (w != null) "%.1f W".format(w) else "\u2014 W"
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
        val state = c.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val pauseSupported = state?.actions?.and(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L
        val playSupported = state?.actions?.and(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L
        if (playing && !pauseSupported || !playing && !playSupported) {
            diag("media toggle unavailable: active session does not advertise ${if (playing) "pause" else "play"}")
            return
        }
        runCatching {
            if (playing) c.transportControls.pause() else c.transportControls.play()
        }.onSuccess {
            haptic()
            handler.postDelayed({ refreshMedia() }, 180L)
        }.onFailure { error -> diag("media toggle failed: ${error.javaClass.simpleName}") }
    }

    private fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val current = estimatedPlaybackPosition(c.playbackState)
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
        val target = if (deltaMs < 0L) (current + deltaMs).coerceAtLeast(0L) else {
            if (current > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE else current + deltaMs
        }
        c.transportControls.seekTo(duration?.let { target.coerceAtMost(it) } ?: target)
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
        if (shouldShowActivityNotificationCard()) return
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

    private fun enqueueNotification(p: PendingNotif) {
        notificationQueue.remove(p.key)
        notificationQueue[p.key] = p
        mediaNotificationKey = p.key
        mediaNotificationExpanded = false
        while (notificationQueue.size > maxQueuedNotifications) {
            val oldest = notificationQueue.keys.firstOrNull() ?: break
            notificationQueue.remove(oldest)
            notificationOverflow++
        }
        pending = notificationQueue.values.lastOrNull()
        refreshNotificationBadge()
    }

    private fun removeQueuedNotification(key: String?) {
        if (key == null) return
        notificationQueue.remove(key)
        if (notificationQueue.isEmpty()) {
            notificationOverflow = 0
            userFocusedNotification = false
        }
        pending = notificationQueue.values.lastOrNull()
        refreshNotificationBadge()
    }

    private fun refreshNotificationBadge() {
        if (!::notificationBadge.isInitialized) return
        val total = notificationQueue.size + notificationOverflow
        notificationBadge.text = if (notificationOverflow > 0) "20+" else total.toString()
        notificationBadgeTarget.contentDescription = "$total unread notifications. Tap to open the latest notification"
        notificationBadge.visibility = vis(total > 0)
        if (::mediaNotificationCard.isInitialized) renderMediaNotificationCard()
        if (::notificationBubble.isInitialized) updateNotificationBubble(presentationState())
        updateLockscreenNotificationSummary(total)
    }

    private fun notificationBadgeSizeDp(): Int =
        CapsulePrefs.notificationBadgeSize(prefs.getInt("notificationBadgeSize", CapsulePrefs.notificationBadgeSizeDefault))

    private fun notificationBadgeFillColor(): Int =
        CapsulePrefs.notificationBadgeColor(
            prefs.getString("notificationBadgeColor", "theme"),
            notificationQueueStyle().accent
        )

    private fun notificationBadgeTextColor(): Int =
        CapsulePrefs.notificationBadgeTextColor(notificationBadgeFillColor())

    private fun applyNotificationBadgeAppearance() {
        if (!::notificationBadge.isInitialized) return
        val badgeSize = notificationBadgeSizeDp()
        val fillColor = notificationBadgeFillColor()
        notificationBadge.textSize = badgeSize * 0.46f
        notificationBadge.minWidth = dp(badgeSize)
        notificationBadge.minHeight = dp(badgeSize)
        val horizontalPadding = dp((badgeSize / 8).coerceAtLeast(2))
        notificationBadge.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        notificationBadge.setTextColor(CapsulePrefs.notificationBadgeTextColor(fillColor))
        notificationBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        notificationBadge.requestLayout()
    }

    private fun notificationBadgeGravity(corner: String): Int = when (corner) {
        "top_start" -> Gravity.TOP or Gravity.LEFT
        "bottom_start" -> Gravity.BOTTOM or Gravity.LEFT
        "bottom_end" -> Gravity.BOTTOM or Gravity.RIGHT
        "right_side" -> Gravity.CENTER_VERTICAL or Gravity.RIGHT
        else -> Gravity.TOP or Gravity.RIGHT
    }

    private fun notificationBadgeContentGravity(corner: String): Int =
        if (corner == "right_side") Gravity.CENTER_VERTICAL or Gravity.LEFT else notificationBadgeGravity(corner)

    private fun sideBadgeSlot(): Int =
        if (prefs.getString("notificationBadgeCorner", "top_end") == "right_side") dp(56) else 0

    private fun activityBubbleBadgeReserve(): Int {
        if (notificationBubbleSlotActive || (notificationQueue.isEmpty() && notificationOverflow == 0)) return 0
        val corner = prefs.getString("notificationBadgeCorner", "top_end")
        return if (corner == "top_end" || corner == "bottom_end") dp(48) else 0
    }

    /** Keep the side bubble and a right-corner queue badge in separate hit regions. */
    private fun activityBubbleSlot(): Int =
        if (activityBubbleSlotActive) dp(80) + activityBubbleBadgeReserve() else 0

    private fun notificationBubbleSlot(): Int =
        if (notificationBubbleSlotActive) dp(112) else 0

    private fun overlayRightSlots(includeActivityBubble: Boolean = !expanded): Int =
        sideBadgeSlot() + if (includeActivityBubble) activityBubbleSlot() else 0

    private fun overlayLeftSlots(includeNotificationBubble: Boolean = !expanded): Int =
        if (includeNotificationBubble) notificationBubbleSlot() else 0

    private fun overlaySideSlots(
        includeActivityBubble: Boolean = !expanded,
        includeNotificationBubble: Boolean = !expanded
    ): Int = overlayRightSlots(includeActivityBubble) + overlayLeftSlots(includeNotificationBubble)

    private fun shouldShowActivityBubble(state: CapsulePresentationState = presentationState()): Boolean {
        if (!prefs.getBoolean("splitActivityOn", false) || expanded || callActive ||
            stopwatchStart > 0L || qsActive() || widgetActive() ||
            (lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true))) return false
        val secondary = state.secondary ?: return false
        return secondary.id != suppressedActivityBubbleKey
    }

    /** A queued notification gets its own left-side pill; it never becomes a card under media/Maps/charging. */
    private fun shouldShowNotificationBubble(state: CapsulePresentationState = presentationState()): Boolean {
        if (expanded || callActive || stopwatchStart > 0L || qsActive() || widgetActive() ||
            (lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true)) || notificationQueue.isEmpty()) return false
        return state.notificationHost != null && state.foreground !in setOf(
            CapsuleActivityKind.CALL, CapsuleActivityKind.STOPWATCH, CapsuleActivityKind.LIVE,
            CapsuleActivityKind.NOTIFICATION
        )
    }

    private fun setNotificationBadgeCorner(corner: String) {
        if (!::notificationBadgeTarget.isInitialized || !::notificationBadge.isInitialized) return
        val targetGravity = notificationBadgeGravity(corner)
        val contentGravity = notificationBadgeContentGravity(corner)
        val targetParams = notificationBadgeTarget.layoutParams as? FrameLayout.LayoutParams ?: return
        val badgeParams = notificationBadge.layoutParams as? FrameLayout.LayoutParams ?: return
        if (targetParams.gravity == targetGravity && badgeParams.gravity == contentGravity) return
        targetParams.gravity = targetGravity
        targetParams.setMargins(0, 0, 0, 0)
        if (corner == "right_side") targetParams.rightMargin = touchPad()
        notificationBadgeTarget.layoutParams = targetParams
        badgeParams.gravity = contentGravity
        badgeParams.setMargins(0, 0, 0, 0)
        when (corner) {
            "top_start" -> { badgeParams.topMargin = dp(1); badgeParams.leftMargin = dp(1) }
            "bottom_start" -> { badgeParams.bottomMargin = dp(1); badgeParams.leftMargin = dp(1) }
            "bottom_end" -> { badgeParams.bottomMargin = dp(1); badgeParams.rightMargin = dp(1) }
            "right_side" -> { badgeParams.leftMargin = dp(1); badgeParams.rightMargin = dp(1) }
            else -> { badgeParams.topMargin = dp(1); badgeParams.rightMargin = dp(1) }
        }
        notificationBadge.layoutParams = badgeParams
    }

    /** Keep the preferred corner when clear; otherwise move the badge away from visible controls. */
    private fun avoidNotificationBadgeOverlap() {
        if (!::notificationBadgeTarget.isInitialized || notificationBadge.visibility != View.VISIBLE) return
        val container = root ?: return
        val size = dp(44)
        if (container.width < size || container.height < size) return
        val rootLocation = IntArray(2)
        container.getLocationOnScreen(rootLocation)
        val importantViews = buildList {
            addAll(listOf(icon, title, subtitle, miniText, miniRight, miniChargeRing))
            addAll(listOf(btnPrev, btnBack10, btnPlay, btnForward10, btnNext, btnAnswer, btnDecline))
            addAll(listOf(progress, elapsedText, remainText, navTurn, navStreet, navEta, navArrive, navLeft))
            addAll(listOf(mediaNotificationTitle, mediaNotificationBody, mediaNotificationPosition))
            addAll(listOf(notificationPrevious, notificationPosition, notificationNext))
            addAll(chargeTop)
            addAll(chargeBottom)
        }.filter { it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
        val protectedRects = importantViews.map { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            intArrayOf(
                location[0] - rootLocation[0], location[1] - rootLocation[1],
                location[0] - rootLocation[0] + view.width, location[1] - rootLocation[1] + view.height
            )
        }
        val preferred = prefs.getString("notificationBadgeCorner", "top_end")
            ?.takeIf { it in setOf("top_start", "top_end", "bottom_start", "bottom_end", "right_side") } ?: "top_end"
        val candidates = listOf(preferred, "top_start", "top_end", "bottom_start", "bottom_end").distinct()
        fun overlapArea(corner: String): Long {
            val left = when {
                corner == "right_side" -> container.width - touchPad() - size
                corner.endsWith("start") -> 0
                else -> container.width - size
            }
            val top = when {
                corner == "right_side" -> (container.height - size) / 2
                corner.startsWith("top") -> 0
                else -> container.height - size
            }
            return protectedRects.sumOf { rect ->
                val w = (minOf(left + size, rect[2]) - maxOf(left, rect[0])).coerceAtLeast(0)
                val h = (minOf(top + size, rect[3]) - maxOf(top, rect[1])).coerceAtLeast(0)
                w.toLong() * h
            }
        }
        val bestCorner = candidates.minByOrNull(::overlapArea) ?: preferred
        setNotificationBadgeCorner(bestCorner)
    }

    private fun currentMediaNotification(): PendingNotif? {
        val items = notificationQueue.values.toList()
        if (items.isEmpty()) {
            mediaNotificationKey = null
            return null
        }
        return items.firstOrNull { it.key == mediaNotificationKey }
            ?: items.last().also { mediaNotificationKey = it.key }
    }

    private fun renderMediaNotificationCard() {
        if (!::mediaNotificationCard.isInitialized) return
        val item = currentMediaNotification()
        if (item == null) {
            mediaNotificationCard.visibility = View.GONE
            mediaNotificationActionsRow.visibility = View.GONE
            mediaNotificationExpanded = false
            return
        }
        mediaNotificationTitle.text = item.title
        mediaNotificationBody.text = item.body.ifBlank { appLabel(item.pkg) }
        mediaNotificationBody.maxLines = if (mediaNotificationExpanded) 4 else 1
        val actionCount = item.actions.size.coerceAtMost(mediaNotificationActionButtons.size)
        mediaNotificationPosition.text = if (actionCount > 0) "Latest notification · actions below" else "Latest notification"
        mediaNotificationContent.contentDescription = when {
            mediaNotificationExpanded && actionCount > 0 -> "Latest notification details expanded. $actionCount actions available."
            mediaNotificationExpanded -> "Latest notification details expanded."
            actionCount > 0 -> "Latest notification preview. Tap to show actions."
            else -> "Latest notification preview. Tap to expand."
        }
        mediaNotificationActionButtons.forEachIndexed { actionIndex, button ->
            val action = item.actions.getOrNull(actionIndex)
            button.text = action?.title?.toString().orEmpty()
            button.contentDescription = action?.title?.toString()?.let { "Notification action: $it" }
                ?: "Unavailable notification action"
            button.visibility = vis(mediaNotificationExpanded && action != null)
        }
        mediaNotificationActionsRow.visibility = vis(mediaNotificationExpanded && actionCount > 0)
        if (mediaNotificationRenderedKey != item.key) {
            mediaNotificationIcon.setImageDrawable(appIcon(item.pkg))
            mediaNotificationRenderedKey = item.key
        }
        val queueStyle = notificationQueueStyle()
        mediaNotificationCard.background = notificationQueueSurface()
        mediaNotificationTitle.setTextColor(queueStyle.primary)
        mediaNotificationBody.setTextColor(queueStyle.secondary)
        mediaNotificationPosition.setTextColor(queueStyle.secondary)
        // This renderer also runs when the badge count changes. Never let that refresh
        // resurrect the expanded card while the pill is collapsed or privacy-masked.
        mediaNotificationCard.visibility = View.GONE
    }

    /** Keep the selected notification card, but make the rest of the queue visible underneath it. */
    private fun renderNotificationQueueStack(activityHost: Boolean) {
        if (!::notificationQueueStack.isInitialized) return
        val items = notificationQueue.values.toList()
        val selectedKey = if (activityHost) mediaNotificationKey else notifKey
        val remaining = items.filterNot { it.key == selectedKey }.asReversed()
        notificationQueueStackRows.removeAllViews()
        if (remaining.isEmpty()) {
            notificationQueueStack.visibility = View.GONE
            return
        }
        val style = notificationQueueStyle()
        remaining.forEach { item ->
            val entry = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(5), dp(6), dp(5))
                background = notificationQueueSurface()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
                isClickable = true
                isFocusable = true
                contentDescription = "${item.title}. ${item.body}. Select notification."
            }
            val copy = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(uiCtx).apply {
                    text = item.title.ifBlank { appLabel(item.pkg) }
                    setTextColor(style.primary)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(uiCtx).apply {
                    text = item.body.ifBlank { appLabel(item.pkg) }
                    setTextColor(style.secondary)
                    textSize = 10f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            entry.addView(copy)
            entry.addView(TextView(uiCtx).apply {
                text = "×"
                contentDescription = "Dismiss ${item.title}"
                setTextColor(style.secondary)
                textSize = 18f
                gravity = Gravity.CENTER
                minWidth = dp(44)
                minHeight = dp(44)
                setOnClickListener {
                    val selected = if (activityHost) mediaNotificationKey else notifKey
                    removeQueuedNotification(item.key)
                    runCatching { cancelNotification(item.key) }
                    if (selected == item.key) {
                        if (activityHost) mediaNotificationKey = notificationQueue.values.lastOrNull()?.key
                        else notificationQueue.values.lastOrNull()?.let { presentNotif(it, 2) }
                        mediaNotificationExpanded = false
                    }
                    applyVisibility()
                }
            })
            entry.setOnClickListener {
                handler.removeCallbacks(collapse)
                if (activityHost) {
                    mediaNotificationKey = item.key
                    mediaNotificationExpanded = false
                    renderMediaNotificationCard()
                    applyVisibility()
                } else {
                    focusNotifications()
                    presentNotif(item, 2)
                    if (!expanded) setExpanded(true) else applyVisibility()
                    armNotifTimer()
                }
                haptic()
            }
            notificationQueueStackRows.addView(entry)
        }
        notificationQueueStack.visibility = View.VISIBLE
    }

    private fun browseMediaNotification(delta: Int) {
        val items = notificationQueue.values.toList()
        if (items.size < 2) return
        val index = items.indexOfFirst { it.key == mediaNotificationKey }
        val current = if (index >= 0) index else items.lastIndex
        val next = items[(current + delta).coerceIn(0, items.lastIndex)]
        mediaNotificationKey = next.key
        mediaNotificationExpanded = false
        haptic()
        renderMediaNotificationCard()
    }

    private fun toggleMediaNotificationDetails() {
        if (currentMediaNotification() == null) return
        mediaNotificationExpanded = !mediaNotificationExpanded
        haptic()
        renderMediaNotificationCard()
        applyVisibility()
    }

    private fun runMediaNotificationAction(index: Int) {
        val action = currentMediaNotification()?.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
        haptic()
        mediaNotificationExpanded = false
        renderMediaNotificationCard()
        applyVisibility()
    }

    private fun openMediaNotification() {
        val item = currentMediaNotification() ?: return
        mediaNotificationKey = item.key
        handler.removeCallbacks(collapse)
        mediaNotificationExpanded = false
        focusNotifications()
        presentNotif(item, 2)
        setExpanded(true)
        armNotifTimer()
    }

    private fun dismissMediaNotification(key: String? = null) {
        val item = (if (key == null) currentMediaNotification() else notificationQueue[key]) ?: return
        removeQueuedNotification(item.key)
        mediaNotificationExpanded = false
        runCatching { cancelNotification(item.key) }
        val next = notificationQueue.values.lastOrNull()
        mediaNotificationKey = next?.key
        if (notifKey == item.key && !liveActivityIsShowing()) {
            if (next != null) presentNotif(next, 1) else {
                showingNotif = false
                notifIntent = null
                notifKey = null
                notifLevel = 0
                replyAction = null
                notifActions = emptyList()
                userFocusedNotification = false
            }
        }
        if (!showingNotif && controller != null && showMusic()) renderMedia()
        applyVisibility()
    }

    private fun updateLockscreenNotificationSummary(count: Int) {
        if (!prefs.getBoolean("keepNotif", true) || !prefs.getBoolean("showOnLockScreen", true) ||
            !lockScreenActive() || count == lastLockscreenSummaryCount) return
        lastLockscreenSummaryCount = count
        runCatching {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, "keep")
            } else Notification.Builder(this)
            val summary = builder
                .setSmallIcon(R.drawable.ic_nav_pointer)
                .setContentTitle("Capsule")
                .setContentText(if (count == 0) "Pill is ready" else "$count new notification${if (count == 1) "" else "s"} · unlock to view")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(7001, summary)
        }
    }

    private fun browseQueuedNotification(delta: Int) {
        val items = notificationQueue.values.toList()
        if (items.isEmpty() || callActive || stopwatchStart > 0L || liveActivityIsShowing()) return
        val current = items.indexOfFirst { it.key == notifKey }
        val start = if (current >= 0) current else items.lastIndex
        val next = items[(start + delta).coerceIn(0, items.lastIndex)]
        handler.removeCallbacks(collapse)
        focusNotifications()
        presentNotif(next, 2)
        if (!expanded) setExpanded(true) else applyVisibility()
        refreshNotificationBadge()
        armNotifTimer()
    }

    private fun restoreQueuedNotifications() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        val eligible = active.filter { sbn ->
            val n = sbn.notification
            val missed = n.category == Notification.CATEGORY_MISSED_CALL
            sbn.packageName != packageName && sbn.packageName !in blockedApps() &&
                sbn.packageName != CapsulePrefs.MAPS_PKG && prefs.getBoolean("showNotifs", true) &&
                (missed || (!sbn.isOngoing && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    n.category !in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_NAVIGATION,
                        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT) &&
                    LiveActivityPolicy.classify(sbn) == null))
        }.sortedBy { it.postTime }.takeLast(maxQueuedNotifications)
        eligible.forEach { sbn ->
            val n = sbn.notification
            val titleText = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: return@forEach
            val bodyText = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val reply = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
            val actions = n.actions?.filter { it.remoteInputs.isNullOrEmpty() }?.take(2) ?: emptyList()
            enqueueNotification(PendingNotif(
                sbn.key, sbn.packageName, titleText, bodyText, n.contentIntent, n.color,
                reply, actions, n.category == Notification.CATEGORY_MISSED_CALL, sbn.postTime
            ))
        }
        applyVisibility()
    }

    private fun setExpanded(e: Boolean) {
        val r = root ?: return
        if (e == expanded) {
            applyVisibility()
            return
        }
        if (!e) {
            mediaNotificationExpanded = false
            // Returning from a companion activity always restores the scheduler's stable main owner.
            focusedActivityId = null
        }
        val style = pillAnimationStyle()
        val flight = style == "continuity" && motionEnabled() && prepareChargeIconFlight(e)
        val artFlight = style == "continuity" && motionEnabled() &&
            activityWinner() == "media" && !lockScreenActive() && prepareMediaArtFlight()
        if (flight && e) chargeBox.alpha = 0f
        // Keep a visual copy of the expanded contents over the shrinking surface so
        // controls and details don't disappear before the collapse motion finishes.
        snapshotOld(collapse = !e)
        expanded = e
        syncOverlayRowInsets()
        val priorCompanionDirection = companionMotionExpansionOverride
        // Side pills do the complementary part of the same transition: as the main
        // pill opens they fold toward it; as it closes they unfold from its edge.
        companionMotionExpansionOverride = !e
        try {
            applyVisibility()
        } finally {
            companionMotionExpansionOverride = priorCompanionDirection
        }
        staggerIn()
        animateSize()
        if (flight) startChargeIconFlight(e)
        if (artFlight) startMediaArtFlight()
        animatePillEdge(style)
        if (style == "settling_dock") animateSettlingChargeIcon(e)
    }

    // Reference motion uses a restrained lit edge to make the active surface easier to track.
    private fun animatePillEdge(animationStyle: String) {
        val surface = pill?.background as? GradientDrawable ?: return
        pillEdgeAnimator?.cancel()
        val style = pillStyle()
        val base = if (prefs.getBoolean("blur", false) && style.id == "capsule") 0x66FFFFFF else style.outline
        if (!motionEnabled() || animationStyle == "settling_dock") {
            surface.setStroke(dp(1), base)
            return
        }
        val accent = pillAccent()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (prefs.getInt("springPreset", 1)) { 0 -> 260L; 2 -> 440L; else -> 340L }
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { value ->
                val t = value.animatedValue as Float
                val pulse = if (t < 0.42f) t / 0.42f else 1f - ((t - 0.42f) / 0.58f)
                val color = android.animation.ArgbEvaluator().evaluate(pulse * 0.58f, base, accent) as Int
                surface.setStroke(dp(1 + pulse.coerceAtLeast(0f).times(1.5f).toInt()), color)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (pillEdgeAnimator !== animation) return
                    pillEdgeAnimator = null
                    surface.setStroke(dp(1), base)
                }
            })
        }
        pillEdgeAnimator = animator
        animator.start()
    }

    private fun prepareMediaArtFlight(): Boolean {
        val continuing = mediaArtFlightAnimator != null && mediaArtFlightBitmap?.isRecycled == false
        if (continuing) {
            val oldAnimator = mediaArtFlightAnimator
            mediaArtFlightAnimator = null
            oldAnimator?.cancel()
            mediaArtFlightPending = true
            icon.alpha = 0f
            return true
        }
        if (icon.visibility != View.VISIBLE || icon.width <= 0 || icon.height <= 0) return false
        val bitmap = runCatching {
            Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888).also { icon.draw(Canvas(it)) }
        }.getOrNull() ?: return false
        val loc = IntArray(2)
        icon.getLocationOnScreen(loc)
        mediaArtFlightBitmap?.takeIf { !it.isRecycled }?.recycle()
        mediaArtFlightBitmap = bitmap
        mediaArtFlightRect = RectF(loc[0].toFloat(), loc[1].toFloat(),
            (loc[0] + icon.width).toFloat(), (loc[1] + icon.height).toFloat())
        mediaArtFlightPending = true
        icon.alpha = 0f
        return true
    }

    private fun startMediaArtFlight() {
        val container = root as? FrameLayout ?: run { cancelMediaArtFlight(); return }
        container.post {
            val bitmap = mediaArtFlightBitmap
            if (bitmap == null || bitmap.isRecycled || icon.width <= 0 || icon.height <= 0 ||
                icon.visibility != View.VISIBLE) {
                cancelMediaArtFlight()
                return@post
            }
            val image = mediaArtFlightView ?: ImageView(uiCtx).also {
                it.scaleType = ImageView.ScaleType.CENTER_CROP
                mediaArtFlightView = it
                container.addView(it, FrameLayout.LayoutParams(1, 1))
            }
            image.setImageBitmap(bitmap)
            image.visibility = View.VISIBLE
            val from = RectF(mediaArtFlightRect)
            val duration = when (prefs.getInt("springPreset", 1)) { 0 -> 260L; 2 -> 440L; else -> 340L }
            fun targetRect(): RectF {
                val loc = IntArray(2)
                icon.getLocationOnScreen(loc)
                return RectF(loc[0].toFloat(), loc[1].toFloat(),
                    (loc[0] + icon.width).toFloat(), (loc[1] + icon.height).toFloat())
            }
            fun place(rect: RectF) {
                val rootLoc = IntArray(2)
                container.getLocationOnScreen(rootLoc)
                val lp = image.layoutParams as FrameLayout.LayoutParams
                lp.width = rect.width().toInt().coerceAtLeast(1)
                lp.height = rect.height().toInt().coerceAtLeast(1)
                lp.leftMargin = (rect.left - rootLoc[0] - container.paddingLeft).toInt()
                lp.topMargin = (rect.top - rootLoc[1]).toInt()
                image.layoutParams = lp
                mediaArtFlightRect.set(rect)
            }
            place(from)
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    val end = targetRect()
                    place(RectF(
                        from.left + (end.left - from.left) * f,
                        from.top + (end.top - from.top) * f,
                        from.right + (end.right - from.right) * f,
                        from.bottom + (end.bottom - from.bottom) * f
                    ))
                    icon.alpha = f
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (mediaArtFlightAnimator !== animation) return
                        mediaArtFlightAnimator = null
                        cancelMediaArtFlight()
                    }
                })
            }
            mediaArtFlightPending = false
            mediaArtFlightAnimator = animator
            animator.start()
        }
    }

    private fun cancelMediaArtFlight() {
        mediaArtFlightPending = false
        mediaArtFlightAnimator?.cancel()
        mediaArtFlightAnimator = null
        mediaArtFlightView?.let { view ->
            view.setImageDrawable(null)
            view.visibility = View.GONE
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        mediaArtFlightView = null
        mediaArtFlightBitmap?.takeIf { !it.isRecycled }?.recycle()
        mediaArtFlightBitmap = null
        if (::icon.isInitialized) icon.alpha = 1f
    }

    private fun prepareChargeIconFlight(expanding: Boolean): Boolean {
        val continuing = (chargeFlightAnimator != null || chargeFlightPending) && chargeFlightBitmap?.isRecycled == false
        if (continuing) {
            cancelChargeIconFlight(keepImage = true)
            chargeRing.alpha = 0f
            miniChargeRing.alpha = 0f
            chargeFlightPending = true
            return true
        }
        val source = if (expanding) miniChargeRing else chargeRing
        val destination = if (expanding) chargeRing else miniChargeRing
        if (source.width <= 0 || source.height <= 0 || source.visibility != View.VISIBLE) return false
        val bitmap = runCatching {
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                source.draw(Canvas(it))
            }
        }.getOrNull() ?: return false
        val loc = IntArray(2)
        source.getLocationOnScreen(loc)
        chargeFlightBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        chargeFlightBitmap = bitmap
        chargeFlightRect = RectF(loc[0].toFloat(), loc[1].toFloat(),
            (loc[0] + source.width).toFloat(), (loc[1] + source.height).toFloat())
        chargeFlightPending = true
        source.alpha = 0f
        destination.alpha = 0f
        return true
    }

    private fun startChargeIconFlight(expanding: Boolean) {
        val container = root as? FrameLayout ?: run {
            cancelChargeIconFlight()
            return
        }
        val destination = if (expanding) chargeRing else miniChargeRing
        container.post {
            val bitmap = chargeFlightBitmap
            if (bitmap == null || bitmap.isRecycled || destination.width <= 0 || destination.height <= 0) {
                cancelChargeIconFlight()
                return@post
            }
            val screen = IntArray(2)
            destination.getLocationOnScreen(screen)
            val end = RectF(screen[0].toFloat(), screen[1].toFloat(),
                (screen[0] + destination.width).toFloat(), (screen[1] + destination.height).toFloat())
            val image = chargeFlightView ?: ImageView(uiCtx).also {
                it.scaleType = ImageView.ScaleType.FIT_XY
                chargeFlightView = it
                container.addView(it, FrameLayout.LayoutParams(1, 1))
            }
            image.setImageBitmap(bitmap)
            image.visibility = View.VISIBLE
            val from = RectF(chargeFlightRect)
            val duration = when (prefs.getInt("springPreset", 1)) {
                0 -> 260L
                2 -> 440L
                else -> 340L
            }
            fun place(rect: RectF) {
                val rootLoc = IntArray(2)
                container.getLocationOnScreen(rootLoc)
                val lp = image.layoutParams as FrameLayout.LayoutParams
                lp.width = rect.width().toInt().coerceAtLeast(1)
                lp.height = rect.height().toInt().coerceAtLeast(1)
                lp.leftMargin = (rect.left - rootLoc[0] - container.paddingLeft).toInt()
                lp.topMargin = (rect.top - rootLoc[1]).toInt()
                image.layoutParams = lp
                chargeFlightRect.set(rect)
            }
            place(from)
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = if (motionEnabled()) duration else 1L
                interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    val targetLoc = IntArray(2)
                    destination.getLocationOnScreen(targetLoc)
                    val liveEnd = RectF(targetLoc[0].toFloat(), targetLoc[1].toFloat(),
                        (targetLoc[0] + destination.width).toFloat(), (targetLoc[1] + destination.height).toFloat())
                    place(RectF(
                        from.left + (liveEnd.left - from.left) * f,
                        from.top + (liveEnd.top - from.top) * f,
                        from.right + (liveEnd.right - from.right) * f,
                        from.bottom + (liveEnd.bottom - from.bottom) * f
                    ))
                    chargeBox.alpha = if (expanding) f else 1f - f
                    chargeTop.forEach { it.alpha = if (expanding) f else 1f - f }
                    chargeBottom.forEach { it.alpha = if (expanding) f else 1f - f }
                    miniRight.alpha = if (expanding) 1f - f else f
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (chargeFlightAnimator !== animation) return
                        chargeFlightAnimator = null
                        cancelChargeIconFlight()
                    }
                })
            }
            chargeFlightPending = false
            chargeFlightAnimator = animator
            animator.start()
        }
    }

    private fun cancelChargeIconFlight(keepImage: Boolean = false) {
        chargeFlightPending = false
        val animator = chargeFlightAnimator
        chargeFlightAnimator = null
        animator?.cancel()
        chargeFlightView?.let { view ->
            view.animate().cancel()
            view.setImageDrawable(null)
            view.visibility = View.GONE
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        chargeFlightView = null
        if (!keepImage) {
            chargeFlightBitmap?.takeIf { !it.isRecycled }?.recycle()
            chargeFlightBitmap = null
            if (::chargeRing.isInitialized) chargeRing.alpha = 1f
            if (::miniChargeRing.isInitialized) miniChargeRing.alpha = 1f
            if (::chargeBox.isInitialized) chargeBox.alpha = 1f
            if (::chargeTop.isInitialized && ::chargeBottom.isInitialized) {
                (chargeTop + chargeBottom).forEach { it.alpha = 1f }
            }
            if (::miniRight.isInitialized) miniRight.alpha = 1f
        }
    }

    private fun pillAnimationStyle(): String =
        (prefs.getString("pillAnimationStyle", "continuity") ?: "continuity").let {
            if (it in setOf("liquid_morph", "continuity", "settling_dock", "capsule_spring")) it else "continuity"
        }

    // Respect the legacy animation switch, reduced motion, and Android's system animator setting.
    // ValueAnimator.areAnimatorsEnabled() is available from API 26; this app supports API 24+.
    private fun motionEnabled(): Boolean {
        if (!prefs.getBoolean("springMotion", true) || prefs.getBoolean("reduceMotion", false)) return false
        return android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()
    }

    // Each visible part of the pill grows in from slightly small and blurry, one after another
    private fun staggerIn() {
        if (pillAnimationStyle() == "settling_dock") {
            settlingDockEntrance()
            return
        }
        val items = listOf(
            miniText, miniRight, icon, textCol, chargeBox, navBox,
            btnPrev, btnPlay, btnNext, barsRow, btnAnswer, btnDecline
        ) + chips
        if (!motionEnabled()) {
            // If motion is disabled while an entrance is queued, clear the old animation state too.
            for (v in items) {
                if (mediaArtFlightPending && v === icon) continue
                if (chargeFlightPending && (v === chargeBox || v === miniRight)) continue
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
            if (mediaArtFlightPending && v === icon) continue
            if (chargeFlightPending && (v === chargeBox || v === miniRight)) continue
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

    private fun settlingDockEntrance() {
        val items = listOf(
            miniText, miniRight, icon, textCol, chargeBox, navBox,
            btnPrev, btnPlay, btnNext, barsRow, btnAnswer, btnDecline
        ) + chips
        if (!motionEnabled()) {
            items.forEach { view ->
                view.animate().cancel()
                view.alpha = 1f
                view.translationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            return
        }
        items.forEach { view ->
            view.animate().cancel()
            blurAnimators.remove(view)?.cancel()
            if (android.os.Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
            if (view.visibility != View.VISIBLE) {
                view.alpha = 1f
                view.translationY = 0f
                return@forEach
            }
            view.alpha = 0f
            view.translationY = dp(7).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.3f))
                .start()
        }
    }

    private fun animateSettlingChargeIcon(expanding: Boolean) {
        val target = if (expanding) chargeRing else miniChargeRing
        val other = if (expanding) miniChargeRing else chargeRing
        other.animate().cancel()
        other.scaleX = 1f
        other.scaleY = 1f
        if (target.visibility != View.VISIBLE) return
        if (!motionEnabled()) {
            target.scaleX = 1f
            target.scaleY = 1f
            return
        }
        val initialScale = if (expanding) 0.62f else 1.55f
        target.animate().cancel()
        target.scaleX = initialScale
        target.scaleY = initialScale
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.3f))
            .start()
    }

    // Expanded height depends on what the pill is showing: grid, music, notification or call, or charging
    private fun targetInnerH(): Int {
        if (lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true)) return collapsedTargetHeight()
        // Collapsed notification state is represented only by the count badge. Never let the
        // queued notification's text/action height keep the compact pill expanded vertically.
        if (!expanded) return collapsedTargetHeight()
        val notificationWinner = activityWinner()
        if (!callActive && !qsActive() && !widgetActive() &&
            (showingNotif || notificationWinner == "notifications") &&
            notificationWinner in setOf("live", "notifications") &&
            !shouldShowActivityNotificationCard()) {
            val defaultMaximum = 220
            val available = (resources.displayMetrics.heightPixels - lp.y - dp(48)).coerceAtLeast(dp(64))
            val configuredMaximum = if (prefs.getBoolean("customSize_notifications", false)) {
                customSizePx("notifications", "ExpandedHeight", dp(defaultMaximum), dp(64), available)
            } else {
                dp(prefs.getInt("expH_notif", defaultMaximum)).coerceIn(dp(64), available)
            }
            return notificationPanelContentHeight(configuredMaximum).coerceAtMost(available)
        }
        if (qsActive()) {
            val rows = (CapsulePrefs.qsItems.size + pinnedPackages().size + QS_COLS - 1) / QS_COLS
            return rows * dp(46) + dp(16)
        }
        if (widgetActive()) return dp(prefs.getInt("imageHeight", 120))
        if (idleTasksPreviewVisible()) {
            val tasks = CapsuleTaskStore.filtered(
                prefs,
                prefs.getString("taskPreviewList", CapsuleTaskStore.ALL_LISTS) ?: CapsuleTaskStore.ALL_LISTS
            )
            val count = minOf(tasks.size, prefs.getInt("taskPreviewLimit", 3).coerceIn(1, 8))
            val desired = dp(82 + count * 36)
            val available = (resources.displayMetrics.heightPixels - lp.y - dp(48)).coerceAtLeast(dp(100))
            val screenFitHeight = desired.coerceAtMost(available)
            return customSizePx("tasks", "ExpandedHeight", screenFitHeight, screenFitHeight, available)
        }
        val key = when {
            callActive -> "expH_notif"
            shouldShowMediaWithNotification() -> "expH_music"
            shouldShowChargingWithNotification() -> "expH_charge"
            shouldShowNavigationWithNotification() -> "expH_nav"
            showingNotif -> "expH_notif"
            mapsShown() -> "expH_nav"
            controller != null && showMusic() -> "expH_music"
            else -> "expH_charge"
        }
        val configured = prefs.getInt(key, if (key == "expH_music") 220 else if (key == "expH_nav") 112 else if (key == "expH_notif") 220 else 64)
        val minimum = when {
            callActive -> 144
            key == "expH_music" -> 168
            else -> 0
        }
        val queueDetailsHeight = if (mediaNotificationExpanded) 80 else 0
        val queueStackHeight = if (notificationQueue.size > 1) ((notificationQueue.size - 1) * 58 + 6).coerceAtMost(132) else 0
        val combinedMinimum = when {
            shouldShowMediaWithNotification() -> 228 + queueDetailsHeight + queueStackHeight
            shouldShowChargingWithNotification() -> 164 + queueDetailsHeight + queueStackHeight
            shouldShowNavigationWithNotification() -> 220 + queueDetailsHeight + queueStackHeight
            else -> 0
        }
        val floor = dp(maxOf(minimum, combinedMinimum))
        val desired = dp(maxOf(configured, minimum, combinedMinimum))
        val available = (resources.displayMetrics.heightPixels - lp.y - dp(48)).coerceAtLeast(floor)
        return customSizePx(activePillSizingSection(), "ExpandedHeight", desired, floor, available)
    }

    /** Fit short notification text naturally; the user's height value caps longer content. */
    private fun notificationPanelContentHeight(maximumHeight: Int): Int {
        if (!::title.isInitialized || !::subtitle.isInitialized) return maximumHeight
        val iconScale = prefs.getInt("sectionIconScale_notifications", 100).coerceIn(70, 160) / 100f
        val iconHeight = (dp(40) * iconScale).toInt().coerceIn(dp(24), dp(80))
        val textWidth = (expandedTargetWidth() - iconHeight - dp(28)).coerceAtLeast(dp(72))
        val titleHeight = notificationTextHeight(title, textWidth, 1)
        val bodyLineHeight = notificationTextHeight(subtitle, textWidth, 1).coerceAtLeast(dp(12))
        val hasActionRow = notifLevel >= 2 && (
            notifActions.isNotEmpty() ||
                (replyAction != null && prefs.getBoolean("replyChips", true))
            )
        val actionHeight = if (hasActionRow) dp(52) else 0
        val actionGap = if (hasActionRow) dp(6) else 0
        val breathingRoom = dp(16)
        val headerBase = maxOf(iconHeight, titleHeight)
        val safeMinimum = headerBase + bodyLineHeight + actionHeight + actionGap + breathingRoom
        val bodyBudget = (maximumHeight - headerBase - actionHeight - actionGap - breathingRoom)
            .coerceAtLeast(bodyLineHeight)
        val bodyLines = (bodyBudget / bodyLineHeight).coerceIn(1, 3)
        subtitle.maxLines = bodyLines
        subtitle.ellipsize = TextUtils.TruncateAt.END
        val bodyHeight = notificationTextHeight(subtitle, textWidth, bodyLines)
        val contentHeight = maxOf(iconHeight, titleHeight + bodyHeight) +
            actionHeight + actionGap + breathingRoom
        return maxOf(safeMinimum, contentHeight).coerceAtMost(maxOf(safeMinimum, maximumHeight))
    }

    private fun notificationTextHeight(view: TextView, width: Int, maxLines: Int): Int {
        val text = view.text ?: return 0
        if (text.isEmpty()) return 0
        return runCatching {
            StaticLayout.Builder.obtain(text, 0, text.length, view.paint, width.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(view.includeFontPadding)
                .setLineSpacing(view.lineSpacingExtra, view.lineSpacingMultiplier)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(width.coerceAtLeast(1))
                .setMaxLines(maxLines.coerceAtLeast(1))
                .build()
                .height
        }.getOrElse { dp(18) * maxLines.coerceAtLeast(1) }
    }

    // ---------- shared spring motion system ----------
    // One display-frame callback drives size, corner, press, and swipe channels. Each channel is
    // analytical and frame-rate independent; retargeting preserves its current velocity.
    private val springW = CapsuleSpring()
    private val springH = CapsuleSpring()
    private val springCorner = CapsuleSpring()
    private val springX = CapsuleSpring()
    private val springPressX = CapsuleSpring()
    private val springPressY = CapsuleSpring()
    private val springAlpha = CapsuleSpring()
    private val springBubbleX = CapsuleSpring()
    private val springBubbleScale = CapsuleSpring()
    private val springBubbleAlpha = CapsuleSpring()
    private val notificationSpringX = CapsuleSpring()
    private val notificationSpringScale = CapsuleSpring()
    private val notificationSpringAlpha = CapsuleSpring()
    private var springRunning = false
    private var sizeMotionActive = false
    private var swipeSpringActive = false
    private var pressSpringActive = false
    private var alphaSpringActive = false
    private var bubbleSpringActive = false
    private var notificationSpringActive = false
    private var swipeDismissPending = false
    private var swipeReturnPending = false
    private var springLast = 0L

    private fun postSpringFrame() {
        if (springRunning) return
        springRunning = true
        springLast = 0L
        android.view.Choreographer.getInstance().postFrameCallback(springFrame)
    }

    private fun cornerTarget(height: Int): Float {
        val pad = touchPad()
        return minOf(
            ((height - 2 * pad).coerceAtLeast(0)) / 2f,
            dp(if (expanded) pillStyle().cornerDp else 40).toFloat()
        )
    }

    private val springFrame = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val r = root
            if (r == null || !r.isAttachedToWindow) {
                // Do not keep scheduling display-frame callbacks for an overlay that has been removed.
                // A subsequent animateSize() call will initialise the spring from the current layout.
                springRunning = false
                sizeMotionActive = false
                swipeSpringActive = false
                pressSpringActive = false
                alphaSpringActive = false
                bubbleSpringActive = false
                notificationSpringActive = false
                springLast = 0L
                return
            }
            if (!motionEnabled()) {
                // The system animation scale can change while a spring is already running.
                // Finish at the latest target immediately instead of continuing to animate.
                val pad = touchPad()
                val sideSlotsBeforeClose = overlaySideSlots()
                val mainWidthTargetBeforeClose = springW.target
                lp.width = (springW.target + 0.5f).toInt().coerceAtLeast(
                    miniW + 2 * pad + overlaySideSlots() - dp(4)
                )
                lp.height = (springH.target + 0.5f).toInt().coerceAtLeast(collapsedTargetHeight() + 2 * pad - dp(4))
                springW.snapTo(springW.target)
                springH.snapTo(springH.target)
                springCorner.snapTo(cornerTarget(lp.height))
                springX.snapTo(springX.target)
                springPressX.snapTo(1f)
                springPressY.snapTo(1f)
                springAlpha.snapTo(1f)
                lp.x = springX.value.toInt()
                pill?.scaleX = 1f
                pill?.scaleY = 1f
                pill?.alpha = 1f
                (pill?.background as? GradientDrawable)?.cornerRadius = springCorner.value
                runCatching { wm.updateViewLayout(r, lp) }
                syncPanelY()
                springRunning = false
                sizeMotionActive = false
                swipeSpringActive = false
                pressSpringActive = false
                alphaSpringActive = false
                bubbleSpringActive = false
                activityBubbleHidePending = false
                if (::activityBubble.isInitialized) {
                    springBubbleX.snapTo(if (activityBubbleTargetVisible) 0f else -companionOffset())
                    springBubbleScale.snapTo(if (activityBubbleTargetVisible) 1f else companionHiddenScale())
                    springBubbleAlpha.snapTo(if (activityBubbleTargetVisible) 1f else 0f)
                    activityBubble.translationX = springBubbleX.value
                    activityBubble.scaleX = springBubbleScale.value
                    activityBubble.scaleY = springBubbleScale.value
                    activityBubble.alpha = springBubbleAlpha.value
                    if (!activityBubbleTargetVisible) {
                        activityBubble.visibility = View.GONE
                        activityBubbleSlotActive = false
                        activityBubbleLastSlotWidth = 0
                    }
                }
                notificationSpringActive = false
                notificationBubbleHidePending = false
                if (::notificationBubble.isInitialized) {
                    notificationSpringX.snapTo(if (notificationBubbleTargetVisible) 0f else companionOffset())
                    notificationSpringScale.snapTo(if (notificationBubbleTargetVisible) 1f else companionHiddenScale())
                    notificationSpringAlpha.snapTo(if (notificationBubbleTargetVisible) 1f else 0f)
                    notificationBubble.translationX = notificationSpringX.value
                    notificationBubble.scaleX = notificationSpringScale.value
                    notificationBubble.scaleY = notificationSpringScale.value
                    notificationBubble.alpha = notificationSpringAlpha.value
                    if (!notificationBubbleTargetVisible) {
                        notificationBubble.visibility = View.GONE
                        notificationBubbleSlotActive = false
                        notificationBubbleLastSlotWidth = 0
                    }
                }
                syncOverlayRowInsets(updateWindow = false)
                val sideSlotsAfterClose = overlaySideSlots()
                val sideSlotDelta = sideSlotsAfterClose - sideSlotsBeforeClose
                val resolvedWidth = (if (expanded) expandedTargetWidth() else compactTargetWidth()) +
                    2 * pad + overlaySideSlots(includeActivityBubble = !expanded, includeNotificationBubble = !expanded)
                val finalWidth = if (sideSlotDelta != 0) {
                    (mainWidthTargetBeforeClose + sideSlotDelta).toInt().coerceAtLeast(
                        miniW + 2 * pad + sideSlotsAfterClose - dp(4)
                    )
                } else resolvedWidth
                val resolvedHeight = (if (expanded) targetInnerH() else collapsedTargetHeight()) + 2 * pad
                lp.width = finalWidth
                lp.height = resolvedHeight
                springW.snapTo(finalWidth.toFloat())
                springH.snapTo(resolvedHeight.toFloat())
                springCorner.snapTo(cornerTarget(resolvedHeight))
                runCatching { wm.updateViewLayout(r, lp) }
                syncPanelY()
                swipeDismissPending = false
                swipeReturnPending = false
                springLast = 0L
                return
            }
            val dt = if (springLast == 0L) 1f / 60f
            else ((frameTimeNanos - springLast) / 1_000_000_000f).coerceIn(0f, 0.05f)
            springLast = frameTimeNanos
            val speed = prefs.getString("springSpeed", "medium") ?: "medium"
            val profile = CapsuleMotionProfile.from(speed, pillAnimationStyle(), expanded)
            if (sizeMotionActive) {
                springW.advance(dt, profile.widthResponse, profile.dampingRatio)
                springH.advance(dt, profile.heightResponse, profile.dampingRatio)
                springCorner.advance(dt, 0.36f, profile.dampingRatio)
                if (springW.isSettled(0.6f, 5f) && springH.isSettled(0.6f, 5f) &&
                    springCorner.isSettled(0.6f, 5f)) {
                    springW.snapTo(springW.target)
                    springH.snapTo(springH.target)
                    springCorner.snapTo(springCorner.target)
                    sizeMotionActive = false
                }
            }
            if (swipeSpringActive) {
                springX.advance(dt, profile.gestureResponse, 0.94f)
                if (springX.isSettled(0.6f, 6f)) {
                    springX.snapTo(springX.target)
                    swipeSpringActive = false
                    if (swipeReturnPending) {
                        swipeReturnPending = false
                        if (showingNotif) armNotifTimer()
                    }
                }
                lp.x = springX.value.toInt()
            }
            if (pressSpringActive) {
                springPressX.advance(dt, 0.20f, 0.88f)
                springPressY.advance(dt, 0.20f, 0.88f)
                if (springPressX.isSettled(0.001f, 0.01f) && springPressY.isSettled(0.001f, 0.01f)) {
                    springPressX.snapTo(springPressX.target)
                    springPressY.snapTo(springPressY.target)
                    pressSpringActive = false
                }
                pill?.scaleX = springPressX.value
                pill?.scaleY = springPressY.value
            }
            if (alphaSpringActive) {
                springAlpha.advance(dt, 0.22f, 1f)
                if (springAlpha.isSettled(0.002f, 0.02f)) {
                    springAlpha.snapTo(springAlpha.target)
                    alphaSpringActive = false
                }
                pill?.alpha = springAlpha.value
            }
            if (bubbleSpringActive && ::activityBubble.isInitialized) {
                val bubbleProfile = companionMotionProfile(activityBubbleMotionExpanding)
                val bubbleDamping = companionDamping(bubbleProfile)
                springBubbleX.advance(dt, bubbleProfile.widthResponse, bubbleDamping)
                springBubbleScale.advance(dt, bubbleProfile.widthResponse, bubbleDamping)
                springBubbleAlpha.advance(dt, bubbleProfile.widthResponse * 0.78f, 1f)
                activityBubble.translationX = springBubbleX.value
                activityBubble.scaleX = springBubbleScale.value
                activityBubble.scaleY = springBubbleScale.value
                activityBubble.alpha = springBubbleAlpha.value
                if (springBubbleX.isSettled(0.45f, 5f) &&
                    springBubbleScale.isSettled(0.004f, 0.02f) &&
                    springBubbleAlpha.isSettled(0.004f, 0.02f)) {
                    springBubbleX.snapTo(springBubbleX.target)
                    springBubbleScale.snapTo(springBubbleScale.target)
                    springBubbleAlpha.snapTo(springBubbleAlpha.target)
                    activityBubble.translationX = springBubbleX.value
                    activityBubble.scaleX = springBubbleScale.value
                    activityBubble.scaleY = springBubbleScale.value
                    activityBubble.alpha = springBubbleAlpha.value
                    bubbleSpringActive = false
                    if (activityBubbleHidePending) {
                        activityBubbleHidePending = false
                        val previousSideSlots = overlaySideSlots()
                        activityBubble.visibility = View.GONE
                        activityBubbleSlotActive = false
                        activityBubbleLastSlotWidth = 0
                        releaseCompanionSlotWithoutMainMotion(previousSideSlots)
                    }
                }
            }
            if (notificationSpringActive && ::notificationBubble.isInitialized) {
                val bubbleProfile = companionMotionProfile(notificationBubbleMotionExpanding)
                val bubbleDamping = companionDamping(bubbleProfile)
                notificationSpringX.advance(dt, bubbleProfile.widthResponse, bubbleDamping)
                notificationSpringScale.advance(dt, bubbleProfile.widthResponse, bubbleDamping)
                notificationSpringAlpha.advance(dt, bubbleProfile.widthResponse * 0.78f, 1f)
                notificationBubble.translationX = notificationSpringX.value
                notificationBubble.scaleX = notificationSpringScale.value
                notificationBubble.scaleY = notificationSpringScale.value
                notificationBubble.alpha = notificationSpringAlpha.value
                if (notificationSpringX.isSettled(0.45f, 5f) &&
                    notificationSpringScale.isSettled(0.004f, 0.02f) &&
                    notificationSpringAlpha.isSettled(0.004f, 0.02f)) {
                    notificationSpringX.snapTo(notificationSpringX.target)
                    notificationSpringScale.snapTo(notificationSpringScale.target)
                    notificationSpringAlpha.snapTo(notificationSpringAlpha.target)
                    notificationBubble.translationX = notificationSpringX.value
                    notificationBubble.scaleX = notificationSpringScale.value
                    notificationBubble.scaleY = notificationSpringScale.value
                    notificationBubble.alpha = notificationSpringAlpha.value
                    notificationSpringActive = false
                    if (notificationBubbleHidePending) {
                        notificationBubbleHidePending = false
                        val previousSideSlots = overlaySideSlots()
                        notificationBubble.visibility = View.GONE
                        notificationBubbleSlotActive = false
                        notificationBubbleLastSlotWidth = 0
                        releaseCompanionSlotWithoutMainMotion(previousSideSlots)
                    }
                }
            }
            if (sizeMotionActive) {
                val pad = touchPad()
                // Never smaller than the camera hole, even in the small bounce at the end of a collapse.
                lp.width = (springW.value + 0.5f).toInt().coerceAtLeast(miniW + 2 * pad + overlaySideSlots() - dp(4))
                lp.height = (springH.value + 0.5f).toInt().coerceAtLeast(collapsedTargetHeight() + 2 * pad - dp(4))
                (pill?.background as? GradientDrawable)?.cornerRadius = springCorner.value
            }
            if (r.isAttachedToWindow) runCatching { wm.updateViewLayout(r, lp) }
            if (sizeMotionActive) syncPanelY()
            val finishDismiss = swipeDismissPending && !swipeSpringActive
            if (finishDismiss) {
                swipeDismissPending = false
                dismissSwiped()
                lp.x = swipeBaseX
                springX.snapTo(lp.x.toFloat())
                runCatching { wm.updateViewLayout(r, lp) }
                springAlpha.snapTo(0f)
                springAlpha.retarget(1f)
                alphaSpringActive = motionEnabled()
                if (!alphaSpringActive) pill?.alpha = 1f
            }
            if (!sizeMotionActive && !swipeSpringActive && !pressSpringActive &&
                !alphaSpringActive && !bubbleSpringActive && !notificationSpringActive) {
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
        sizeMotionActive = false
        swipeSpringActive = false
        pressSpringActive = false
        alphaSpringActive = false
        bubbleSpringActive = false
        notificationSpringActive = false
        activityBubbleHidePending = false
        notificationBubbleHidePending = false
        swipeDismissPending = false
        swipeReturnPending = false
        springLast = 0L
    }

    private fun animateSize() {
        val r = root ?: return
        val pad = touchPad()
        val toW = (if (expanded) expandedTargetWidth() else compactTargetWidth()) + 2 * pad +
            overlaySideSlots(includeActivityBubble = !expanded)
        val toH = (if (expanded) targetInnerH() else collapsedTargetHeight()) + 2 * pad
        if (!motionEnabled()) {
            // Motion disabled in app settings or system settings: jump straight to the new size
            stopSpring()
            lp.width = toW
            lp.height = toH
            springW.snapTo(toW.toFloat())
            springH.snapTo(toH.toFloat())
            springCorner.snapTo(cornerTarget(toH))
            springPressX.snapTo(1f)
            springPressY.snapTo(1f)
            springAlpha.snapTo(1f)
            pill?.scaleX = 1f
            pill?.scaleY = 1f
            pill?.alpha = 1f
            (pill?.background as? GradientDrawable)?.cornerRadius = springCorner.value
            if (r.isAttachedToWindow) runCatching { wm.updateViewLayout(r, lp) }
            syncPanelY()
            return
        }
        if (!sizeMotionActive) {
            springW.snapTo(lp.width.toFloat())
            springH.snapTo(lp.height.toFloat())
            springCorner.snapTo((pill?.background as? GradientDrawable)?.cornerRadius ?: cornerTarget(lp.height))
        }
        springW.retarget(toW.toFloat())
        springH.retarget(toH.toFloat())
        springCorner.retarget(cornerTarget(toH))
        sizeMotionActive = true
        postSpringFrame()
    }

    private fun animatePress(scale: Float) {
        val surface = pill ?: return
        if (!motionEnabled()) {
            stopSpring()
            springPressX.snapTo(1f)
            springPressY.snapTo(1f)
            surface.scaleX = 1f
            surface.scaleY = 1f
            return
        }
        if (!pressSpringActive) {
            springPressX.snapTo(surface.scaleX)
            springPressY.snapTo(surface.scaleY)
        }
        springPressX.retarget(scale)
        springPressY.retarget(scale)
        pressSpringActive = true
        postSpringFrame()
    }

    private fun activePillSizingSection(): String = when {
        callActive -> "calls"
        else -> when (activityWinner()) {
            "media" -> "media"
            "navigation" -> "maps"
            "charging" -> "charging"
            "notifications", "live" -> "notifications"
            "stopwatch", null -> "tasks"
            else -> "notifications"
        }
    }

    private fun customSizePx(section: String, dimension: String, fallbackPx: Int, minPx: Int, maxPx: Int): Int {
        if (!prefs.getBoolean("customSize_$section", false)) return fallbackPx.coerceIn(minPx, maxPx)
        val fallbackDp = (fallbackPx / resources.displayMetrics.density).toInt()
        return dp(prefs.getInt("size${dimension}_$section", fallbackDp)).coerceIn(minPx, maxPx)
    }

    private fun collapsedTargetHeight(): Int {
        val screenMax = (resources.displayMetrics.heightPixels - dp(48)).coerceAtLeast(dp(40))
        return customSizePx(activePillSizingSection(), "CollapsedHeight", miniH, dp(28), screenMax)
    }

    private fun compactTargetWidth(): Int {
        val screenMax = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(96))
        val chargeCompact = !expanded && !callActive && activityWinner() == "charging" && charging &&
            showCharging() && !qsActive() && !widgetActive()
        val minWidth = minOf(dp(104), screenMax)
        val fallback = when {
            shouldShowCollapsedNotificationPreview() -> minOf(dp(260), screenMax)
            chargeCompact -> dp(prefs.getInt("chargeMiniW", 116)).coerceIn(minWidth, minOf(screenMax, dp(280)))
            mapsShown() || callCompact() -> minOf(dp(240), screenMax)
            !expanded && controller != null && showMusic() &&
                mediaCompactActivityActive() ->
                dp(prefs.getInt("mediaMiniW", 280)).coerceIn(minWidth, minOf(screenMax, dp(420)))
            else -> miniW
        }
        val configured = customSizePx(activePillSizingSection(), "CollapsedWidth", fallback, minWidth, screenMax)
        val sideBudget = overlaySideSlots(includeActivityBubble = true, includeNotificationBubble = true)
        val availableForMain = (resources.displayMetrics.widthPixels - 2 * touchPad() - sideBudget)
            .coerceAtLeast(minWidth)
        return configured.coerceAtMost(availableForMain)
    }

    /** Resolve the closed width without depending on the current expanded state. */
    private fun collapsedReferenceWidth(section: String, screenMax: Int): Int {
        val minWidth = minOf(dp(104), screenMax)
        val defaultWidth = when (section) {
            "media" -> dp(prefs.getInt("mediaMiniW", 280)).coerceIn(minWidth, minOf(screenMax, dp(420)))
            "charging" -> dp(prefs.getInt("chargeMiniW", 116)).coerceIn(minWidth, minOf(screenMax, dp(280)))
            "maps", "calls" -> minOf(dp(240), screenMax)
            "notifications" -> if (notificationQueue.isNotEmpty()) minOf(dp(260), screenMax) else miniW
            else -> miniW
        }.coerceIn(minWidth, screenMax)
        if (!prefs.getBoolean("customSize_$section", false)) return defaultWidth
        val fallbackDp = (defaultWidth / resources.displayMetrics.density).toInt()
        return dp(prefs.getInt("sizeCollapsedWidth_$section", fallbackDp)).coerceIn(minWidth, screenMax)
    }

    /** Expanded media gets a real wide-card width; keep companion/badge space outside its surface. */
    private fun expandedTargetWidth(): Int {
        val screenMax = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(200))
        val section = activePillSizingSection()
        val mediaActive = section == "media" && controller != null && showMusic()
        val configured = if (mediaActive) dp(prefs.getInt("mediaExpandedW", 380)) else expW
        val collapsedWidth = collapsedReferenceWidth(section, screenMax)
        val minUseful = maxOf(dp(180), collapsedWidth + dp(16)).coerceAtMost(screenMax)
        val fallback = configured.coerceAtMost(screenMax).coerceAtLeast(minOf(minUseful, screenMax))
        return customSizePx(section, "ExpandedWidth", fallback, minUseful, screenMax)
    }

    /** The row must stop reserving the collapsed companion-bubble slot after the main pill opens. */
    private fun syncOverlayRowInsets(updateWindow: Boolean = true) {
        val row = pill ?: return
        val params = row.layoutParams as? FrameLayout.LayoutParams ?: return
        val pad = touchPad()
        val corner = prefs.getString("notificationBadgeCorner", "top_end")
        val sideBadgeSpace = if (corner == "right_side") dp(56) else 0
        val bubbleSpace = if (!expanded) activityBubbleSlot() else 0
        val notificationSpace = if (!expanded) notificationBubbleSlot() else 0
        val desired = intArrayOf(pad + notificationSpace, pad, pad + sideBadgeSpace + bubbleSpace, pad)
        if (params.leftMargin != desired[0] || params.topMargin != desired[1] ||
            params.rightMargin != desired[2] || params.bottomMargin != desired[3]) {
            params.setMargins(desired[0], desired[1], desired[2], desired[3])
            row.layoutParams = params
            row.requestLayout()
        }
        if (::notificationBadgeTarget.isInitialized) {
            val badgeParams = notificationBadgeTarget.layoutParams as? FrameLayout.LayoutParams
            if (badgeParams != null) {
                val badgeEndSpace = when (corner) {
                    "right_side" -> pad
                    else -> pad + bubbleSpace
                }
                val badgeStartSpace = if (corner == "right_side") sideBadgeSpace else 0
                val badgeLeftInset = if (corner == "top_start" || corner == "bottom_start") pad + notificationSpace else badgeStartSpace
                val expectedGravity = notificationBadgeGravity(corner ?: "top_end")
                val badgeGravity = if (corner == "right_side") Gravity.CENTER_VERTICAL or Gravity.RIGHT else expectedGravity
                if (badgeParams.gravity != badgeGravity || badgeParams.rightMargin != badgeEndSpace ||
                    badgeParams.leftMargin != badgeLeftInset) {
                    badgeParams.gravity = badgeGravity
                    badgeParams.leftMargin = badgeLeftInset
                    badgeParams.rightMargin = badgeEndSpace
                    notificationBadgeTarget.layoutParams = badgeParams
                }
            }
        }
        if (::lp.isInitialized) {
            val right = overlayRightSlots(includeActivityBubble = !expanded)
            val left = overlayLeftSlots(includeNotificationBubble = !expanded)
            lp.x = resolvedOffX + (right - left) / 2
            if (updateWindow && root?.isAttachedToWindow == true) runCatching { wm.updateViewLayout(root, lp) }
        }
    }

    /** Remove transparent companion space while preserving the main pill's bounds and spring velocity. */
    private fun releaseCompanionSlotWithoutMainMotion(previousSideSlots: Int) {
        syncOverlayRowInsets(updateWindow = false)
        val currentSideSlots = overlaySideSlots()
        val windowDelta = currentSideSlots - previousSideSlots
        if (windowDelta != 0 && ::lp.isInitialized) {
            val minimum = miniW + 2 * touchPad() + currentSideSlots - dp(4)
            if (sizeMotionActive) {
                springW.offsetBy(windowDelta.toFloat())
                lp.width = (springW.value + 0.5f).toInt().coerceAtLeast(minimum)
            } else {
                lp.width = (lp.width + windowDelta).coerceAtLeast(minimum)
                springW.snapTo(lp.width.toFloat())
            }
        }
        root?.takeIf { it.isAttachedToWindow }?.let { runCatching { wm.updateViewLayout(it, lp) } }
        if (sizeMotionActive) syncPanelY()
    }

    /** Show a compact notification only when it will not displace Maps, media, charging, calls, or live activities. */
    private fun shouldShowCollapsedNotificationPreview(): Boolean =
        !expanded && !callActive && stopwatchStart <= 0L && notificationQueue.isNotEmpty() &&
            !liveActivityIsShowing() && !(controller != null && showMusic()) && !mapsShown() &&
            !(charging && showCharging()) && !qsActive() && !widgetActive() &&
            !(lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true))

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
    private fun snapshotOld(collapse: Boolean = false) {
        if (pillAnimationStyle() == "settling_dock" && !collapse) {
            clearSnapshot()
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 31) return
        if (!motionEnabled()) {
            // If motion was disabled while a prior snapshot was fading out, remove it immediately.
            clearSnapshot()
            return
        }
        val p = pill ?: run { clearSnapshot(); return }
        val r = root as? FrameLayout ?: return
        val now = SystemClock.uptimeMillis()
        if ((!collapse && now - lastSnapAt < 80) || p.width <= 0 || p.height <= 0 || !r.isAttachedToWindow) return
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
            val anchorCollapseToTop = collapse && prefs.getBoolean("expandFromStatusBar", true)
            val iv = ImageView(uiCtx).apply {
                setImageBitmap(bmp)
                layoutParams = FrameLayout.LayoutParams(
                    p.width,
                    p.height,
                    if (anchorCollapseToTop) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
                ).apply {
                    if (anchorCollapseToTop) topMargin = touchPad()
                }
                pivotX = p.width / 2f
                pivotY = if (anchorCollapseToTop) 0f else p.height / 2f
            }
            r.addView(iv)
            snapView = iv
            snapBitmap = bmp
            val transitionDuration = if (collapse) when (prefs.getInt("springPreset", 1)) {
                0 -> 260L
                2 -> 440L
                else -> 340L
            } else 140L
            val finalScale = if (collapse) 0.92f else 0.88f
            iv.animate()
                .alpha(0f)
                .scaleX(finalScale)
                .scaleY(if (anchorCollapseToTop) 1f else finalScale)
                .setDuration(transitionDuration)
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.1f))
                .withEndAction { clearSnapshot(iv) }
                .start()
            snapBlurAnimator = ValueAnimator.ofFloat(0.1f, 8f).apply {
                duration = transitionDuration
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
        if (!prefs.getBoolean("pinnedOn", true)) {
            diag("pinned apps gesture ignored: feature is disabled")
            return
        }
        if (panelOpen) closePanel() else openPanel()
    }

    // Theme-aware stat glyphs: one consistent set of metrics, drawn with each pill's icon weight and accent.
    private class ChargeMetricIconView(
        ctx: Context,
        private val kind: Int,
        private val palette: () -> PillStylePalette,
        private val tint: () -> Int
    ) : View(ctx) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val path = android.graphics.Path()

        override fun onDraw(canvas: android.graphics.Canvas) {
            val size = minOf(width, height).toFloat()
            if (size <= 0f) return
            val style = palette()
            val cx = width / 2f
            val cy = height / 2f
            val u = size * 0.42f
            paint.color = tint()
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = size * when (style.id) {
                "oneui" -> 0.12f
                "iphone" -> 0.085f
                "nothing" -> 0.105f
                else -> 0.095f
            }
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            paint.pathEffect = if (style.id == "nothing") {
                android.graphics.DashPathEffect(floatArrayOf(size * 0.12f, size * 0.07f), 0f)
            } else null
            path.reset()
            when (kind) {
                0 -> { // ETA: clock face and hands
                    canvas.drawCircle(cx, cy, u * 0.78f, paint)
                    canvas.drawLine(cx, cy, cx, cy - u * 0.43f, paint)
                    canvas.drawLine(cx, cy, cx + u * 0.35f, cy + u * 0.18f, paint)
                }
                1 -> { // Voltage: bolt
                    path.moveTo(cx + u * 0.18f, cy - u)
                    path.lineTo(cx - u * 0.46f, cy + u * 0.08f)
                    path.lineTo(cx - u * 0.04f, cy + u * 0.08f)
                    path.lineTo(cx - u * 0.18f, cy + u)
                    path.lineTo(cx + u * 0.48f, cy - u * 0.18f)
                    path.lineTo(cx + u * 0.08f, cy - u * 0.18f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                2 -> { // Current: directional flow
                    canvas.drawLine(cx - u, cy, cx + u * 0.62f, cy, paint)
                    canvas.drawLine(cx + u * 0.18f, cy - u * 0.44f, cx + u * 0.62f, cy, paint)
                    canvas.drawLine(cx + u * 0.18f, cy + u * 0.44f, cx + u * 0.62f, cy, paint)
                    canvas.drawCircle(cx - u * 0.55f, cy - u * 0.55f, size * 0.055f, paint)
                    canvas.drawCircle(cx - u * 0.55f, cy + u * 0.55f, size * 0.055f, paint)
                }
                3 -> { // Battery percentage
                    val rect = android.graphics.RectF(cx - u * 0.75f, cy - u * 0.62f, cx + u * 0.65f, cy + u * 0.62f)
                    canvas.drawRoundRect(rect, size * 0.12f, size * 0.12f, paint)
                    canvas.drawRoundRect(cx + u * 0.68f, cy - u * 0.25f, cx + u, cy + u * 0.25f, size * 0.08f, size * 0.08f, paint)
                    canvas.drawLine(cx - u * 0.48f, cy, cx + u * 0.18f, cy, paint)
                }
                4 -> { // Temperature: stem and bulb
                    val bulbY = cy + u * 0.48f
                    canvas.drawLine(cx, cy - u * 0.92f, cx, bulbY, paint)
                    canvas.drawCircle(cx, bulbY, size * 0.16f, paint)
                    canvas.drawLine(cx + u * 0.34f, cy - u * 0.55f, cx + u * 0.58f, cy - u * 0.55f, paint)
                    canvas.drawLine(cx + u * 0.34f, cy - u * 0.12f, cx + u * 0.52f, cy - u * 0.12f, paint)
                }
                else -> { // Watts: compact power spark
                    path.moveTo(cx + u * 0.18f, cy - u)
                    path.lineTo(cx - u * 0.48f, cy + u * 0.08f)
                    path.lineTo(cx - u * 0.02f, cy + u * 0.08f)
                    path.lineTo(cx - u * 0.18f, cy + u)
                    path.lineTo(cx + u * 0.5f, cy - u * 0.18f)
                    path.lineTo(cx + u * 0.08f, cy - u * 0.18f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
            paint.pathEffect = null
        }
    }

    // Charging indicator. The reference ring keeps its bolt still and lets the irregular outline breathe.
    private inner class MediaWaveformView(ctx: Context) : View(ctx) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = pillStyle().primary
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        private val levels = floatArrayOf(.28f, .58f, .88f, .48f, .72f, .36f, .66f)
        private var frame = 0
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (!isShown) return
            val gap = width / (levels.size * 2f)
            val barW = gap * .72f
            val mid = height / 2f
            paint.color = pillStyle().primary
            levels.forEachIndexed { i, level ->
                val animated = (level * (.52f + .48f * kotlin.math.abs(kotlin.math.sin((frame * .19f) + i * .8f)))).coerceAtLeast(.12f)
                val barH = height * animated
                val x = gap * (i * 2 + 1)
                canvas.drawRoundRect(x, mid - barH / 2f, x + barW, mid + barH / 2f, barW, barW, paint)
            }
            frame++
            postInvalidateDelayed(if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) 75L else 220L)
        }
        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == View.VISIBLE) invalidate()
        }
    }

    private class ChargeRingView(
        ctx: Context,
        private val selectedStyle: () -> Int,
        private val selectedShape: () -> Int = { 0 },
        private val percent: () -> Int = { 50 },
        private val ringTint: () -> Int = { 0xFFFFFFFF.toInt() },
        private val waveSpacingDp: () -> Int = { 12 },
        private val ringSpeed: () -> Int = { 5 },
        private val ringThickness: () -> Int = { 3 },
        private val waveHeight: () -> Int = { 9 },
        private val waveSoftness: () -> Int = { 55 },
        private val innerWaveSpacing: () -> Int = { 28 },
        private val animationOn: () -> Boolean = { true },
        private val remainderThickness: () -> Int = { 2 },
        private val remainderTint: () -> Int = { 0xFF777777.toInt() },
        private val waveCycleDurationMs: () -> Long = { 6_980L },
        private val referenceRingSizeDp: () -> Int = { 48 },
        private val sharedWavePhase: (() -> Float)? = null
    ) : View(ctx) {
        private val circularWaveRenderer = CircularWaveChargeRenderer(ctx)
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
        private val remainingTrack = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            color = 0xFFFFFFFF.toInt()
        }
        private val body = android.graphics.RectF()
        private val inner = android.graphics.RectF()
        private val boltPath = android.graphics.Path()
        private var phase = 0f
        private var completionPulse = 0f
        private var lastObservedPercent: Int? = null
        private val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                phase = it.animatedFraction
                invalidate()
            }
        }
        private val completionAnimator = android.animation.ValueAnimator.ofFloat(0f, Math.PI.toFloat()).apply {
            duration = 640L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                completionPulse = kotlin.math.sin(it.animatedValue as Float).toFloat()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completionPulse = 0f
                    invalidate()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    completionPulse = 0f
                }
            })
        }
        private fun updateRunning() {
            val shape = selectedShape().coerceIn(0, 11)
            val progressRing = shape == 9 || shape == 11
            // Circular Wave keeps its charged sweep moving even at 100%, where the
            // complete active contour becomes a softly rotating full ring.
            val needsContinuousMotion = animationOn() &&
                (shape == 11 || percent().coerceIn(0, 100) < 100 || !progressRing)
            if (needsContinuousMotion && isAttachedToWindow && isShown && windowVisibility == View.VISIBLE) {
                val speed = ringSpeed().coerceIn(1, 20)
                val duration = if (shape == 11) {
                    waveCycleDurationMs().coerceIn(580L, 24_000L)
                } else {
                    (4000L - (speed - 1) * 180L).coerceIn(580L, 4000L)
                }
                if (!anim.isStarted) {
                    anim.duration = duration
                    anim.start()
                } else if (anim.duration != duration) {
                    val currentFraction = anim.animatedFraction
                    anim.duration = duration
                    anim.currentPlayTime = (currentFraction * duration).toLong()
                }
            } else {
                anim.cancel()
                if (!animationOn()) {
                    completionAnimator.cancel()
                    completionPulse = 0f
                    phase = 0f
                }
            }
        }
        fun refreshAnimationState() { updateRunning(); invalidate() }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRunning() }
        override fun onDetachedFromWindow() {
            anim.cancel()
            completionAnimator.cancel()
            completionPulse = 0f
            super.onDetachedFromWindow()
        }
        override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateRunning() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val unit = minOf(w, h)
            if (unit <= 0f) return
            val currentPercent = percent().coerceIn(0, 100)
            observeChargeCompletion(currentPercent)
            updateRunning()
            val bw = unit * 0.62f
            val bh = unit * 0.76f
            val left = (w - bw) / 2f
            val top = (h - bh) / 2f
            val stroke = maxOf(1.5f, unit * 0.055f)
            body.set(left, top, left + bw, top + bh)
            outline.strokeWidth = stroke
            val inset = stroke * 1.8f
            inner.set(left + inset, top + inset, left + bw - inset, top + bh - inset)
            val shape = selectedShape().coerceIn(0, 11)
            val animated = animationOn()
            val style = selectedStyle().coerceIn(0, 2)
            val chargeFraction = percent().coerceIn(0, 100) / 100f
            val wave = if (animated) (0.5 + 0.5 * kotlin.math.sin(phase * Math.PI * 6.0)).toFloat() else chargeFraction
            if (shape >= 8) {
                when (shape) {
                    9 -> drawCraftedChargeRing(canvas, w, h, unit)
                    10 -> drawHorologistChargeRing(canvas, w, h, unit)
                    11 -> drawWaveChargeRing(canvas, w, h, unit)
                    else -> drawIrregularRing(canvas, w, h, unit, style, wave, false)
                }
            } else if (shape == 0) {
                canvas.drawRoundRect(body, unit * 0.13f, unit * 0.13f, outline)
                val terminal = android.graphics.RectF(left + bw * 0.32f, top - unit * 0.10f, left + bw * 0.68f, top + unit * 0.015f)
                canvas.drawRoundRect(terminal, unit * 0.04f, unit * 0.04f, fill)
            } else {
                val cx = w / 2f
                val cy = h / 2f
                val p = android.graphics.Path()
                when (shape) {
                    1 -> { // Lightning bolt
                        p.moveTo(cx + unit*.06f, cy-unit*.38f); p.lineTo(cx-unit*.20f, cy+unit*.02f)
                        p.lineTo(cx-unit*.02f, cy+unit*.02f); p.lineTo(cx-unit*.07f, cy+unit*.36f)
                        p.lineTo(cx+unit*.20f, cy-unit*.08f); p.lineTo(cx+unit*.03f, cy-unit*.08f); p.close()
                    }
                    2 -> { // Power plug
                        p.moveTo(cx-unit*.16f,cy-unit*.22f); p.lineTo(cx-unit*.16f,cy-unit*.02f)
                        p.quadTo(cx,cy+unit*.15f,cx+unit*.16f,cy-unit*.02f); p.lineTo(cx+unit*.16f,cy-unit*.22f)
                        p.moveTo(cx,cy+unit*.13f); p.lineTo(cx,cy+unit*.31f)
                        canvas.drawLine(cx-unit*.24f,cy-unit*.24f,cx-unit*.24f,cy-unit*.04f,outline)
                        canvas.drawLine(cx+unit*.24f,cy-unit*.24f,cx+unit*.24f,cy-unit*.04f,outline)
                    }
                    3 -> { // Leaf
                        p.moveTo(cx-unit*.25f,cy+unit*.25f); p.quadTo(cx-unit*.20f,cy-unit*.28f,cx+unit*.25f,cy-unit*.25f)
                        p.quadTo(cx+unit*.25f,cy+unit*.20f,cx-unit*.25f,cy+unit*.25f); p.close()
                        p.moveTo(cx-unit*.18f,cy+unit*.18f); p.lineTo(cx+unit*.16f,cy-unit*.15f)
                    }
                    4 -> { // Spark
                        p.moveTo(cx,cy-unit*.36f); p.lineTo(cx+unit*.09f,cy-unit*.08f)
                        p.lineTo(cx+unit*.34f,cy); p.lineTo(cx+unit*.09f,cy+unit*.08f)
                        p.lineTo(cx,cy+unit*.36f); p.lineTo(cx-unit*.09f,cy+unit*.08f)
                        p.lineTo(cx-unit*.34f,cy); p.lineTo(cx-unit*.09f,cy-unit*.08f); p.close()
                    }
                    5 -> { // Pixel battery: squared shoulders and a distinct top terminal
                        p.moveTo(cx-unit*.20f,cy-unit*.27f); p.lineTo(cx+unit*.20f,cy-unit*.27f)
                        p.lineTo(cx+unit*.20f,cy-unit*.19f); p.lineTo(cx+unit*.28f,cy-unit*.19f)
                        p.lineTo(cx+unit*.28f,cy+unit*.24f); p.lineTo(cx+unit*.20f,cy+unit*.24f)
                        p.lineTo(cx+unit*.20f,cy+unit*.31f); p.lineTo(cx-unit*.20f,cy+unit*.31f)
                        p.lineTo(cx-unit*.20f,cy+unit*.24f); p.lineTo(cx-unit*.28f,cy+unit*.24f)
                        p.lineTo(cx-unit*.28f,cy-unit*.19f); p.lineTo(cx-unit*.20f,cy-unit*.19f); p.close()
                    }
                    6, 7 -> Unit // Draw the standalone glyph and ring after the fill animation.
                }
                if (shape == 2 || shape == 3 || shape == 5) canvas.drawPath(p, outline)
                else if (shape != 6 && shape != 7) canvas.drawPath(p, fill)
            }
            if (shape == 6) {
                val dotRadius = unit * 0.055f
                val litCount = if (animated) ((phase * 9f).toInt() % 9) + 1
                    else ((chargeFraction * 9f) + 0.5f).toInt().coerceIn(0, 9)
                for (row in 0..2) for (col in 0..2) {
                    fill.alpha = if (row * 3 + col < litCount) 255 else 64
                    canvas.drawCircle(w / 2f + (col - 1) * unit * 0.19f,
                        h / 2f + (row - 1) * unit * 0.19f, dotRadius, fill)
                }
                fill.alpha = 255
            } else if (shape == 7) {
                val radius = unit * 0.30f
                outline.strokeWidth = maxOf(2f, unit * 0.075f)
                canvas.drawCircle(w / 2f, h / 2f, radius, outline)
                fill.style = android.graphics.Paint.Style.STROKE
                fill.strokeWidth = outline.strokeWidth
                fill.strokeCap = android.graphics.Paint.Cap.ROUND
                canvas.drawArc(w/2f-radius, h/2f-radius, w/2f+radius, h/2f+radius,
                    -90f, 360f * if (animated) phase else chargeFraction, false, fill)
                fill.style = android.graphics.Paint.Style.FILL
            } else if (shape in 0..5) {
                canvas.save()
                canvas.clipRect(inner)
                when (style) {
                0 -> {
                    // Smooth liquid fill.
                    val level = if (animated) 0.28f + 0.62f * wave else chargeFraction
                    val fillTop = inner.bottom - inner.height() * level
                    canvas.drawRoundRect(inner.left, fillTop, inner.right, inner.bottom, unit * 0.07f, unit * 0.07f, fill)
                }
                1 -> {
                    // Three clear charge segments light up in sequence.
                    val gap = maxOf(1f, unit * 0.035f)
                    val segmentH = (inner.height() - gap * 2f) / 3f
                    val litSegments = if (animated) ((phase * 9f).toInt() % 4)
                        else kotlin.math.ceil(chargeFraction * 3f).toInt()
                    for (i in 0..2) {
                        val segment = android.graphics.RectF(
                            inner.left, inner.bottom - (i + 1) * segmentH - i * gap,
                            inner.right, inner.bottom - i * (segmentH + gap)
                        )
                        if (i < litSegments) {
                            canvas.drawRoundRect(segment, unit * 0.04f, unit * 0.04f, fill)
                        }
                    }
                }
                else -> {
                    // Gentle pulse mode: a steady base and a breathing bolt.
                    if (animated) {
                        fill.alpha = (95 + 100 * wave).toInt().coerceIn(0, 255)
                        canvas.drawRoundRect(inner, unit * 0.07f, unit * 0.07f, fill)
                    } else {
                        val fillTop = inner.bottom - inner.height() * chargeFraction
                        canvas.drawRoundRect(inner.left, fillTop, inner.right, inner.bottom, unit * 0.07f, unit * 0.07f, fill)
                    }
                    fill.alpha = 255
                }
                }
                canvas.restore()
            }
            if (shape != 6 && shape != 11) {
                val u = unit * 0.22f
                val ringShape = shape >= 8
                // Keep the bolt visually anchored while the ring's progress highlight moves.
                val boltScale = 1f
                if (ringShape) {
                    bolt.color = ringTint()
                    bolt.alpha = 255
                }
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
                if (ringShape) {
                    bolt.color = 0xFFFFFFFF.toInt()
                    bolt.alpha = 255
                }
            }
        }

        private fun observeChargeCompletion(currentPercent: Int) {
            val previousPercent = lastObservedPercent
            lastObservedPercent = currentPercent
            if (currentPercent < 100) {
                if (previousPercent == 100) {
                    completionAnimator.cancel()
                    completionPulse = 0f
                }
                return
            }
            if (previousPercent != null && previousPercent < 100 && animationOn() &&
                isAttachedToWindow && isShown && windowVisibility == View.VISIBLE) {
                completionAnimator.cancel()
                completionAnimator.start()
            }
        }

        private fun lobeCountFor(unit: Float): Int {
            val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
            val radiusDp = unit / density * 0.305f
            val circumferenceDp = (2f * Math.PI.toFloat()) * radiusDp
            val spacing = waveSpacingDp().coerceIn(3, 24).toFloat()
            return (circumferenceDp / spacing + 0.5f).toInt().coerceIn(3, 32)
        }

        private fun drawIrregularRing(canvas: android.graphics.Canvas, w: Float, h: Float, unit: Float, style: Int, wave: Float, crafted: Boolean) {
            val progress = percent().coerceIn(0, 100) / 100f
            val cx = w / 2f
            val cy = h / 2f
            val radius = unit * if (crafted) 0.305f else 0.30f
            val density = lobeCountFor(unit)
            remainingTrack.color = ringTint()
            remainingTrack.alpha = if (crafted) 62 else 48
            val thickness = ringThickness().coerceIn(2, 10) * resources.displayMetrics.density
            remainingTrack.strokeWidth = thickness
            val trackRadius = radius - remainingTrack.strokeWidth * 0.7f
            canvas.drawCircle(cx, cy, trackRadius, remainingTrack)
            if (progress <= 0f) return
            // The dim track is the remaining charge arc; the brighter irregular stroke is progress.
            // Keep the gap centered at the top so it reads as an intentional progress indicator.
            val gapCenter = -90f
            val gapSweep = 360f * (1f - progress)
            val startDegrees = gapCenter + gapSweep / 2f
            val start = Math.toRadians(startDegrees.toDouble())
            val sweep = 360f * progress
            val segments = maxOf(32, (220f * progress).toInt())
            val movingPhase = phase * (Math.PI * 2.0)
            val path = android.graphics.Path()
            var drawing = false
            for (i in 0..segments) {
                val f = i.toFloat() / segments
                if (style == 1 && (i % 10 == 8 || i % 10 == 9)) {
                    drawing = false
                    continue
                }
                val theta = start + Math.toRadians((sweep * f).toDouble())
                val angle = theta.toFloat()
                val amplitude = if (style == 0) 0.025f else if (crafted) 0.012f else 0.018f
                val wobble = amplitude * kotlin.math.sin(angle * density + movingPhase).toFloat() +
                    amplitude * 0.38f * kotlin.math.sin(angle * density * 1.85f - movingPhase * 0.72).toFloat()
                val r = radius * (1f + wobble)
                val x = cx + kotlin.math.cos(theta).toFloat() * r
                val y = cy + kotlin.math.sin(theta).toFloat() * r
                if (!drawing) path.moveTo(x, y) else path.lineTo(x, y)
                drawing = true
            }
            outline.color = ringTint()
            val baseStroke = thickness
            outline.strokeWidth = if (style == 2) baseStroke * (0.94f + 0.10f * wave) else baseStroke
            outline.alpha = if (style == 2) (185 + 70 * wave).toInt().coerceIn(0, 255) else 255
            outline.strokeCap = android.graphics.Paint.Cap.ROUND
            outline.strokeJoin = android.graphics.Paint.Join.ROUND
            canvas.drawPath(path, outline)
            outline.alpha = 255
            outline.color = 0xFFEAF7F0.toInt()
        }

        /**
         * The reference's scallops are a designed contour, not noise that changes each frame.
         * Keep the contour fixed while a short highlight travels along the charged portion.
         */
        private fun drawCraftedChargeRing(canvas: android.graphics.Canvas, w: Float, h: Float, unit: Float) {
            val progress = percent().coerceIn(0, 100) / 100f
            val cx = w / 2f
            val cy = h / 2f
            val radius = unit * 0.305f
            val thickness = ringThickness().coerceIn(2, 10) * resources.displayMetrics.density
            val scallops = lobeCountFor(unit)
            val pointsPerScallop = 8
            val pointCount = scallops * pointsPerScallop
            val profile = floatArrayOf(0f, 0.28f, 0.70f, 1f, 0.68f, 0.22f, -0.20f, -0.32f)
            // A fixed, repeating hand-tuned cadence makes neighboring lobes feel related
            // without making the ring look like a mathematically perfect gear.
            val lobeWeights = floatArrayOf(0.90f, 1.10f, 0.82f, 1.02f, 1.16f, 0.88f, 1.06f, 0.94f,
                1.12f, 0.84f, 1.00f, 1.08f, 0.86f, 1.14f, 0.96f, 1.04f)
            val points = Array(pointCount) { index ->
                val lobe = index / pointsPerScallop
                val local = index % pointsPerScallop
                val weight = lobeWeights[lobe % lobeWeights.size]
                val radial = radius * (1f + 0.060f * weight * profile[local])
                val angle = Math.toRadians(-90.0 + index * (360.0 / pointCount))
                android.graphics.PointF(
                    cx + kotlin.math.cos(angle).toFloat() * radial,
                    cy + kotlin.math.sin(angle).toFloat() * radial
                )
            }

            val contour = android.graphics.Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in points.indices) {
                    val p0 = points[(i - 1 + pointCount) % pointCount]
                    val p1 = points[i]
                    val p2 = points[(i + 1) % pointCount]
                    val p3 = points[(i + 2) % pointCount]
                    // Catmull-Rom converted to cubic Bezier keeps the scallops smooth and joins seamless.
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6f,
                        p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f,
                        p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y
                    )
                }
                close()
            }
            val measure = android.graphics.PathMeasure(contour, true)
            val length = measure.length
            if (length <= 0f) return

            // The remaining battery is visible as a subdued, equally thick track underneath.
            remainingTrack.color = ringTint()
            remainingTrack.alpha = 108
            remainingTrack.strokeWidth = thickness
            remainingTrack.strokeCap = android.graphics.Paint.Cap.ROUND
            remainingTrack.strokeJoin = android.graphics.Paint.Join.ROUND
            canvas.drawPath(contour, remainingTrack)

            outline.color = ringTint()
            outline.alpha = 255
            outline.strokeWidth = thickness * (1f + 0.10f * completionPulse)
            outline.strokeCap = android.graphics.Paint.Cap.ROUND
            outline.strokeJoin = android.graphics.Paint.Join.ROUND
            if (progress > 0f) {
                val charged = android.graphics.Path()
                val chargedLength = length * progress
                if (progress >= 0.999f) charged.set(contour)
                else measure.getSegment(0f, chargedLength, charged, true)
                canvas.drawPath(charged, outline)

                // A short traveling glint communicates active charging without warping the icon.
                if (animationOn() && progress < 0.999f) {
                    val glintLength = minOf(chargedLength * 0.075f, unit * 0.12f).coerceAtLeast(thickness * 1.4f)
                    val glintStart = phase * (chargedLength - glintLength).coerceAtLeast(0f)
                    val glint = android.graphics.Path()
                    if (glintStart + glintLength <= chargedLength) {
                        measure.getSegment(glintStart, glintStart + glintLength, glint, true)
                        outline.alpha = 150
                        outline.strokeWidth = thickness * 1.22f
                        canvas.drawPath(glint, outline)
                    }
                }
            }
            outline.alpha = 255
            outline.color = 0xFFEAF7F0.toInt()
        }

        /** Horologist-inspired rotating wavy progress: fixed lobes, rotating charged sweep. */
        private fun drawHorologistChargeRing(canvas: android.graphics.Canvas, w: Float, h: Float, unit: Float) {
            drawCircularWave(canvas, w, h, unit, lobes = lobeCountFor(unit).coerceAtLeast(4),
                rotation = if (animationOn()) phase * 360f else 0f, waveAmount = 0.055f)
        }

        /** Circular Wave charging ring: fixed wave shape, with motion reserved for progress. */
        private fun drawWaveChargeRing(canvas: android.graphics.Canvas, w: Float, h: Float, unit: Float) {
            circularWaveRenderer.draw(
                canvas = canvas,
                centerX = w / 2f,
                centerY = h / 2f,
                unitPx = unit,
                percent = percent(),
                tint = ringTint(),
                waveSpacingDp = waveSpacingDp(),
                thicknessDp = ringThickness(),
                remainderThicknessDp = remainderThickness(),
                remainderColor = remainderTint(),
                waveHeightPercent = waveHeight(),
                innerWaveSpacingPercent = innerWaveSpacing(),
                edgeSoftnessPercent = waveSoftness(),
                phase = sharedWavePhase?.invoke()?.coerceIn(0f, 1f) ?: phase,
                motionEnabled = animationOn(),
                referenceRingSizeDp = referenceRingSizeDp()
            )
        }

        private fun drawCircularWave(
            canvas: android.graphics.Canvas,
            w: Float,
            h: Float,
            unit: Float,
            lobes: Int,
            rotation: Float,
            waveAmount: Float,
            softness: Float = 0f
        ) {
            val progress = percent().coerceIn(0, 100) / 100f
            val cx = w / 2f
            val cy = h / 2f
            val radius = unit * 0.305f
            val thickness = ringThickness().coerceIn(2, 10) * resources.displayMetrics.density
            // The wave geometry stays still. This prevents the icon from looking like it
            // is wobbling; only the short highlight moves along the charged part.
            val wavePhase = 0.0
            val count = 240
            val contour = android.graphics.Path()
            for (i in 0..count) {
                val a = i.toFloat() / count * (Math.PI * 2.0)
                val baseWave = kotlin.math.sin(lobes * a + wavePhase).toFloat()
                // Low softness slightly tightens the crests; high softness returns to a
                // pure sine contour, which has the gentlest and most rounded transitions.
                val edgeExponent = 0.82f + 0.18f * softness
                val shapedWave = (if (baseWave < 0f) -1f else 1f) *
                    Math.pow(kotlin.math.abs(baseWave).toDouble(), edgeExponent.toDouble()).toFloat()
                val r = radius * (1f + waveAmount * shapedWave)
                val angle = a + Math.toRadians(rotation.toDouble())
                val x = cx + kotlin.math.cos(angle).toFloat() * r
                val y = cy + kotlin.math.sin(angle).toFloat() * r
                if (i == 0) contour.moveTo(x, y) else contour.lineTo(x, y)
            }
            contour.close()
            val measure = android.graphics.PathMeasure(contour, true)
            val length = measure.length
            if (length <= 0f) return
            remainingTrack.color = ringTint()
            remainingTrack.alpha = 108
            remainingTrack.strokeWidth = thickness
            remainingTrack.strokeCap = android.graphics.Paint.Cap.ROUND
            remainingTrack.strokeJoin = android.graphics.Paint.Join.ROUND
            canvas.drawPath(contour, remainingTrack)
            if (progress <= 0f) return
            outline.color = ringTint()
            outline.alpha = 255
            val isFull = progress >= 0.999f
            outline.strokeWidth = thickness * (if (isFull) 1f + 0.10f * completionPulse else 1f)
            outline.strokeCap = android.graphics.Paint.Cap.ROUND
            outline.strokeJoin = android.graphics.Paint.Join.ROUND
            val charged = android.graphics.Path()
            if (progress >= 0.999f) charged.set(contour)
            else measure.getSegment(0f, length * progress, charged, true)
            canvas.drawPath(charged, outline)
            if (animationOn() && !isFull) {
                val chargedLength = length * progress
                val glintLength = minOf(chargedLength, maxOf(thickness * 1.35f, unit * 0.10f))
                val glintTravel = (chargedLength - glintLength).coerceAtLeast(0f)
                val glintStart = phase * glintTravel
                val glint = android.graphics.Path()
                if (glintLength > 0f) {
                    measure.getSegment(glintStart, glintStart + glintLength, glint, true)
                    outline.alpha = 165
                    outline.strokeWidth = thickness * 1.18f
                    canvas.drawPath(glint, outline)
                }
            }
            outline.alpha = 255
            outline.color = 0xFFEAF7F0.toInt()
        }
    }

    // Notification text is line-limited and ellipsized; only horizontal motion is intercepted here.
    private class NotificationBodyPanel(ctx: Context) : FrameLayout(ctx) {
        var horizontalGestureHandler: ((MotionEvent) -> Boolean)? = null

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            // Let the child keep taps while a clearly horizontal gesture dismisses the notification.
            if (horizontalGestureHandler?.invoke(event) == true) return true
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Once intercepted, keep tracking through ACTION_UP to finish the swipe.
            if (horizontalGestureHandler?.invoke(event) == true) return true
            return super.onTouchEvent(event)
        }
    }

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

    private fun pinnedRows() = prefs.getInt("pinnedRows", 2).coerceIn(1, 4)

    private fun panelCols(): Int {
        val rows = pinnedRows()
        return ((pinnedPackages().size + rows - 1) / rows).coerceAtLeast(1).coerceAtMost(8)
    }

    /** The old control center is now a focused pinned-app launcher, opened from the pill. */
    private fun buildPanel(card: LinearLayout) {
        card.addView(TextView(uiCtx).apply {
            text = "Pinned apps"
            setTextColor(pillStyle().primary)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), 0, dp(2), dp(4))
        })
        card.addView(TextView(uiCtx).apply {
            text = "Your selected apps"
            setTextColor(pillStyle().secondary)
            textSize = 12f
            setPadding(dp(2), 0, dp(2), dp(10))
        })
        pTiles = GridLayout(uiCtx).apply {
            columnCount = panelCols()
            rowCount = pinnedRows()
            useDefaultMargins = false
        }
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
        val iconSize = prefs.getInt("pinnedIconSize", 32).coerceIn(20, 48)
        d?.setBounds(0, 0, dp(iconSize), dp(iconSize))
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
                height = dp(prefs.getInt("pinnedIconSize", 32).coerceIn(20, 48) + 34)
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
        val apps = pinnedPackages().map { "app:$it" }
        pTiles.rowCount = pinnedRows()
        if (apps.isEmpty()) {
            val empty = TextView(uiCtx).apply {
                text = "No pinned apps yet. Choose apps in Capsule Lite settings."
                setTextColor(pillStyle().secondary)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(18), dp(16), dp(18))
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(0, pinnedRows()), GridLayout.spec(0, panelCols(), 1f)
                ).apply { width = -1 }
            }
            pTiles.addView(empty)
        } else apps.forEach { pTiles.addView(panelTile(it)) }
        refreshPanelTiles()
    }

    private fun refreshPanelTiles() {
        for (i in 0 until pTiles.childCount) {
            val v = pTiles.getChildAt(i) as? TextView ?: continue
            val id = v.tag as? String ?: continue
            if (id.startsWith("app:")) {
                v.setTextColor(pillStyle().primary)
                (v.background as? GradientDrawable)?.apply {
                    setColor(pillStyle().control)
                    setStroke(dp(1), pillStyle().outline)
                }
            }
        }
    }

    private fun refreshPanel() {
        if (!panelOpen) return
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
        val accent = pillAccent()
        qsButtons.forEach { (id, tv) ->
            val active = on[id] == true
            (tv.background as GradientDrawable).setColor(if (active) accent else 0xFF333333.toInt())
            tv.setTextColor(if (active) Color.BLACK else Color.WHITE)
        }
    }

    // ---------- what the pill shows ----------

    private fun collapsedMediaIndicator(): String {
        val stored = prefs.getString("collapsedMediaIndicator", null)
        return stored?.takeIf { it in setOf("off", "waveform", "visualizer", "art_pulse") }
            ?: if (prefs.getBoolean("miniMediaWaveform", true)) "waveform" else "off"
    }

    private var albumArtPulseAnimator: ValueAnimator? = null

    private fun updateAlbumArtPulse(shouldPulse: Boolean) {
        if (!::icon.isInitialized) return
        if (!shouldPulse || !motionEnabled()) {
            albumArtPulseAnimator?.cancel()
            albumArtPulseAnimator = null
            icon.scaleX = 1f
            icon.scaleY = 1f
            icon.alpha = 1f
            return
        }
        if (albumArtPulseAnimator?.isStarted == true) return
        albumArtPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val pulse = animator.animatedValue as Float
                icon.scaleX = 1f + 0.035f * pulse
                icon.scaleY = 1f + 0.035f * pulse
                icon.alpha = 0.96f + 0.04f * pulse
            }
            start()
        }
    }

    private fun showCharging() = prefs.getBoolean("showCharging", true)
    private fun showMusic() = prefs.getBoolean("showMusic", true)
    private fun mapsUsesDirectionCard(): Boolean =
        prefs.getString("mapsExpandedLayout", null)?.let { it == "direction_card" }
            ?: prefs.getBoolean("mapsReferenceLayout", true)

    private fun shouldShowMediaWithNotification(): Boolean {
        return false
    }

    private fun canKeepQueueBesideActivity(): Boolean {
        return false
    }

    private fun shouldShowChargingWithNotification(): Boolean =
        false

    private fun shouldShowNavigationWithNotification(): Boolean =
        false

    private fun shouldShowActivityNotificationCard(): Boolean =
        false

    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE

    private fun publishNotificationQueueStatus(
        winner: String?,
        activityWithNotification: Boolean,
        notifOn: Boolean,
        lockPrivate: Boolean,
        hidden: Boolean
    ) {
        val count = notificationQueue.size + notificationOverflow
        val summary = when {
            count == 0 -> "Queue empty. New notifications appear here."
            lockPrivate -> "$count waiting · hidden by lock-screen privacy."
            hidden -> "$count waiting · temporarily hidden while a widget or quick settings is active."
            activityWithNotification -> "$count waiting · browse below the active media, navigation, or charging content in the expanded pill."
            notifOn -> "$count queued · notification details are open in the pill. Swipe or use the queue controls to browse."
            !expanded && shouldShowCollapsedNotificationPreview() -> "$count waiting · latest notification preview is in the compact pill; tap the text to open it."
            !expanded -> "$count waiting · current activity stays visible; tap the count badge to open notifications."
            winner == "navigation" -> "$count waiting · navigation has priority; notifications are available when navigation yields."
            winner == "charging" -> "$count waiting · charging has priority; notifications are available when charging yields."
            winner == "media" -> "$count waiting · media has priority; expand media to see notifications beneath its controls."
            else -> "$count waiting · expand the pill to browse notifications."
        }
        if (prefs.getString("queueStateSummary", null) != summary) {
            prefs.edit().putString("queueStateSummary", summary).apply()
        }
    }

    /** Resolves the current Capsule event and renders its compact or expanded content. */
    private fun applyVisibility() {
        if (pill == null) return
        root?.visibility = View.VISIBLE
        if (::lp.isInitialized && root?.isAttachedToWindow == true &&
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(root, lp) }
        }
        pill?.gravity = if (expanded && prefs.getBoolean("expandFromStatusBar", true)) {
            Gravity.TOP or Gravity.START
        } else {
            Gravity.CENTER_VERTICAL
        }
        refreshMediaControlStyle()
        val lockPrivate = lockScreenActive() && prefs.getBoolean("lockScreenPrivacy", true)
        val qsOn = qsActive()
        val presentation = presentationState()
        val winner = presentation.foreground?.id
        val queueIsForeground = winner == "notifications" && notificationQueue.isNotEmpty()
        if (queueIsForeground && !showingNotif) {
            val queued = notificationQueue.values.lastOrNull()
            queued?.let { item ->
                notifKey = item.key
                mediaNotificationKey = item.key
                notifIntent = item.intent
                replyAction = item.reply
                notifActions = item.actions
                notifLevel = if (expanded) 2 else 1
                title.text = item.title.ifBlank { appLabel(item.pkg) }
                subtitle.text = item.body.ifBlank { appLabel(item.pkg) }
                icon.setImageDrawable(appIcon(item.pkg))
            }
        }
        val inner = targetInnerH()
        val mapsWide = !expanded && (mapsShown() || callCompact())
        val chargeCompact = !expanded && !callActive && activityWinner() == "charging" && charging &&
            showCharging() && !qsOn && !widgetActive()
        val notificationCompact = shouldShowCollapsedNotificationPreview() && !lockPrivate
        var contentChanged = false
        if (qsOn != lastQs || inner != lastInner || mapsWide != lastMapsWide ||
            chargeCompact != lastChargeMini || notificationCompact != lastNotificationMini) {
            snapshotOld()
            lastQs = qsOn
            lastInner = inner
            lastMapsWide = mapsWide
            lastChargeMini = chargeCompact
            lastNotificationMini = notificationCompact
            contentChanged = true
            animateSize()
        }
        val widgetOn = !qsOn && widgetActive()
        val hideAll = qsOn || widgetOn
        val mediaSessionAvailable = controller != null && showMusic()
        val mediaPlaying = mediaIsPlaying()
        val pausedSessionCanResume = mediaSessionCanResume()
        updateActivityBubble(presentation)
        updateNotificationBubble(presentation)
        val mediaWithNotification = !hideAll && shouldShowMediaWithNotification()
        val chargeWithNotification = !hideAll && shouldShowChargingWithNotification()
        val navigationWithNotification = !hideAll && shouldShowNavigationWithNotification()
        val activityWithNotification = mediaWithNotification || chargeWithNotification || navigationWithNotification
        if (mediaWithNotification) renderMedia()
        // Notification details belong to an expanded surface. In compact mode, keep only the
        // count badge; rendering title/actions there competes with the charging/media indicators.
        val notifOn = !hideAll && expanded && (showingNotif || queueIsForeground) &&
            winner in setOf("live", "notifications") && !activityWithNotification
        publishNotificationQueueStatus(winner, activityWithNotification, notifOn, lockPrivate, hideAll)
        val musicOn = !hideAll && expanded && !callActive && mediaSessionAvailable &&
            (winner == "media" || mediaWithNotification || (pausedSessionCanResume && winner == null))
        val miniMusic = !hideAll && !expanded && !callActive && winner == "media" && mediaCompactActivityActive()
        val mapsMini = !hideAll && !expanded && mapsShown()
        val notificationMini = !hideAll && notificationCompact
        val compactNotification = if (notificationMini) notificationQueue.values.lastOrNull() else null
        compactNotification?.let { item ->
            title.text = item.title.ifBlank { appLabel(item.pkg) }
            subtitle.text = item.body.ifBlank { appLabel(item.pkg) }
            icon.setImageDrawable(appIcon(item.pkg))
            artRow.contentDescription = "${item.title.ifBlank { appLabel(item.pkg) }} notification. Tap to open."
            artRow.isClickable = true
        } ?: run {
            artRow.contentDescription = null
            artRow.isClickable = false
        }
        val swOn = stopwatchStart > 0
        val chargeMini = !hideAll && !expanded && !callActive && winner == "charging" && charging && showCharging()
        val replyOn = !hideAll && expanded && !callActive && notifOn && notifLevel >= 2 && replyAction != null &&
            prefs.getBoolean("replyChips", true) && !lockPrivate
        val actionOn = !hideAll && expanded && !callActive && notifOn && notifLevel >= 2 &&
            notifActions.isNotEmpty() && !lockPrivate
        val callOn = !hideAll && expanded && callActive
        val chargeOn = !hideAll && expanded && !callActive && chargeExpandedShown() &&
            (winner == "charging" || chargeWithNotification)
        val navOn = !hideAll && expanded && !callActive &&
            (mapsShown() || (navigationWithNotification && winner == "notifications"))
        val notifStackOn = !hideAll && expanded && !callActive && notifOn && (replyOn || actionOn)
        val queueNavOn = false
        val queueStackOn = false
        val mapsCardLayout = mapsUsesDirectionCard()
        val mediaCardLayout = prefs.getBoolean("mediaReferenceLayout", true)
        val tasksPreviewOn = expanded && pillIsIdleForTasks() && !pausedSessionCanResume && !hideAll && !lockPrivate
        setMusicLayout((musicOn && mediaCardLayout) || notifStackOn || callOn ||
            (chargeOn && chargeWithNotification) || navigationWithNotification || tasksPreviewOn || queueStackOn)
        qsGrid.visibility = vis(qsOn)
        widgetImage.visibility = vis(widgetOn)
        if (qsOn) refreshQsStates()

        artRow.visibility = vis(!hideAll && (expanded || miniMusic || notificationMini) && !lockPrivate)
        val collapsedIndicator = collapsedMediaIndicator()
        updateAlbumArtPulse(miniMusic && !lockPrivate && collapsedIndicator == "art_pulse" && mediaPlaying)
        miniMediaWaveform.visibility = vis(miniMusic && !lockPrivate && collapsedIndicator == "waveform")
        barsRow.visibility = vis(!hideAll && (musicOn || (miniMusic && collapsedIndicator == "visualizer")) &&
            (micRunning || ambientRunning))
        (textCol.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.weight = if (miniMusic) 2.2f else 1f
            textCol.layoutParams = params
        }
        (miniVizSpacer.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.weight = if (miniMusic) 0.65f else 1f
            miniVizSpacer.layoutParams = params
        }
        miniVizSpacer.visibility = vis(miniMusic && (miniMediaWaveform.visibility == View.VISIBLE || barsRow.visibility == View.VISIBLE))
        navBox.visibility = vis(navOn && !lockPrivate && mapsCardLayout)
        icon.visibility = vis(!hideAll && (expanded || miniMusic || notificationMini) && !chargeOn && (!navOn || !mapsCardLayout) && !lockPrivate)
        textCol.visibility = vis(!hideAll && (expanded || miniMusic || notificationMini) && !chargeOn &&
            (!navOn || !mapsCardLayout) && !lockPrivate)
        val textColParams = textCol.layoutParams as? LinearLayout.LayoutParams
        textColParams?.let {
            it.marginEnd = dp(if (notificationMini) 30 else 6)
            textCol.layoutParams = it
        }
        val sectionForContent = activePillSizingSection()
        val sectionIconScale = (prefs.getInt("sectionIconScale_$sectionForContent", 100).coerceIn(70, 160) / 100f)
        val sectionTextScale = (prefs.getInt("sectionTextScale_$sectionForContent", 100).coerceIn(80, 160) / 100f)
        val artBaseSize = if (callOn || musicOn) dp(56) else if (notificationMini) dp(34) else dp(40)
        val artLimit = if (miniMusic || notificationMini) (collapsedTargetHeight() - dp(4)).coerceAtLeast(dp(24)) else dp(80)
        val artSize = (artBaseSize * sectionIconScale).toInt().coerceIn(dp(24), dp(80)).coerceAtMost(artLimit)
        (icon.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = artSize
            params.height = artSize
            icon.layoutParams = params
        }
        icon.background = GradientDrawable().apply {
            shape = if (miniMusic || (!expanded && notificationMini)) GradientDrawable.OVAL
                else if (callOn || musicOn || notificationMini) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            cornerRadius = if (miniMusic || (!expanded && notificationMini)) artSize / 2f
                else dp(pillStyle().artworkCornerDp).toFloat()
            setColor(Color.TRANSPARENT)
        }
        icon.clipToOutline = true
        icon.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        val textScale = (prefs.getInt("textScale", 100) / 100f) * sectionTextScale
        title.textSize = (if (callOn) 18f else if (musicOn) 16f else if (notificationMini) 13f else 14f) * textScale
        subtitle.textSize = (if (callOn || musicOn) 13f else if (notificationMini) 11f else 12f) * textScale
        title.maxLines = if (miniMusic) 1 else 2
        title.ellipsize = TextUtils.TruncateAt.END
        if (notificationMini || miniMusic) subtitle.maxLines = 1
        else if (notifOn) subtitle.maxLines = subtitle.maxLines.coerceIn(1, 3)
        else subtitle.maxLines = Int.MAX_VALUE
        subtitle.ellipsize = if (notificationMini || miniMusic || notifOn) TextUtils.TruncateAt.END else null
        if (callOn || musicOn || notificationMini) {
            title.setTextColor(pillStyle().primary)
            subtitle.setTextColor(pillStyle().secondary)
        }
        chargeBox.visibility = vis(chargeOn)
        if (chargeOn) updateChargeBox()
        if (navOn) updateNavBox()
        ctrlRow.visibility = vis(musicOn && !lockPrivate)
        timeRow.visibility = vis(musicOn && !lockPrivate)
        val hasMediaDuration = (controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) > 0L
        mediaWaveProgress.visibility = vis(musicOn && !lockPrivate && hasMediaDuration)
        mediaNotificationCard.visibility = View.GONE
        notificationQueueStack.visibility = View.GONE
        taskPreviewPanel.visibility = vis(tasksPreviewOn)
        if (tasksPreviewOn) renderTaskPreview()
        btnPrev.visibility = vis(musicOn && !lockPrivate)
        btnBack10.visibility = vis(musicOn && !lockPrivate)
        btnPlay.visibility = vis(musicOn && !lockPrivate)
        miniPlayButton.visibility = vis(miniMusic && !lockPrivate)
        val miniControlSize = (dp(34) * sectionIconScale).toInt().coerceIn(dp(28), dp(44))
            .coerceAtMost((collapsedTargetHeight() - dp(4)).coerceAtLeast(dp(28)))
        (miniPlayButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = miniControlSize
            params.height = miniControlSize
            miniPlayButton.layoutParams = params
        }
        btnForward10.visibility = vis(musicOn && !lockPrivate)
        btnNext.visibility = vis(musicOn && !lockPrivate)
        callControlsRow.visibility = vis(callOn && !lockPrivate &&
            (callAnswer != null && callState == 1 || callHangup != null))

        miniChargeRing.visibility = vis(chargeMini)
        val chargeIconScale = (prefs.getInt("sectionIconScale_charging", 100).coerceIn(70, 160) / 100f)
        val collapsedHeightDp = (collapsedTargetHeight() / resources.displayMetrics.density).toInt()
        val miniRingMaxDp = (collapsedHeightDp - 4).coerceAtLeast(24)
        val miniRingSize = ((prefs.getInt("chargeRingSize", 48).coerceIn(36, 80) * 0.72f) * chargeIconScale)
            .toInt().coerceIn(24, 52).coerceAtMost(miniRingMaxDp)
        (miniChargeRing.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = dp(miniRingSize)
            params.height = dp(miniRingSize)
            miniChargeRing.layoutParams = params
        }
        miniText.visibility = vis(!hideAll && (!expanded && (callActive || swOn || chargeMini || mapsMini) || lockPrivate))
        miniText.text = when {
            lockPrivate -> if (notificationQueue.isNotEmpty()) "${notificationQueue.size}+" else "Capsule"
            callActive -> if (callState == 2) "" else "Incoming"
            swOn -> "SW " + fmt(stopwatchElapsed())
            mapsMini -> mapsMiniText()
            chargeMini -> ""
            else -> "\u26A1"   // the percentage is shown on the right of the camera hole
        }
        val k = (prefs.getInt("textScale", 100) / 100f) * sectionTextScale
        miniText.textSize = if (mapsMini) 15f * k else 13f * k
        miniText.setTextColor(if (lockPrivate) pillStyle().primary else if (!callActive && !swOn && !mapsMini && lastTempC >= 42.0) 0xFFFF9F0A.toInt() else Color.WHITE)
        val chargeSplit = chargeMini && !swOn && !mapsMini
        val mapsSplit = mapsMini && !swOn
        val callSplit = !hideAll && !expanded && callActive && callState == 2
        miniSpacer.visibility = vis(chargeSplit || mapsSplit || callSplit)
        miniRight.visibility = vis((chargeSplit || mapsSplit || callSplit) && !lockPrivate)
        if (callSplit) {
            miniRight.text = callTimerText()
            miniRight.textSize = 15f * k
            miniRight.setTextColor(0xFF30D158.toInt())
        } else if (mapsSplit) {
            val eta = MapsNav.eta.trim()
            val distanceLeft = MapsNav.distanceLeft.trim()
            // Show both remaining time and full-trip distance in the collapsed pill.
            val mapRightLabel = listOf(
                eta,
                distanceLeft.takeIf { it.isNotEmpty() }?.let { "$it left" }
            ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                .ifEmpty { MapsNav.arrival.ifEmpty { MapsNav.turnDistance } }
            miniRight.textSize = 12f * k
            val trafficTint = mapsTrafficTint()
            val coloredEta = eta.ifEmpty { MapsNav.arrival.trim() }
            miniRight.text = if (trafficTint != null && coloredEta.isNotEmpty() && mapRightLabel.startsWith(coloredEta)) {
                android.text.SpannableString(mapRightLabel).apply {
                    setSpan(android.text.style.ForegroundColorSpan(trafficTint), 0, coloredEta.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else mapRightLabel
            miniRight.setTextColor(pillStyle().primary)
        } else {
            val percentText = "$chargePct%"
            val wattsText = chargeWatts?.takeIf { it.isFinite() && it > 0.05 }
                ?.let { String.format(java.util.Locale.getDefault(), "%.1f W", it) } ?: "— W"
            val percentSize = prefs.getInt("chargePctTextSize", 13).coerceIn(10, 24)
            when (prefs.getString("chargeMiniReadout", "percent") ?: "percent") {
                "watts" -> {
                    miniRight.text = wattsText
                    miniRight.textSize = 12f
                    miniRight.contentDescription = if (wattsText == "— W") "Charging power unavailable" else "Charging power $wattsText"
                }
                "both" -> {
                    val label = "$percentText · $wattsText"
                    miniRight.textSize = 12f
                    miniRight.text = android.text.SpannableString(label).apply {
                        setSpan(android.text.style.AbsoluteSizeSpan(percentSize, true), 0, percentText.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.AbsoluteSizeSpan(10, true), percentText.length, label.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    miniRight.contentDescription = "Battery $chargePct percent, charging power $wattsText"
                }
                else -> {
                    miniRight.text = percentText
                    miniRight.textSize = percentSize.toFloat()
                    miniRight.contentDescription = "Battery $chargePct percent"
                }
            }
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
        notificationActionsScroll.visibility = vis(replyOn || actionOn || queueNavOn)
        replyRow.visibility = vis(replyOn || actionOn || queueNavOn)
        actionBtns.forEachIndexed { i, b ->
            b.text = notifActions.getOrNull(i)?.title?.toString() ?: ""
            b.visibility = vis(actionOn && i < notifActions.size)
        }
        val currentNotificationIndex = notificationQueue.values.indexOfFirst { it.key == notifKey }
        notificationPosition.text = "Latest notification"
        notificationPrevious.visibility = View.GONE
        notificationPosition.visibility = View.GONE
        notificationNext.visibility = View.GONE
        btnAnswer.visibility = vis(callOn && !lockPrivate && callAnswer != null && callState == 1)
        btnDecline.visibility = vis(callOn && !lockPrivate && callHangup != null)
        callDeclineLabel.text = if (callState == 1) "Decline" else "End call"
        callDeclineLabel.setTextColor(pillStyle().secondary)
        callAnswerLabel.setTextColor(pillStyle().secondary)
        callDeclineGroup.visibility = vis(callOn && !lockPrivate && callHangup != null)
        callAnswerGroup.visibility = vis(callOn && !lockPrivate && callAnswer != null && callState == 1)
        refreshNotificationBadge()
        if (contentChanged) staggerIn()
        root?.post { avoidNotificationBadgeOverlap() }
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
        mediaNotificationCard.layoutParams = if (music) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        } else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
        if (showingNotif || callActive) {
            if (::mediaWaveProgress.isInitialized) mediaWaveProgress.visibility = View.GONE
            return
        }
        val c = controller ?: return
        val md = c.metadata
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        var pos = estimatedPlaybackPosition(st)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        if (subtitle.text.toString() != artist) subtitle.text = artist
        val trackTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        val progressDescription = if (dur > 0L) "Track progress, $trackTitle" else "Track progress unavailable for this track"
        if (mediaWaveProgress.contentDescription?.toString() != progressDescription) {
            mediaWaveProgress.contentDescription = progressDescription
        }
        if (dur > 0) {
            pos = pos.coerceIn(0L, dur)
            if (!pillSeeking) {
                mediaWaveProgress.progress = ((pos.toFloat() / dur.toFloat()) * mediaWaveProgress.max).toInt().coerceIn(0, mediaWaveProgress.max)
            }
            val elapsed = fmt(pos)
            val remaining = "-" + fmt(dur - pos)
            if (elapsedText.text.toString() != elapsed) elapsedText.text = elapsed
            if (remainText.text.toString() != remaining) remainText.text = remaining
        } else {
            mediaWaveProgress.progress = 0
            val elapsed = fmt(pos)
            if (elapsedText.text.toString() != elapsed) elapsedText.text = elapsed
            if (remainText.text.isNotEmpty()) remainText.text = ""
        }
        if (mediaWaveProgress.visibility != View.VISIBLE) mediaWaveProgress.visibility = View.VISIBLE
        progress.visibility = View.GONE
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
                val lockActive = lockScreenActive()
                if (lockActive != lockScreenWasActive) {
                    lockScreenWasActive = lockActive
                    applyVisibility()
                }
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
        } else if (shouldShowActivityNotificationCard() || !showingNotif || activityWinner() !in setOf("live", "notifications")) {
            renderMedia()
        }
        applyVisibility()
    }

    // Keep the collapsed left label tied to the upcoming maneuver; the right side already carries
    // trip ETA and total distance remaining, so repeating total distance here is misleading.
    private fun mapsMiniText(): String = MapsNav.turnDistance.ifEmpty {
        MapsNav.distanceLeft.ifEmpty { MapsNav.eta.ifEmpty { "Maps" } }
    }

    private fun mapsTrafficTint(): Int? =
        if (prefs.getBoolean("mapsTrafficColors", true)) MapsNav.trafficTint() else null

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
        val textScale = prefs.getInt("sectionTextScale_maps", 100).coerceIn(80, 160) / 100f
        val iconScale = prefs.getInt("sectionIconScale_maps", 100).coerceIn(70, 160) / 100f
        navTurn.textSize = 30f * textScale
        navStreet.textSize = 16f * textScale
        navEta.textSize = 22f * textScale
        navArrive.textSize = 14f * textScale
        navLeft.textSize = 14f * textScale
        val arrowSize = dp((40 * iconScale).toInt().coerceIn(28, 56))
        (navArrow.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = arrowSize
            params.height = arrowSize
            navArrow.layoutParams = params
        }
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
        navEta.setTextColor(mapsTrafficTint() ?: pillStyle().primary)
        navArrive.text = MapsNav.arrival
        navLeft.text = if (MapsNav.distanceLeft.isEmpty()) "" else "${MapsNav.distanceLeft} left"
    }

    // The small icon left of the camera hole in the collapsed pill (a turn arrow, or the green phone). key = null: no icon.
    private fun setMiniIcon(
        key: String?, res: Int = 0, tint: Int = Color.WHITE,
        bmp: android.graphics.Bitmap? = null, tintBmp: Boolean = false
    ) {
        val scale = prefs.getInt("sectionIconScale_${activePillSizingSection()}", 100).coerceIn(70, 160) / 100f
        val iconSize = if (key == null) 0 else dp((20 * scale).toInt().coerceIn(16, 32))
        val k = "${key.orEmpty()}:$iconSize"
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
        d?.setBounds(0, 0, iconSize, iconSize)
        miniText.compoundDrawablePadding = dp(6)
        miniText.setCompoundDrawablesRelative(d, null, null, null)
    }

    // A new turn opens the navigation pill for a few seconds. Distance updates never do.
    private fun checkMapsManeuver() {
        val seq = MapsNav.maneuverSeq
        if (seq != seenManeuver) {
            seenManeuver = seq
            val now = SystemClock.uptimeMillis()
            if (prefs.getBoolean("mapsInfo", true) && mapsShown() && !expanded && now - lastMapsPeek > 8000) {
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
        var trafficLevel: String? = null
        val ex = n.extras
        for (k in listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT
        )) {
            ex.getCharSequence(k)?.let { value ->
                value.toString().takeIf { it.isNotBlank() }?.let { texts.add(it) }
                if (trafficLevel == null) trafficLevel = MapsNav.trafficLevelFromSpans(value)
            }
        }
        // A custom notification layout holds the real texts and the arrow picture
        @Suppress("DEPRECATION")
        val views = listOfNotNull(n.bigContentView, n.contentView, n.headsUpContentView)
        for (rv in views) {
            val v = runCatching { rv.apply(this, FrameLayout(this)) }.getOrNull() ?: continue
            val found = scrapeViews(v, texts) { observed -> if (trafficLevel == null) trafficLevel = observed }
            if (arrow == null) arrow = found
        }
        if (arrow == null) {
            arrow = runCatching { n.getLargeIcon()?.loadDrawable(this)?.let { drawableToBitmap(it) } }.getOrNull()
        }
        if (prefs.getBoolean("debugMaps", false)) diag("maps notification: texts=${texts.take(14)}, arrow picture found=${arrow != null}")
        MapsNav.updateFromNotification(texts, arrow, trafficLevel)
        return MapsNav.active
    }

    // Collects the texts and the first real picture of a notification layout. Android's own icons
    // (the small app icon, the expand button) are skipped.
    private fun scrapeViews(root: View, texts: MutableList<String>, onTrafficLevel: (String) -> Unit): Bitmap? {
        var found: Bitmap? = null
        fun walk(v: View, depth: Int) {
            if (depth > 25) return
            if (v is TextView) v.text?.let { value ->
                value.toString().trim().takeIf { it.isNotEmpty() }?.let { texts.add(it) }
                if (MapsNav.isDurationText(value)) MapsNav.trafficLevelFromColor(v.currentTextColor)?.let(onTrafficLevel)
            }
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
        prefs.getBoolean("mapsInfo", true) && MapsNav.active && activityWinner() == "navigation"

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
        val c = if (showMusic() && (activityWinner() == "media" || shouldShowMediaWithNotification())) controller else null
        if (c == null) {
            if (charging && showCharging() && activityWinner() == "charging") {
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
            val playPauseIcon = when (pillStyle().id) {
                "oneui" -> if (playing) R.drawable.ic_oneui_pause else R.drawable.ic_oneui_play
                else -> if (playing) R.drawable.ic_pause else R.drawable.ic_play
            }
            btnPlay.setImageResource(playPauseIcon)
            miniPlayButton.setImageResource(playPauseIcon)
            val playDescription = if (playing) "Pause playback" else "Resume playback"
            if (btnPlay.contentDescription?.toString() != playDescription) {
                btnPlay.contentDescription = playDescription
            }
            if (miniPlayButton.contentDescription?.toString() != playDescription) {
                miniPlayButton.contentDescription = playDescription
            }
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val previousToken = controller?.sessionToken
        controller?.unregisterCallback(controllerCallback)
        val next = if (!showMusic()) null else (
            list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list?.firstOrNull()
            )
        if (previousToken != next?.sessionToken) {
            lastMediaPlayingAtElapsed = 0L
            mediaWasPlaying = false
            mediaCompactGraceUntilElapsed = 0L
            handler.removeCallbacks(mediaCompactGraceExpiry)
        }
        controller = next
        next?.registerCallback(controllerCallback)
        updateMediaPauseGrace(next?.playbackState)
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

        // Incoming and ongoing calls use a live call surface. Missed calls are ordinary
        // durable notifications so they remain in the unread queue after ringing ends.
        val isMissedCall = n.category == Notification.CATEGORY_MISSED_CALL
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
                if (prefs.getBoolean("mapsInfo", true) && feedMaps(n)) {
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
            // Keep ordinary alerts queued behind an active timer/download instead of interrupting it.
            if (!isMaps && showingNotif &&
                ((liveChronometerKey != null && notifKey == liveChronometerKey) ||
                    (downloadKey != null && notifKey == downloadKey))) {
                enqueueNotification(PendingNotif(sbn.key, sbn.packageName, t, body, n.contentIntent,
                    n.color, reply, others, isMissedCall, sbn.postTime))
                return@uiPost
            }
            if (isMaps) {
                val nowMs = SystemClock.uptimeMillis()
                if (!debugMaps && prefs.getBoolean("mapsInfo", true) && MapsNav.active && mapsShown()) {
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
            val p = PendingNotif(sbn.key, sbn.packageName, t, body, n.contentIntent, n.color,
                reply, others, isMissedCall, sbn.postTime)
            enqueueNotification(p)
            val navigationOwnsPill = MapsNav.active && prefs.getBoolean("mapsInfo", true) && !debugMaps
            if (callActive || lockScreenActive() || navigationOwnsPill ||
                !notificationMayInterrupt(activityWinner())) {
                if (navigationOwnsPill && showingNotif && !liveActivityIsShowing()) {
                    handler.removeCallbacks(collapse)
                    showingNotif = false
                    notifIntent = null
                    notifKey = null
                    notifLevel = 0
                    replyAction = null
                    notifActions = emptyList()
                    setExpanded(false)
                }
                refreshNotificationBadge()
                applyVisibility()
                return@uiPost
            }
            if (!prefs.getBoolean("notifAutoExpand", true)) return@uiPost   // kept for a tap on the pill
            presentNotif(p, 1)
            setExpanded(true)
            armNotifTimer()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        try {
            handleRemoved(sbn, null)
        } catch (t: Throwable) {
            diag("notification removal error: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int
    ) {
        try {
            handleRemoved(sbn, reason)
        } catch (t: Throwable) {
            diag("notification removal error: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun handleRemoved(sbn: StatusBarNotification, reason: Int?) {
        if (sbn.packageName == CapsulePrefs.MAPS_PKG) {
            mapsKeys.remove(sbn.key)
            if (mapsNavKeys.remove(sbn.key) && mapsNavKeys.isEmpty()) {
                // Navigation ended: forget the notification's data and the arrow picture
                MapsNav.notifAlive = false
                MapsNav.active = false
                MapsNav.trafficLevel = ""
                MapsNav.arrow = null
                MapsNav.arrowVersion++
            }
        }
        uiPost {
            val timeout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                reason == NotificationListenerService.REASON_TIMEOUT
            val mediaHadNotificationCard = shouldShowActivityNotificationCard()
            if (!timeout) removeQueuedNotification(sbn.key)
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
                val nextQueued = notificationQueue.values.lastOrNull()
                if (mediaHadNotificationCard && !liveActivityIsShowing()) {
                    if (nextQueued != null) {
                        presentNotif(nextQueued, 1)
                    } else {
                        notifKey = null
                        notifIntent = null
                        replyAction = null
                        notifActions = emptyList()
                        notifLevel = 0
                        showingNotif = false
                        if (controller != null && showMusic()) renderMedia()
                    }
                    applyVisibility()
                } else {
                    notifKey = null
                    replyAction = null
                    if (!callActive && showingNotif) {
                        handler.removeCallbacks(collapse)
                        collapse.run()
                    }
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
        removeQueuedNotification(notifKey)
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
            uiPost {
                val waiting = pending
                val canShowDigest = intent.action == Intent.ACTION_USER_PRESENT && waiting != null &&
                    !callActive && !MapsNav.active && notificationMayInterrupt(activityWinner())
                if (canShowDigest) {
                    presentNotif(waiting!!, 1)
                    setExpanded(true)
                    armNotifTimer()
                } else {
                    showEvent(msg)
                }
            }
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
        chargePlugged = plugged
        val voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        lastTempC = tempC

        val wasCharging = charging
        // Only show the separate pill while Android reports active charging, not merely a cable
        // being connected or a battery that has already reached FULL.
        charging = plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING
        chargePct = level * 100 / scale
        val pluggedOrFull = plugged != 0 &&
            status in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
        if (!pluggedOrFull) {
            chargeAlerted = false
            fullAlerted = false
        } else if (prefs.getBoolean("chargeAlert", true)) {
            if (charging && chargePct >= prefs.getInt("chargeAlertAt", 80) && !chargeAlerted) {
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
        ChargingSessionHistory.record(
            this,
            charging,
            chargePct,
            tempC,
            chargeVolts,
            chargeMa?.div(1000.0),
            chargeWatts
        )
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
