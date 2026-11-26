package com.example.sonet.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Nested-scroll hook for the app list: a downward drag that *starts* while the list is
 * already at the top, and travels at least [thresholdPx], calls [onThresholdReached] (for
 * haptics) and then [onTrigger] once when the finger lifts.
 * Dragging the list back up to the top and continuing does not count, so a fast
 * scroll-to-top doesn't accidentally open the shade.
 */
class SwipeDownToOpenShade(
    private val listState: LazyListState,
    private val thresholdPx: Float,
    private val onThresholdReached: () -> Unit,
    private val onTrigger: () -> Unit,
) : NestedScrollConnection {
    private var gestureActive = false
    private var startedAtTop = false
    private var pulled = 0f
    private var fired = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && !gestureActive) {
            gestureActive = true
            startedAtTop = !listState.canScrollBackward
            pulled = 0f
            fired = false
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !startedAtTop) return Offset.Zero
        if (available.y > 0f) {
            pulled += available.y
            if (!fired && pulled >= thresholdPx) {
                fired = true
                onThresholdReached()
            }
            // Consume it so the list doesn't show the stretch overscroll effect.
            return Offset(0f, available.y)
        }
        if (consumed.y < 0f) startedAtTop = false // user scrolled into the list instead
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // SystemUI ignores expand requests while the finger is still down, so open the
        // shade when the drag is released rather than the moment the threshold is crossed.
        if (gestureActive && fired) onTrigger()
        gestureActive = false
        fired = false
        return Velocity.Zero
    }
}
