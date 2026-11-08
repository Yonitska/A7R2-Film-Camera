# A7R2 Film Camera

**An experimental, open-source in-camera film camera for the Sony A7R II
(ILCE-7RM2).** It runs *inside the camera* as a Sony PMCA app: no phone, PC, or
network is needed to take a photo. The app applies a full 3D LUT to 42 MP RAW
captures, offers a camera-native "film recipe" mode, records video, and replaces
the usual Sony interface with a film-shelf / darkroom UI.

> **Status: beta, unfinished, and not affiliated with Sony.** The current build
> is `0.9.0-beta.5-a7r2.53`, signed with a local test key and tested only on an
> ILCE-7RM2 running firmware 4.01. It is not a polished product. Read
> [Known issues](#known-issues) before installing it.

The Android package is `org.hongen.sonylutprobe`; the on-camera display name is
**A7R2胶片相机**. This repository is the source tree for the A7R II port.

## What it is

Sony PMCA apps are Android applications installed on the camera and launched
from the camera's app menu. This project uses Sony's private `CameraEx`,
`CameraSequence`, and `DefaultDevelopFilter` interfaces at runtime to:

- capture a 42 MP RAW frame (7952 × 5304),
- develop it with Sony's still pipeline,
- apply a complete 3D LUT in native code on the camera,
- save the camera's original JPEG **and** the LUT-graded JPEG to the SD card.

Live view cannot run a full 3D LUT at 42 MP, so the finder uses a fitted
matrix/gamma approximation of the selected LUT. The saved graded JPEG always
uses the full LUT.

There is also a second, deliberately different path: **film recipe mode**.
Instead of processing RAW, it maps a film recipe onto Sony camera parameters
(Creative Style, white balance, saturation, contrast, etc.) and lets the camera
write its own JPEG. This is faster and needs no RAW pipeline, but it is an
approximation of the recipe, not a full LUT.

## Features

- **True LUT photo mode** — full 3D LUT processing at 42 MP, EXTRA FINE JPEG,
  original + graded files saved as a pair.
- **Film recipe mode** — 77 Recipe Lab recipes across 12 camera groups, mapped
  to Sony picture parameters; camera-native JPEG output.
- **Film-style interface** — film shelf/canisters, seven-segment exposure HUD,
  shutter animation, darkroom progress, contact sheet, and playback.
- **Manual exposure** — P/A/S/M, ISO, shutter, aperture, EV, white balance,
  front/rear/control dials, and selectable AF point.
- **Video recording** — 4K XAVC S, AVCHD, and MP4 profiles through Sony's
  `MediaRecorder`; exposure/WB/AF controls and an approximate film look.
- **Live film switching** — C3 opens the film strip in photo, recipe, and movie
  modes.
- **Face-priority AF toggle** — uses the camera's native face detection; the app
  does not draw a fake face box. Subject Eye AF is not exposed by this body.
- **Pose Director** — optional posing-card overlay. The public repository does
  **not** include a pose pack; supply your own compatible pack.
- **Five languages** — Simplified Chinese, Traditional Chinese, English,
  Japanese, and Korean.
- **Battery/status readouts, help, and about pages.**
- **Phone transfer (experimental)** — a companion Android app and a camera-side
  Wi-Fi Direct service. This is currently **not usable**; see known issues.

## Known issues

These are the current, user-reported problems. They are not fixed yet unless a
later release note says otherwise.

1. **The shutter can hang after a photo.** After one shot the camera may appear
   stuck; pressing the shutter again is required to continue.
2. **Recipe mode cannot capture.** In the approximate film-recipe mode the app
   stays on the "writing to storage" screen instead of saving a photo.
3. **Phone connection mode is unavailable.** The camera-side transfer service or
   the phone companion cannot currently establish a usable connection.
4. **Many other bugs.** This is a beta built around private Sony interfaces.
   Camera hangs, failed saves, and mode-switch problems are still possible.

The app can leave the camera in a state that requires restarting it. Do not
install it on a body you cannot afford to reset, and do not use it for
irreplaceable work. Diagnostic journals are written under `SONYLUT/` on the SD
card; they are the most useful thing to attach to a bug report.

Development board: <https://github.com/users/Yonitska/projects/1>

## Installation

1. Install [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) on a computer.
2. Connect the camera in MTP USB mode and install the release APK. This project
   is built and signed with a **local test key**, not a Sony key.
3. Open **A7R2胶片相机** from the camera's app menu.

No ADB, Telnet, or Wi-Fi is required for ordinary photo capture. The optional
`dev-connect` tool is a development aid, not part of the shooting flow.

Supported body: **ILCE-7RM2, firmware 4.01** (the only body this port has been
tested on). Other Sony models are not supported. The A7R II 42m memory map is
extracted from the camera and is not included in this repository; see
[Assets and redistribution](#assets-and-redistribution).

## Assets and redistribution

This is a **source-first** repository. Several assets used by the maintainer's
local camera build cannot be redistributed publicly, so they are intentionally
absent:

- **Sample `.CUB` LUTs** — the ten Fuji/Ilford/Kodak/Ricoh files have no
  license text. The public `android-assets/CATALOG.JSN` is empty. Import your
  own `.cube` files from the SD card (`LUTS/`) in the app.
- **Pose pack and pose images** — the ShotDirector-derived card data is limited
  to personal study and is not included. Without a pack the Pose Director has
  no cards.
- **Sony firmware memory maps** — including the A7R II 42m map. `vendor/` is
  git-ignored and never part of the public source.

The untracked `local-assets/` directory is the maintainer's private overlay for
local device builds; it is not published and grants no rights. See
[ASSET_LICENSES.md](ASSET_LICENSES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.

Fonts (SIL OFL), the first-party UI catalogs, and the Unsplash camera photos are
included under their respective terms. The original code is GPL-3.0-only.

## Building

There is no Gradle build. The project uses the Android SDK command-line tools,
`javac`, `d8`, `aapt2`, `zipalign`, and `apksigner`, plus NDK r16b for the
native LUT/JPEG code.

Requirements: Linux or macOS, JDK 17, Android SDK with build-tools 36 and 30.0.3,
NDK r16b, Python 3, and a 32-bit ARM toolchain for the native tests.

```sh
export ANDROID_SDK=/path/to/Android/Sdk
export ANDROID_NDK=/path/to/android-ndk-r16b
export JAVA_HOME=/path/to/jdk-17

python3 scripts/bootstrap_dependencies.py      # pinned external sources
bash scripts/build_host.sh                     # host native + Java tests
python3 scripts/test_offline.py
bash scripts/test_product_ui.sh
bash scripts/test_transfer.sh
bash scripts/test_lut_cache.sh
bash scripts/test_preview_fit.sh
bash scripts/build_android.sh --release
python3 scripts/check_package.py --variant release
```

See [docs/BUILD.md](docs/BUILD.md) for the full setup and
[README-BUILD-LOCAL.md](README-BUILD-LOCAL.md) for an example Linux
environment. A fresh clone has no sample LUTs and can build with the empty
catalog; the APK will start with no built-in films until you import a `.cube`.

The phone companion (also experimental) is built separately:

```sh
bash scripts/build_phone.sh
python3 scripts/check_phone_package.py
```

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/app/` | Library, capture launcher, gallery, and movie activities |
| `android/capture/` | `PhotoActivity`/`PreJpegProbeActivity`, RAW capture, exposure/AF, save gates |
| `android/preview/` | Approximate preview fitting, matrix/gamma session, native-preview driver |
| `android/recipe/` | Film recipe data and Sony parameter mapping |
| `android/ui/` | Film shelf, HUDs, contact sheet, pose overlay, UI font/style |
| `android/transfer/`, `shared/transfer/` | Camera Wi-Fi Direct service and transfer protocol |
| `phone/` | Android phone companion app (separate APK) |
| `native/` | C++ 3D LUT, UYVY, JPEG, cache, and preview fitter |
| `android-assets/` | Public assets only: fonts, UI catalogs, Unsplash photos, empty LUT catalog |
| `android-tests/`, `scripts/` | Host tests, build, packaging, and device-log tools |
| `docs/` | Port history, architecture, build, and release notes |
| `dev-connect/` | Optional OpenMemories-based development connector |

## Credits and references

This project is a modified A7R II port of the original
[**sony-lut-camera**](https://github.com/EnglandLobster/sony-lut-camera) by
**瓦尔基里** (Xiaohongshu ID). The original project and this port are licensed
**GPL-3.0-only**; the original author's attribution must be preserved.

A7R II port and film-camera interface: **Yonitska (Bilibili)**. This repository
is based on the original project above and contains substantial new code for
42 MP capture, the film UI, recipes, video, transfer, and A7R II adaptation.

Special thanks to these projects and authors:

- **Recipe Lab** — André Domingues / voxivoid, GPL-3.0,
  <https://github.com/voxivoid/recipe-lab>. The 77-recipe dataset is imported
  and re-mapped independently in Java; no Recipe Lab application code or UI is
  copied. Required attribution: **Based on Recipe Lab by André Domingues**.
- **M-Film** (HNGSeries, GPL-2.0) — design and motion inspiration only. No code
  or artwork is copied.
- **Film Studio / 胶片工坊** — used only to observe Sony camera API behavior.
  It is PolyForm Noncommercial with a proprietary Sony base; no code, smali,
  resources, or images from it are included.
- **ShotDirector** (MIT) — the Wi-Fi Direct `DirectManager` enable/startGo
  sequence and the S1 `autoFocus`/`cancelAutoFocus` pattern. Its pose-card data
  is **not** MIT and is not included here.
- **OpenMemories Tweak** (MIT) — Java bridge used only by the optional
  `dev-connect` tool.
- **Sony-PMCA-RE** — installation tool for PMCA apps.
- **bonyback1's Ricoh mod** (Apache-2.0) — interface reference only.
- **libjpeg-turbo**, **Noto Sans SC/KR** (SIL OFL), and **Unsplash** photos used
  in the camera picker and launcher icon.

Sony's firmware classes and camera interfaces are used at runtime only. No Sony
source code or firmware data is included in the public repository. This is an
unofficial community project and is not affiliated with or endorsed by Sony.

## License

Original code and documentation: **GNU GPL v3.0 only** — see [LICENSE](LICENSE).
Third-party code and assets retain their own licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
[ASSET_LICENSES.md](ASSET_LICENSES.md). If you distribute a modified build, keep
the GPL notices, the original author's attribution, and the Recipe Lab
attribution.

---

[中文说明](README.zh-CN.md) · [A7R II port notes](docs/A7R2_PORT.md) ·
[User guide](docs/USER_GUIDE.md) · [Build](docs/BUILD.md) ·
[Project board](https://github.com/users/Yonitska/projects/1)
