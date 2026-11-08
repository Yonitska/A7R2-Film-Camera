# OpenMemories Tweak native libraries

These prebuilt ARM libraries are extracted from the official
**OpenMemories Tweak v0.11** release:

- Release: <https://github.com/ma1co/OpenMemories-Tweak/releases/tag/v0.11>
- APK: `OpenMemories-Tweak-release-0.11.apk`
- APK SHA-256: `80a61a3a3d50e2dd22492cdd96cf56c8b1523c83662ae3def59fd52b28675a47`

| File | SHA-256 | License |
| --- | --- | --- |
| `armeabi/libtweak.so` | `0c5f7b90107a0b9248ed0df50cea0e4d3824f8b53e0785bad1272d3a00ec2d20` | MIT — OpenMemories Tweak, © 2016 ma1co |
| `armeabi/libadbd.so` | `e0af66f44523e179e0f184f1dcf62ace366de7e7699139c78e800f878893b2d6` | Apache-2.0 — AOSP `system/core` adbd, built by Tweak |

`libtweak.so` is a JNI library. Its symbols are bound to the package
`com.github.ma1co.openmemories.tweak`; the Java bridge in this repository keeps
that package. It expects the camera's own `libosal_uipc.so`, `libstdc++.so`
and related system libraries, which are not redistributed here.

`libadbd.so` is an executable renamed to `lib*.so` so Android extracts it.
It must be started with Tweak's `Shell.execAndroid` chroot/environment path,
not loaded with `System.loadLibrary`.

The libraries are copied into both `lib/armeabi` and `lib/armeabi-v7a` in the
APK. ABI selection is per APK: an armeabi-only library is invisible when the
camera picks armeabi-v7a as the primary ABI. These ARMv5 binaries run on the
ARMv7 A7R II.

OpenMemories-Platform sources used by `libtweak.so` are MIT:
<https://github.com/ma1co/OpenMemories-Platform>. The AOSP adbd source is
Apache-2.0: <https://android.googlesource.com/platform/system/core>.

`libprotectiontweak.so` is not bundled: Video/Region/Protection Backup.bin
tweaks are out of scope for this phase.
