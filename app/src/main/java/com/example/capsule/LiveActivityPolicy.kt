package com.example.capsule

import android.app.Notification
import android.service.notification.StatusBarNotification

/** Shared classification and priority rules for notification-backed live activities. */
internal object LiveActivityPolicy {
    enum class Kind(val priority: Int) {
        TIMER(70), PROGRESS(60)
    }

    fun classify(sbn: StatusBarNotification): Kind? {
        if (!sbn.isOngoing) return null
        val n = sbn.notification
        if (n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0L) {
            return Kind.TIMER
        }
        val max = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (n.category == Notification.CATEGORY_PROGRESS || max > 0 || indeterminate) {
            return Kind.PROGRESS
        }
        return null
    }

    fun ordered(candidates: List<StatusBarNotification>): List<StatusBarNotification> =
        candidates.sortedWith(
            compareByDescending<StatusBarNotification> { classify(it)?.priority ?: Int.MIN_VALUE }
                .thenByDescending { it.postTime }
        )
}
