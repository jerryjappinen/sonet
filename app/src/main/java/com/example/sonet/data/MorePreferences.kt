package com.example.sonet.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers which apps the user moved into or out of the More folder. */
class MorePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("more_folder", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(read())
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    fun setInMore(app: AppEntry, inMore: Boolean) {
        val next = _overrides.value.toMutableMap()
        // Only store choices that differ from the heuristic, so the heuristic can evolve.
        if (inMore == app.inMoreByDefault) next.remove(app.key) else next[app.key] = inMore
        prefs.edit {
            putStringSet(KEY_IN_MORE, next.filterValues { it }.keys)
            putStringSet(KEY_IN_MAIN, next.filterValues { !it }.keys)
        }
        _overrides.value = next
    }

    private fun read(): Map<String, Boolean> {
        val inMore = prefs.getStringSet(KEY_IN_MORE, emptySet()).orEmpty()
        val inMain = prefs.getStringSet(KEY_IN_MAIN, emptySet()).orEmpty()
        return inMore.associateWith { true } + inMain.associateWith { false }
    }

    private companion object {
        const val KEY_IN_MORE = "in_more"
        const val KEY_IN_MAIN = "in_main"
    }
}
