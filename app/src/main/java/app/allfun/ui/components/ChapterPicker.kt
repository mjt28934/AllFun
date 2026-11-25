package app.allfun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.allfun.data.db.EpisodeEntity
import app.allfun.ui.glass.AFDivider
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTextField
import app.allfun.ui.glass.GlassTokens
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.rememberHaptics

/**
 * Chapter switcher for long serials. A menu with hundreds of entries is unusable, so this
 * is a searchable, scrollable glass sheet that opens at the current chapter.
 */
@Composable
fun ChapterPickerDialog(
    chapters: List<EpisodeEntity>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(chapters, query) {
        if (query.isBlank()) {
            chapters.mapIndexed { index, chapter -> index to chapter }
        } else {
            chapters.mapIndexedNotNull { index, chapter ->
                if (chapter.title.contains(query, ignoreCase = true)) index to chapter else null
            }
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(max = 620.dp),
                tier = GlassTier.FROSTED,
                shape = RoundedCornerShape(GlassTokens.rPanel),
                shadow = true
            ) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            AFText(
                                text = "Chapters",
                                style = AFTheme.type.title,
                                color = AFTheme.colors.textHi
                            )
                            AFText(
                                text = "${chapters.size} " +
                                    if (chapters.size == 1) "chapter" else "chapters",
                                style = AFTheme.type.caption,
                                color = AFTheme.colors.textLo
                            )
                        }
                        AFIconButton(
                            icon = AFIcons.Close,
                            contentDescription = "Close chapter list",
                            onClick = onDismiss
                        )
                    }
                    GlassTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search chapters",
                        leading = AFIcons.Search,
                        trailing = {
                            if (query.isNotEmpty()) {
                                AFIconButton(
                                    icon = AFIcons.Close,
                                    contentDescription = "Clear search",
                                    onClick = { query = "" },
                                    visualSize = 32.dp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                    if (rows.isEmpty()) {
                        AFText(
                            text = "No chapter matches \"$query\".",
                            style = AFTheme.type.body,
                            color = AFTheme.colors.textLo,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.heightIn(max = 460.dp)
                        ) {
                            items(rows, key = { it.second.id }) { (index, chapter) ->
                                ChapterRow(
                                    title = chapter.title,
                                    selected = index == selectedIndex,
                                    onClick = {
                                        onSelect(index)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AFTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accentWash else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Open $title"
            ) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AFText(
            text = title,
            style = AFTheme.type.body,
            color = if (selected) colors.accent else colors.textMid,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(Modifier.width(10.dp))
            AFIcon(
                icon = AFIcons.Check,
                contentDescription = "Current chapter",
                tint = colors.accent,
                size = 20.dp
            )
        }
    }
    AFDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AFTheme.colors.hairline)
}
