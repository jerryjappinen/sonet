package com.example.sonet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonet.data.AppEntry
import com.example.sonet.data.AppRepository
import com.example.sonet.data.AppSections
import com.example.sonet.data.DefaultApps
import com.example.sonet.data.InstalledApps
import com.example.sonet.data.GroupStore
import com.example.sonet.data.MorePreferences
import com.example.sonet.data.partitionApps
import com.example.sonet.ui.LauncherActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.Collator

class LauncherViewModel(application: Application) : AndroidViewModel(application), LauncherActions {
    private val repository = AppRepository(application)
    private val morePreferences = MorePreferences(application)
    private val groupStore = GroupStore(application)

    private val apps: StateFlow<InstalledApps?> = repository.changes()
        .conflate()
        .map { repository.loadApps() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sections: StateFlow<AppSections> =
        combine(apps.filterNotNull(), morePreferences.overrides, groupStore.groups) { apps, overrides, groups ->
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            partitionApps(apps, overrides, groups, collator::compare)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppSections())

    init {
        // First run: put the common Google apps in a "Google" group, once the app list is known.
        viewModelScope.launch {
            val installed = apps.filterNotNull().first()
            val googleApps = installed.apps.filter { it.packageName in DefaultApps.GOOGLE_GROUP_PACKAGES }
            val grouped = groupStore
                .seedDefaultsOnce(mapOf(DefaultApps.GOOGLE_GROUP_NAME to googleApps.map { it.key }))
                .toSet()
            // Like any app added to a group: if it's later removed, it goes to the main list.
            googleApps.filter { it.key in grouped }.forEach { morePreferences.setInMore(it, false) }
        }
    }

    /** Moving an app into More takes it out of its group; moving it out puts it in the main list. */
    override fun setInMore(app: AppEntry, inMore: Boolean) {
        if (inMore) groupStore.removeMember(app.key)
        morePreferences.setInMore(app, inMore)
    }

    override fun createGroup(name: String, apps: List<AppEntry>) {
        // Apps leave More when grouped, so removing them from the group later puts them in the main list.
        apps.forEach { morePreferences.setInMore(it, false) }
        groupStore.create(name.trim(), apps.map { it.key })
    }

    override fun addToGroup(groupId: String, app: AppEntry) {
        morePreferences.setInMore(app, false)
        groupStore.addMember(groupId, app.key)
    }

    override fun removeFromGroup(app: AppEntry) = groupStore.removeMember(app.key)

    override fun renameGroup(groupId: String, name: String) = groupStore.rename(groupId, name.trim())

    override fun ungroup(groupId: String) = groupStore.ungroup(groupId)
}
