# AllFun — Liquid Glass design record

This is the working record for the 0.13.x interface. 0.13.0 rebuilt every screen on the glass
library; 0.13.1 refined the material itself (position-aware lens, edge bend, tinted glass) and
fixed the bottom sheet's window. It states what was built, which decisions were taken where
the brief left room, and what was deliberately left out. It describes the code in
`app/src/main/java/app/allfun/ui/glass/` and the screens that use it.

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
- Radii: panel 26, sheet 34, menu 24, input 20, poster 10, capsule 100, badge 10. Inner
  corners stay concentric by subtracting padding at the call site.
- Glass alpha: clear 0.18 body + 0.04 white, frosted 0.48 body + 0.05 white; AMOLED uses
  the opaque `bg0` at 0.55; light theme uses 0.36 tint. The body is deliberately more
  transparent than a classic frosted card: the edge bend, not the fill, carries legibility.
- Type: Fraunces (display/title) + Inter Tight (UI), both OFL variable fonts bundled in
  `res/font`. Scale 36/28/24/17/15/13/12/11sp; digits use `tnum` in every numeric role.

## 3. How the glass is drawn

`GlassSurface` draws its body inside a clipped outline:

1. Frosted body: `drawWorldLens` samples the same deterministic field the screen uses **at the
   surface's own position** (`LocalWorldField` + `boundsInRoot`), magnified 1.05x about the
   panel centre; then a `bg0` tint and a white wash.
2. Clear body: the same lens at 1.12x, a 70% `bg0` tint plus a 7% → 1% white sheen.
3. Edge bend: an `edgeRing` (outer outline minus a concentric inner one, `Path.op` difference)
   re-samples the world at 1.26x (clear 1.45x) before the body tint, so a tinted control stays
   tinted to its edge instead of growing a clear rim. The ring also gets a faint white stroke.
4. Inset (tier C): a 6% white wash only, used for tracks and wells.
5. Body tint (`tint`/`tintAlpha`) after the optics, then selected (13% accent wash) and pressed
   (5% white flash) states.
6. Thickness: a 20% black shade under the lit top edge and a 15% black shade over the bottom
   45%, which is what makes the surface read as a slab rather than a sticker.
7. Grain (2.2% frosted, 1.2% clear) and a broad corner hotspot.
8. Specular rim: a 1.8dp stroke with a vertical gradient (bright top, dim bottom), inside the
   clip. A vertical gradient rather than a sweep so the highlight can never rotate on a wide
   or narrow control.
9. A 900ms specular sweep on High only, never looping.

Shadow is a single soft drop (`glassShadow`), never stacked. Lite reduces blur from 28 to
16dp. `GlassSurface` also handles press scale, `Role.Button`, disabled alpha and the
optional click target, so controls cannot drift from the recipe.

Tinted glass is a first-class state: `GlassPrimaryAction` is a clear capsule with
`tint = accent`, `tintAlpha` 0.52 (0.62 light) and a shadow, and the switch track uses a
near-solid accent (0.92) when checked. The segmented control's selected pill is neutral white
glass, and the nav blob is a soft accent/white gradient with no outline, so selected states
read as light on glass rather than as coloured outlines.

### Shader status

The brief's SDF/backdrop shader is **not shipped as a GPU shader**. The glass body is drawn
with Compose/Canvas primitives: a deterministic field sampled through an affine zoom, alpha
tints, linear sheen, grain shader and clipped outlines. Because the world is a known function,
the lens can sample "behind" a panel arithmetically instead of reading the window: `WorldField`
records the field's root bounds and `drawWorldLens` translates the field to the panel's own
origin and scales it about the panel centre. That gives real displacement (the middle and the
edge no longer show the same pixels) with no framebuffer read and no second blur pass. The
same path keeps minSdk 26 working, avoids sampling the live window every frame, and keeps lazy
grids free of shader allocations. A `RuntimeShader` refraction path (API 33+) with
Standard/Lite fallback remains a possible future enhancement; the current lens is the
intentional stand-in. No `AGSL`/`.agsl` file exists in the repo.

### 0.13.1 refinement decisions

- **Sample where the glass is.** Panels and capsules read `LocalWorldField` and their own
  `boundsInRoot`; they no longer use a fixed offset. Moving a panel across the screen now
  changes what it refracts.
- **The edge is the lens.** A concentric ring re-samples the field at a harder zoom and is
  drawn *before* the body tint. This is the bend; it is also why tinted controls keep their
  colour at the rim instead of showing a clear halo.
- **The rim is vertical, not angular.** A sweep-gradient rim rotates its highlight as the
  control's aspect ratio changes. A top-bright, bottom-dim vertical stroke behaves like light
  from above on every shape, so it replaced the sweep rim.
- **Tint after optics.** `GlassSurface` grew `tint`/`tintAlpha`; the primary action is now a
  tinted clear capsule rather than a flat accent pill, so it belongs to the same material.
- **More transparent, larger radii.** Clear 0.18 / frosted 0.48 / AMOLED 0.55 / light 0.36,
  with panel 26, sheet 34, menu 24, input 20, poster 10. The body shows more world; the rim,
  thickness shades and grain keep text legible.
- **A world with something to bend.** The field gained a cool top light (7%), a diagonal light
  shaft (5%) and a vignette (40% at the edges). A flat field makes a lens invisible.
- **Sheets are windows, not cards.** `GlassSheet` now runs in a full-display dialog window
  (`FLAG_LAYOUT_IN_SCREEN`, no fitted insets, top-start gravity, no system dim), caps its
  height at `maxHeight - 56dp`, scrolls its content and pads for the navigation bar. The scrim
  dropped to 32%. Dialogs share the same window setup so their centring is not shifted by the
  status bar.
- **Encoding fix.** Six `Â·` sequences in `CategoryScreen` and nine in `DownloadsScreen` were
  replaced with `·`; they were double-encoded separators from an earlier edit.

Skipped in 0.13.1: no live backdrop refraction of posters or artwork under a panel (the lens
bends the world field, not the scrolling content — that needs a GPU backdrop shader), no
per-frame blur, and no change to any screen's logic, routes, settings keys or data.

## 4. Glass level

`GlassLevel` is a render-time switch, not a separate design:

| | High | Standard (default) | Lite |
| --- | --- | --- | --- |
| World field | full | full | flat `bg0` + one blob |
| Lens inside frosted panels | yes | yes | no |
| Grain | 2.2% / 1.2% | 2.2% / 1.2% | none |
| Sweep | yes | no | no |
| Shadow | 28dp / 10dp | 28dp / 10dp | 16dp / 6dp |
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
The tag-triggered release workflow repeated the tests, built and verified the signed APK,
and published [v0.13.0](https://github.com/mjt28934/AllFun/releases/tag/v0.13.0)
(`AllFun-0.13.0-release.apk`, 62,113,401 bytes, SHA-256
`fab927aacf23ed8acd770b8c36298846d30b966551e17d8f0e6dbda0688189d2`, `versionCode 1300`,
same `CN=AllFun` signer).

The 0.13.1 record: the same five tasks pass (66 unit tests, lint 0 errors, 35 warnings), the
release APK reports `versionCode 1301` / `versionName 0.13.1` and `CN=AllFun`, and the sheet,
primary action, nav and dialog were checked on a 1080x2340 API 35 emulator before tagging.

## 12. File map

- `ui/glass/` — the material, components and world.
- `ui/AllFunRoot.kt` — shell, nav, transitions.
- `ui/category`, `ui/library`, `ui/search`, `ui/detail`, `ui/downloads`, `ui/settings`,
  `ui/extensions`, `ui/update`, `ui/reader`, `ui/player` — screens rebuilt on the library.
- `ui/components/ChapterPicker.kt`, `QuickAdd.kt`, `ViewModel.kt` — remaining shared UI.
- `ui/theme/Theme.kt` — Material bridge and `LocalGlassLevel` mapping.
- `data/settings/SettingsStore.kt` — `glass_quality` persistence.
