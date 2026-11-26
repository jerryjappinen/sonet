package com.example.sonet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonet.data.AppEntry
import com.example.sonet.data.AppRepository

val IconSize = AppRepository.ICON_SIZE_DP.dp
val RowHorizontalPadding = 24.dp

/** Text colors for a row: on the wallpaper they come from [WallpaperContrast], in popups from the theme. */
@Immutable
data class RowStyle(val text: Color, val secondaryText: Color, val shadow: Shadow?)

fun WallpaperContrast.rowStyle() = RowStyle(text, secondaryText, textShadow)

@Composable
fun surfaceRowStyle() = RowStyle(
    text = MaterialTheme.colorScheme.onSurface,
    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
    shadow = null,
)

/**
 * One line in a list: [leading] icon, [title], optional [count]. [gestures] goes *after* the
 * click handler so a long press can stop the tap from firing. [menu] is the long-press menu.
 */
@Composable
fun ListRow(
    title: String,
    style: RowStyle,
    onClick: () -> Unit,
    gestures: Modifier,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    horizontalPadding: Dp = RowHorizontalPadding,
) {
    val highlight = if (highlighted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else Color.Transparent
    Box(modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(highlight)
                .clickable(onClick = onClick)
                .then(gestures)
                .padding(horizontal = horizontalPadding - 8.dp, vertical = 10.dp)
                .alpha(if (dimmed) 0.35f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = style.shadow),
                color = style.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = style.shadow),
                    color = style.secondaryText,
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = onDismissMenu,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = menu,
        )
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp = IconSize) {
    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(size))
}

/** A folder-style tile showing up to four of the folder's icons in a 2×2 grid. */
@Composable
fun FolderIcon(apps: List<AppEntry>) {
    val mini = (IconSize - 8.dp) / 2
    Box(
        Modifier
            .size(IconSize)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(3.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { AppIcon(it, mini) }
                }
            }
        }
    }
}
