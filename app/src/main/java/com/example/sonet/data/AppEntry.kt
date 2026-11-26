package com.example.sonet.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    /** Stable identifier, used for list keys and the More overrides. */
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: ImageBitmap,
    /** Whether the heuristic puts this app in More when the user hasn't decided. */
    val inMoreByDefault: Boolean,
    /** False for preinstalled system apps, which can't be uninstalled. */
    val canUninstall: Boolean,
) {
    val packageName: String get() = component.packageName
}

/** Apps that have their own floating button, and are therefore left out of the list. */
data class ButtonApps(
    val cameraPackage: String? = null,
    val walletPackage: String? = null,
) {
    val packages: Set<String> get() = setOfNotNull(cameraPackage, walletPackage)
}

data class InstalledApps(
    val apps: List<AppEntry> = emptyList(),
    val buttons: ButtonApps = ButtonApps(),
)

/** A group with its installed apps, alphabetical. */
data class GroupSection(
    val group: AppGroup,
    val apps: List<AppEntry>,
)

/** What the home screen shows, top to bottom: ungrouped apps, groups, then More. */
data class AppSections(
    val main: List<AppEntry> = emptyList(),
    val groups: List<GroupSection> = emptyList(),
    val more: List<AppEntry> = emptyList(),
    val buttons: ButtonApps = ButtonApps(),
) {
    fun group(id: String): GroupSection? = groups.firstOrNull { it.group.id == id }
}

/**
 * Splits apps into the main list, the user's groups and the More folder.
 * - Apps with a floating button ([ButtonApps]) aren't listed at all.
 * - Group membership wins over everything else.
 * - [moreOverrides] maps an app key to the user's explicit More choice (true = in More);
 *   apps without one fall back to the heuristic.
 * - Groups whose apps are all uninstalled are hidden (kept, in case they come back).
 * App order is preserved, so sort [apps] before calling; groups are ordered by [nameOrder].
 */
fun partitionApps(
    installed: InstalledApps,
    moreOverrides: Map<String, Boolean>,
    groups: List<AppGroup>,
    nameOrder: Comparator<String>,
): AppSections {
    val buttonPackages = installed.buttons.packages
    val apps = installed.apps.filter { it.packageName !in buttonPackages }
    val groupOf = buildMap { groups.forEach { g -> g.members.forEach { put(it, g.id) } } }
    val (grouped, ungrouped) = apps.partition { it.key in groupOf }
    val appsByGroup = grouped.groupBy { groupOf.getValue(it.key) }
    val sections = groups
        .mapNotNull { g -> appsByGroup[g.id]?.let { GroupSection(g, it) } }
        .sortedWith(compareBy(nameOrder) { it.group.name })
    val (more, main) = ungrouped.partition { moreOverrides[it.key] ?: it.inMoreByDefault }
    return AppSections(main = main, groups = sections, more = more, buttons = installed.buttons)
}
