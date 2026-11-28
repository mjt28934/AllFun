# AllFun — Quiet Reading Room redesign

## 1. Screen inventory

**Recording availability:** no attached recording or app capture exists in the project workspace. This inventory follows the supplied timestamps and descriptions; the crop, frame changes, status bar, navigation inset, and dp density could not be independently verified. The supplied estimate is 411 × 860 dp, and the layout targets 360–412 dp widths.

| Time | Screen / state | Visual and verbal audit |
|---|---|---|
| 0:00–0:07 | Library, empty; search and category filters | The clipped Manga chip makes the row look broken; a generic centered empty state and “Browse sources” make the first screen feel like a setup wizard. |
| 0:03 | Browse, Books, empty | A BROWSE overline and five same-weight icons compete with the content; the source-empty explanation and tonal action are longer than the task. |
| 0:10, 0:33 | Settings, 10 rows | Flat rows repeat icon tiles, wrapped subtitles and dividers without grouped hierarchy or at-a-glance values. |
| 0:12 | Settings → About | The implementation stack is presented as a paragraph, so credits read like developer notes rather than useful About content. |
| 0:16 | Settings → App Updates | A tonal status card gives a routine version check the same visual weight as an error or action. |
| 0:21 | Extensions → Installed, empty | The shared icon-circle / centered-heading template repeats; its paragraph explains storage internals. |
| 0:24 | Extensions → Updates, empty | “Everything is up to date” is followed by a repository comparison log; the extra explanation and button dominate a quiet status. |
| 0:27 | Extensions → Discover, Movies & TV | A large Stremio card interrupts the list row rhythm; source counts and addon setup copy are verbose. |
| 0:30 | Extensions → Discover, Books & Novels | Dense results are undermined by mid-token truncation and install controls that do not share a consistent row hierarchy. |
| 0:36 | Browse, Books, empty | Repeats the same empty-state composition and says sources never become tabs, an implementation detail. |
| 0:39 | Search books & novels, empty | The outlined field is a second accent; the explanation about every source being queried is unnecessary and may wrap. |
| 0:42 | Filters & sorting sheet | The long facets disclaimer uses developer language; sort chips and explanatory copy obscure the choices. |
| 0:45 | Extensions → Repositories, empty | The repository tab clips; the empty state is generic and describes setup mechanics at length. |
| 0:48 | Browse “+” popup | The popup covers the empty-state copy, and four descriptions wrap into an uneven menu. |

### Confirmed / corrected audit

The supplied audit is consistent with the project’s described screens. I could confirm the relevant old strings and remaining Material input controls in source, but could not verify the exact recorded rendering. In the repository, the top-level bottom bar already uses a custom five-item layout, and Library already uses custom filters. The current implementation has six distinct line illustrations (shelf, plug, puzzle, check, cards and loupe), but the three category empty states shared one illustration before this pass. Settings was one ungrouped group before this pass. Font files are bundled locally, so the UI does not depend on a network font download.

## 2. Design thesis and implementation audit

Quiet Reading Room treats the interface as a calm contents page: warm dark surfaces, editorial serif screen titles, compact sans-serif controls, mono technical metadata, hairline structure and a single amber action colour. Covers and source results carry visual interest. Empty states start at the left, name the missing thing in plain language, and offer one next action. Movies, Books, Manga, Library and Settings remain the main destinations; extensions stay infrastructure rather than becoming a bottom-nav destination.

The existing tree already contained most shared design primitives and theme files. This pass finished prominent copy and screen-level gaps while preserving repositories, media, navigation and settings logic. The recording-derived screen audit is therefore not a frame-verified visual QA report.

## 3. Token sheet

### Colour

All values are sRGB hex. Contrast is WCAG 2.2 relative-luminance contrast (foreground/background). Ratios below cover the text roles actually used over each base surface; decorative marks and non-text borders are not text.

| Role | Dark | Light |
|---|---|---|
| bg | `#0F0E0C` | `#F6F2EA` |
| surface1 | `#171511` | `#FBF8F2` |
| surface2 | `#1F1C17` | `#EFE9DE` |
| surface3 | `#292520` | `#FFFFFF` |
| hairline | `#2E2A24` | `#DDD5C6` |
| outlineStrong | `#433D34` | `#B9AF9C` |
| textPrimary | `#EDE7DB` | `#1E1B16` |
| textSecondary | `#ABA397` | `#5E574B` |
| textTertiary | `#918979` | `#6C6455` |
| accent | `#E3A04A` | `#8A5209` |
| onAccent | `#1A1206` | `#FFF8EC` |
| accentContainer | `#3A2A14` | `#F2DEBF` |
| danger | `#E0715F` | `#B3402F` |
| success | `#8FB573` | `#4F6B39` |
| Movies / Books / Manga marks | `#86A8C4` / `#A9B58A` / `#C99A86` | `#456B8A` / `#5E6B3F` / `#8A5B45` |

| Text pair | Dark ratios on bg / surface1 / surface2 / surface3 | Light ratios on bg / surface1 / surface2 / surface3 |
|---|---:|---:|
| Primary | 15.67 / 14.81 / 13.79 / 12.36 | 15.37 / 16.19 / 14.20 / 17.16 |
| Secondary | 7.73 / 7.31 / 6.81 / 6.10 | 6.40 / 6.74 / 5.91 / 7.14 |
| Tertiary | 5.57 / 5.26 / 4.90 / 4.39 | 5.24 / 5.52 / 4.84 / 5.85 |
| Accent | 8.63 / 8.16 / 7.60 / 6.81 | 5.71 / 6.02 / 5.28 / 6.38 |
| Danger | 6.15 / 5.81 / 5.41 / 4.85 | 5.10 / 5.37 / 4.71 / 5.69 |
| onAccent on accent | 8.29 | 6.04 |
| Accent on accentContainer | 6.18 | 4.85 |

Light success is `#4F6B39` in code (ratio 5.38 on bg, 4.97 on surface2); dark success is `#8FB573` (ratio 8.29 on bg, 7.30 on surface2). Tertiary must not be placed on dark surface3: that pair is 4.39:1. The dark palette avoids that use. The supplied target says light accent/container should be 6.2:1; the listed hexes compute to 4.85:1, so the exact supplied pair does not meet that claim, though it remains above 4.5:1 for normal text.

### Type, shape, spacing and motion

| Token | Value |
|---|---|
| Screen title | Newsreader Medium, 32/38 sp, −0.3 sp |
| Sheet title | Newsreader Medium, 22/28 sp |
| Row title | Hanken Grotesk SemiBold, 16/22 sp |
| Body / small | Hanken Grotesk Regular, 15/22 and 13/18 sp |
| Label / nav / overline | Hanken Medium, 13/16; 11/14; 11/14 sp (+0.2 / +0.8 tracking) |
| Technical metadata | IBM Plex Mono Regular, 12/16 sp |
| Radius | Cover 4; badge 4; chip 8; button 10; field/group 12; sheet top 20 dp |
| Spacing | 4, 8, 12, 16, 20, 24, 32, 48 dp; screen inset 20 dp |
| Motion | 180–280 ms, decelerating enters, no overshoot; system animator scale 0 disables transitions |

Fonts are bundled in `app/src/main/res/font/`. Dark and light palettes are fixed by default; dynamic colour is opt-in in Appearance.

## 4. Compose theme and components

The semantic palette, Material role mapping, typography, radii, and local fonts live in `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, and `Theme.kt`. `ui/components/Design.kt` contains the shared top bar, search field, custom chips and tabs, buttons, grouped settings rows, and sheet. `Common.kt` and `Illustrations.kt` supply cover cards and distinct line-art empty states. `AllFunRoot.kt` provides the custom bottom bar.

`DesignPreviews.kt` provides a shared component specimen in dark and light themes at 1.0 and 1.3 font scale. It covers the top bar, search, chips, tabs, buttons, settings groups, a populated cover card and an empty state. Device screenshot review was unavailable because no emulator or attached video is present. The shared preview specimen covers the core components and four palette/font-scale combinations; it does not provide an individual preview for every component and screen.

## 5. Screens

Library, Browse, Search, Extensions, Settings, About and App Updates use the shared theme and components. The “+” source chooser remains a bottom sheet. Filters remain a sheet. Library keeps its populated cover grid and list mode. Browse retains source results and loading/error behavior. Extensions retains the current extra Disabled tab because it exposes existing installed-extension controls; its tabs scroll instead of clipping. Screen previews are not included.

## 6. String rewrite table

| Old | New |
|---|---|
| Nothing here yet / Titles you save, read or download land on this shelf. Install an extension to start browsing. / Browse sources | Your shelf is empty / Saved and downloaded titles appear here. / Add a source |
| No books sources yet / Install a source or add-on and its titles will appear here automatically. Sources never become separate tabs. / Manage extensions | No book sources / Install a novel plugin to browse and read here. / Add a source |
| No manga sources yet / … / Manage extensions | No manga sources / Install a manga extension to browse and read here. / Add a source |
| No movie sources yet / … / Manage extensions | No video sources / Add a Stremio addon or Jellyfin server to browse here. / Add a source |
| Facets come from metadata your sources actually returned. Titles without metadata are not guessed. | Filters depend on what your sources provide. |
| Everything is up to date / AllFun compared 0 installed extensions with 2 repositories. Nothing newer was published. | Up to date / No updates in 2 repositories. |
| No extensions installed / Discover manga extensions and novel plugins from real repositories, or connect a Stremio addon or Jellyfin server. Installed files stay in AllFun’s private storage. | No extensions installed / Find extensions in Discover, or add a Stremio addon or Jellyfin server. / Installed files stay in AllFun’s private storage. |
| No repositories configured / Add the address of a Mihon/Komikku manga repository or an LNReader plugin index to install extensions without leaving AllFun. / Repositories | No repositories / Add a Mihon/Komikku repository or an LNReader plugin index. / Add repository |
| Stremio add-on / Play films and episodes from a manifest URL | Stremio addon / Films and series from a manifest URL |
| Install a Tachiyomi or Komikku .apk from this device | Install a .apk from this device |
| Install an LNReader .js plugin file | Install a .js plugin file |
| No installed source returned “{query}”. Search only reports real responses; nothing is simulated. | No installed source found results for “{query}”. |
| You’re on the latest version. / Installed version {version} / Check again | Up to date · {version} / Last checked · {time} / Check again |
| One library for manga, light novels and video. Kotlin and Jetpack Compose, Room for the shelf, Media3 for playback, a WebView novel reader, and a source layer that runs Jellyfin, Stremio addons, Tachiyomi/Komikku extensions and LNReader plugins. | One library for manga, light novels and video. / Built with: Kotlin · Jetpack Compose · Room · Media3 · WebView reader · Jellyfin · Stremio addons · Tachiyomi/Komikku extensions · LNReader plugins |
| Install Stremio add-on | Add addon |
| Paste an addon URL to add its catalogs and streams. Only the addon itself is contacted; nothing is uploaded. | Paste an addon URL. Only the addon is contacted. |
| Filters & sorting | Sort & filter |

## 7. Changelog

- Kept Movies, Books, Manga, Library and Settings as the main navigation destinations.
- Kept existing source, search, filtering, library, update and settings behavior.
- Added Settings section headers for Experience, Content and System.
- Replaced developer-facing empty-state text in Library, Browse, Search and Extensions.
- Converted About’s stack paragraph into a short description plus “Built with” and “Notes” sections.
- Replaced the up-to-date update card with a compact status, version, last-check line and text action. Release and error states retain their existing controls.
- Replaced the Discover Stremio tonal panel with a neutral list row and compact Add addon action.
- Changed category empties to use the plug illustration, following the supplied per-screen illustration list.
- Kept the Disabled extension tab as a fifth, scrollable tab because removing it would hide existing behavior.
- Kept form-style Material `OutlinedTextField` controls in URL/password and chapter-jump dialogs. They are not the screen search control; screen search uses the custom `SearchField`.

## 8. Self-check

| Check | Result |
|---|---|
| Squint test | Not frame-verified: the recording was unavailable. Static layout has a screen title and one primary state/action, but the test needs emulator screenshots. |
| Accent count | Palette and changed screens follow the two-use rule at rest; no screenshot audit was possible. |
| 360 dp / font scale 1.3 clipping | Not verified on device. Search field is single-line with ellipsis; chip labels are single-line; tabs scroll. |
| Default-component hunt | No `NavigationBar`, `AssistChip`, or `Card(` usages found in app UI source. `FilledTonalButton` is imported in CategoryScreen but not used. `OutlinedTextField` remains only in form dialogs / chapter picker; settings had an unused import, removed in this pass. |
| Contrast | Ratios listed above were computed from the supplied hex values. All listed text pairs meet 4.5:1 except dark tertiary on surface3 (4.39:1); that pairing is prohibited by the design rule. Borders and icon strokes still need rendered non-text contrast inspection. |
| Six empty states | Six separate Canvas illustrations exist. The category source-empty illustration is the plug; strings differ by screen. Search has several context-specific empty messages. |
| Copy test | The principal library, category, extension, Stremio, update and About strings above are rewritten. A complete repository-wide copy review remains outstanding. |
