package com.example.capsule

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CapsuleSubtask(
    val title: String,
    val completed: Boolean = false
)

internal data class CapsuleTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val details: String = "",
    val dueAtMillis: Long? = null,
    val listName: String = CapsuleTaskStore.DEFAULT_LIST,
    val starred: Boolean = false,
    val completed: Boolean = false,
    val subtasks: List<CapsuleSubtask> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

/** Small local-only task store. It deliberately has no account or network dependency. */
internal object CapsuleTaskStore {
    const val DEFAULT_LIST = "Inbox"
    const val ALL_LISTS = "All tasks"
    const val TODAY = "Today"
    const val UPCOMING = "Upcoming"
    const val STARRED = "Starred"
    const val TASKS_KEY = "capsuleTasksJson"
    const val LISTS_KEY = "capsuleTaskListsJson"

    @Synchronized
    fun load(prefs: SharedPreferences): List<CapsuleTask> {
        val raw = prefs.getString(TASKS_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    if (title.isEmpty()) continue
                    val subtasksJson = item.optJSONArray("subtasks") ?: JSONArray()
                    val subtasks = buildList {
                        for (subIndex in 0 until subtasksJson.length()) {
                            val sub = subtasksJson.optJSONObject(subIndex) ?: continue
                            val subTitle = sub.optString("title").trim()
                            if (subTitle.isNotEmpty()) add(CapsuleSubtask(subTitle, sub.optBoolean("completed")))
                        }
                    }
                    add(
                        CapsuleTask(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = title,
                            details = item.optString("details"),
                            dueAtMillis = if (item.has("dueAtMillis") && !item.isNull("dueAtMillis")) item.optLong("dueAtMillis") else null,
                            listName = item.optString("listName").ifBlank { DEFAULT_LIST },
                            starred = item.optBoolean("starred"),
                            completed = item.optBoolean("completed"),
                            subtasks = subtasks,
                            createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(prefs: SharedPreferences, tasks: List<CapsuleTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            val subtasks = JSONArray()
            task.subtasks.forEach { subtasks.put(JSONObject().put("title", it.title).put("completed", it.completed)) }
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("details", task.details)
                    .put("dueAtMillis", task.dueAtMillis ?: JSONObject.NULL)
                    .put("listName", task.listName)
                    .put("starred", task.starred)
                    .put("completed", task.completed)
                    .put("subtasks", subtasks)
                    .put("createdAtMillis", task.createdAtMillis)
            )
        }
        prefs.edit().putString(TASKS_KEY, array.toString()).apply()
    }

    @Synchronized
    fun lists(prefs: SharedPreferences): List<String> {
        val saved = runCatching {
            val array = JSONArray(prefs.getString(LISTS_KEY, null).orEmpty())
            (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotEmpty) }
        }.getOrDefault(emptyList())
        return (listOf(DEFAULT_LIST) + saved + load(prefs).map { it.listName }).distinct()
    }

    @Synchronized
    fun addList(prefs: SharedPreferences, value: String): Boolean {
        val name = value.trim().take(32)
        if (name.isEmpty() || name.equals(ALL_LISTS, true) || name.equals(TODAY, true) ||
            name.equals(UPCOMING, true) || name.equals(STARRED, true) ||
            lists(prefs).any { it.equals(name, true) }) return false
        val names = lists(prefs).filterNot { it == DEFAULT_LIST } + name
        val array = JSONArray().apply { names.forEach(::put) }
        prefs.edit().putString(LISTS_KEY, array.toString()).apply()
        return true
    }

    fun filtered(
        prefs: SharedPreferences,
        filter: String,
        includeCompleted: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CapsuleTask> {
        val tasks = load(prefs)
        val startOfToday = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + 24L * 60L * 60L * 1000L
        return tasks.asSequence()
            .filter { includeCompleted || !it.completed }
            .filter { task ->
                when (filter) {
                    TODAY -> task.dueAtMillis?.let { it >= startOfToday && it < endOfToday } == true
                    UPCOMING -> task.dueAtMillis?.let { it >= endOfToday } == true
                    STARRED -> task.starred
                    ALL_LISTS -> true
                    else -> task.listName == filter
                }
            }
            .sortedWith(
                compareBy<CapsuleTask> { it.completed }
                    .thenByDescending { it.starred }
                    .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.createdAtMillis }
            )
            .toList()
    }
}
