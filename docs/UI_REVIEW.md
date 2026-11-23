# UI review — AllFun 0.9.x

This is a working review, not a redesign pitch. It records what was inspected, what
changed in this pass, and the backlog that is still worth doing. Every claim about the
current app points at a screen or file in this repository; items marked **proposed** have
not been built yet.

## Method

1. Walked every destination: Movies, Books, Manga, Library, Global search, detail pages,
   manga reader, novel reader, Extensions, Downloads, Settings, Updates.
2. Checked each screen against six questions:
   - Is the primary action obvious in the first screenful?
   - Is the layout top-to-bottom (no hidden sideways content the user must discover)?
   - Are loading, empty, error and offline states honest and distinct?
   - Can every control be reached with one thumb, a keyboard and a screen reader?
   - Does it survive large font scale, dark/light and dynamic colour?
   - Does it do work on the main thread or allocate inside composition?
3. Compared reader behaviour with Komikku/Tachiyomi conventions (tap zones, chrome,
   volume keys, per-chapter progress).
4. Kept what already worked: the 0.9.0 detail pages, the bottom bar, the download queue
   and the 0.7.3–0.8.1 behaviour.

## Reference-driven pass

The second pass took patterns from the apps below. Only what fits AllFun's structure was
adopted; everything else is listed as backlog rather than imitated.

| Reference | Pattern taken | Where |
| --- | --- | --- |
| namida, Aves, Chocola, Symphonica | A compact "Continue" strip of in-progress titles at the top of the library, with progress on the artwork instead of a separate queue screen | `LibraryScreen.kt`, `LibraryViewModel.kt` |
| komikku | The end of a chapter is an explicit boundary with the next chapter one tap away, not a silent stop at the last image | `MangaReaderScreen.kt` (`ChapterEndCard`) |
| mpvEx, mpvFlux, Flux | Playback and reading are foreground activities: the screen stays awake while the reader is open, and chrome stays out of the content | `MangaReaderScreen.kt`, `NovelReaderScreen.kt` |
| MaterialFiles | Dense, honest rows: catalogue cards say "Offline" when files are actually on the device, instead of leaving the user to guess | `ContentCards.kt`, `CategoryViewModel.downloadedMedia` |
| nowinandroid, Jetchat, compose-samples | State-driven screens with explicit loading / empty / error states, and one source of truth per surface | existing screens, unchanged conventions |
| WallFlow, Syncara, WizeStream | Material 3 colour roles and dynamic colour carried through every new surface, including reader chrome and cards | theme usage in the new components |

### Added in this pass

- **Continue row in the Library.** Started-but-unfinished titles, newest first, capped at
  eight, respecting the selected content group. It is the first item of the shelf, so it
  scrolls away with the grid; it is hidden while searching and the search field sits above
  it, so the field does not move when the first character hides the row.
- **End-of-chapter card** at the bottom of a manga chapter in vertical mode, with the
  chapter title, "Chapter N of M", Previous and Next chapter. It clears the system
  navigation inset, so the buttons are not under the gesture bar.
- **Keep screen on** while either reader is open.
- **"Offline" badge** on catalogue and search cards for titles with completed downloads.

### Deliberately not copied

- **Two-pane adaptive detail** (nowinandroid/Jetchat): worth doing, but it changes
  navigation and both detail layouts, so it belongs in its own change.
- **Player overlay redesign** (mpvEx/mpvFlux): the video player already has a working
  control surface; re-skinning it is a separate, testable piece of work.
- **Pull-to-refresh on the catalogue**: the refresh action and its progress line already
  exist; adding a second gesture for the same command needs device testing first.

## What changed in this pass

### Category screens (Movies, Books, Manga)

- **Vertical grid replaces horizontal shelf carousels.** All provider results are in one
  adaptive `LazyVerticalGrid` (`CategoryScreen.kt`), so the page is scrolled in one
  direction and every title is reachable without sideways swipes.
- **Popular and Latest are first-class sorts.** `CategorySort` now offers `Popular`,
  `Latest`, `Title A–Z` and `By source`. Popular/Latest use the shelves the providers
  actually returned (`POPULAR`, `TRENDING`, `LATEST`, `RECENT`, `OTHER`), never a guessed
  score. The ordering is computed in `CategoryViewModel.UiState.sorted` and covered by
  `CategorySortTest`.
- **The sort row is visible, not buried.** A chip row under the header carries all four
  orders in short labels, so none of them falls off the screen edge; the result count,
  source count and filter state live in the pill row above it.
- **Browse-all is gone as a mode.** The main screen already shows everything and loads
  more in place, so there is no second grid that looks identical to the first.
- **Favorites/watchlist flow in the same grid** instead of a separate carousel, capped
  at six preview rows with a "Library" action on the section header that opens the
  Library tab with the same save/restore behaviour as the bottom bar.
- **Search failures are summarised, not dumped.** A failed provider search shows one
  "3 sources could not be searched" line with a Details toggle instead of a stack of
  error strings pushing results down.
- **Header hierarchy tightened:** title, then search/filter/refresh, then tagline plus
  Extensions/quick-add, then title/source/issue pills. No control changed behaviour.

### Readers (manga and novel)

- **Chrome no longer toggles on touch-down.** The manga vertical reader toggled the bars
  on the first pointer down, so starting a scroll flashed them. It now requires a real
  tap, ignores tap-to-stop-a-fling for 180 ms, and hides the bars when a scroll actually
  starts (`MangaReaderScreen.kt`, `VerticalPages`).
- **Novel taps are detected inside the document.** Compose pointer input over an
  `AndroidView` does not reliably see WebView touches, and taps that stopped a scroll
  toggled the bars. The injected reader script now reports a tap only when the finger
  moved less than 12 px for less than 350 ms, skips links, and does not report the
  programmatic restore scroll (`NovelReaderScreen.kt`, `ScrollBridge`).
- **Scrolling hides the chrome** in both readers so the page is not covered; a tap brings
  it back. Paged manga keeps the tap zones: left/right page or chapter, centre chrome.

### Detail pages (manga and books)

- **Provider Markdown is rendered as prose.** Weeb Central and similar sources write
  descriptions as Markdown, so sequel lists used to appear as
  `[Wanted!](https://weebcentral.com/series/…)`. `String.displayDescription()`
  (`ContentModels.kt`) keeps the link label, drops the URL and the emphasis/heading
  markers, and leaves paragraphs intact; both detail screens use it, and
  `ContentModelsTest` covers links, bare URLs, headings and untouched prose.

## 0.9.2 follow-up pass

A focused pass on what the 0.9.1 screens still got in the way of, plus the defects found
while reading through the app on the emulator.

### Library

- **The scope row is gone.** "Everything / Downloaded / Favorites / Recently added /
  Recently opened" duplicated the content-group chips and the favorites filter already in
  the grid. `LibraryScope`, `scopeCounts`, `setScope`, the scope predicates and the
  scope-specific empty states were deleted rather than hidden, so there is one filter
  model in `LibraryViewModel` and one chip row in `LibraryScreen`.

### Category headers

- **Five actions, one row.** Search, filter, refresh, extensions and quick-add used to be
  split between the title row and the tagline row. They now share a single, right-aligned
  action row under the tagline, each with a 48 dp target, so "Manhwa & Manga" keeps its
  full width and the controls line up with each other.
- **Search results keep the browse grid.** Browse used `Adaptive(108.dp)` and search
  `Adaptive(116.dp)`, which produced three columns while browsing and two oversized ones
  while searching. `GRID_MIN_CELL` is now shared by the browse grid, the search grid and
  the loading skeleton, so the column count does not change between states.

### Settings

- **Settings is a hub, not one long scroll.** Eleven rows (Appearance, Library, Reading,
  Playback, Downloads, Extensions, Storage, Network, Backup, App updates, About) show
  their current value as a subtitle and open a short dedicated screen, following
  Komikku's settings structure. A search field filters rows by title, subtitle and
  keywords. Download queue, extensions and app updates keep their existing screens.
- **Scroll position survives a sub-screen.** The hub's `LazyListState` is remembered above
  the page switch, so Back returns to the row you left instead of the top of the list.
- **The novel sliders are continuous.** Material 3 renders a row of tick dots for stepped
  sliders, which turned the font-size and line-spacing controls into a dotted progress
  bar. They are continuous now, the value is rounded by the callback, and it is written
  once the finger lifts instead of on every dragged pixel.
- **Library database size is real.** Storage measured `files/databases`, but Room keeps
  `allfun.db` beside `files/`, so the row always read 0 B. It now measures the directory
  `getDatabasePath("allfun.db")` reports.

### Detail pages

- **Action labels fit.** "Add to library" was clipped to "Add to libr…" in the
  quarter-width column of the manga/book action row. Labels wrap to two centred lines at
  the same 12 sp, which also survives a large system font size.

## Findings and backlog

### P0 — correctness and comprehension

| Item | Status | Notes |
| --- | --- | --- |
| Reader bars toggled by scrolling | Fixed | See above. |
| Category content hidden in side-scrolling rows | Fixed | One vertical grid. |
| Sort order did not offer Popular/Latest | Fixed | Lane-based, honest. |
| Empty category and filtered-empty looked the same | Fixed | Separate messages and actions. |
| Duplicate "See all" screen with no extra action | Fixed | Removed; pagination is inline. |
| Library sort menu used one icon for every order | Fixed | Per-order glyphs, accent only on the current one. |
| Detail descriptions showed raw Markdown links | Fixed | Sanitised once, shared by both detail screens. |

### P1 — high value, not yet built

1. **Shelf identity on cards in the merged grid.** The grid loses the "Popular now" /
   "Latest updates" labels. Add a small badge or a section toggle ("Show shelves") when
   the user chooses Popular/Latest, without going back to horizontal rows.
2. **Continue reading at the top of the library and reading categories.** The library
   already computes progress (`LibraryRow.fraction`). A compact "Continue" strip with
   the last three unfinished titles would save a search on every open. *Done in 0.9.1 for
   the Library; the category tabs still have no Continue row.*
3. **Search state in the category header.** The search field is injected between the
   header and the grid, which shifts the content. Reserve its space or open a dedicated
   search surface so the grid does not jump.
4. **Filter sheet summary.** Show the number of matches next to "Show results" and keep
   the sheet open while metadata streams in; today the count is only on the screen
   behind it.
5. **Reader: chapter edges.** Done for vertical manga (`ChapterEndCard`). Paged mode still
   needs the same boundary card at the last page; the chrome slider already seeks.
6. **Download state on category cards.** Done: `CategoryViewModel.downloadedMedia` feeds
   an "Offline" badge on catalogue and search cards.
7. **Large-screen layout.** The adaptive grid handles tablets, but the detail pages and
   readers stay single-column. Add a two-pane detail layout above 840 dp
   (`WindowSizeClass`) and cap reader line length.

### P2 — polish

1. **Library filter rows.** Two horizontal chip rows plus a search field is a lot of
   chrome. Consider one row of category chips and a filter button, or a `FlowRow` at
   large font scale.
2. **Sort menu icons.** Done in this pass: the library sort menu carries a glyph per
   order, with the accent tint reserved for the current one.
3. **Motion budget.** Phase transitions, chrome slides and tab cross-fades already honour
   `LocalReduceMotion`; verify the new grid does not animate on filter changes.
4. **Reader settings discoverability.** The settings icon is in the top bar only. A long
   press on the centre tap zone (or a first-run hint) would surface mode/fit faster.
5. **Colour contrast.** Check `onSurfaceVariant` over `surfaceContainer` in light mode and
   the category accent chips in both themes with the accessibility scanner; the palette
   is close to 4.5:1 but not measured yet.
6. **Strings.** Category copy is inline English. Move user-visible strings to resources
   before localisation; the reader already has the structure for it.
7. **Library preview overflow.** The category grid previews at most six library rows;
   confirm the "6 more in your library" subtitle matches the real overflow count once a
   large library exists (it is computed, not hardcoded).

## Verified on the emulator

Build `assembleRelease` installed over the previous signed build on Android 15 / API 35
(`allfun35`), with the existing library, extensions and feeds preserved:

- Movies, Books and Manga each render the vertical grid; Manga showed 63 titles from 63
  source entries with the Favorites preview and a working **Library** shortcut.
- Popular/Latest/A–Z/Source switch in place; the filter sheet applies a source facet and
  the screen reports "Filtered" with a clear row.
- One Piece detail renders the provider's sequel list as prose
  ("Wanted! (Alternate Story)") instead of `[Wanted!](https://…)`.
- Manga reader (vertical): the bars were visible on open, disappeared on a scroll without
  any tap, and returned after a single tap; paged mode still has the tap zones.
- The novel reader's document-level tap/scroll bridge was not exercised live: both
  installed novel sources returned an empty chapter list on this machine on 2026-11-23
  (the detail screen said so honestly), so there was nothing to scroll. Re-run this check
  when a novel source responds.
- **Continue strip:** the Library showed "2 in progress" with 36% and 2% on the artwork;
  the strip scrolled away with the grid, and typing in the search field did not move the
  field when the strip hid.
- **End-of-chapter card:** reached the bottom of One Piece chapter 3 in vertical mode with
  the chrome hidden, then tapped **Next chapter** once and landed at the top of chapter 4.
  The buttons sit above the system gesture inset.
- **Offline badge:** not exercised live, because the test library had no completed
  download at the time; the state is fed by `DownloadStatus.COMPLETED` only.

### 0.9.2 checks

- **Library:** the scope chip row is gone; the content-group chips, Continue strip and
  grid remain, and both grid and list layouts render.
- **Category headers:** Movies, Books and Manga each show title and tagline at full width
  with search / filter / refresh / extensions / add on one row beneath.
- **Settings:** hub rows show live subtitles ("Grid · Recently updated", "141%"-style
  reader values are on the Reading page), search filters to Reading for "font", Back
  returns to the same row, sub-screens render, and Storage reports
  `1.4 MB` for the database instead of `0 B`.
- **Novel sliders:** dragging text size moved it from 100% to 141% and the value survived
  leaving and reopening the page.
- **Category search:** "one" in Movies returns 38 results in the same three-column grid as
  browsing, with the loading skeleton in that grid too.
- **Manga detail:** One Piece renders the hero, action row, prose synopsis with the
  provider's related-series list, and the Resume/Start floating button.
- **Not re-checked after the last label change:** the "Add to library" wrap on the manga
  action row was built and inspected in code only; verify on the next device pass.

## What to measure next

- **Startup and navigation:** Macrobenchmark for cold start, Movies → detail, and
  Library → reader. The 0.9.0 detail stubs should keep detail open under 300 ms on the
  emulator profile; measure on a mid-range device before claiming a number.
- **Reader jank:** frame timing while scrolling a 40-page chapter and while the WebView
  loads the next novel chapter.
- **Grid cost:** first composition of the category grid with 200+ titles; check that
  `sorted()` is not on the main thread for large feeds (it runs on the UI state copy, so
  keep feeds bounded or memoise by feed identity).
- **Accessibility:** TalkBack pass over the category grid, reader tap zones and the sort
  chips; verify the tap zones have content descriptions and that the centre zone is not
  the only way to reach the bars (volume keys and the chapter picker remain available).

## Guardrails for future UI work

- Keep the bottom bar to the three categories, Library and Settings. Providers and
  extensions are configuration, not destinations.
- Every screen ships loading, empty, error and offline states; no placeholder buttons.
- No horizontal carousel for primary content. Horizontal is reserved for chips and
  short action rows.
- Reader chrome is hidden while reading and restored by a deliberate tap.
- Respect `LocalReduceMotion`, large font scale, RTL and the system dark theme.
