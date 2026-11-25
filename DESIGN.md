# AllFun — Liquid Glass design record

This is the working record for the 0.13.0 interface. It states what was built, which
decisions were taken where the brief left room, and what was deliberately left out. It
describes the code in `app/src/main/java/app/allfun/ui/glass/` and the screens that use it.

## 1. The material

There is one material: **liquid glass over a dark warm world**.

| Layer | What it is | Where it appears |
| --- | --- | --- |
| L0 world | Opaque gradient field (`#0A0B0E` + four radial blobs) with 2–3% grain | Every screen's background |
| L1 artwork | Posters, covers, video, manga pages | Inside cards, readers, player |
| L2 frosted | The main reading surface: panels, sheets, bottom chrome | One panel per group |
| L3 clear | Floating controls: top capsules, chips, icon buttons, nav | Above L2 or directly on L1 |

Rules that the implementation enforces:

- **No glass-on-glass.** A frosted panel never contains another `GlassSurface`. List rows
  inside a panel use hairlines and bare rows. Sheets own their surface; content inside is
  plain text and controls. `GlassTopCapsule` is clear and floats above content, not inside
  a panel.
- **One panel per group.** Installed extensions share one panel, chapters are bare rows on
  the world, settings subpages are one panel per section.
- **Never pure white glass.** Clear glass is a low-alpha `bg0` tint plus a vertical white
  sheen; frosted glass is a heavier `bg0` tint over a lens of the world. On light theme the
  same recipe inverts to warm paper tones.
- **No coloured icon tiles, no gradients behind content, no glow halos, no looping world
  animation.** The world is static; grain does not animate.

## 2. Tokens

All values live in `Tokens.kt` (`GlassTokens`, `AFColors`, `AFType`, `Section`). Screens do
not invent colours, radii, spacing or durations.

- Neutrals: `bg0 #0A0B0E`, `bg1 #101217`, `bg2 #171A21`; text `#F1EFEA / #B0ADA6 / #7A7873`.
  Light theme uses `#F2EFE9 / #FBF9F4 / #E7E3DA` and dark text equivalents.
- Section accents: Movies `#FF5E8A`, Books `#2CC5C0`, Manga `#8B7CFF`, Library `#E7B36B`,
  System `#B9B4AA`. Status colours: ok `#5BD08A`, slow `#F2B84B`, failed `#FF6B6B`, always
  paired with an icon or a label.
- Space: 4dp base, 20dp gutter, 12dp grid gap, 32dp section gap, 16dp panel padding.
- Radii: panel 24, sheet 32, menu 22, input 18, poster 8, capsule 100, badge 10. Inner
  corners stay concentric by subtracting padding at the call site.
- Glass alpha: clear 0.26 body + 0.04 white, frosted 0.54 body + 0.05 white; AMOLED uses
  the opaque `bg0` at 0.62; light theme uses 0.42 tint.
- Type: Fraunces (display/title) + Inter Tight (UI), both OFL variable fonts bundled in
  `res/font`. Scale 36/28/24/17/15/13/12/11sp; digits use `tnum` in every numeric role.

## 3. How the glass is drawn

`GlassSurface` draws its body inside a clipped outline:

1. Frosted body: a `drawWorldLens` sample of the same deterministic field the screen uses,
   offset slightly upward, then a `bg0` tint and a white wash.
2. Clear body: `bg0` tint plus a 7% → 1% white vertical sheen.
3. Inset (tier C): a 6% white wash only, used for tracks and wells.
4. Selected state: a 13% accent wash; pressed state: a 5% white flash.
5. 2dp top edge sheen and a 1dp inner shadow.
6. 2% grain on standard, 1.2% on clear, none at Lite.
7. A 900ms specular sweep on High only, never looping.

Shadow is a single soft drop (`glassShadow`), never stacked. Lite reduces blur from 24 to
14dp. `GlassSurface` also handles press scale, `Role.Button`, disabled alpha and the
optional click target, so controls cannot drift from the recipe.

### Shader status

The brief's SDF/backdrop shader is **not shipped as a GPU shader**. The glass body is drawn
with Compose/Canvas primitives: radial-gradient lens, alpha tints, linear sheen, grain
shader and clipped outline. This keeps minSdk 26 working with one code path, avoids
sampling the live window every frame (the single most expensive part of real glass), and
keeps lazy grids free of shader allocations. A `RuntimeShader` refraction path (API 33+)
with Standard/Lite fallback remains a possible future enhancement; the current lens is the
intentional stand-in. No `AGSL`/`.agsl` file exists in the repo.

## 4. Glass level

`GlassLevel` is a render-time switch, not a separate design:

| | High | Standard (default) | Lite |
| --- | --- | --- | --- |
| World field | full | full | flat `bg0` + one blob |
| Lens inside frosted panels | yes | yes | no |
| Grain | 2.5% / 1.2% | 1.5% / 0.7% | none |
| Sweep | yes | no | no |
| Shadow | 24dp / 8dp | 24dp / 8dp | 14dp / 5dp |
| Press scale | yes | yes | yes |

The value is persisted as `glass_quality` (`GlassQuality.HIGH/STANDARD/LITE`, default
STANDARD) and mapped to `LocalGlassLevel` in `theme/Theme.kt`. Settings → Look & feel exposes
it with a live preview. This is the only data-layer addition made by the redesign; it only
affects rendering.

## 5. Motion and haptics

- Springs for glass controls (damping 0.8, response 0.35s); 120/200/320ms fades for content.
- Screen transitions are fades with a small slide/scale; tab changes 200ms, deeper screens
  320ms. Reduce Motion collapses them to 120ms crossfades.
- Stagger is 30ms, capped at 12 items. No looping animation anywhere; the only loop is the
  arc spinner, which is progress feedback, not decoration.
- Haptics: tick on selection/press, medium on sheet open or destructive confirm, success on
  completed install/download. All routed through `rememberHaptics()`.

## 6. Icons

`AFIcons` is a hand-drawn monoline set: 24dp viewport, 1.75dp stroke, round caps/joins.
It covers every visible chrome action (back, forward, chevrons, search, filter, sort, list,
grid, sliders, play/pause, download, heart, trash, plus, refresh, share, info, folder, eye,
lock, link, server, code, clock, globe, bookmark, Wi-Fi, clapper, book, manga, stack, more,
close, check, arrows, sparkle). Glyphs are bare on glass; there are no coloured tiles.

No Material icon imports remain in the UI layer. Long-tail glyphs that the app does not
currently need were not recreated.

## 7. Components

`ui/glass/` contains the library:

`Tokens`, `Theme` (composition locals + `AFTheme`), `World` (backdrop, lens, grain, tilt),
`GlassSurface` (`CLEAR/FROSTED/INSET`, shadow, sweep), `Primitives` (`AFText`,
`AFIcon`, `AFIconButton`, `GlassIconButton`, `AFDivider`, `AFTextButton`, `AFOverline`),
`Controls` (`GlassPrimaryAction`, `GlassButton`, `GlassSwitch`, `GlassSegmented`,
`GlassChip`, `GlassSlider`, `GlassTextField`, `GlassProgress`, `GlassBadge`, `AFSpinner`,
`StatusLine`), `TopBar` (`GlassTopCapsule`, `ScreenHeader`, `TopScrim`), `Nav`
(`GlassNav` + travelling blob + hide-on-scroll state), `Sheet` (`GlassSheet`,
`GlassDialog`, `GlassPromptDialog`, `SheetRow`, toast), `Poster` (`PosterCard`,
`PosterArtwork`, typographic fallback), `States` (`GlassEmpty`, `SkeletonRows`, `ErrorPanel`)
and `Haptics`.

## 8. Screen decisions

- **Shell** (`AllFunRoot.kt`): each destination owns a `WorldBackdrop` and a `SectionScope`;
  the `NavHost` is transparent and the bottom `GlassNav` floats over content. Tab screens
  reserve ~140dp bottom padding so no row is clipped by the nav or the system bar.
- **Browse**: no collection hero, no "Movie" chips, no "Load more". A fixed adaptive
  3-column grid (108dp minimum), auto-load near the end, a sort capsule and a filter sheet,
  and a frosted search overlay.
- **Search**: one search field in a top capsule, category/source chip rows, grouped poster
  rows, recent searches as bare rows, skeletons and a source-issues panel.
- **Detail (movies)**: hero panel, one primary Play/Continue action, library/favourite glass
  buttons, About panel, source summary opening a sheet, season/episode rows with download
  state, overflow actions in a capsule.
- **Reading detail**: hero panel with poster, Continue/Start, Save/Like/Download row, source
  panel, About panel with genres as a caption line (no glass chips), chapter header with a
  filter capsule, bare chapter rows on the world for cheap long lists, `DownloadRing` drawn
  with Canvas arcs, sort/filter sheet, chapter action sheet.
- **Library**: grid/list toggle, search, filter chips, sort sheet, remove-download dialog,
  hide-on-scroll nav.
- **Downloads**: no stat card. A header with a live queue summary, one frosted panel per
  title with hairline rows, progress bars, sort/more/concurrency sheets, segmented
  concurrency 1–4, row/group action sheets and confirmations.
- **Settings**: a hub of grouped frosted panels, bare monoline icons, search; subpages are
  one panel per section with hairline rows. No gradient header. New GLASS LEVEL control with
  live preview. Material You, AMOLED and Light variants keep working.
- **Extensions**: five tabs (Installed/Discover/Updates/Repositories/Disabled) as chips,
  category scope chips, search field, extension rows with avatars or initials, repository
  panels, featured addon directory, Stremio/Jellyfin panels, add-repository and add-addon
  sheets, details sheet and remove confirmation.
- **Updates/About**: top capsule with a Check action, release panel, download/verify/install
  progress, up-to-date and failure states, notes expander.
- **Readers**: reading canvas stays neutral black; the top capsule and bottom frosted panel
  float over the page, hide on tap/scroll and come back on pause. Chapter picker is a glass
  dialog, reader settings a glass sheet with segmented controls and sliders.
- **Player**: player surface is untouched Media3; Compose chrome floats as a clear top
  capsule (title/subtitle/next episode), three glass transport circles and a frosted bottom
  panel with time, slider and source/next actions. Stream picker is a full-screen world list
  with bare rows.
- **First run**: inherits the same shell and glass panels; no separate illustration system.

## 9. What is honestly not done

- **No before/after screenshots per screen per Glass level.** The repo has no instrumented
  screenshot pipeline and the work was verified by compilation, unit tests, lint and the
  release build, not by a device capture pass. This deliverable is skipped, not faked.
- **No measured fps table.** The design avoids backdrop sampling, blur in lazy items and
  shader allocation in rows, and caps live clear surfaces; no Macrobenchmark/frame-timing
  run was executed in this environment. The performance section below is design-level.
- **No live window refraction.** See "Shader status".
- **No full RTL / 200% font / foldable visual audit.** Layouts use edge-to-edge insets,
  adaptive grids and scale with font size, but each screen was not photographed in those
  configurations.
- **WebView reading content is not glass.** The novel text surface is the user's chosen
  paper theme; the chrome is glass. This is deliberate: text needs an opaque reading plane.
- **`ui/theme/Theme.kt` still imports Material 3.** It is the bridge that supplies the
  Material colour scheme, typography and shapes to the handful of low-level platform
  surfaces and to any extension-rendered UI; no screen imports Material widgets. Listed as
  the one accepted remnant.
- **Legacy dead files removed.** `components/Common.kt`, `ContentCards.kt`, `Feedback.kt`
  and `States.kt` were superseded by the glass library and had zero call sites; their only
  live symbol, `rememberViewModel`, moved to `components/ViewModel.kt`.

## 10. Logic changes

- `SettingsStore`: added `GlassQuality`, the `glassQuality` field, `glass_quality`
  encode/decode and `setGlassQuality`. No existing key or feature changed.
- `SettingsViewModel`: exposes the new value and setter.
- `AllFunRoot`: glass shell and destinations; navigation destinations, routes and arguments
  are unchanged.
- No provider, extension, download, backup, playback or library logic changed. Every action
  that existed before is still reachable; some moved into overflow sheets or capsules.
- Dead legacy UI files were deleted (see above); `ChapterPickerDialog` was rebuilt on glass
  with the same signature.

## 11. Verification

Run from the repository root:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain
```

The 0.13.0 record: compile, 66 unit tests, lint (0 errors) and both APK variants pass.

## 12. File map

- `ui/glass/` — the material, components and world.
- `ui/AllFunRoot.kt` — shell, nav, transitions.
- `ui/category`, `ui/library`, `ui/search`, `ui/detail`, `ui/downloads`, `ui/settings`,
  `ui/extensions`, `ui/update`, `ui/reader`, `ui/player` — screens rebuilt on the library.
- `ui/components/ChapterPicker.kt`, `QuickAdd.kt`, `ViewModel.kt` — remaining shared UI.
- `ui/theme/Theme.kt` — Material bridge and `LocalGlassLevel` mapping.
- `data/settings/SettingsStore.kt` — `glass_quality` persistence.
