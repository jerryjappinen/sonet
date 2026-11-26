package com.example.sonet.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A user-made group of apps. [members] are [AppEntry.key]s. */
data class AppGroup(
    val id: String,
    val name: String,
    val members: List<String>,
)

/**
 * Persists the user's app groups. An app belongs to at most one group, and a group
 * is deleted once its last app is removed from it.
 */
class GroupStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_groups", Context.MODE_PRIVATE)

    private val _groups = MutableStateFlow(read())
    val groups: StateFlow<List<AppGroup>> = _groups.asStateFlow()

    /** Creates a group holding [members] (taken out of any other group) and returns its id. */
    fun create(name: String, members: List<String>): String {
        val id = UUID.randomUUID().toString()
        update { groups -> groups.withoutMembers(members) + AppGroup(id, name, members.distinct()) }
        return id
    }

    /**
     * Creates the default groups the first time the launcher runs. [defaults] maps a group
     * name to its members; members already in a group are skipped, as are groups that would
     * end up with fewer than two apps. Returns the members that were grouped.
     */
    fun seedDefaultsOnce(defaults: Map<String, List<String>>): List<String> {
        if (prefs.getBoolean(KEY_SEEDED, false)) return emptyList()
        val alreadyGrouped = _groups.value.flatMapTo(HashSet()) { it.members }
        val grouped = buildList {
            defaults.forEach { (name, members) ->
                val fresh = members.filterNot { it in alreadyGrouped }
                if (fresh.size >= 2) {
                    create(name, fresh)
                    addAll(fresh)
                }
            }
        }
        prefs.edit { putBoolean(KEY_SEEDED, true) }
        return grouped
    }

    fun rename(id: String, name: String) =
        update { groups -> groups.map { if (it.id == id) it.copy(name = name) else it } }

    /** Deletes the group; its apps go back to the main list. */
    fun ungroup(id: String) = update { groups -> groups.filterNot { it.id == id } }

    fun addMember(id: String, member: String) = update { groups ->
        groups.withoutMembers(listOf(member)).map {
            if (it.id == id) it.copy(members = it.members + member) else it
        }
    }

    fun removeMember(member: String) = update { groups -> groups.withoutMembers(listOf(member)) }

    private fun List<AppGroup>.withoutMembers(members: List<String>): List<AppGroup> =
        map { it.copy(members = it.members - members.toSet()) }.filter { it.members.isNotEmpty() }

    private fun update(transform: (List<AppGroup>) -> List<AppGroup>) {
        val next = transform(_groups.value)
        prefs.edit { putString(KEY, write(next)) }
        _groups.value = next
    }

    private fun read(): List<AppGroup> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val members = obj.getJSONArray("members")
            AppGroup(
                id = obj.getString("id"),
                name = obj.getString("name"),
                members = List(members.length()) { members.getString(it) },
            )
        }
    }.getOrDefault(emptyList())

    private fun write(groups: List<AppGroup>): String = JSONArray().apply {
        groups.forEach { group ->
            put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("members", JSONArray(group.members))
            })
        }
    }.toString()

    private companion object {
        const val KEY = "groups"
        const val KEY_SEEDED = "defaults_seeded"
    }
}
