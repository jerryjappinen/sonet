package com.example.sonet.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.sonet.data.AppEntry

/** Something an app can be dropped on in the main list. */
sealed interface DropTarget {
    data class App(val key: String) : DropTarget
    data class Group(val id: String) : DropTarget
    data object More : DropTarget
}

/**
 * Drag-and-drop state for the main list. Rows register their on-screen bounds with
 * [dropTarget]; while an app is dragged, [hovered] is the target under the finger.
 */
@Stable
class DragState(private val moveThresholdPx: Float) {
    var dragging by mutableStateOf<AppEntry?>(null)
        private set
    /** Finger position in root (screen) coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set
    /** True once the finger moved far enough for this to count as a drag, not a press. */
    var moved by mutableStateOf(false)
        private set
    var hovered by mutableStateOf<DropTarget?>(null)
        private set

    private var start = Offset.Zero
    private val bounds = HashMap<DropTarget, Rect>()

    fun start(app: AppEntry, position: Offset) {
        dragging = app
        start = position
        pointer = position
        moved = false
        hovered = null
    }

    fun move(position: Offset) {
        pointer = position
        if (!moved && (position - start).getDistance() > moveThresholdPx) moved = true
        updateHover()
    }

    /** Re-checks the target under the finger, e.g. after the list auto-scrolls. */
    fun updateHover() {
        val self = dragging?.let { DropTarget.App(it.key) }
        hovered = if (!moved) null else bounds.entries
            .firstOrNull { (target, rect) -> target != self && rect.contains(pointer) }
            ?.key
    }

    /** Ends the drag and returns where the app was dropped, or null if it wasn't really dragged. */
    fun finish(): Pair<AppEntry, DropTarget?>? {
        val result = dragging?.takeIf { moved }?.let { it to hovered }
        cancel()
        return result
    }

    fun cancel() {
        dragging = null
        moved = false
        hovered = null
    }

    internal fun register(target: DropTarget, rect: Rect) { bounds[target] = rect }
    internal fun unregister(target: DropTarget) { bounds.remove(target) }
}

/** Marks a row as a drop target, tracking its position as the list scrolls. */
@Composable
fun Modifier.dropTarget(state: DragState, target: DropTarget): Modifier {
    DisposableEffect(state, target) { onDispose { state.unregister(target) } }
    return onGloballyPositioned { state.register(target, it.boundsInRoot()) }
}

/**
 * Long press for rows that only have a menu. [onLongPress] gives feedback while held;
 * [onRelease] fires when the finger lifts, which is when the menu should open: a popup
 * window appearing while the finger is still down stops touch events reaching the
 * main window and leads to an ANR.
 */
@Composable
fun Modifier.longPress(key: Any, onLongPress: () -> Unit, onRelease: () -> Unit): Modifier =
    longPressDrag(key, onLongPress = { onLongPress() }, onDrag = {}, onRelease = onRelease, onCancel = {})

/**
 * Long press then (optionally) drag. [onLongPress] fires when the press is recognised and
 * [onDrag] for each move, both with root coordinates; [onRelease] or [onCancel] ends it.
 * All events after the long press are consumed, so a plain tap handler on the same row
 * (placed *before* this modifier) doesn't fire on release.
 */
@Composable
fun Modifier.longPressDrag(
    key: Any,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val holder = remember { CoordinatesHolder() }

    return onGloballyPositioned { holder.coordinates = it }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                longPress.consume()
                currentOnLongPress(holder.toRoot(longPress.position))
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                        event.changes.forEach { it.consume() }
                        if (change.changedToUpIgnoreConsumed()) {
                            released = true
                            break
                        }
                        currentOnDrag(holder.toRoot(change.position))
                    }
                } finally {
                    if (released) currentOnRelease() else currentOnCancel()
                }
            }
        }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
    fun toRoot(local: Offset): Offset =
        coordinates?.takeIf { it.isAttached }?.localToRoot(local) ?: local
}
