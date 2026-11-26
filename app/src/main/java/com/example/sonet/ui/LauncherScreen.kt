package com.example.sonet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.sonet.R
import com.example.sonet.data.AppEntry
import com.example.sonet.data.AppSections
import com.example.sonet.system.expandNotificationShade
import com.example.sonet.system.launchApp
import com.example.sonet.system.openAppInfo
import com.example.sonet.system.openCamera
import com.example.sonet.system.openWallet
import com.example.sonet.system.uninstallApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FabAreaHeight = 176.dp // keeps the last rows clear of the floating buttons
private val DragIconSize = 48.dp
private val AutoScrollEdge = 96.dp
private const val MORE_FOLDER = "more"

/** Everything the home screen can change. Implemented by the ViewModel. */
interface LauncherActions {
    fun setInMore(app: AppEntry, inMore: Boolean)
    fun createGroup(name: String, apps: List<AppEntry>)
    fun addToGroup(groupId: String, app: AppEntry)
    fun removeFromGroup(app: AppEntry)
    fun renameGroup(groupId: String, name: String)
    fun ungroup(groupId: String)
}

private sealed interface NameDialog {
    data class Create(val apps: List<AppEntry>) : NameDialog
    data class Rename(val groupId: String, val currentName: String) : NameDialog
}

@Composable
fun LauncherScreen(
    sections: AppSections,
    homePressed: Flow<Unit>,
    actions: LauncherActions,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val contrast = rememberWallpaperContrast()
    val wallpaperStyle = contrast.rowStyle()
    val drag = remember { DragState(moveThresholdPx = with(density) { 16.dp.toPx() }) }

    // Group id or MORE_FOLDER for the open popup.
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    // Which main-list row ("app:<key>" / "group:<id>") has its long-press menu open.
    var openMenu by remember { mutableStateOf<String?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var groupPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    var screenHeight by remember { mutableIntStateOf(0) }

    val swipeDown = remember(listState) {
        SwipeDownToOpenShade(
            listState = listState,
            thresholdPx = with(density) { 48.dp.toPx() },
            onThresholdReached = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
            onTrigger = { expandNotificationShade(context) },
        )
    }

    fun resetToTop() {
        openFolder = null
        openMenu = null
        nameDialog = null
        groupPickerFor = null
        drag.cancel()
        scope.launch { listState.animateScrollToItem(0) }
    }

    fun onDrop(app: AppEntry, target: DropTarget?) {
        when (target) {
            is DropTarget.App -> sections.main.firstOrNull { it.key == target.key }
                ?.let { nameDialog = NameDialog.Create(listOf(app, it)) }
            is DropTarget.Group -> actions.addToGroup(target.id, app)
            DropTarget.More -> actions.setInMore(app, true)
            null -> Unit
        }
    }

    // Home while already home, or Back: close everything and return to the top.
    LaunchedEffect(homePressed) { homePressed.collect { resetToTop() } }
    BackHandler { resetToTop() }

    // A tick each time the dragged app moves over a new target.
    LaunchedEffect(drag.hovered) {
        if (drag.hovered != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    // While dragging near the top or bottom edge, scroll the list so far-away targets are reachable.
    LaunchedEffect(drag.moved) {
        if (!drag.moved) return@LaunchedEffect
        val edge = with(density) { AutoScrollEdge.toPx() }
        while (isActive && drag.moved) {
            val y = drag.pointer.y
            val speed = when {
                y < edge -> -(edge - y) / edge
                y > screenHeight - edge -> (y - (screenHeight - edge)) / edge
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed * 24f)
                drag.updateHover()
            }
            withFrameNanos { }
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // Light scrim over the wallpaper so the list stays readable on busy images.
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screenHeight = it.height }
            .background(contrast.scrim)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(swipeDown),
            contentPadding = PaddingValues(
                top = statusBarPadding.calculateTopPadding() + 32.dp,
                bottom = navBarPadding.calculateBottomPadding() + FabAreaHeight,
            ),
        ) {
            items(sections.main, key = { "app:" + it.key }) { app ->
                val isDragged = drag.dragging?.key == app.key
                // Keep the dragged row composed even if auto-scroll moves it off screen,
                // otherwise its gesture would be cancelled mid-drag.
                val pinnable = LocalPinnableContainer.current
                DisposableEffect(isDragged) {
                    val handle = if (isDragged) pinnable?.pin() else null
                    onDispose { handle?.release() }
                }
                val menuKey = "app:" + app.key
                ListRow(
                    title = app.label,
                    style = wallpaperStyle,
                    onClick = { launchApp(context, app) },
                    gestures = Modifier.longPressDrag(
                        key = app.key,
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.start(app, it)
                        },
                        onDrag = { drag.move(it) },
                        onRelease = {
                            // Dragged: drop it. Held still: open the menu (only now, see longPress).
                            val dropped = drag.finish()
                            if (dropped != null) onDrop(dropped.first, dropped.second) else openMenu = menuKey
                        },
                        onCancel = { drag.cancel() },
                    ),
                    menuOpen = openMenu == menuKey,
                    onDismissMenu = { openMenu = null },
                    menu = {
                        MenuItem(R.string.add_to_group) { openMenu = null; groupPickerFor = app }
                        MenuItem(R.string.move_to_more) { openMenu = null; actions.setInMore(app, true) }
                        AppInfoAndUninstall(app) { openMenu = null }
                    },
                    leading = { AppIcon(app) },
                    // Highlighted while held (before moving) and when another app hovers over it.
                    highlighted = drag.hovered == DropTarget.App(app.key) || (isDragged && !drag.moved),
                    dimmed = isDragged && drag.moved,
                    modifier = Modifier.dropTarget(drag, DropTarget.App(app.key)),
                )
            }

            items(sections.groups, key = { "group:" + it.group.id }) { section ->
                val group = section.group
                val menuKey = "group:" + group.id
                ListRow(
                    title = group.name,
                    style = wallpaperStyle,
                    count = section.apps.size,
                    onClick = { openFolder = group.id },
                    gestures = Modifier.longPress(
                        key = group.id,
                        onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onRelease = { openMenu = menuKey },
                    ),
                    menuOpen = openMenu == menuKey,
                    onDismissMenu = { openMenu = null },
                    menu = {
                        MenuItem(R.string.rename) {
                            openMenu = null
                            nameDialog = NameDialog.Rename(group.id, group.name)
                        }
                        MenuItem(R.string.ungroup) { openMenu = null; actions.ungroup(group.id) }
                    },
                    leading = { FolderIcon(section.apps) },
                    highlighted = drag.hovered == DropTarget.Group(group.id),
                    modifier = Modifier.dropTarget(drag, DropTarget.Group(group.id)),
                )
            }

            if (sections.more.isNotEmpty()) {
                item(key = "more-folder") {
                    ListRow(
                        title = stringResource(R.string.more),
                        style = wallpaperStyle,
                        count = sections.more.size,
                        onClick = { openFolder = MORE_FOLDER },
                        gestures = Modifier,
                        menuOpen = false,
                        onDismissMenu = {},
                        menu = {},
                        leading = { FolderIcon(sections.more) },
                        highlighted = drag.hovered == DropTarget.More,
                        modifier = Modifier.dropTarget(drag, DropTarget.More),
                    )
                }
            }
        }

        // Fade list text out under the status bar so it doesn't collide with status icons.
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarPadding.calculateTopPadding() + 24.dp)
                .background(
                    Brush.verticalGradient(
                        0f to contrast.scrim.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = RowHorizontalPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Each button only appears when its app exists; that app is then left out of the list.
            sections.buttons.walletPackage?.let { wallet ->
                RoundButton(R.drawable.ic_wallet, stringResource(R.string.open_wallet)) { openWallet(context, wallet) }
            }
            if (sections.buttons.cameraPackage != null) {
                RoundButton(R.drawable.ic_camera, stringResource(R.string.open_camera)) { openCamera(context) }
            }
        }

        // The icon following the finger while dragging.
        val dragged = drag.dragging
        if (dragged != null && drag.moved) {
            val half = with(density) { (DragIconSize / 2).toPx() }
            Box(
                Modifier
                    .offset { IntOffset((drag.pointer.x - half).roundToInt(), (drag.pointer.y - half * 2.5f).roundToInt()) }
                    .shadow(8.dp, CircleShape)
            ) {
                AppIcon(dragged, DragIconSize)
            }
        }
    }

    FolderPopupHost(
        sections = sections,
        openFolder = openFolder,
        onClose = { openFolder = null },
        actions = actions,
        onPickGroup = { groupPickerFor = it },
    )

    groupPickerFor?.let { app ->
        val currentGroup = sections.groups.firstOrNull { s -> s.apps.any { it.key == app.key } }?.group?.id
        GroupPickerDialog(
            groups = sections.groups.filter { it.group.id != currentGroup },
            onPick = { id -> groupPickerFor = null; actions.addToGroup(id, app) },
            onNewGroup = { groupPickerFor = null; nameDialog = NameDialog.Create(listOf(app)) },
            onDismiss = { groupPickerFor = null },
        )
    }

    when (val dialog = nameDialog) {
        is NameDialog.Create -> GroupNameDialog(
            title = stringResource(R.string.new_group_title),
            initialName = "",
            onConfirm = { name -> nameDialog = null; actions.createGroup(name, dialog.apps) },
            onDismiss = { nameDialog = null },
        )
        is NameDialog.Rename -> GroupNameDialog(
            title = stringResource(R.string.rename_group_title),
            initialName = dialog.currentName,
            onConfirm = { name -> nameDialog = null; actions.renameGroup(dialog.groupId, name) },
            onDismiss = { nameDialog = null },
        )
        null -> Unit
    }
}

/** Shows the open group or More as a popup; closes itself if the folder disappears (e.g. emptied). */
@Composable
private fun FolderPopupHost(
    sections: AppSections,
    openFolder: String?,
    onClose: () -> Unit,
    actions: LauncherActions,
    onPickGroup: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isMore = openFolder == MORE_FOLDER
    val group = openFolder?.takeUnless { isMore }?.let(sections::group)
    val apps = if (isMore) sections.more else group?.apps.orEmpty()

    LaunchedEffect(openFolder, apps.isEmpty()) {
        if (openFolder != null && apps.isEmpty()) onClose()
    }
    if (openFolder == null || apps.isEmpty()) return

    var openMenu by remember(openFolder) { mutableStateOf<String?>(null) }
    FolderPopup(
        title = if (isMore) stringResource(R.string.more) else group!!.group.name,
        apps = apps,
        onDismiss = onClose,
    ) { app ->
        ListRow(
            title = app.label,
            style = surfaceRowStyle(),
            onClick = { onClose(); launchApp(context, app) },
            gestures = Modifier.longPress(
                key = app.key,
                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onRelease = { openMenu = app.key },
            ),
            menuOpen = openMenu == app.key,
            onDismissMenu = { openMenu = null },
            menu = {
                if (isMore) {
                    MenuItem(R.string.add_to_group) { openMenu = null; onPickGroup(app) }
                    MenuItem(R.string.move_out_of_more) { openMenu = null; actions.setInMore(app, false) }
                } else {
                    MenuItem(R.string.move_to_group) { openMenu = null; onPickGroup(app) }
                    MenuItem(R.string.remove_from_group) { openMenu = null; actions.removeFromGroup(app) }
                }
                AppInfoAndUninstall(app) { openMenu = null }
            },
            leading = { AppIcon(app) },
            horizontalPadding = 16.dp,
        )
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun AppInfoAndUninstall(app: AppEntry, closeMenu: () -> Unit) {
    val context = LocalContext.current
    MenuItem(R.string.app_info) { closeMenu(); openAppInfo(context, app) }
    if (app.canUninstall) {
        MenuItem(R.string.uninstall) { closeMenu(); uninstallApp(context, app) }
    }
}

@Composable
private fun RoundButton(iconRes: Int, description: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = description)
    }
}
