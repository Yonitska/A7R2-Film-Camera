# Sony A7R II (ILCE-7RM2) 42MP local port

Status: **local test build, not a public release**. The A7R II capture path is
implemented but has not yet been validated on a camera.

## Detected platform (read-only diagnostic, 2026-11-01)

| Property | Value |
| --- | --- |
| `model.name` | `ILCE-7RM2` |
| `device.memory` | `ILDCSC_MUSASHI8Gb` |
| `mem.rawimage.size.in.mega.pixel` | `42` |
| Firmware | `4.01` |
| Picture sizes | `7952x5304`, `7952x4472`, `5168x3448`, `5168x2912`, `3984x2656`, `3984x2240` |
| Current picture size | `7952x5304` |
| JPEG quality values | `95,50,25` |
| Storage / drive | `jpeg` / `single` |
| RGB matrix / extended gamma | both advertised as supported |

The camera exposes no preview sizes through `Camera.Parameters`. The app's
native preview path does not use `preview-size`; it writes a Gamma table and RGB
matrix through `CameraEx`, so the empty preview-size list does not block it.

## Real 42m memory map

`MAP42R1.BIN` is the unmodified firmware map read from
`/system/etc/memmap/libILDCSC_MUSASHI8Gb42m_PictureEffectPlus_00_00.so`:

- 1,980 bytes = 99 × 5 little-endian uint32 records
- SHA-256 `a869d22be91332d494f0fe26f6285f998938f4480bf24efe2db86bb37305a621`
- Copied to `vendor/sony-a7r2-memmap/MAP42R1.BIN`; provenance in `SOURCE.md`.

The A6300 24m reference map is 1,860 bytes / 93 records. The A7R II firmware
provides only the `00_00` revision for 42m; it is the exact map used by the
camera's own PictureEffectPlus effect. The build validates both maps by hash.

## Chroma order

The A7R II developed image stores chroma as **V Y0 U Y1** (VYUY), while the
A6300 path consumes **U Y0 V Y1** (UYVY). Treating the A7R II buffer as UYVY
swaps Cb/Cr and produces the strong blue/cyan skin cast seen in the first
A7R II test build (`a7r2.1`). From `a7r2.2`, the 42m profile passes a swap flag
through every UYVY read/write path: full LUT, cached LUT, sparse cache sampling,
source-preview and fused review.

If a future build needs the other order, put a text file on the SD card at
`SONYLUT/UVORDER.TXT` containing exactly `VYUY` or `UYVY`. It overrides the
built-in profile choice and is logged as `uv_order` in `PROBE.JSN`.

## Code changes relative to upstream 0.9.0-beta.5

- `android/capture/CaptureProfile.java`: exact 24m/42m platform profiles,
  dimensions, map asset, map revision, firmware path, timeouts.
- `PreJpegProbeActivity`:
  - detects `device.memory` + `mem.rawimage.size.in.mega.pixel`;
  - selects `MAP24R1.BIN` (24m) or `MAP42R1.BIN` (42m) from `map_revision=auto`;
  - uses 7952×5304 for A7R II (6000×4000 for A6300);
  - if the camera is not already at the full 3:2 size, selects it and restores
    the original size on close;
  - checks developed image and saved JPEG dimensions against the profile;
  - 60 s save timeout and 8 s preview transition timeout on A7R II;
  - UI labels the actual resolution.
- `native/jpeg_io.cpp`: software JPEG ceiling raised from 32MP to 45MP.
- `native/device_memory.cpp`: diagnostic stream ceiling raised from 24MiB to 64MiB.
  The production path uses Sony `storeImage`, not this stream reader.
- Native UYVY processing accepts the A7R II VYUY chroma order (swap flag) for
  LUT, sparse-cache sampling, source preview and fused review.
- Optional `SONYLUT/UVORDER.TXT` override (`VYUY` / `UYVY`).
- Version `0.9.0-beta.5-a7r2.2` / code 98 fixed the saved-photo VYUY cast.

### a7r2.3 — processing preview and A7R II controls

- `DevelopingPreview`: the processing page no longer uses the deliberately
  inverted blue "negative". It shows a neutral dimmed copy of the current,
  ungraded photograph and reveals the graded preview when ready.
- A7R II only (`manualControls` profile): entering capture switches to
  `scene-mode=manual-exposure`; leaving restores the original mode and saved
  exposure/WB values.
  - Front dial (DIAL_1): aperture; rear dial (DIAL_2): shutter; control wheel
    (KURU, with DIAL_3 fallback): ISO. Aperture/shutter use CameraEx
    increment/decrement; ISO uses the supported `iso-sensitivity-values`.
  - Down enters the app-drawn flexible-spot AF selector; arrows move the
    -1000..1000 scalar point; center confirms; MENU cancels. The AF area/point
    is restored on exit.
  - Right/Left cycles safe white-balance presets. The approximate-preview
    baseline is rebased after WB changes so half-press restore still verifies.
- Playback: C3/zoom scan codes (659/610/611/623/533/636) toggle 2x; arrows pan
  while zoomed; unzoomed paging is unchanged. Delete (595/513) asks for
  confirmation, then removes the original, graded JPEG and catalog receipt.
- Version `0.9.0-beta.5-a7r2.3` / code 99.

### a7r2.4 — first A7R II feedback fixes

- Processing page: never show source pixels before the graded preview; use a
  neutral dark frame. The Sony graphics plane is kept muted during processing
  and restored when the verified preview is ready (the A7R II appeared to
  overlay a stale blue review frame). `SOURCE.PNG` and `GRADE.PNG` are saved in
  the run directory for diagnosis.
- Exposure preview: set `shooting-preview=iris_ss_iso` while in M mode so the
  live view reflects aperture/shutter/ISO; restore the original value on exit.
- Latency: high-frequency aperture/shutter/ISO and dial writes no longer go
  synchronously to the SD journal (logcat only); the HUD readout reuses the
  last camera snapshot instead of issuing another `getParameters`.
- AF: X and Y are committed separately, then two Sony empty-parameter/modifier
  orders are tried; errors now log the actual `focus-area-type/x/y` readback.
- Playback zoom accepts C1/C2/C3/custom/AF-MF/focus/WB/ISO scan codes plus
  native zoom tele/wide. `SONYLUT/KEYLOG.TXT` records every Gallery key-down
  (key, scan, action) so the exact C3 code can be identified if it still does
  not respond.
- Version `0.9.0-beta.5-a7r2.4` / code 100.

### a7r2.5 — stability, preview and playback polish

- Approximate LUT live preview is disabled on the 42m profile
  (`CaptureProfile.originalPreviewOnly`). The A7R II applies Sony's RGB
  matrix/extended gamma to its camera OSD/graphics plane as well as the live
  view, which tinted the app's own capture/processing screens blue while the
  saved JPEGs were correct. The A6300 path is unchanged.
- Product captures no longer write `SOURCE.PNG`/`GRADE.PNG` (each was a
  multi-second SD write inside the RAW callback and caused the 10 s black
  screen). The dim-source transition and Sony graphics restore are always
  active again. Diagnostic PNGs remain only in the explicit
  `source_reference` probe.
- M-mode live exposure: `shooting-preview` now accepts A7R II's
  `iris_ss_iso_aeunlock` (as well as `iris_ss_iso`) and is committed through
  Sony's empty-parameter `ParametersModifier.setShootingPreviewMode`, the path
  Sony's own shooting app uses; the bare key is only a fallback. It is
  re-asserted after each exposure change and after each capture, so the finder
  follows the dial.
- Aperture/shutter/ISO and WB work is queued to a dedicated looper, coalesced
  per dial burst, and applied with one parameter readback. The UI thread only
  accumulates counts, and a barrier drains the queue before the shutter.
- AF selection is now UI-local: arrows move the drawn box instantly, then one
  `setFocus` commit happens on OK (or full-press). Sony quantizes the point to
  its AF grid, so the driver accepts a readback that moved in the requested
  direction instead of demanding exact equality. This is the a7r2.4 vertical
  failure (`requested 194, actual 190`) and the horizontal stutter.
- Playback zoom: C3 toggles 1x↔2x; the control wheel (KURU/DIAL_3, not the
  front/rear dials) steps 1/1.5/2/3/4/6/8x with a 220 ms ease-out animation;
  arrows pan; BACK or C3 exits. A first screen nail opens instantly, then the
  original JPEG is decoded at up to ~2048 px in the background and swapped in
  without losing zoom/pan. Gallery heap budgets are 12 MiB (nails) plus
  32 MiB (detail) and the manifest requests a large heap.
- Version `0.9.0-beta.5-a7r2.5` / code 101.

Known limits of a7r2.5: the A7R II live view and app UI stay at original
colors (no approximate LUT preview) until a matrix path that excludes the
graphics plane is proven; the detail decode may take 1–3 s on first zoom.

### a7r2.6 — preview red/blue, exposure guard and pan direction

- Native capture/Library previews wrote bytes R,G,B,A into an Android
  `ARGB_8888` bitmap. On the A7R II that storage is a 32-bit ARGB word
  (bytes B,G,R,A), so red and blue were exchanged: an orange chair displayed
  as exactly the same-saturation blue. `previewRow` in `native/uyvy.cpp` and
  the Library frame writer in `native/jni.cpp` now write one packed ARGB
  value per pixel. The saved JPEGs and LUT math were never affected.
- Playback panning: pressing UP/RIGHT now moves the image in the same
  direction the user expects (the matrix translation is negated).
- Capture guard: Sony's preview display mode (`iris_ss_iso_aeunlock`) can
  unlock AE. On the A7R II half-press the app now restores the saved preview
  mode, re-asserts `scene-mode=manual-exposure`, rewrites the exact
  aperture/shutter/ISO/WB the user dialed, and then locks AE through
  `ParametersModifier.setAutoExposureLock(true)` before Sony meters the shot.
  While half-pressed, further dial changes do not re-enable the preview mode.
  The preview and AE lock are restored on half-press release or after the
  shot. This addresses ISO 160 → ISO 16000 and F2 → F9 captures while the app
  HUD still showed the dial values.
- Diagnostics: `manual_capture_guard` logs the camera state before/after the
  guard; `manual_pre_shot` logs the state at full press; `jpeg_exif` logs
  ISO/FNumber/ExposureTime for both saved files so a metadata-only mismatch
  can be distinguished from the actual exposure. The color-model audit
  (`PreviewColorAudit`) remains available behind the `source_reference`
  diagnostic intent.
- Version `0.9.0-beta.5-a7r2.6` / code 102.

### a7r2.7 — pose director, A/S/M and movie mode

- C2 opens the ported ShotDirector/Film Studio pose director over the live
  view: 176 bundled pose cards, permanent category strip, arrows/front dial
  browse cards, left/right change category, MENU/OK/delete close; the shutter
  remains available. Pose photos are read from `POSE/` on the SD card first;
  this build also bundles the 176 diagrams in `assets/POSE`, so a card folder
  the camera refuses to list still shows photos instead of the neutral
  diagram. The bundled CJK font is stored uncompressed.
- C1 cycles M → A → S → M. In M the front dial is aperture, the rear dial
  shutter; in A the front dial is aperture and the rear dial exposure
  compensation; in S the rear dial is shutter and the front dial exposure
  compensation. The control dial remains ISO in all modes. The bottom exposure
  strip now shows shutter, aperture, ISO, EV and WB. The selection is saved in
  SharedPreferences and restored in the next photo session (including after
  returning from movie mode). *(The A/S dial mapping is superseded by a7r2.8:
  the gain/control wheel is the fixed parameter.)*
- Half-press capture guard is mode-aware: M locks AE after reasserting the
  manual pair; A/S keep the selected mode, the user's parameter and EV, and
  leave the other parameter to the camera meter.
- Left in the photo view closes the still CameraSequence session and returns an
  `open_video` handoff to the library, which starts `VideoActivity` in the
  `:video` process with the current LUT id. Movie mode
  uses `CameraEx.OpenOptions.setRecordingMode(1)`, Sony `MediaRecorder`, and
  `getSupportedCamcoderProfiles()` to build a format menu (XAVC S 4K / XAVC S /
  AVCHD / MP4, resolution, frame-rate and bitrate as reported by the body).
  MENU opens the format picker; shutter/OK starts and stops; Left stops and
  saves, then returns an `open_photo` handoff that the library turns into a
  fresh photo session. The selected profile is remembered per app. Clips are written to the
  camera media store; the app does not list or play videos.
- Video color uses the fitted matrix/gamma of the selected LUT (the same
  approximate profile as live preview), because the still 3D LUT cannot be
  applied to Sony's encoded stream. Whether the encoded 4K/HD video includes
  that matrix is still to be verified on the body. Audio relies on the camera's
  MediaRecorder defaults; `RECORD_AUDIO` is declared.
- Main menu shows the generated build date (`BuildInfo.DATE`, written by
  `scripts/stage_android.py`) and the support note is now
  “仅支持A7R2 嘉嘉专用”.
- Version `0.9.0-beta.5-a7r2.7` / code 103. The previous a7r2.6 APK and source
  snapshot are kept under `dist/baseline-a7r2.6/`.

### a7r2.8 — movie settings, non-blocking recorder, gain-wheel A/S

- Movie prepare/stop/format-switch no longer block the worker Looper that
  delivers Sony `MediaRecorder` callbacks. a7r2.7 waited on latches inside
  `prepare()`/`stop()`, so `OnPrepared` and `OnStreamWrite.onCompleted` could
  not run: prepare looked like a 12 s timeout and stop looked stuck for 45 s
  even though the file was already complete. The recorder now prepares
  asynchronously with a 3 s fallback, one start retry if the body was not
  actually ready, and stop completion is driven purely by callbacks. Video
  journal writes are flushed and `sync()`ed per event.
- Video has a settings root menu (MENU): format, white balance, AF mode,
  exposure mode P/A/S/M, and exposure compensation. WB uses a popup list with
  arrow/dial selection instead of horizontal cycling, and rebases the
  approximate LUT WB baseline. AF mode is read from `af-mode-values` and applied
  with readback; only modes the body reports are listed.
- Video exposure follows the same gain-wheel policy as photos: M front
  aperture, rear shutter, gain wheel ISO (AUTO reachable); A gain wheel
  aperture with front/rear EV and AUTO ISO; S gain wheel shutter with front/rear
  EV and AUTO ISO; P front/rear EV and gain-wheel ISO. The video HUD now shows
  mode, shutter, aperture, ISO, EV and WB. Down enters AF-point selection with
  the existing Sony focus-point path; the shutter confirms the point and
  starts/stops recording.
- Photo mode fixes from the same feedback batch: in A/S the gain/control wheel
  now sets the one fixed parameter (aperture in A, shutter in S) while ISO is
  forced to AUTO and the front/rear dials provide EV; M can select ISO AUTO in
  the ISO list; Right opens a WB popup (arrows/gain wheel select, OK applies,
  MENU/Left cancels) instead of a clamped cycle; dial handling uses one final
  camera read to reduce latency.
- Video is still the fitted matrix/gamma approximation, not a per-frame 3D LUT;
  the encoded stream cannot take the still LUT. The complete Film Studio UI was
  not transplanted because its camera pipeline differs; the settings that
  matter for exposure and focus are covered above.
- Version `0.9.0-beta.5-a7r2.8` / code 104.

### a7r2.9 — DCIM recovery, LUTS scan diagnostics

- Field logs from the a7r2.8 capture failure show
  `failed prepare java.io.IOException: DCIM directory unavailable`: the SD
  card had no DCIM directory (for example after the user cleaned the card to
  add LUTS). `PhotoFiles` no longer aborts at this point; it creates the
  standard `DCIM` directory (or reports the exact path if creation fails), so
  the camera store service can add its own shot directory.
- The capture failure page now shows the failing stage and exception text
  (`拍摄未完成 · <stage>` / the reason) instead of only the generic message.
- LUTS scanning now accepts a one-level nested `LUTS/LUTS` layout, keeps a
  canonical containment check for symlinks, and writes
  `SONYLUT/LUTSCAN.TXT` with the exact path, directory flags, every listed
  entry and the final count. The import page shows the path and count. This
  distinguishes “files are not on the card”, “Sony FuFsys has not refreshed
  the directory”, and “files were placed in a nested folder”.
- Version `0.9.0-beta.5-a7r2.9` / code 105.

### a7r2.10 — movie exposure/AF and FuFsys LUT names

- Movie exposure writes now use Sony's empty-Parameters commit path
  (`createEmptyParameters` + `createParametersModifier` + `tryIgnoreInhibit`)
  instead of rewriting the full movie Camera.Parameters snapshot, which the
  A7R II rejects with `setParameters failed`. It covers scene mode, ISO, EV and
  WB; aperture/shutter still use the CameraEx relative increments. If the body
  rejects the empty object, the driver retries with the full snapshot and logs
  the detailed patch on failure. Since the body never delivers `OnPrepared`,
  the prepare fallback is shortened to 1.2 s; a premature start is retried.
- Video opens in the photo session's exposure mode (M/A/S/P), or the last movie
  mode, instead of always P. The front/rear/control wheel mapping therefore
  matches the photo UI: M front aperture/rear shutter/gain ISO, A gain aperture
  + front/rear EV, S gain shutter + front/rear EV, P gain ISO + front/rear EV.
  A/S force AUTO ISO. The HUD hint names the current mapping.
- Video AF: a half press now issues `Camera.autoFocus`; confirming an AF point
  also triggers a focus cycle. `af-mode=movie` is labeled “MOVIE 连续 AF”.
- Field `LUTSCAN.TXT` proved the external LUT problem is Sony FuFsys naming:
  `README.TXT` (8.3 uppercase) is readable, while externally copied files with
  spaces, long names or Chinese names list as `file=false size=0` (Chinese names
  appear as `####-###.cube`). The scanner and import page now explain that LUTS
  files must use 8.3 uppercase ASCII names such as `NEUTRAL.CUB`, and the scan
  report marks those unreadable entries. Resolve's boilerplate
  `TITLE "Generated by Resolve"` is ignored in favour of the file name, so
  imported LUTs remain distinguishable.
- Version `0.9.0-beta.5-a7r2.10` / code 106.

### a7r2.11 — auto ISO readout, gallery recovery, video keys and focus

- Photo/video A/S with ISO AUTO now shows the measured value, e.g.
  `AUTO 640`, instead of only `ISO AUTO`. The telemetry coalescing window was
  shortened from 250 ms to 50 ms so the HUD follows the image.
- Video HUD updates no longer perform a second `Camera.getParameters()` after
  a dial: `VideoExposure.readout()` reuses the final map from the commit that
  just happened. Exposure menu rows reflect the same snapshot.
- Gallery hides receipts whose photographs no longer exist (a cleaned or
  recreated DCIM). If all receipts are stale it explains how many are missing
  instead of failing on the first one; Library's photo list applies the same
  filter.
- Video C1 cycles M → A → S → M like photo C1. Right opens the video WB list.
  The front/rear/control wheels keep the photo mapping; the settings menu is no
  longer the only way to change exposure.
- Video focus: at open the driver logs `focus-mode`/`af-mode` and, if the body
  reports manual/fixed/infinity, tries to set `focus-mode=auto` (then
  `continuous-video`). A half press and AF-point confirmation issue
  `Camera.autoFocus`; `video_focus_*` records the result.
- Video look defaults to the camera's original colour because the A7R2 photo
  live view uses original colour (`originalPreviewOnly`). MENU → 取景色彩 can
  opt into the fitted approximate LUT; it is not the same as the full 3D LUT in
  the saved JPEG and cannot be made identical by the Sony MediaRecorder path.
- Version `0.9.0-beta.5-a7r2.11` / code 107.

### a7r2.12 — shutter-only movie recording and live AF arrows

- The video centre/OK button no longer starts or stops recording. Only the
  shutter (full press, including the movie shutter) starts/stops; inside the
  settings menu OK still applies a setting, and inside AF-point selection OK
  still confirms the point.
- While a clip is recording, the four arrow keys are live AF-point controls:
  each step commits the Sony flexible-spot point immediately, so AF-C can
  re-focus during the clip; the HUD box is shown for two seconds. Left no longer
  exits while recording — stop with the shutter first. The AF controller gained
  `liveMove()` so this path does not enter the selection UI or spend a second
  camera transaction.
- Version `0.9.0-beta.5-a7r2.12` / code 108.

### a7r2.13 — optional photo skin smoothing and chroma denoise

- MENU → 更多 → 照片美化 configures two independent strengths:
  皮肤平滑 and 降噪, each 关闭 / 弱 / 强 (default 关闭). Settings are stored
  in SharedPreferences (`beauty_skin`, `beauty_denoise`) and apply only to the
  LUT-graded JPEG; the original JPEG is stored before this pass and is never
  modified. Video is not processed.
- The native pass runs inside the retained-image LUT call, before the 3D LUT:
  `beauty_uyvy` performs a 3×3 skin-mask-selective luma blend with an
  edge-protection term, plus a luma-aware 3×3 chroma denoise. Skin detection
  uses the camera's pre-LUT YCbCr, which is more reliable than the graded
  colour; the fused 750×500 review bitmap therefore shows the effect too.
- New JNI `deviceLutReviewBeauty(image, cache, bitmap, swapUV, skin, denoise)`
  and `applyImage(..., skin, denoise)`; the stats JSON now includes
  `beauty_ms`, `skin_strength` and `denoise_strength`. Defaults remain 0, so
  the normal path is unchanged.
- A host test (`scripts/test_uyvy_native.sh`) checks that skin luma variance
  and chroma noise fall, a non-skin edge survives, padding/guards are intact,
  zero strengths are a no-op and swapUV results match.
- 42MP full-size processing adds time (about 0.8 s on the host CPU; the camera
  is slower, expect a few seconds). This is deliberately a light local filter,
  not AI retouching.
- Version `0.9.0-beta.5-a7r2.13` / code 109. Includes a7r2.12's shutter-only
  movie recording and live AF arrow controls.

### Baseline a7r2.13

- a7r2.13 is the approved baseline before the M-Film-inspired UI work.
  `dist/baseline-a7r2.13/` keeps the APK, a source snapshot and SHA-256.
  APK SHA-256 `26eeb82daf263d01d50ce0f08dc9c2d97a6ae98c778b67aebec5b8c907237103`,
  source archive SHA-256 `0ada1497329ca96cb251926298e78381ed14280bb8e34d9c7f4523987554b71c`.

### UI reference: M-Film

- Reference downloaded to `/home/topeet/Sony/M-Film-reference` (source and
  720×480 previews). M-Film is a GPL-2.0 Magic-Lantern film-camera UI; this
  project is GPL-3.0-only, so its code and raster assets are reference-only
  and are not copied. Barlow is OFL and may be used separately.
- Design language worth adapting (original implementation in our Java canvas):
  charcoal/black panels, amber accent, cream type, condensed sans for titles,
  small-caps labels, seven-segment digits for exposure/counter, fixed top
  status bar and bottom key-hint strip.
- M-Film's motion: spring/physics carousel, cubic ease-out
  (`1-(1-t)^3`), 340 ms push/pop, 520 ms fade-dip, 420 ms shutter-open
  transitions, snapshot-based screen slides, a film shelf with perspective
  canisters, roll dial, red-LED viewfinder HUD and a darkroom developing line.
  We can reproduce the feel with `View`/`Canvas` animation at 30–35 fps,
  redrawing only while a value changes.

### a7r2.14 — M-Film-style motion prototype

- MENU → 更多 → UI 动画预览 opens a self-contained prototype that does not
  touch CameraEx, photo or video. It is used to decide whether the camera panel
  can animate the inspired UI before any core screen is replaced.
- The prototype draws a dark/amber film shelf with a spring carousel, generated
  canisters (original vector art; no M-Film assets), film-strip motion, animated
  title/toast, a compact frame/EV HUD and a six-blade shutter iris.
- Controls: ◀▶ / front/rear dials move the shelf; ▲▼ / control wheel move the
  EV needle; SET/shutter closes and opens the iris and advances a frame; C1
  cycles 30 fps / 60 fps / uncapped; MENU/BACK exits.
- The overlay shows FPS, average draw time and the target rate. Stats are
  written to `SONYLUT/Rxxxxxxx/UI-MOTION.JSN` every two seconds and as a
  summary on exit. The prototype target is 30 fps; the camera panel will show
  whether it can hold 30/60.
- Version `0.9.0-beta.5-a7r2.14` / code 110. All a7r2.13 photo/video features
  and the a7r2.13 baseline are unchanged.

### a7r2.15 — safe diagnostic motion page

- a7r2.14's motion preview rebooted the camera on entry. a7r2.15 removes the
  continuous redraw from startup: the page draws one static frame and starts
  animating only after a key, so a graphics problem cannot run a full-screen
  software-canvas loop at power-on of the page.
- The background and seven canisters are cached into small bitmaps; gradients
  and shaders are removed. C1 runs an eight-second 30/60/unbounded benchmark
  and logs `MOTION.JSN` (8.3 name; the previous hyphenated UI-MOTION.JSN was
  rejected by FuFsys). `CRASH.TXT` and step events (`motion_create`,
  `motion_font`, `motion_view`, `motion_content`, `motion_first_draw`,
  `motion_settle`, `motion_bench`) make a platform reset diagnosable.
- Core photo/video code is unchanged. Version `0.9.0-beta.5-a7r2.15` / code 111.

### a7r2.16 — Canvas compatibility for the motion prototype

- The a7r2.15 fallback identified the initialization failure: the camera's
  Canvas implementation does not provide `drawOval` (an uncaught
  `NoSuchMethodError` is what restarted the camera in a7r2.14). a7r2.16 removes
  `drawOval`, `drawArc`, `drawPath` and the float-argument `drawRoundRect`
  overload from the prototype. It now uses only methods already proven by the
  working product UI: `drawRect`, `drawLine`, `drawCircle`,
  `drawRoundRect(RectF,...)`, `drawBitmap` and `drawText`.
- The page still enters as one static frame; C1 benchmarks for eight seconds.
  `MOTION.JSN`/`CRASH.TXT` remain 8.3 diagnostic files. The fallback TextView
  catches setup errors instead of letting the process die.
- Core photo/video code unchanged. Version `0.9.0-beta.5-a7r2.16` / code 112.

### a7r2.17 — motion prototype performance pass

- First benchmark measured 4.5 fps. The prototype now enables hardware
  acceleration only for `UiMotionDemoActivity` (the rest of the app stays on
  the camera-compatible software path), bakes all static labels (including CJK
  key hints, tabs and HUD labels) into the cached background bitmap, disables
  bitmap filtering/alpha in the carousel, and draws five instead of seven
  canisters.
- The benchmark starts at 30 fps. C1 starts/stops an eight-second run; each new
  run advances the benchmark rate 30 → 60 → unlimited. Normal carousel/HUD
  animation remains capped at 30 fps.
- `MOTION.JSN` still records rate, FPS and worst draw time. If hardware
  acceleration is not usable on this camera, the next fallback is the software
  path with dirty-rect redraw or a lower internal resolution.
- Core photo/video code unchanged. Version `0.9.0-beta.5-a7r2.17` / code 113.

### a7r2.18 — motion prototype ceiling measurement

- Field logs from a7r2.17: the camera panel is **640×480**; first draw
  175–307 ms and sustained 4–6 fps even with hardware acceleration requested.
  The frame cap/rate was not the limiter; drawing is.
- a7r2.18 now targets the native 640×480 surface directly (no canvas scale),
  still bakes static type into the background bitmap, and adds a minimal
  benchmark (C2) that draws only a solid background and a moving circle. C1
  runs the full shelf benchmark at 30/60/unlimited; C2 runs the minimal scene
  for 8 seconds.
- `MOTION.JSN` now records `motion_hw` (`view.isHardwareAccelerated()`),
  a per-section draw breakdown (background / shelf / text / stats) and the
  minimal benchmark. This tells us whether the ceiling is the whole-screen
  compositor or the canister/bitmap workload.
- Core photo/video code unchanged. Version `0.9.0-beta.5-a7r2.18` / code 114.

### a7r2.19 — vector scene after the bitmap finding

- a7r2.18 data: the panel is 640×480, hardware acceleration reports
  `view=false`, the minimal circle benchmark holds 23–25 fps, but the full
  shelf is 6 fps. The draw breakdown shows the full-screen background bitmap
  alone costs ~101 ms; canisters ~26 ms, dynamic text ~5 ms. The Canvas can
  draw simple vector content at ~25 fps, but bitmap blits are extremely slow.
- a7r2.19 removes the full-screen background bitmap and all canister bitmaps.
  Background, film strip, top/bottom bars, tabs, HUD labels and key hints are
  drawn as vector shapes/text; canisters are vector art, with label text only
  on the focused canister. Static type remains part of the per-frame vector
  path so the next `MOTION.JSN` breakdown can show whether text or the
  canister geometry is now the limit.
- C1 runs the full scene at 30 fps; C2 remains the minimal circle reference.
- Core photo/video code unchanged. Version `0.9.0-beta.5-a7r2.19` / code 115.

### a7r2.21 — text and chrome reduction

- a7r2.19 data: the full scene was still ~120 ms/frame (bg 60 ms, shelf 18 ms,
  dynamic text 37 ms, stats 4 ms); the minimal circle stayed at 23–25 fps. The
  full-screen bitmap was gone, but per-frame text and static chrome replaced it
  as the bottleneck.
- a7r2.21 uses the system sans-serif for Latin labels (the CJK UiFont is used
  only for the Chinese key hints), and hides static chrome (top labels, tabs,
  HUD titles, key hints) **while the animation is running**; it returns on the
  idle/static frame. The moving scene draws three canisters instead of five.
- C1 measures the moving scene; C2 the minimal circle. `MOTION.JSN` keeps the
  bg/shelf/text/stats breakdown. If text is still dominant, the next step is a
  low-text 24 fps scene or a separate static/dynamic layer.
- Core photo/video code unchanged. Version `0.9.0-beta.5-a7r2.21` / code 117.

The production photo path is still the original fast CameraSequence path:
Sony RAW develop → 42MP UYVY in device memory → 3D LUT applied in place →
original JPEG store → graded JPEG store. Video remains a separate,
unverified experiment.

## Build

```bash
cd /home/topeet/Sony/sony-lut-camera
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK=/home/topeet/Android/Sdk
export ANDROID_NDK=/home/topeet/Android/android-ndk-r16b
bash scripts/build_android.sh --release
```

Output: `dist/SonyLutTest.apk` (this local port keeps the upstream output name).

## Camera test checklist

1. Camera USB mode **MTP**; install with pmca-gui → *Install app*.
2. Camera still settings: **JPEG**, **Single**, **Extra Fine**, **L: 3:2**.
   The port will also try to select L: 3:2 if another size is set.
3. Open **LUT Camera** from the camera app list, choose a LUT, half-press to
   focus, full-press to shoot.
4. Expected: two new JPEGs in DCIM, both 7952×5304 Extra Fine — the untouched
   original and the LUT-graded version. Review shows the graded image; OK
   toggles original/graded.
5. Check half-press return, next shot in the same session, playback, exit and
   re-entry.
6. If capture fails, keep the error screen and copy the newest
   `SONYLUT/Rxxxxxxx/PROBE.JSN` (and `CRASH.TXT` if present) from the SD card.

### a7r2.3 control checklist

1. **Processing page**: after a shot, the developing thumbnail should be a dim
   but natural-colour copy of the current photograph, then fade to the LUT
   result. It must not show the old inverted blue negative. Saved originals and
   graded files remain 7952×5304.
2. **M mode**: logs should show `scene=manual-exposure`. Front dial changes
   aperture, rear dial changes shutter, the control wheel changes ISO; the HUD
   and saved EXIF follow. Exiting should restore the previous camera mode.
   If M readback fails, first set the camera's physical mode dial to **M** and
   retry; report the log.
3. **AF**: Down enters the app AF box; arrows move it, center confirms, MENU
   cancels. Half-press should focus near the box. The log records the commit
   path and readback; if the box moves opposite to the arrows, report the log.
4. **WB**: Right/Left cycles safe presets; the HUD shows the WB label and saved
   colors change. With approximate preview enabled, half-press/restore must not
   report a protected-baseline mismatch.
5. **Playback**: C3 (or the zoom scan code) toggles 1x/2x; arrows pan while
   zoomed; paging works at 1x. Delete asks for confirmation, then removes both
   JPEGs and the catalog receipt. Copy/compare unintended behavior and raw scan
   codes from `PROBE.JSN` / logcat `LutUI`.
6. **Key calibration**: the app accepts C3 659 plus 610/611/623/533/636,
   custom 588, AF/MF-mode 597 and the C1/C2 keys; KURU/DIAL_3 for the control
   wheel. Gallery key-downs are appended to `SONYLUT/KEYLOG.TXT`; press C2 and
   C3 once in playback and include that file if C3 still does not respond.
7. **Processing diagnosis**: each A7R II shot also writes `SOURCE.PNG` and
   `GRADE.PNG` in `SONYLUT/Rxxxxxxx`. If processing is still blue, send these
   two PNGs plus the screen photo; that distinguishes the app preview from the
   Sony graphics plane.

### a7r2.22 / a7r2.23 UI migration checklist

The product screens now use an original film-camera visual language (charcoal
panels, cream type, amber highlight, seven-segment exposure digits). Only the
design was studied from the M-Film reference; no GPL code or assets are used.

1. **Shelf**: five canisters are visible; the carousel springs between them and
   only the shelf band is repainted. Text on the focused canister appears after
   motion settles. C1/C2 still report the FPS probe.
2. **Menus**: library, import, gallery, help, options and beauty pages use the
   new list styling; row focus moves with the dial/d-pad and scrolls by page.
   Roll card under the preview shows total shots and the 36-frame roll.
3. **Photo HUD**: top mode badge + LUT name + battery; right-hand EV scale;
   bottom seven-segment shutter/aperture/ISO/EV/WB row; WB menu and AF box in
   the same palette. On entering live view a 420 ms shutter curtain opens over
   the preview. Processing is a darkroom page with a red safelight and four
   lit film frames; the live controls stay responsive.
4. **Video HUD**: standby shows the mode badge, status and format; recording
   shows a red dot, seven-segment timer and a red strip; menu rows match the
   photo menu. Exposure row is unchanged in content.
5. **Playback**: review bars use the same palette; the original/LUT chip pair
   sits under the top bar and hides with the UP info toggle. Comparing and
   deleting show a short toast.
6. **Motion pass (a7r2.24)**: page changes push in over 340 ms with a short
   black dip; the focused row indicator grows over 180 ms; LUT previews
   crossfade in over 220 ms; shelf/list actions show a transient toast;
   the WB and video format menus slide/fade in; the video REC LED blinks once
   per second; entering processing closes and opens the shutter over the
   darkroom; the safelight scan line tracks the reveal.
7. Confirm photo capture, video start/stop, AF, WB, zoom/pan, delete-group and
   return-to-capture paths are unchanged. If a screen looks stale after a
   partial redraw, note which page and send the screen photo. If the push
   transitions feel slow on the camera, report which page and the delay.

### a7r2.25 digital readouts + contact-print playback

1. **Bottom strip**: all five values are drawn with the seven-segment renderer;
   no plain-text value remains. Shutter shows `1/125` with a real slash and
   seconds as `2"`; aperture is `F2.8`; AUTO ISO reads `A640`/`A1600` (A plus
   the measured ISO); EV keeps its `+`/`-`; WB becomes a tube code
   (`AUt`/`dAY`/`SHd`/`InC`/`FLS`/`tEP`/`CUS`/`FLU`/`Und`/`---`). The photo and
   video HUD share the same code.
2. **Capture thumbnails**: after the receipt is written, the finished 750x500
   LUT review is scaled to 480x320 and stored as
   `SONYLUT/PHOTOS/THUMB/Pxxxxxxx.JPG` (8.3, ignored by the catalog). Deleting
   a pair also deletes its contact print.
3. **Contact sheet**: DOWN at 1x in playback opens a 4x3 contact print;
   LEFT/RIGHT = older/newer, UP/DOWN = rows, dial/control wheel also move,
   OK opens the selected photo, MENU/BACK closes. The grid paints the saved
   thumbnails as unfiltered 144x96 blits. Photos from older builds have no
   contact print; they are filled from Sony's existing screen nail (never a
   full 42MP decode) and the 480x320 print is written back to THUMB.
4. **Playback resolution**: the screen nail is still shown first, then after
   450 ms an `inSampleSize` decode of the actual original/graded JPEG (up to
   2048 px, as for zoom) replaces it without resetting zoom or pan. Page flips
   debounce the detail decode so only the settled photo is decoded.
5. Verify on the camera: AUTO ISO shows A + a number; fractions show the slash;
   WB codes match the selected preset; the contact sheet opens quickly and its
   thumbnails are sharp; opening a photo shows the sampled original; delete
   still removes the pair and its thumbnail.

### a7r2.26 stability + playback polish

1. **Seven-segment fix**: the digit segment tables were wrong (1 had no
   lower-right, 2/5/7/9 lit the wrong bars, 4 lacked upper-left). Digits now
   use the standard a-g sets; letter tables were corrected too (F/S/D/T).
2. **Playback reboot**: filling the contact sheet no longer decodes 42MP
   originals. Missing contact prints are generated from Sony's screen nail,
   serialized behind a loader lock, paced 160 ms apart, and written back as
   480x320. The 1x detail decode waits for thumbnails and page nails to go
   idle, and is skipped while the contact sheet is open. Every stage appends a
   line to `SONYLUT/GALLERY.TXT`; Java crashes dump their stack there.
3. **Video -> photo hang**: VideoActivity waits 900 ms after releasing
   CameraEx, and the library waits another 1200 ms before launching the
   capture process. The library shows "正在切换到拍照模式" during the handoff.
4. **Playback entrance**: opening a photo (entry or contact-sheet selection)
   plays a 420 ms shutter-curtain reveal.
5. **Yellow panels**: solid amber information blocks were replaced by charcoal
   paper tabs with hairline amber frames (mode badge, original/LUT switch).
   Film canisters now use a cream paper label between two narrow colour bands
   instead of flat colour behind the title. M-Film's own GPL-2.0 imagery is
   still not bundled; the look is rebuilt with original vector drawing.

### a7r2.27 movie handoff reset fix

The a7r2.26 handoff still reset the body. Evidence copied from the camera:
`SONYLUT/R01D604C/PROBE.JSN` is 0 bytes, the video journal ends at
`video_closed`, and the next capture journal starts at **104468 ms uptime**
while the previous boot had been up past **1919 s** — the camera rebooted during
the video -> photo switch, and the "hang" was the reboot itself. The recorder in
that session had been marked ready by the 1200 ms fallback and was released
618 ms later, while the 4K pipeline was likely still being allocated.

1. Exit is deferred until `prepare` has finished and the recorder has been
   stable for 1800 ms (`video_exit_wait`); a bounded 5 s guard still lets a
   failed prepare exit.
2. `releaseEverything()` is followed by 2500 ms before the result/finish, and
   the `:video` process kills itself 1 s after `finish()`.
3. The library polls `ActivityManager` until the `:video` process is gone
   (8 s cap), waits another 2500 ms, then launches the capture process with the
   UI showing each stage.
4. The still-camera open now explicitly requests `setRecordingMode(0)` and
   `setMovieMode(0)` instead of relying on inherited defaults.
5. `SONYLUT/HANDOFF.TXT` records video return, process exit, settle and capture
   launch on the library side, so a failure can be placed even if the capture
   process loses its journal. A capture that dies before drawing shows a toast
   and restores the LUT page; a 15 s preparation watchdog clears the old
   "正在加载 LUT 照片" stuck state.

### a7r2.28 seven-segment F, full-42MP playback, capture entrance, canister type

1. **Aperture F**: the letter segment table omitted `F` from the lower-left and
   middle segments (`e`/`g`), so `F2.8` lit only the top bar and upper-left.
   `F` now uses a/e/f/g as on a real tube.
2. **Playback**: the gallery heap is 24 MB (`GALLERY.TXT` shows
   `max=24576K`), so the old whole-JPEG `loadDetail` could only sample to
   994x663 and frequently failed with `内存不足`. At 1x Sony's ~1024 px screen
   nail already exceeds this 640x480 panel. Zoom detail now comes from
   `BitmapRegionDecoder`: only the visible rectangle of the 7952x5304 JPEG is
   decoded, at a sample tuned to the panel (tiles are ~1.2-6.5 MB). The tile is
   placed by source coordinates so zoom and pan stay continuous. EXIF
   orientations 1/3/6/8 are mapped without allocating a rotated copy; flipped
   orientations fall back to the nail. Nail cache and contact-sheet thumbs are
   released before a tile decode.
3. **Capture entrance**: the shutter curtain used to start at the shutter press,
   underneath Sony's own blackout, so only its first frame was ever visible.
   It now starts 120 ms after `restoreGraphics` returns the graphics plane, then
   the darkroom develop animation follows when the source copy arrives.
4. **Film canisters**: the cream label carries two larger lines, maker above
   and film type below (e.g. `Kodak` / `Portra400`), with the LUT grid size
   below; previous label text was 8 px and unreadable at shelf distance.

### a7r2.29 1x playback quality, directions, film-camera naming and credits

1. **1x playback quality**: a7r2.28 filled 1x with Sony's compressed screen
   nail (~1024 px preview), which the user saw as lower quality than older
   builds. `SonyPhotoPreview.loadBase()` now decodes the original JPEG once at
   `PhotoTiles.sampleFor(...,1400)` — typically sample 8, 994x663, ~2.6 MB for
   42MP — and swaps it over the nail via `replaceImageBitmap`. Only one base
   bitmap is kept; it is dropped before a new photo, a tile zoom or delete,
   and restored ahead of the preview cache. Region tiles still handle zoom.
2. **Navigation directions**: gallery contact sheet and page turns now follow
   the visible grid (`LEFT` previous, `RIGHT` next, dial in the same order);
   zoom pan moves the picture with the key instead of against it. The film
   shelf uses `LEFT`/`RIGHT` and the rotary dial (`controlDial`) to select a
   stock; the old left/right popup shortcuts are gone (import lives under
   MENU, playback under the playback key). Up/down keep their order.
3. **Canister label**: the maker now sits on the wide colour band at the foot
   of the canister, much larger and with automatic dark/cream ink for contrast;
   the film type stays on the cream paper label, grid size beneath it.
4. **Film-camera naming**: product name is `A7R2胶片相机`; `LUT 库` became
   `胶卷库`, `选择你的色彩` became `选择胶卷`, and the post-shot status reads
   `正在冲洗照片` (with `· 美肤/降噪` when enabled). Technical `.cube` file
   guidance keeps the word LUT.
5. **Credits**: the options footer and a new `关于` page name the original
   author 瓦尔基里 (小红书 / 小红薯 ID) and the upstream
   `sony-lut-camera` project, state that this port is also released under
   GPL-3.0-only, and thank the original author and open-source contributors.
   `README.md` carries the same attribution section.

### a7r2.30 film-load transition

Selecting a stock now replaces the old "正在加载照片" busy page with a
full-screen loading sequence drawn in `CameraPage`:

1. **Canister alone**: the panel is cleared to charcoal and only the chosen
   film canister fades/scales in, with `正在加载胶卷` below it.
2. **Leader pulled out**: after 380 ms the canister slides left and a 35 mm
   leader strip is drawn out of it — perforations, frame windows and an amber
   leading edge. This follows M-Film's loading gesture (design reference only;
   no M-Film code or imagery is used). If preparation is slower, the strip
   stays with a pulsing loading dot.
3. **Shutter blink**: when `PreviewFitting.ensure()` and the LUT selection are
   ready, the two-curtain shutter from the UI motion demo closes over the
   panel (360 ms, diagonal curtain texture, amber centre line).
4. **Capture hand-off**: `PhotoActivity` is launched while the curtain is fully
   closed, so its old "正在准备拍摄" loading page never appears. `PhotoHud`'s
   loading mode now paints only the charcoal background until the viewfinder is
   ready.

Keys are swallowed during the sequence, `onPause`/cancel/timeout clear the
state, and the preview-warning confirmation replays the same animation before
launching. Preparation still runs in the existing background task, so the
15 s watchdog and preview-error pages are unchanged.

### a7r2.31 playback pan and contact-sheet dial

- When zoomed, the arrow keys move the viewing window rather than the bitmap:
  `RIGHT`/`DOWN` reveal the right/lower part of the frame. The control dial
  continues to zoom.
- In the contact sheet, the rear control dial now moves one photo along the
  row (same as `LEFT`/`RIGHT`) instead of jumping a whole row; the arrow keys
  and the front/rear dials keep their previous, correct directions.

### a7r2.32 playback OOM, film labels and MOVIE button

- `SONYLUT/GALLERY.TXT` recorded `java.lang.OutOfMemoryError` in
  `SonyPhotoPreview.load()` while the 24 MB heap was already holding cached
  screen nails, contact prints and a 42 MP base decode. That uncaught OOM
  killed the shared camera-host process, which looks like a camera reset.
- All screen-nail/original decodes are now serialized on the loader instance
  so a 42 MP base tile and a nail can never decode concurrently. `load()`
  catches OOM, retries once at half resolution with RGB_565, and otherwise
  returns a normal `IOException`. The gallery fallback handler leaves playback
  instead of killing the process.
- Playback caches were cut to fit the heap (nail cache 7 MB, detail cache 8 MB,
  contact prints 3 MB). A base decode first drops contact prints and cached
  neighbouring nails; the nail cache entry is released once the base is shown.
  Prefetch is skipped while free heap is below 7 MB, and the visible photo
  gets a reclaim pass first.
- Film shelf: both label lines now show the film name (a word-aware wrap;
  short names stay on one line). The `25³` LUT badge is gone from the
  canister; the selected film's type is drawn after the page title, e.g.
  `选择胶卷  胶卷类型 25³`.
- Video: Sony's dedicated MOVIE button reports scan code 515 with key code 0
  (`video_key_unhandled` in earlier VIDEO.JSN logs). It now toggles recording
  exactly like a full shutter press, including while the AF-point picker is
  open.

### a7r2.33 preview setting, film-edge chrome, video look and standby AF

- Choosing a film no longer opens a preview-mode page, even when the fitted
  profile is missing or noisy. The global `当前胶卷的取景` choice is applied
  directly and the film-load animation always hands over to the finder.
- Photo review's solid amber footer is now a charcoal film edge with a sprocket
  row. The same perforation silhouette was added to the photo finder's bottom
  strip and to both black bars of the movie HUD.
- Movie viewfinding is always the approximate film look. The `取景胶卷`
  original-colour menu entry and its preference gate were removed; the fitted
  matrix/gamma is applied on open.
- While movie standby is idle (no recording, menu, AF picker or save), a
  guarded one-shot `Camera.autoFocus` runs every 1.6 s. The A7R II keeps AFC
  active only during recording; re-arming the camera's AF gives continuous
  standby focus without changing the movie AF mode.
- About: original author and GitHub are one row (小红薯 ID), the port is
  credited to `Yonitska（小破站ID）`, and the About page now thanks the
  reference projects: M-Film (HNGSeries, GPL-2.0), Film Studio, Recipe Lab
  (André Domingues / voxivoid, MIT), ShotDirector (MIT) and bonyback1's Ricoh
  mod (Apache-2.0). README attribution matches; design inspiration only, no
  third-party code or imagery copied.
- Version `0.9.0-beta.5-a7r2.33` / code 129.

### a7r2.34 always-visible AF frame and full help index

- The AF spot is permanent finder chrome in both modes. Photo capture no
  longer gates `PhotoHud.setAf` on manual mode or an active selector: every
  `productLive()` refresh shows the frame at the current point. Cancelling the
  selector keeps the frame instead of hiding it.
- Movie mode shows the AF frame as soon as CameraEx opens, and it stays through
  standby, recording, AF selection and cancellation. The temporary "hide the
  live AF box after two seconds" runnable was removed; only a failed camera
  open hides it.
- MENU → 使用说明 now lists 34 entries covering the current feature set:
  shelf selection and film-load motion, per-film preview choice, LUT import and
  8.3 naming, half/full press, AF selection and cancel, C1 exposure modes,
  front/rear/control dials, AUTO ISO, EV, WB, C2 pose director, the two-JPEG
  save, darkroom animation, beauty settings, playback, contact sheet, zoom/pan,
  whole-pair delete, movie recording formats/look, standby AFC, movie AF and
  exposure/WB, the movie→photo release guard, film-edge chrome, the finder
  info toggle, and About. The page continues past four rows with the usual
  paged list navigation.
- The "after the film animation, wait a few seconds" gap is the capture
  process opening `CameraEx` (about 3 s: camera open, exposure/AF restoration,
  RAW registration, preview start). The old loading screen was plain charcoal,
  so it read as a hang. `PhotoHud` now draws a staged film-loading page: the
  canister and leader stay visible, the film name is shown, and four stage
  beads advance through 装卷 → 相机 → 设置 → 取景 between the blocking Sony
  calls. The library hands over 420 ms after the shutter curtain starts
  closing.
- Version `0.9.0-beta.5-a7r2.34` / code 130.

### a7r2.35 approximate fitting retained on A7R II

- Confirmed by the user on hardware: the A7R II does support the fitted
  approximate look. The library always runs `PreviewFitting.ensure` again
  before capture, and photo capture loads the fitted profile on the 42m
  profile too; the `originalPreviewOnly` gate no longer blocks it. The global
  `当前胶卷的取景` choice still selects fitted versus original colour.
- If the stored profile is missing, capture fits it on the spot
  (`PreviewFitting.ensure`) instead of aborting. If that fails, the finder
  falls back to the camera's original colour and a `preview_profile_unavailable`
  event is logged; the shot is never blocked.
- The staged loading page from a7r2.34 is unchanged.
- Version `0.9.0-beta.5-a7r2.35` / code 131.

### a7r2.36 upstream performance port

- Downloaded upstream `EnglandLobster/sony-lut-camera` current `main`
  (`v0.9.0-beta.7-rc.9`) beside the project and compared it with the
  `v0.9.0-beta.5` base used by this port.
- Borrowed the product-only lazy `PhotoFiles` mode: capture no longer
  enumerates every JPEG in DCIM before opening CameraEx. Sony's named
  completed-store receipt still resolves the exact new file, and duplicate
  receipts are still suppressed after acceptance. Diagnostics keep the
  snapshot they need. On cards with many photos this removes startup work
  proportional to the photo count.
- Borrowed the Surface lifecycle gate: camera open now waits for
  `surfaceCreated`/`surfaceChanged`, not `Surface.isValid()`. On older PMCA
  API levels `isValid()` can remain false after a usable push-buffer surface
  has been delivered, which delayed or blocked the first open.
- Already present in this port, so not borrowed again: header-only JPEG
  confirmation after the named receipt, notification-path lookup without a
  DCIM scan, tolerance of nonzero receipt identifiers, 42MP native memory-map
  handling, and highest-quality JPEG selection.
- Deliberately not borrowed: upstream's A6300-only `namedCompletionVerified`
  whitelist (our A7R II path already trusts the named receipt plus header
  check), the 8/16 MiB LUT cache caps (they protect low-memory bodies but
  would evict more entries and slow the 42MP pass), and the full
  `CaptureStoreCompletion` index-recovery machine (it mainly prevents a lost
  callback from stalling a save; it is not a normal-path speedup and is a
  larger change than this pass).
- Version `0.9.0-beta.5-a7r2.36` / code 132.

### a7r2.37 approximate film recipes (Recipe Lab)

- Adds a persisted `拍摄模式` setting under `MENU → 更多`:
  - **真 LUT 模式** keeps the existing film shelf, RAW/develop path, full 3D
    LUT, original-plus-graded JPEG pair, beauty/denoise and fitted finder.
  - **近似胶片配方模式** is camera-first: choose one of 12 camera groups,
    then a recipe. The selected recipe is written into the camera's own
    Creative Style, saturation/contrast/sharpness, WB (auto or Kelvin with
    A-B/G-M), Picture Effect, exposure compensation and DRO before the
    shutter. The camera writes one JPEG; no RAW, map, filter, develop pass or
    LUT is used, so the photo is ready to use as saved.
- Imported Recipe Lab v1.11.1 data: 77 recipes in 12 groups. The mapping was
  reimplemented in `android/recipe/` from the upstream GPL-3.0 data, not
  copied from its app code. Kelvin values are passed in the camera's raw
  parameter units (for example 4000, not 400000). A-B is copied and G-M is
  negated because the camera counts magenta positive.
- Recipe video uses the same camera controls before `MediaRecorder.prepare`;
  no approximate LUT matrix is applied to the encoded clip. The movie
  recorder's empty-parameter path may drop unsupported keys on some bodies;
  drops are logged as `dropped=` in `PROBE.JSN` and never block recording.
- The camera picker uses twelve 480×300 Unsplash photographs bundled under
  `assets/recipe/cameras/`; they are UI illustrations only. Gallery contact
  prints and full-screen playback understand the single-JPEG receipts, and
  delete removes the one file plus its receipt.
- About and the third-party notices include the required attribution
  `Based on Recipe Lab by André Domingues` and the GPL-3.0 upstream link.
- Version `0.9.0-beta.5-a7r2.37` / code 133.

### a7r2.38 camera-first live film switching and pose photos

- Choosing a camera in the recipe picker now opens the still finder directly
  with that camera's remembered film; the intermediate film page was removed.
  The film can be changed inside the finder.
- **C3 film strip (photo and video):** C3 opens a numbered film chip in the
  top centre. The dial / arrows browse the current camera's films; every step
  is applied to the camera through the serialized control thread, so the
  finder shows the new look immediately. C3 confirms and persists the choice
  in the cross-process `recipe-selection.txt` (so Library, `:capture` and
  `:video` agree); MENU/BACK cancels and restores the previous film. The
  shutter and other controls are consumed while the strip is open.
- Each live apply starts from the pre-recipe control snapshot before writing
  the recipe, so a keep-WB / no-effect recipe cannot inherit the previous
  film's Kelvin or Picture Effect sub-option (`RecipeCameraLookTest`).
- Recipe capture no longer uses `CameraEx.startDirectShutter`. `takePicture`
  runs on the worker looper with a 20-second UI-side timeout; a timeout or a
  refused capture returns the gate to READY and the finder stays usable
  instead of freezing `close()` and requiring a battery pull. Late store
  callbacks after a timeout are still catalogued.
- Pose Director: POSE images are indexed from `/storage/sdcard0` and every
  common Sony mount alias, including category subfolders. Card changes paint
  the diagram immediately and debounce the bitmap decode by 1.7 s on a
  background executor; stale decodes are recycled. `SONYLUT/POSE.TXT` records
  the scanned roots, folder and indexed file count for device diagnosis.
- Version `0.9.0-beta.5-a7r2.38` / code 134.

### a7r2.39 post-preview recipe apply and bundled pose fallback

- Recipe colour is now applied only after `preview_started`, on the serialized
  manual-control thread. The previous pre-preview patch could block inside
  Sony's parameter service, leaving the finder black and the body unresponsive
  (sometimes the hardware shutter still worked). The patch is committed once
  and read back once; enum values absent from the body's advertised
  `*-values` lists are skipped instead of retried. A 9-second watchdog reports
  `recipe_apply_timeout` without taking the finder down.
- `close()` waits for an in-flight startup patch (bounded) before restoring
  camera controls, and live film switches no longer hold `controlLock` across
  vendor calls, so teardown and MENU/BACK cannot deadlock on a slow RPC.
- Pose Director bundles `c001…c176` under `assets/POSE` when built with the
  local `POSE/` folder. The SD-card folder is still preferred; the bundle is a
  fallback for cameras whose FUSE mount exposes `POSE` but returns no names.
  `SONYLUT/POSE.TXT` now also records `read`, `exec`, raw entry counts, the
  first eight names and the bundled-image count.
- Version `0.9.0-beta.5-a7r2.39` / code 135.

### a7r2.40 pre-preview recipe patch, safe live switching, pose diagnostics

- Recipe colour now commits while the preview is still stopped, the only
  window in this activity where Sony parameter writes are proven reliable
  without a `CameraSequence` (the launch freeze happened at the post-preview
  patch). A/S mode selection, the snapshot and the colour patch all run before
  `startPreview()`; `takePicture()` remains the ordinary camera path.
- C3 film switching stops the preview around the patch and restarts it after
  a 200 ms coalescing delay, so wheel bursts issue one vendor transaction
  instead of one per step. If the patch throws, the preview is restarted
  before the failure is reported.
- `RecipeCameraLook` now only clears `rgb-matrix-mode` when a LUT session left
  it on instead of rewriting an unchanged value. The rest of the patch still
  restores the pre-recipe snapshot, so keep-WB / no-effect recipes cannot
  inherit the previous film's controls.
- Recipe sessions rewrite a tiny `STAGE.TXT` per coarse startup step
  (`camera_open_begin` → `manual_open_done` → `recipe_apply_begin` →
  `recipe_look_ready` → `preview_started` → `closed`). `PROBE.JSN` has arrived
  empty after hard resets because fsync is a no-op on Sony's FUSE mount; the
  fresh marker file survives those resets.
- Pose Director no longer requires `File.isFile()` while indexing: the A7R II
  FUSE mount marks regular files with a mode that makes that call false even
  though the names list correctly. `SONYLUT/POSELOAD.TXT` now traces each
  decode (`file`/`asset`/`bitmap`/`decode_error`) and the UI apply, including
  stale-generation discards, so a remaining display failure names its stage.
  SD stems are indexed as both `c001` and `C0001`/`0001` forms (the card uses
  four-digit names while the pack ids are three-digit); bundled asset lookup
  also retries literal `POSE/<id>` and `POSE/cNNN` paths instead of trusting
  only the assets listing.
- Version `0.9.0-beta.5-a7r2.40` / code 136.

### a7r2.41 Recipe-Lab-style recipe finder

- Recipe photo no longer starts `ManualExposure` on the A7R II. The previous
  path wrote M scene, ISO and `shooting-preview` and then switched to A before
  the Creative Style/WB patch; that combination reset the body at launch on
  every build from a7r2.38 to a7r2.40. Recipe Lab, which supports the A7R II,
  only opens `CameraEx`, reads the parameters, and writes the recipe — it
  never starts an exposure session for a look. Recipe mode now follows that
  path: camera open → read-only recipe snapshot → `startPreview()`.
- The recipe is committed once, 250 ms after `preview_started`, while the
  finder is live, using the same one-shot `Camera.Parameters` patch as Recipe
  Lab. If the camera refuses the patch, the finder keeps the original colour
  and the shutter still works.
- C3 film switching applies each film in place on the live preview (no
  `stopPreview()`/`startPreview()` around the patch), with the same 200 ms
  coalescing. The previous stop/restart sequence was a second reset suspect.
- Recipe HUD no longer depends on a manual-exposure session: film name,
  camera, C3 strip, pose entry, info toggle and the centre AF frame stay
  visible. C1/AF/WB/manual-dial keys are inactive in recipe mode; the body's
  own dials and AF remain available, as in Recipe Lab.
- `STAGE.TXT` continues to record `recipe_snapshot_*`, `recipe_apply_*` and
  `preview_started` so a remaining reset can still be placed.
- Version `0.9.0-beta.5-a7r2.41` / code 137.

### a7r2.42 Recipe look before preview, photos instead of sketches

- Recipe mode now follows Recipe Lab's exact startup order: open `CameraEx`,
  read the recipe snapshot, commit the Creative Style/WB/DRO patch **before**
  `setPreviewDisplay()`/`startPreview()`, then start the live view. The
  a7r2.41 run still reset after `preview_started` without any further stage
  marker, so the post-preview commit window was the remaining difference from
  the working upstream app.
- Recipe mode no longer calls `OpenOptions.setPreview(false)`. Recipe Lab
  passes null `OpenOptions`; LUT mode keeps the no-preview session it has
  always used. Explicit still recording/movie mode is retained so a recipe
  launched after video cannot inherit the movie engine.
- The 250 ms post-preview apply is gone. Once the preview notification
  arrives, recipe mode only arms the ordinary camera shutter; it performs no
  parameter writes in that window.
- Pose Director no longer draws a stick figure at all. The bundled
  `PoseSilhouetteView` is removed. The image box shows the decoded photo, or
  a neutral text state (`正在载入…`, `无图片`, or the decode error); missing
  images never fall back to a drawn body.
- Card photos decode on the `pose-images` thread after a 180 ms debounce
  instead of 1.7 s, and the last 24 decoded cards are cached. The stale
  check now accepts a decode whenever the card is still the current one, so
  a slow decode that outlived its generation token is no longer thrown away.
  `POSELOAD.TXT` records render/cache/load/apply stages.
- Version `0.9.0-beta.5-a7r2.42` / code 138.

### a7r2.43 Recipe journal off the UI thread, heartbeat watchdog

- Recipe-mode `event()` no longer writes or fsyncs on the caller's thread.
  Every line is queued to a dedicated `recipe-journal` thread that opens,
  appends, and closes `PROBE.JSN` per line. The a7r2.42 build called
  `flush()` + `getFD().sync()` for every recipe event, including
  `recipe_ready` on the UI thread and store notifications on Sony callback
  threads; on this body's FUSE mount that sync can stall while the capture
  engine is live. The three newest launch freezes stopped at
  `preview_started state=NEW` with an empty `PROBE.JSN`, which fits a UI
  thread blocked in `sync()` (sometimes the hardware shutter still fired
  because the camera service itself was alive). LUT-mode journaling is
  unchanged.
- `STAGE.TXT` writes are queued on the same thread without fsync, so no
  recipe stage touches the card from the UI or a Sony callback.
- New `HEART.TXT` watchdog: a daemon thread rewrites the file every two
  seconds with `elapsedRealtime`, `ui_age_ms` (age of the last UI-thread
  heartbeat, 500 ms cadence), gate state, recipe name, `workerActive`, and
  `lastStage`. A hard hang or process kill now leaves the exact step and
  whether the UI thread was blocked; the marker survives because each write
  is a fresh open/truncate/write/close.
- New post-preview stage markers so a freeze can be placed precisely:
  `preview_returned`, `recipe_ui_begin`, `recipe_shutter_entry`,
  `recipe_ready`, `shutter_key action=… scan=… gate=…`, `arm_begin`,
  `armed`, `take_picture_begin`/`take_picture_returned ms=…`/
  `take_picture_error`/`take_picture_skipped`, `store_notified`,
  `store_verify`, `store_header_ok`, `preview_resume_begin`/`resumed`/
  `preview_resume_error`.
- `close()` stops the heartbeat before the camera is released.
- Version `0.9.0-beta.5-a7r2.43` / code 139.

### a7r2.44 Recipe S1/S2 keys never reach Sony's native handler

- `R0034E27` (a7r2.43) finally placed the freeze. Startup completed:
  `recipe_look_applied` at 218383, `preview_started` at 218635,
  `recipe_ready` at 218916. The next log line is
  `shutter_key action=0 scan=516 gate=READY` at 248913 — the user's
  half-press. `HEART.TXT` then shows `ui_age_ms=30870` with
  `stage=shutter_key action=0 scan=516 gate=READY`: the **UI thread** was
  stuck for over 30 seconds inside `super.dispatchKeyEvent()` for S1 until
  the process was killed. The recipe log, stage file and preview were all
  healthy; only the vendor key path hung.
- Recipe mode now consumes S1/S2 itself and never forwards them to
  `super.dispatchKeyEvent()`. This mirrors Recipe Lab exactly:
  - S1 down → `camera.autoFocus(null)`, logged as `recipe_autofocus`;
  - S1 up → `camera.cancelAutoFocus()`;
  - both run on a dedicated `recipe-af` looper, not the UI thread, so even a
    blocked vendor focus transaction cannot stop key dispatch or the
    heartbeat; logs add `recipe_autofocus[_cancel][_error]` with elapsed ms;
  - S2 down → the existing `takePicture()` capture path (`arm()`);
  - S2 up → consumed; upstream calls `cancelTakePicture()` here, but our
    `takePicture()` is posted to the worker, so cancelling could beat the
    trigger. The store notification or the 20 s recovery timeout finishes it.
- The native handler can forward S1/S2 safely in LUT mode only because that
  path owns a registered `CameraSequence`/direct-shutter session. A recipe
  has no sequence, so Sony's own key-triggered capture transaction ran
  against the freshly written Creative Style/WB and deadlocked.
- New stage markers `recipe_s1_down`, `recipe_s1_up`, `recipe_s2_down gate=…`,
  `recipe_s2_up` and event `recipe_autofocus[_error]` make a repeat
  distinguishable from the old failure.
- Recipe mode now swallows every key it does not bind instead of passing it
  to the vendor handler (`recipe_key_swallowed` logs the first press of each
  scan). MOVIE (515/637), C1 (622), AEL and DISP can otherwise start native
  camera transactions while the recipe session is live.
- `armRecipe()` refuses to start `takePicture` while a C3 film switch is still
  writing parameters (`arm_blocked film_apply_busy`); the user presses the
  shutter again. S1 focus is skipped in the same window.
- If `Camera.takePicture` files the JPEG without a `StoreImageComplete`
  receipt, the worker scans DCIM seven seconds after the trigger for the
  newest JPEG written around the press with the configured 7952×5304 header
  (`store_scan_begin`/`_found`/`_miss`/`_error`) and feeds the normal
  verification path. The scan is a no-op once the receipt advanced the gate.
- On the 20 s capture timeout the app now also calls Sony's
  `cancelTakePicture()` (from the `recipe-af` looper, never the UI thread) to
  release a wedged vendor trigger before returning the finder.
- `VideoActivity` no longer fsyncs `VIDEO.JSN` per event on the UI thread;
  the movie engine can make a FUSE sync stall just like the still path.
- Version `0.9.0-beta.5-a7r2.44` / code 140.

### a7r2.45 Recipe finder runs callback-free; main-looper tracing

- `R002BDF0` (a7r2.44) moved the freeze earlier. The recipe look applied
  (181261–181586), `startPreview()` returned (181620), the vendor
  `preview_started` notification arrived at 181823 and set the gate to
  `PREVIEW`, but the UI heartbeat's last beat is 181808. The posted
  `startRecipeShutter` never ran (`stage=preview_started`,
  `ui_age_ms=12199` at the final HEART write); no key had been pressed
  (no `shutter_key`/`recipe_s1_down`). The UI thread was blocked in a
  message that was already running before/at preview start — the last
  remaining main-thread callback sources were the Sony listeners.
- Recipe mode now registers **no `CameraEx` listener** at all
  (`camera_listeners_skipped`): no `PreviewStartListener`,
  `ShutterListener` or `StoreImageCompleteListener`. This is exactly the
  configuration Recipe Lab has proven on this body. S1/S2 handling is
  unchanged (our own handler, off-UI AF); the shutter itself was already
  our own `takePicture()` call.
- `startPreview()` returning now makes the recipe finder ready directly
  (`recipe_preview_returned` → `recipe_ui_posted` → `recipe_ui_begin` →
  `recipe_shutter_entry`); Recipe Lab likewise never waits for a preview
  notification. `onPreviewStarted` remains for LUT/approximate modes.
- Recipe mode also skips `CaptureTelemetry` (Sony change listeners into
  the UI) and `SonyReviewView.startDisplayTracking()` (DisplayManager
  listener into the UI). The finder's shutter/aperture/ISO readouts are
  seeded once from the parameters `open()` already read; recipes never
  change exposure. `showGraphicsOutput()` is a no-op when display tracking
  is off.
- Without the store receipt, the worker polls DCIM starting 1.2 s after the
  trigger, every 800 ms up to 9 s. The candidate must be absent from a
  pre-shot DCIM baseline (`recipe_store_baseline`, worker thread), keep a
  stable byte size for 350 ms, and carry a valid 7952×5304 JPEG header.
  Accepted files are added to the baseline; a scan miss refreshes it so a
  late file cannot be attributed to the next shot. Logs:
  `recipe_store_scan_begin/_retry/_candidate/_growing/_found/_miss/_error`.
- Diagnostics: the main looper now has a `setMessageLogging` printer that
  stores the banner of the currently dispatching message (`ui_msg`,
  `ui_msg_running`, `ui_msg_age_ms`). `HEART.TXT` copies it plus stack
  summaries for `main`, `prejpeg-probe`, `sonylut-manual-controls`,
  `recipe-af` and `recipe-journal` every beat; when `ui_age_ms ≥ 2000` a
  deeper `STACK.TXT` is written. Both survive a battery pull and name the
  blocking Java/native frame if a freeze repeats.
- Version `0.9.0-beta.5-a7r2.45` / code 141.

### a7r2.46 Recipe finder keys: C1, dials, AF, WB, video and C3

- On-device confirmation of a7r2.45: recipe preview, camera-native photo and
  the pose director all work. The other keys still did nothing because
  recipe mode never created a `ManualExposure` session (`manual==null`), so
  the C1/dial/WB branches were skipped, and the film strip listened only for
  Sony's C3 scan 659. `R0038196` logs six C3 attempts as scan **588**;
  `R004401C` logs **620**; the rear/control dials (635/634) were swallowed.
- `A7R2Keys.filmKey()` now accepts 659 **and** 588/620/636/533/610/611 for
  the film strip. C1 (622) and C2 (623) are deliberately excluded so they
  keep exposure and pose. The strip also confirms with the same family.
- `ManualExposure.openPreservingScene()` opens the recipe session after the
  live view and after the recipe look: it keeps the camera's current
  P/A/S/M, WB, EV and ISO. Nothing is written in P/A/S; only M re-asserts
  Sony's live exposure-preview mode. `MODE_P` joins the C1 cycle in recipe
  mode (M → A → S → P → M), with the gain wheel on ISO and the front/rear
  dials on EV. The LUT finder keeps M → A → S → M.
- The session starts 700 ms after `recipe_shutter_entry`, on the serialized
  `sonylut-manual-controls` thread, and defers while a capture or film apply
  owns the camera. Keys pressed before it is ready are logged
  (`manual_not_ready`) instead of failing silently.
- Recipe key routing: C1 cycles exposure mode; front/rear/control dials queue
  exposure steps; DOWN enters AF point selection (works even before the
  exposure session); RIGHT or the WB button (604) opens the WB menu;
  LEFT or MOVIE (515) hands off to `VideoActivity` with the recipe extras;
  C3/588/620 opens and confirms the film strip.
- The M capture guard (`prepareCapture`) is skipped in recipe mode: recipe
  captures are camera-native and already verified in a7r2.45. A successful
  live film apply now calls `manual.refresh()` so C1/dials re-read the new
  WB/EV. Close restores the exposure session **before** the recipe look.
- HUD footer: `C3 胶片 · C1 模式 · ↓ AF · → WB · ← 录像 · C2 摆姿`.
- Version `0.9.0-beta.5-a7r2.46` / code 142; 6,327,574 B, SHA-256
  `c9d96a64fd25a982cd3fdf4cb6181529677e24e0c5f21d9023af1fe4bc4c29da`.

Device checklist: wait for `recipe_exposure_ready`; C1 cycles P/A/S/M;
dials change EV/ISO/aperture/shutter; DOWN/arrows/OK select the AF point;
RIGHT opens WB; MOVIE/LEFT opens recipe video; C3 or the 588/620 buttons open
the film strip and switch films live. Watch `manual_open_recipe`,
`manual_mode_cycle`, `manual_dial`, `film_apply_done`, `video_key` and any
`recipe_exposure_error`/`manual_error` in `PROBE.JSN`.

### a7r2.47 Recipe capture: no darkroom page, shot-scoped receipt, battery, C3 confirm/animation

- Multi-shot freeze in a7r2.46: the DCIM scan for shot N could finish after
  shot N+1 was armed and call `recipeStoring()`, moving the new shot's gate
  ARMED → SAVING. The scan for N+1 then refused (`state != ARMED`), the
  develop/processing page stayed up with no completion, and the UI looked
  hung. `PreJpegGate` now has shot-scoped transitions
  `recipeStoring(shot)`/`recipeReady(shot)`; a late receipt for an older shot
  is rejected and the next capture keeps its ARMED state. Host test:
  `PASS recipe JPEG store is shot-scoped; stale receipt cannot consume the
  next capture`.
- The receipt path no longer decodes/metadata-scans a 42MP JPEG on the same
  `work` handler that runs `takePicture()` and the DCIM scan. A dedicated
  `recipe-receipt` `HandlerThread` handles the catalog/thumbnail work, so the
  next shutter press is not queued behind the previous decode.
- Film apply and capture arm are mutually exclusive under `controlLock`:
  `filmApplyInProgress` + `recipeApplyBusy` block `armRecipe`; the blocked
  shot records `filmApplyPendingId` and shows “胶片切换中 · 请再按快门”.
  The apply path defers while the gate is not READY
  (`film_apply_deferred`); `reapplyPendingFilm()` runs after the save receipt
  and after live recovery, and a successful apply commits
  `appliedRecipeId`. This removes the parameter-write-during-exposure race.
- Recipe capture uses one shutter animation only: no `beginDeveloping()`,
  no darkroom/LUT step, no processing page. `PhotoHud.beginRecipeCapture()`
  fires the shutter curtain and a top-center “正在写入照片 · x.xs” chip with
  three advancing film-edge marks; after 7 s it turns into
  “相机写入较慢 · 请稍候”. The chip is cleared by `endRecipeCapture()` when
  the save receipt lands or the app returns to live. Scan window extended to
  15 s (stable 400 ms). A timeout while ARMED cancels the shutter and
  recovers live with “拍摄超时 · 可再次按下快门”; otherwise it only shows a
  soft hint and never enters the processing page.
- Recipe battery: recipe mode previously skipped `CaptureTelemetry`
  entirely, so the HUD battery stayed at its placeholder. Added
  `CaptureTelemetry.openBatteryOnly(parameters)`: seeds shutter/aperture,
  registers only the sticky `ACTION_BATTERY_CHANGED` broadcast (no vendor
  CameraEx listeners, no calls into CameraEx), and logs
  `recipe_battery_ready`. The recipe observer copies only `battery`/
  `charging` onto the live readout, leaving the recipe's seeded exposure
  values intact.
- C3 film strip confirmation: while the strip is open, the full shutter
  (scan 518 or `KEYCODE_CAMERA`) and the center OK key confirm/apply the
  highlighted film and exit selection, exactly like C3. Half-press (S1) is
  deliberately not a confirm. Log `film_confirm_key`.
- Fast dial animation: `PhotoHud.setFilmStatus()` now receives the current
  film name/position/count plus previous/next names. `drawFilmStrip()` draws
  a charcoal sprocket ribbon with three vector cards (previous/current/next),
  canister colour strips, local position numbers and an amber outline on the
  current film. A dial move slides the cards 38 px with a 190 ms ease-out;
  redraws run at ~30 fps only while the selection is open. No bitmaps, no
  allocation-heavy work in `onDraw`.
- Version `0.9.0-beta.5-a7r2.47` / code 143; 6,330,936 B, SHA-256
  `0e1079127a2328cc22e7968b12fb2ca58112631e25c75e3e416451f2a5348c43`.

Device checklist for a7r2.47: take 5–10 recipe-mode shots in a row — each
should show only the shutter + write chip, return to live, and never enter
the darkroom/processing page; the battery percentage should be present in
recipe mode and update after charging state changes. Open the film strip
with C3 and confirm with the shutter and with the center OK key; both should
apply the highlighted film and leave selection. Move the dial while the
strip is open — the three-card ribbon should slide within ~200 ms without
dropping frames. Logs: `recipe_battery_ready`, `recipe_arm_blocked`,
`film_apply_deferred`, `film_apply_done`, `film_confirm_key`,
`recipe_timeout_soft`, `recipe_timeout_recovered`.

### a7r2.48 Recipe multi-shot: release Sony's capture trigger; new app icon

- a7r2.47 on-device log `R005616E` proved the remaining photo bug: shot 1
  saved normally, then every later full press logged
  `recipe_take_picture_error java.lang.RuntimeException: takePicture failed`
  within ~15 ms (shots 2–11). A bare `Camera.takePicture(null,null,null)` on
  this body leaves Sony's capture trigger latched until the shutter release
  is paired with `CameraEx.cancelTakePicture()`. Recipe Lab's `onKeyUp(K_S2)`
  and ShotDirector's `SHUTTER_UP` both do exactly that; our finder only
  forwarded the press. The preview-resume call in `verifyRecipeStore()` alone
  could not release the trigger, so `startPreview()` returned but every new
  `takePicture()` was refused.
- Full press now queues `cancelRecipeTrigger()` on the capture worker when
  S2 is released. Because `takePicture()` is already queued on that same
  worker, the cancel always runs after the frame request and never aborts a
  trigger that has not started. `verifyRecipeStore()` also calls
  `restartRecipePreview()` (cancelTakePicture + startPreview) after the JPEG
  is confirmed, so the next press starts from a known-good trigger and live
  view. Both paths log `recipe_trigger_cancel`, `recipe_preview_resumed` or
  the corresponding `_error` event.
- A late store receipt no longer touches a newer shot: the preview restart
  runs only when `gate.shot()==shot`; otherwise it logs
  `recipe_preview_restart_skipped`. A failed/timed-out capture returns
  through `recoverRecipeLive()`, which now posts the same trigger/preview
  restart on the capture worker.
- App icon: the launcher icon was a placeholder made of three coloured bars
  with the text “LUT”. `scripts/build_android.sh` now draws an original
  film-camera pictogram at 4× and downsamples to 72×72: cream body, amber
  top plate and lens ring, dark lens with a highlight, red shutter button,
  viewfinder and a 35 mm sprocket strip along the base. No third-party
  artwork is used.
- Version `0.9.0-beta.5-a7r2.48` / code 144; 6,336,449 B, SHA-256
  `439b433449a102268fd68637963f5408815731e4a0cf7dbcd65857fcb41b5087`.

Device checklist for a7r2.48: shoot at least 8 recipe photos in a row; every
press after the first should write a JPEG and show only the shutter + write
chip, with no “无法启动拍摄”. Logs should show `recipe_trigger_cancel` after
each release and `recipe_preview_resumed origin=store_verified`. Confirm the
new camera icon appears in the PMCA app list.

### a7r2.49 Video C3, five-language UI, realistic launcher icon

- Video C3 still failed after a7r2.46 because the video finder tested
  `A7R2Keys.c3()` only, while the photo film strip used the broad
  `filmKey()`. `R008CFC6` logged three presses of scan **588** (CUSTOM)
  during recipe video that ended as `video_key_unhandled`. The A7R II maps
  its physical C3 to 588 in that session; the same log family previously
  showed 620. A new `A7R2Keys.filmC3()` accepts C3/key 659 plus the narrow
  588/620 aliases, and both the open and confirm paths of
  `VideoActivity` now use it. It deliberately does not reuse `filmKey()`,
  whose 533/636/610/611 members would swallow video AF and focus keys.
- Five-language UI: **MENU → 选项 → 语言** offers 简体中文, 繁體中文,
  English, 日本語 and 한국어. `Lang` persists
  `sony_lut_camera_language` in the shared preferences and translates at
  draw time, so switching re-renders Library, photo finder/HUD, contact
  sheet/review, video and pose UI without restarting the app. The
  first-party catalog `I18N.TSV` holds 702 entries and 44 `{}` rules;
  resolution is exact match → rule (with nested translation of captured
  groups) → compound segments → Traditional conversion → English fallback
  for Japanese/Korean → source. Output punctuation is normalized per
  language (ASCII for English/Korean, Japanese `、` for Japanese).
- Traditional Chinese uses OpenCC `s2twp` phrase conversion for the recipe
  catalog and a generated character map (`TraditionalChinese.java`) for
  labels that have no dedicated zh-TW row. Recipe names and groups use
  Traditional Chinese for zh-TW and the English name for en/ja/ko.
- UI font: the previous CJK font lacked Hangul and a CFF Noto CJK TTC was
  rejected for old-Android risk. `scripts/build_ui_font.py` now merges a
  TrueType Noto Sans SC VF subset (ASCII, GB2312, all Java/TSV characters,
  complete kana and Japanese punctuation) with the Hangul glyphs of Noto
  Sans KR. The result is a static 8,369-glyph TrueType face, 2,398,124 B,
  renamed “Lut Camera Sans”, covered by the bundled OFL notice. Sources,
  digests and coverage are pinned in `FONTINFO.json` and `FONT-NOTICE.md`.
- Launcher icon: the vector film-camera pictogram was replaced by a crop of
  the licensed Unsplash Leica photograph already used in the recipe camera
  picker, with rounded corners and an amber rim. mdpi 72×72 and hdpi 144×144
  PNGs are staged by the build; the old Pillow generator and all stale
  drawable resources are removed. Provenance and the Unsplash License are
  recorded in `ASSET_LICENSES.md`.
- Packaging checks now require `I18N.TSV` (five non-empty columns, at least
  700 rows, matching `{}` counts, unique sources) and `FONT-NOTICE.md` in
  the APK, and verify the `UIFONT.TTF` digest. Host `I18nTest` reports
  `702 rows, 44 rules`.
- Version `0.9.0-beta.5-a7r2.49` / code 145; 6,605,923 B, SHA-256
  `48624419f47a10ae9ef8965497e61aed1cfc0cc24e7d76e5fc6853e51a893cdc`.

Device checklist for a7r2.49: in recipe video (idle and recording), press
C3 — the film strip must open, the dial/arrows must move the highlight, C3
or the shutter must confirm, and MENU/BACK must cancel; the live video look
must change after confirmation. Watch for `video_film_menu_open`,
`video_film_menu_step`, `film_apply_done` and the absence of
`video_key_unhandled` for scan 588. Switch through all five languages and
confirm Library menus/labels, the photo finder, custom HUD, contact-sheet
review and video menu redraw immediately, with no missing-glyph boxes in
Chinese, Japanese or Korean. Finally confirm the new camera icon in the PMCA
app list, then shoot a normal LUT photo as a regression check.

### a7r2.50 Phone auto-transfer, explicit video selection, native face detection

This is the first A7R2 build with a companion phone app. Both APKs use the
local test key and are not public distributions.

- **Camera transfer service.** `TransferService` runs in the main process as
  a foreground service (`START_NOT_STICKY`, `stopWithTask`), so capture in
  `:capture` and video in `:video` are independent of it. `TransferRuntime`
  serializes start/stop; `WifiDirectHost` uses reflection on
  `com.sony.wifi.direct.DirectManager` (enable Wi-Fi → enable Direct → wait
  for the last configuration → `startGo` → read SSID/PSK/interface/P2P
  address from group creation). A 12-second watchdog bounds the join. PSK
  values are never logged. On exit the host closes the server socket,
  unregisters receivers, removes the group, disables Direct and restores the
  original Wi-Fi enabled state before Sony's `ExitRequest` is issued.
- **Photo policy.** `PhotoTransferSource` walks only `SONYLUT/PHOTOS/P*.JSN`
  receipts, skips corrupt/incomplete receipts, and revalidates canonical
  path, regular-file, readability, size and mtime. True LUT mode exposes
  only `PhotoCatalog.Pair.graded`; recipe mode exposes the camera-native
  `single=true` JPEG. Originals, RAW and arbitrary paths never enter the
  manifest.
- **HTTP contract.** An original pure-Java server (no NanoHTTPD) requires
  `X-SonyLut-Token` before file access, binds to the P2P address when
  available, and returns a manifest of stable media IDs. It implements
  `HEAD`, single-range `206`, `416`, `If-Range`, strong `ETag`, `Last-Modified`
  and `Connection: close`, with bounded request lines/headers, at most three
  workers and an idle timeout. Error responses never include filesystem
  paths.
- **Video policy.** The camera page "手机传输 → 选择录像" scans completed
  `.MP4/.MTS/.M2TS` files in the allowed DCIM/MP_ROOT/M4ROOT/AVCHD roots,
  requires the file to be at least 3 seconds old and stable, and stores
  canonical path + size + mtime in `VideoSelectionStore`. The page never
  instantiates `CameraEx`; this is the "camera temporarily closed while
  selecting" behavior. The phone lists only selected videos and downloads
  only after an explicit tap.
- **Phone app.** `SonyLutRemote 1.0` (`org.hongen.sonylutremote`, minSdk 23,
  targetSdk 28, pure Java) pairs with SSID/PSK/pairing code/address. On API
  29+ it uses a reflectively loaded `WifiNetworkSpecifier` connector; on
  API 23–28 it uses `WifiConfiguration`, then binds the process to the
  camera network. A foreground `SyncService` polls photos and resumes
  downloads with `.part` + strong ETag + `Range`/`If-Range`. MediaStore is
  used on API 29+ and public directories plus media scan below it. The
  Android 10+ system confirmation for a no-internet Wi-Fi is documented in
  the app and cannot be bypassed.
- **Face detection.** "更多 → 人脸检测" is on by default. At capture start
  `FaceDetectionController` reads `face-detection-mode` and applies `auto` +
  `setFaceFrameRendering(true)` through `CameraEx$ParametersModifier`.
  **a7r2.51 removed the frame-rendering request** after device journals showed
  it co-occurred with every canceled direct shutter and the failed recipe
  capture; see the a7r2.51 section below. The app registers no
  `FaceDetectionListener` and draws no box. Failures are logged and never
  block capture. Video does not enable this.
- **Eye AF probe.** The first enabled capture writes a read-only
  `SONYLUT/EYEPROBE.TXT` (delete it to re-probe). It lists observed
  eye/face/focus parameter keys and public method names, applied values,
  errors, and `eye-af-verdict`. The observed A7R2 surface has no eye
  parameter or callable eye setter; `STILL_IMAGE_EYE_START_AF` is
  shutter-start AF, not subject Eye AF. The probe never invokes hidden
  setters. **Eye AF is therefore unavailable in a7r2.50; there is no fake
  eye-control switch.**
- **I18N and packaging.** All new camera strings are translated into the
  five languages (`gen_i18n.py --strict`). The release package check now
  requires `TransferService` (not exported, stops with task), the transfer
  permissions, the transfer/face classes, and still rejects diagnostics.

Build artifacts: camera `dist/SonyLutTest.apk`, 6,649,247 B, SHA-256
`acb3239a98dad9b7da58309445badd41ae7438bf9d43bda172710604bb79d154`,
version `0.9.0-beta.5-a7r2.50` / code 146. Phone `dist/SonyLutRemote.apk`,
115,642 B, SHA-256
`c04c8032170e900aa545b5dec25f3a5750bbe5df5ffd50c15979e42ecbc4fdff`,
version 1.0 / code 1. Details are in `dist/BUILDINFO.json` and
`dist/PHONE-BUILDINFO.json`. Both pass their package checks.

Device checklist for a7r2.50 (not yet run on hardware):

1. Pair once, then leave and re-enter the camera app; confirm the SSID/PSK
   and pairing code remain stable, and the phone reconnects with one tap.
2. In LUT mode, shoot several photos while the viewfinder is active; confirm
   each graded JPEG arrives, hashes match the camera's graded file, and the
   original is never listed. Repeat in recipe mode and confirm the delivered
   file is the camera-native JPEG.
3. Toggle face detection; confirm face-priority AF still works, that no
   capture is canceled with it enabled, the camera parameter is restored after
   closing, and `SONYLUT/EYEPROBE.TXT` is written without credentials or
   paths. (a7r2.51 no longer requests the vendor face frame.)
4. Select videos on the camera, download one from the phone, interrupt it,
   and confirm Range resume; then unselect it and confirm the phone reports
   the list no longer contains it.
5. Exit the app: confirm the HTTP port is closed, the Wi-Fi Direct group
   disappears, the notification is gone, and Sony's native camera starts.
6. Record any failure of the Android 10+ system confirmation, service
   survival in the finder, or Wi-Fi Direct PSK stability. Do not describe
   these flows as device-verified until this checklist passes.

### a7r2.51 Capture after mode switching: face rendering and duplicate launches

The SD-card journals showed two unrelated failure mechanisms behind "after one
photo, switching modes can leave the camera unable to shoot".

- **Face frame rendering vs. third-party capture.** Every LUT session that
  requested `setFaceFrameRendering(true)` either ended with
  `ShutterListener status=1` (CANCELED) — 0.65 s, 0.93 s and 3.16 s after
  `armed`, with the physical release arriving at the same moment — or (recipe)
  had `takePicture()` return in 5–9 ms and never wrote a DCIM file for the 15 s
  scan window. Sessions with the same `face-detection-mode=auto` and no
  overlay captured in 52–458 ms. The A7R2 already reports
  `face-detection-mode=auto`, so the overlay was the only parameter delta the
  feature introduced.
  a7r2.51 therefore:
  - never calls `setFaceFrameRendering(true)`;
  - skips `setFaceDetectionMode("auto")` when the camera already reports
    `auto`, so the default session performs no parameter write at all;
  - only writes `auto` when the camera was in another mode, and restores that
    mode before release;
  - logs `face_detection_applied ... rendering=suppressed_capture_safety` and
    records the write/no-write decision in `EYEPROBE.TXT`.

  The menu still enables the camera's face-priority AF. It no longer promises
  a vendor face frame: on this body the overlay is not safe for the
  third-party direct shutter or bare `takePicture()` path.
- **Duplicate capture launches after video.** In one boot, the shelf became
  interactive during the 2.5 s `video_settle_done` wait; a manual recipe
  launch at 617587 and the delayed auto launch at 618736 both started
  `PhotoActivity`. The first session saved a photo and the second returned
  `capture_result code=0 data=false`. a7r2.51 keeps the shelf busy during the
  settle, invalidates the pending video return on a manual launch, page pause
  or new launch, token-guards the film-animation and preview-choice callbacks,
  and adds a single-flight guard around `PhotoActivity`/`VideoActivity`
  starts.

Build artifacts: camera `dist/SonyLutTest.apk`, 6,650,129 B, SHA-256
`29607aa1a26bd02dcd6c053b39366bf3d6d1aee49359cf481f1792a3c4c582b3`,
version `0.9.0-beta.5-a7r2.51` / code 147. Device check: repeat LUT → shelf →
LUT, video → photo, recipe → photo, and face detection on/off, taking at
least two photos in each mode.

### a7r2.52 Movie AF lifecycle and capture-after-video guards

Device report after a7r2.51: LUT stills were fine, movie recording worked but
the lens would not focus, and afterwards neither true-LUT nor recipe stills
could capture.

- **Movie AF never completed.** R00685E2 logged 11 `video_focus_begin` and no
  `video_focus_result`. A single S1 press reaches `dispatchKeyEvent` as scan
  516 and scan 517, so the old code issued two `Camera.autoFocus` requests for
  one press. It also never called `cancelAutoFocus()` when S1 was released;
  in R0053221 the first autoFocus returned `success=true` after 856 ms and
  every later request went unanswered. a7r2.52 now:
  - merges scan 516/517 presses that arrive within 450 ms while the first
    request is still pending;
  - allows only one in-flight request and cancels it on S1 release
    (`video_focus_canceled`), matching the proven `autoFocus(null)` /
    `cancelAutoFocus()` S1 pattern used by ShotDirector (MIT);
  - lets a user half press replace a pending standby request immediately,
    while the 1.6 s standby loop may only replace its own request;
  - stops the standby loop as soon as exit starts and cancels any pending
    focus before restoring parameters, stopping preview and releasing CameraEx.
- **True-LUT full press landed inside the preview gate's WAITING window.**
  In R0079E5F both full presses arrived 579 ms and 551 ms after
  `auto_preview_capture_ready`; the S1/S2 events were therefore consumed by
  `AutoPreviewGate`, the half-press `guardManualCapture()` never ran, and
  `manual_pre_shot ... preview=iris_ss_iso_aeunlock safe=false` reached the
  direct shutter, which Sony cancelled with `status=1`. a7r2.52 calls
  `ensureManualCaptureGuard()` from the gate's `ready()` callback (posted to
  the UI thread) and again immediately before `arm()` on a full press, so the
  M-mode scene/ISO/WB/EV write and `shooting-preview=off` always precede
  `startDirectShutter`.
- **Recipe store after video.** The movie session could leave an unfinished
  vendor AF request behind. Both still paths now call `cancelAutoFocus()`
  immediately after `getNormalCamera()` (`focus_canceled origin=open`) and
  again before restoring the AF area/releasing CameraEx (`origin=close`).

Build artifacts: camera `dist/SonyLutTest.apk`, 6,651,619 B, SHA-256
`d5e641397d40a8d731cc5306fec5808094347db159e9cb1a494fdd9d4e16b36c`,
version `0.9.0-beta.5-a7r2.52` / code 148.

### a7r2.53 S1 release, post-movie capture and the half-press finder colour

The a7r2.52 device run (R0064AAF, R0071DFF) showed that neither of its AF
changes had reached the vendor:

- **`cancelAutoFocus` on S1 release was dead code.** `VideoActivity` returns
  `true` for every `ACTION_UP` near the top of `dispatchKeyEvent`; the
  half-press branch with `cancelVideoFocus("half_release")` sits below that
  return. R0064AAF therefore logged no `video_focus_canceled` for any S1
  release; the first `Camera.autoFocus` stayed in flight and every later
  request was skipped. The release cancel now runs before the generic UP
  return, and the focus call uses the callback-less `autoFocus(null)` form
  already proven by ShotDirector (MIT); the A7R2 movie wrapper never invoked
  `AutoFocusCallback` in 11/11 requests.
- **The still path now focuses on S1.** When the auto-preview gate becomes
  READY it runs the capture guard and then, on the serialized control thread,
  `cancelAutoFocus()` + `autoFocus(null)`. A7R2 cancels this request when S1
  is released. This also clears a request inherited from the movie session;
  every still capture in the 101 pre-a7r2.50 runs ended with
  `ShutterListener status=0`, while a7r2.51/.52 sessions after a movie ended
  with CANCELED 0.6–0.9 s after S2, the timing of an AF timeout. A second
  cancel runs after `startPreview()` in the still session because the earlier
  cancel (before preview) throws `cancelAutoFocus failed` on this body.
  `camera_focus_state` records focus-mode/af-mode/area/point/shooting-preview
  at open for the next comparison.
- **The finder keeps the approximate colour through a half press.** The
  preview baseline is still restored and `startDirectShutter` is still armed
  on it, but the gate now re-applies the approximate matrix/gamma immediately
  afterwards. When S2 arrives, `captureBaseline()` restores the original
  matrix/gamma synchronously before the key is forwarded to Sony, so the
  stored original/developed frame is not tinted. Releasing S1 or finishing a
  shot returns to the approximate look as before. This changes the inherited
  "half-press shows original colour" design only for the A7R2 port; video was
  already always approximate and recipe preview shows the recipe itself.

Build artifacts: maintainer's local camera build (with the untracked
`local-assets/` sample LUTs and pose pack) `dist/SonyLutTest.apk`, 6,652,367 B,
SHA-256 `8e11cd0db2f1810794491bc483be9e43b789aeee5cb80b4f2d6fa8f7b9a9052e`,
version `0.9.0-beta.5-a7r2.53` / code 149. The public source tree ships neither
asset set; see `README.md`. Device check: LUT stills with the
finder staying approximate during half press → movie standby/record half-press
AF → return → LUT stills in S and M → recipe stills; copy the run journals.

### a7r2.54 Half-press hold between LUT shots and recipe save

The a7r2.53 device check: movie AF now works, but a second true-LUT shot
required releasing S1, and recipe mode never left "saving".

- **S1-held shots.** `AutoPreviewGate.shotStarted()` clears the gate's `held`
  flag. After the first shot the gate returns to PREVIEW, so with the user
  still holding S1 the next S2 was swallowed and only a release + new half
  press prepared another capture. a7r2.54 tracks the physical S1 state in
  `PreJpegProbeActivity.s1Held` (scan 516/517/FOCUS down/up). When the
  post-save colour preview returns and S1 is still down, it calls
  `autoPreview.press()` to restore the baseline, restart the preview and arm
  `startDirectShutter` again (`auto_half_rearm`). Releasing S1 during that
  preparation still cancels it through the normal `held` check.
- **Recipe save.** Comparing the last good recipe sessions (R0086C2B,
  R00D2E03: `recipe_store_scan_found` at ~3.6 s) with the failing
  R00805E1 shows the failing session is the only one carrying
  `face_detection_applied origin=recipe`; it logged `takePicture call_ms=16`
  and then no DCIM file. The recipe path is a bare `takePicture()`
  transaction and previously avoided every extra vendor call. a7r2.54 skips
  `FaceDetectionController` entirely for recipes
  (`face_detection_skipped reason=recipe_capture_safety`) and calls
  `cancelAutoFocus()` immediately before `takePicture()`
  (`recipe_focus_cancel`) so an S1 focus drive cannot hold the JPEG
  transaction.
- **Artifacts.** Maintainer build `dist/SonyLutTest.apk`, 6,652,630 B,
  SHA-256 `a10ba74a5059249f632d9bf760777dd2806c762491150649ef7ab8c2b379cfae`,
  version `0.9.0-beta.5-a7r2.54` / code 150. Device check: hold S1 and take
  three true-LUT shots without releasing it; then take at least two recipe
  shots and confirm `recipe_saved`. Journals to attach: `auto_half_rearm`,
  `face_detection_skipped`, `recipe_focus_cancel`, `recipe_take_picture`,
  `recipe_store_scan_found`/`recipe_store_scan_retry`.

### a7r2.55 Revert of the still preview/AF experiments

The a7r2.54 journals showed why the latest build felt worse:

- R002F0A1 repeatedly ran `auto_preview_restore` → `auto_preview_applied` →
  `auto_preview_capture_ready` → `lut_focus_start`, followed milliseconds later
  by `focus_canceled origin=half_release`. Each short S1 press started a focus
  drive and immediately cancelled it; the executor was still being held when a
  full press arrived, and the cycles also re-applied the preview matrix after
  the direct shutter had been armed. R003AE19 then entered movie mode with no
  useful AF, and R004F793 could not focus after returning.
- a7r2.55 restores the still path to the a7r2.52 code: the original
  `AutoPreviewGate` (baseline on S1, approximate colour after release/save),
  no `captureBaseline()`/`keepColorOnHalfPress` re-application, no LUT
  `autoFocus`/`cancelAutoFocus` on S1, no `s1Held` auto re-arm, and no
  open/preview `cancelAutoFocus` calls. `AutoPreviewGateTest` is back to the
  a7r2.52 contract.
- Kept: the a7r2.53 movie fix (release cancel, callback-less `autoFocus`),
  the a7r2.52 capture-guard fallback, the close-time AF cancel, and the
  a7r2.54 recipe isolation (no FaceDetectionController; cancel AF immediately
  before `takePicture()`).
- Artifacts: maintainer build `dist/SonyLutTest.apk`, 6,651,697 B, SHA-256
  `03130a4c5c5397f88c7830adb39e20596fad126fc591f484867ac09a0da58de5`,
  version `0.9.0-beta.5-a7r2.55` / code 151.

## Main risks

- Sony `CameraSequence`/`DefaultDevelopFilter` and the 42m map are private
  firmware interfaces; the A7R II may reject a map revision accepted by the
  A6300.
- 42MP UYVY is about 80.45 MiB; the LUT pass and two JPEG stores take longer
  and use more memory than 24MP. OOM or a vendor timeout would need a fallback.
- The bundled approximate live-view profiles were fitted for A6300 color.
  Full LUT output is unaffected; preview color may differ more on A7R II.
- This local build is signed with the local test key, not the upstream release
  key, and is not suitable for public distribution.
