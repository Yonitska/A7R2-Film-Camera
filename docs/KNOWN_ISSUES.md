# Known issues

Current user-reported issues in the A7R II beta. These are not fixed unless a
later changelog entry says otherwise. This list is also summarized in the
[README](../README.md#known-issues).

## 1. The shutter can hang after a photo

**Symptom:** after a capture, the camera/app appears stuck and the next shutter
press does nothing until it is pressed again.

**Reported behavior:** pressing the shutter a second time continues.

**Status:** open. Relates to the Sony direct-shutter/release path and the
post-capture preview re-arm. Attach `SONYLUT/*/PROBE.JSN` and note whether the
shot was true-LUT or recipe mode.

## 2. Recipe mode cannot capture

**Symptom:** in the approximate film-recipe mode the app stays on the
“writing to storage” / 正在写入照片 screen and no photo is saved.

**Status:** open. Attach the `PROBE.JSN` from the failed recipe session; useful
events include `recipe_pre_shot`, `recipe_armed`, `recipe_take_picture`,
`recipe_store_scan_retry`, `recipe_timeout_*`, and `recipe_saved`.

## 3. Phone connection mode is unavailable

**Symptom:** the camera-side Wi-Fi Direct transfer service and/or the
`SonyLutRemote` phone companion cannot establish a usable connection.

**Status:** open; this feature is experimental and unverified on the A7R II.
Attach `SONYLUT/HANDOFF.TXT`, the transfer service status, and whether the
phone reaches `/v1/ping`. Never post Wi-Fi passwords or pairing codes.

## 4. Many other bugs

This is a beta built on private Sony PMCA interfaces. Camera hangs, failed
saves, and mode-switch problems are still possible. Do not use it for
irreplaceable work. Restart the camera if the app leaves it in a wedged state.
