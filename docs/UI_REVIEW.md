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
   the last three unfinished titles would save a search on every open.
3. **Search state in the category header.** The search field is injected between the
   header and the grid, which shifts the content. Reserve its space or open a dedicated
   search surface so the grid does not jump.
4. **Filter sheet summary.** Show the number of matches next to "Show results" and keep
   the sheet open while metadata streams in; today the count is only on the screen
   behind it.
5. **Reader: page-number jump and chapter edges.** The progress bar is display-only.
   Let a drag on it seek, and show a short "End of chapter — next: …" card at the last
   page in paged mode.
6. **Download state on category cards.** Downloaded titles show no badge in the
   catalogue; the library does. Reuse the download lookup already used by the library.
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
