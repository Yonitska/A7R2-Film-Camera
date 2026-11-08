# Sony LUT Camera — 本地构建记录（2026-11-01）

## 源码

- 仓库：`EnglandLobster/sony-lut-camera`
- 分支/提交：`main @ 700d72ad879b`（2026-10-31，v0.9.0-beta.5）
- 目录：`/home/topeet/Sony/A7R2-Film-Camera`
- 示例 LUT 与摆姿包：本机未跟踪目录 `local-assets/`（公开仓库不包含）
- 许可：GPL-3.0-only（原始代码）；预装 LUT/字体/照片见 `ASSET_LICENSES.md`

## 工具链

| 组件 | 路径/版本 |
|---|---|
| JDK 17 | `/usr/lib/jvm/java-17-openjdk-amd64` |
| Android SDK | `/home/topeet/Android/Sdk`（platforms;android-36、build-tools;36.0.0） |
| NDK r16b | `/home/topeet/Android/android-ndk-r16b` |
| CMake | 3.22.1 |
| Python | 3.12 venv（`/home/topeet/Sony/a7r2-film-studio/.venv`，含 Pillow/scipy/numpy/fonttools） |
| 旧版运行库 | `libncurses5`、`libtinfo5`（NDK r16b 的 clang 需要 libncurses.so.5） |

## 依赖（手动放置，因 GitHub git 直连受限）

- `vendor/libjpeg-turbo/`：libjpeg-turbo @ `8ecba3647edb…`（codeload tarball）
- `vendor/sony-picture-effect-plus/app/src/main/lib/armeabi/libILDCSC_MUSASHI8Gb24m_PictureEffectPlus_00_01.so`：
  1860 字节，SHA-256 `843b70399744074e9544d69982d8b37964747f9c93f907389c3c7b9efb759ee0`
- 未运行 `bootstrap_dependencies.py`（其 git fetch 依赖 GitHub 直连）；构建只需要文件本身。

## Linux 适配（已修改）

- `scripts/build_android.sh`：stripper 按 `uname -s` 选 `darwin-x86_64` / `linux-x86_64`。
- `scripts/check_package.py`：readelf 同样按主机选择。
- 未修改产品代码/构建参数。

## 构建命令

```bash
cd /home/topeet/Sony/A7R2-Film-Camera
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK=/home/topeet/Android/Sdk
export ANDROID_NDK=/home/topeet/Android/android-ndk-r16b
export PATH="/home/topeet/Sony/a7r2-film-studio/.venv/bin:$JAVA_HOME/bin:$ANDROID_SDK/build-tools/36.0.0:$PATH"

# release 需要 out/test.keystore；首次已生成：
# keytool -genkeypair -keystore out/test.keystore -alias lut-test -keyalg RSA -keysize 2048 \
#   -validity 3650 -storepass android -keypass android -dname 'CN=Sony LUT Test'
bash scripts/build_android.sh --release
```

构建流程：交叉编译 libjpeg-turbo（armeabi-v7a + armeabi）→ CMake 编译 `libsonylut.so`
（两种 ABI）→ aapt2 资源/资产 → javac --release 7 → d8 --min-api 10 → 打包 → zipalign →
apksigner（v1 only）→ `check_package.py`。

## 产物

`dist/SonyLutTest.apk`

- 4,447,089 字节
- SHA-256：`a5e0c7c01070ad4a0cc9f935cc22a512a2dbe7662cfbd60ba75e834c14598647`
- package `org.hongen.sonylutprobe`，versionName `0.9.0-beta.5`，versionCode 96
- 应用名 `LUT Camera`，minSdk/targetSdk 10，ABI `armeabi` + `armeabi-v7a`
- v1 签名、恰好 1 张证书；`check_package.py --variant release` 全过
- 10 个内置 LUT、UIFONT、PREVIEW.JPG、CATALOG 均在包内

## 与官方 Release 对比（v0.9.0-beta.5）

官方 APK：4,447,068 字节，SHA-256 `2ed14adf…ae75ed7`，由作者密钥签名。
两包 27 个非签名条目中 **24 个完全一致**（含 `classes.dex`、10 个 LUT、字体、预览图、manifest）。
差异只有：

1. 两个 `libsonylut.so` 的 ELF build-id（20 字节）；
2. libjpeg-turbo 编译日期字符串（官方 `20261028`，本地 `20261031`）；
3. `assets/THIRD_PARTY_NOTICES.md` 一句话：本地源码提交比官方构建提交新（docs 更新）。

## 签名与安装

- 本地签名身份：`out/test.keystore`，alias `lut-test`，口令 `android`（开发缺省）。
- **与官方发行 APK 的密钥不同**：不能 `adb install -r` 覆盖官方安装；需先卸载或使用作者密钥。
- 安装：相机 USB 设为 MTP，用 Sony-PMCA-RE 的 pmca-gui 安装 APK；首次打开会把 10 个 LUT 写入 SD 卡。
- 实机验证边界：官方仅验证 A6300 / 固件 2.01；完整 3D LUT 处理用于成片，实时取景为矩阵/曲线近似，半按快门恢复原色。

## 备注

- 未构建 debug 变体（debug staging 需要 macOS 主机测试生成的 `out/tests` 资产）；release 是发行推荐变体。
- 未运行 `build_host.sh` / `test_offline.py` 的宿主测试（脚本绑定 macOS clang/LLVM）；APK 构建与 `check_package.py` 已通过。
- 修改代码后重新运行 `bash scripts/build_android.sh --release`；保留 `out/test.keystore`。
