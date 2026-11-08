# Sony LUT Camera / A7R2 Film Camera

**让相机在机内完成真正的 3D LUT 调色，拍完直接得到原片和调色 JPEG。** 无需手机、联网或电脑参与出片。支持导入自己的 `.cube`。

> 公开仓库只包含原创代码与已确认授权的资源：**不包含** 10 个示例 LUT、
> 176 张摆姿卡片/图片和 Sony 42m 内存表。示例 LUT 与摆姿包保留在本机未跟踪的
> `local-assets/` 中，仅用于维护者自己的真机构建。英文主说明见
> [README.md](README.md)，当前已知问题也写在那里。

> 当前发行候选：**v0.9.0-beta.5**。上游实测机型为 **Sony A6300 / 固件 2.01**。完整 LUT 用于成片；流畅取景使用矩阵与 Gamma 近似，半按快门时恢复原色。当前发布状态见 [发行记录](docs/RELEASE.md)。
>
> 本仓库同时包含 **A7R2 本地移植增强 `0.9.0-beta.5-a7r2.64`**（ILCE-7RM2 / 42MP，本地测试签名，不对外分发）及配套手机 App **SonyLutRemote 1.0**。功能、构建和真机验证边界见 [A7R2 移植记录](docs/A7R2_PORT.md) 与 [手机传片说明](phone/README.md)。

## 原作者与开源

- 原项目：[**sony-lut-camera**](https://github.com/EnglandLobster/sony-lut-camera)，原作者 **瓦尔基里**（小红薯 ID）。
- A7R II 移植增强：**Yonitska（Bilibili）**（机内胶片相机界面、42MP 冲洗流程、A7R II 适配、手机传片与人脸检测；基于上方原项目地址修改）。原作者信息保留。
- 界面与流程参考：**M-Film**（HNGSeries，GPL-2.0）、**胶片工坊 / Film Studio**（A5100 Film Studio contributors）、**拍摄导演台 / ShotDirector**（MIT）和 **bonyback1 的理光模组**（Apache-2.0）；仅学习设计与交互，未复制其代码或图像。
- 近似胶片配方数据：**配方实验室 / Recipe Lab**（André Domingues / voxivoid，GPL-3.0，© 2026 André Domingues）。本移植版导入其 v1.11.1 的 77 个相机/胶卷配方参数，并以独立 Java 实现映射到索尼相机参数；未复制其应用代码或界面图像。按上游 GPL-3.0 附加条款保留署名：**Based on Recipe Lab by André Domingues**。上游：[github.com/voxivoid/recipe-lab](https://github.com/voxivoid/recipe-lab)。
- 原项目与本移植均以 **GPL-3.0-only** 开源发布，源码随发行公开；修改与再发行请保留同样的开源许可及原作者署名。
- 感谢原作者、移植者与所有参考项目的作者和贡献者。

## 上手使用

**第一次安装？先看 [新手安装与使用教程](docs/USER_GUIDE.md)**：Windows/macOS 工具下载、相机 MTP 设置、选择本地 APK、首次拍摄及常见问题。

**[下载 beta.5 安装包](https://github.com/EnglandLobster/sony-lut-camera/releases/download/v0.9.0-beta.5/SonyLutCamera-v0.9.0-beta.5.apk)** · [发行说明与校验文件](https://github.com/EnglandLobster/sony-lut-camera/releases/tag/v0.9.0-beta.5) · 已验证 Sony A6300 / 固件 2.01。

1. 从 [beta.5 发行页](https://github.com/EnglandLobster/sony-lut-camera/releases/tag/v0.9.0-beta.5) 下载 `SonyLutCamera-v0.9.0-beta.5.apk`。自行编译见 [构建说明](docs/BUILD.md)。
2. 用 [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) 安装 APK。相机使用 MTP USB 模式；详细步骤见 [安装与使用](docs/USER_GUIDE.md)。
3. 打开相机应用列表中的 **A7R2胶片相机**（本移植），选一个胶卷，按中央键进入拍摄。首次打开会把预装 LUT 加入 SD 卡。
4. 正常半按对焦、全按拍照。相机保留原片 JPEG，并另外保存应用完整 LUT 的 JPEG；成片后半按回到取景。

普通拍摄不需要“测试连接”、ADB、Telnet 或 Wi-Fi。开发连接工具不随发行包提供。

## A7R2 本地移植与手机传片

A7R2 版本在胶片相机界面之上增加：

- **真 LUT / 近似配方两种拍摄模式**、42MP 冲洗、C3 机内换胶片、五语言界面。
- **手机自动传片**：相机 App 打开期间，新照片经相机自建 Wi-Fi 传给手机。
  真 LUT 只传 LUT 成片，配方只传相机直出，**原片和 RAW 永不发送**。
- **录像显式传输**：只在相机“手机传输 → 选择录像”中勾选的文件会出现在手机；
  手机仍需逐条点按下载。
- **相机原生人脸优先对焦**：开启后使用 Sony 固件的人脸优先对焦；a7r2.51 起不再
  请求厂商人脸框渲染（该设置在 A7R2 上会使第三方直通快门被取消），App 不自绘假框。
  A7R2 未提供可确认的被摄者 Eye AF 接口，探针报告写在 `SONYLUT/EYEPROBE.TXT`，
  不提供假“眼控”开关。
- 首次配对、Android 10+ 系统 Wi-Fi 确认框、断点续传和录像下载说明见
  [phone/README.md](phone/README.md)。

相机端与手机端均使用本地测试密钥签名，仅供自用，不对外分发。A7R2 传输/人脸功能
尚待真机逐项验证，清单见 [A7R2 移植记录](docs/A7R2_PORT.md)。

## LUT 选择

预装 Fuji、Ilford、Kodak 和 Ricoh 共 10 个指定 LUT（7 个 25³、3 个 33³），默认选择 Fuji C200。用户导入的 LUT 按完整网格处理，支持 **2–65³**。参考 [内置资源](docs/BUILTIN_LUTS.md)。

将独立 `.cube` / `.cub` 放入 SD 卡根目录的 `LUTS/`，在应用里按右键扫描、导入。每个文件最多 64 MiB；应用保存完整表并自动生成近似取景参数。首次拟合差异较大时，可以选择继续近似预览或原色取景，成片始终使用完整 LUT。

## 拍摄与回看

- 自动曝光，左右键／拨轮调节曝光补偿，中央键归零。
- **6000×4000、EXTRA FINE**，原片与 LUT 成片分别保存到 SD 卡；原片指未套自定义 LUT 的 JPEG，不是 RAW 文件。
- 胶卷库右侧照片随选择显示完整 LUT 效果；这张参考图不是实时取景。
- 播放键进入应用内相册；左看较早照片，右看较新照片，中央键比较原片和成片；半按快门返回拍摄。
- MENU 返回胶卷库，再从「更多 → 退出应用」返回索尼原生拍摄。

完整拍摄需要数秒，耗时受场景、曝光与 SD 卡影响。Beta 暂不承诺其他机型、视频 LUT 或全分辨率实时 3D LUT 取景。详细验证边界见 [状态](docs/STATUS.md)。

## 开发与仓库结构

原始代码采用 **GPL-3.0-only**。构建环境为 JDK 17、Android SDK 36（build-tools 36.0.0）和 NDK r16b；macOS 与 Linux 均已适配。Linux 下可设置
`ANDROID_SDK`、`ANDROID_NDK`、`JAVA_HOME` 后直接构建，详见 [本地构建记录](README-BUILD-LOCAL.md)。版本固定的外部依赖由脚本获取。

```sh
python3 scripts/bootstrap_dependencies.py
bash scripts/build_host.sh
python3 scripts/test_offline.py
bash scripts/test_product_ui.sh
bash scripts/test_transfer.sh
bash scripts/test_lut_cache.sh
bash scripts/test_preview_fit.sh
bash scripts/build_android.sh --release
bash scripts/test_phone.sh
bash scripts/build_phone.sh
```

[完整构建说明](docs/BUILD.md) · [贡献规范](AGENTS.md) · [页面操作规则](PRODUCT_UI.md)

| 目录 | 职责 |
| --- | --- |
| `android/{app,capture,preview,storage,ui,platform}/` | 产品页面、拍摄、预览、存储和索尼接口 |
| `android/transfer/`、`shared/transfer/` | 相机 Wi-Fi Direct 传输服务、照片/录像源与纯 Java 协议 |
| `phone/` | Android 手机配套 App（纯 Java，独立构建） |
| `native/` | C++ 完整 LUT、UYVY、JPEG、缓存与预览拟合 |
| `android-assets/` | 预装 LUT、参考缩略图、字体及归属说明 |
| `android-tests/`、`scripts/` | 验证、构建与维护入口 |
| `android/diagnostics/`、`native/probes/` | 开发诊断，发行 APK 不暴露诊断 Activity |
| `dev-connect/` | 可选开发连接工具，不是用户拍摄依赖 |
| `docs/` | 用户指南、构建、架构和发行记录 |

`out/`、`dist/`、`results/`、`vendor/` 保留在本地，不提交密钥、APK、设备照片、固件或私人 LUT 收藏。`PreJpegProbeActivity` 虽保留历史名称，仍是生产拍摄基类，不能删除。

## 许可与致谢

[GPL-3.0](LICENSE) 适用于本项目原始代码；预装 LUT、字体、照片及外部配置分别遵循 [资源许可说明](ASSET_LICENSES.md) 和 [第三方清单](THIRD_PARTY_NOTICES.md)。感谢 OpenMemories、Sony-PMCA-RE 和 libjpeg-turbo。项目为第三方应用，不隶属于 Sony。
