package com.example.capsule

/** Stable IDs used by the event scheduler and the pill renderer. */
internal enum class CapsuleActivityKind(val id: String) {
    CALL("calls"), STOPWATCH("stopwatch"), LIVE("live"), NOTIFICATION("notifications"),
    MEDIA("media"), NAVIGATION("navigation"), CHARGING("charging")
}

/**
 * A scheduler input is a snapshot of current sources. Long-running activities remain candidates
 * until their source ends; notifications are temporary focus candidates backed by the durable
 * notification queue. Keeping this model free of Android Views makes arbitration deterministic.
 */
internal data class CapsuleActivityEvent(
    val kind: CapsuleActivityKind,
    val active: Boolean,
    val persistent: Boolean
)

internal enum class NotificationPlacement { NONE, BADGE, FOREGROUND, COMPANION }

internal data class CapsuleActivitySnapshot(
    val events: List<CapsuleActivityEvent>,
    val priorityOrder: List<String>,
    val queuedNotificationCount: Int,
    val notificationFocusedByUser: Boolean,
    val userFocusedActivity: String? = null
)

/** Output of scheduling: one primary activity and at most one place to compose the queue. */
internal data class CapsuleActivitySchedule(
    val primary: CapsuleActivityKind?,
    val secondary: CapsuleActivityKind?,
    val notificationCompanion: CapsuleActivityKind?,
    val notificationPlacement: NotificationPlacement,
    val resumableActivities: List<CapsuleActivityKind>
) {
    val winnerId: String? get() = primary?.id
}

/**
 * Chooses a single foreground activity while retaining the remaining active activities so they
 * can resume when the foreground source yields. Only one notification companion is selected; this
 * avoids media, Maps and charging all claiming the same expanded area at once.
 */
internal object CapsuleActivityScheduler {
    private val fixedPrecedence = listOf(CapsuleActivityKind.CALL, CapsuleActivityKind.STOPWATCH)
    private val notificationHosts = setOf(
        CapsuleActivityKind.MEDIA, CapsuleActivityKind.NAVIGATION, CapsuleActivityKind.CHARGING
    )
    private val fallbackOrder = listOf(
        CapsuleActivityKind.LIVE, CapsuleActivityKind.NOTIFICATION,
        CapsuleActivityKind.MEDIA, CapsuleActivityKind.NAVIGATION, CapsuleActivityKind.CHARGING
    )

    fun schedule(snapshot: CapsuleActivitySnapshot): CapsuleActivitySchedule {
        val active = snapshot.events.asSequence().filter { it.active }.map { it.kind }.toSet()
        val ordered = (snapshot.priorityOrder.mapNotNull { id ->
            CapsuleActivityKind.entries.firstOrNull { it.id == id }
        } + fallbackOrder).distinct()
        val queued = snapshot.queuedNotificationCount > 0
        val userFocus = snapshot.notificationFocusedByUser && queued && CapsuleActivityKind.LIVE !in active
        val explicitFocus = snapshot.userFocusedActivity?.let { id ->
            CapsuleActivityKind.entries.firstOrNull { it.id == id && it in active }
        }?.takeUnless { it in fixedPrecedence || it == CapsuleActivityKind.NOTIFICATION }
        // A queued notification should not steal the main surface from an ongoing activity.
        // It gets its own notification companion while media, navigation, or charging remains
        // primary; notification focus is promoted only by an explicit user action or when idle.
        val hasPersistentNotificationHost = ordered.any { it in notificationHosts && it in active }
        val orderedPrimary = ordered.firstOrNull {
            it in active && (it != CapsuleActivityKind.NOTIFICATION || !hasPersistentNotificationHost)
        }
        val primary = fixedPrecedence.firstOrNull { it in active }
            ?: explicitFocus
            ?: if (userFocus) CapsuleActivityKind.NOTIFICATION
            else orderedPrimary ?: CapsuleActivityKind.NOTIFICATION.takeIf { queued }

        val secondary = if (primary in setOf(
                CapsuleActivityKind.CALL, CapsuleActivityKind.STOPWATCH, CapsuleActivityKind.NOTIFICATION
            )) null else ordered.firstOrNull { candidate ->
            candidate != primary && candidate != CapsuleActivityKind.CALL &&
                candidate != CapsuleActivityKind.STOPWATCH && candidate != CapsuleActivityKind.NOTIFICATION &&
                candidate in active && snapshot.events.any { it.kind == candidate && it.active && it.persistent }
        }

        val liveOrCallHasExclusiveSurface = primary in setOf(
            CapsuleActivityKind.CALL, CapsuleActivityKind.STOPWATCH, CapsuleActivityKind.LIVE
        )
        val companion = if (queued && !liveOrCallHasExclusiveSurface) {
            when (primary) {
                in notificationHosts -> primary
                CapsuleActivityKind.NOTIFICATION -> ordered.firstOrNull { it in notificationHosts && it in active }
                else -> null
            }
        } else null

        val placement = when {
            !queued -> NotificationPlacement.NONE
            companion != null -> NotificationPlacement.COMPANION
            primary == CapsuleActivityKind.NOTIFICATION -> NotificationPlacement.FOREGROUND
            else -> NotificationPlacement.BADGE
        }
        val resumable = ordered.filter { it in active && it != primary }
        return CapsuleActivitySchedule(primary, secondary, companion, placement, resumable)
    }
}

/** Renderer-facing state, derived separately from source arbitration. */
internal data class CapsulePresentationState(
    val foreground: CapsuleActivityKind?,
    val secondary: CapsuleActivityKind?,
    val notificationPlacement: NotificationPlacement,
    val notificationHost: CapsuleActivityKind?,
    val canResume: List<CapsuleActivityKind>
)

internal object CapsulePresentationStateMachine {
    fun reduce(schedule: CapsuleActivitySchedule) = CapsulePresentationState(
        foreground = schedule.primary,
        secondary = schedule.secondary,
        notificationPlacement = schedule.notificationPlacement,
        notificationHost = schedule.notificationCompanion,
        canResume = schedule.resumableActivities
    )
}
