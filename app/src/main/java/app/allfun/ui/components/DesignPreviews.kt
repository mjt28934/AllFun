package app.allfun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.data.settings.Settings
import app.allfun.data.settings.ThemeMode
import app.allfun.ui.theme.AllFunTheme

/** Shared component specimen, rendered under every supported palette and font scale. */
@Composable
private fun DesignSpecimen(dark: Boolean) {
    AllFunTheme(Settings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTopBar(title = "Library", meta = "18 titles · Recently updated") {
                TopBarAction(Icons.Outlined.Search, "Search", {})
            }
            SearchField(
                value = "",
                onValueChange = {},
                placeholder = "Search title, author or genre",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AllFunChip("All", true, {})
                AllFunChip("Books", false, {})
                AllFunChip("Manga", false, {})
            }
            AllFunTabs(
                labels = listOf("Installed", "Discover", "Updates", "Repositories"),
                selectedIndex = 1,
                onSelect = {},
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AllFunButton("Add a source", {}, variant = AllFunButtonVariant.PRIMARY)
                AllFunButton("Manage", {}, variant = AllFunButtonVariant.SECONDARY)
            }
            SettingsGroup(title = "EXPERIENCE") {
                SettingsRow("Appearance", subtitle = "Dark", value = "Dark", divider = true, onClick = {})
                SettingsRow("Library", subtitle = "Grid", value = "Grid", divider = false, onClick = {})
            }
            MediaCard(
                media = MediaEntity(
                    id = "preview",
                    kind = MediaKind.MANGA,
                    sourceId = "preview",
                    sourceKey = "preview",
                    title = "A sample title",
                    author = "Chapter 42",
                    totalUnits = 42,
                    inLibrary = true
                ),
                onClick = {},
                width = 132
            )
            EmptyState(
                icon = Icons.Outlined.Search,
                title = "No results",
                message = "Try another title or add a source.",
                actionLabel = "Add a source",
                onAction = {},
                illustration = EmptyIllustration.LOUPE
            )
        }
    }
}

@Preview(name = "Quiet Reading Room · dark", showBackground = true, widthDp = 390)
@Composable
private fun DesignDarkPreview() = DesignSpecimen(dark = true)

@Preview(name = "Quiet Reading Room · light", showBackground = true, widthDp = 390)
@Composable
private fun DesignLightPreview() = DesignSpecimen(dark = false)

@Preview(name = "Quiet Reading Room · dark · 1.3", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun DesignDarkLargePreview() = DesignSpecimen(dark = true)

@Preview(name = "Quiet Reading Room · light · 1.3", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun DesignLightLargePreview() = DesignSpecimen(dark = false)
