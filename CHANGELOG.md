# Changelog

## 0.9.0-beta.5-a7r2.53 — 录像 AF 释放、录像后拍照与半按近似取景（A7R2 本地测试版）

- **a7r2.52 的 S1 松开取消没有真正执行。** `VideoActivity.dispatchKeyEvent()` 在
  方法前部对所有 `ACTION_UP` 直接 `return true`，因此末尾半按分支里的
  `cancelVideoFocus("half_release")` 是死代码。R0064AAF 证实：半按后的
  `video_focus_canceled` 一次也没出现，第一次 `autoFocus` 永远占着厂商执行器，
  后续对焦请求全部被跳过/取消。现在 S1 松开在通用 UP 返回之前统一处理。
- 录像 AF 改为 ShotDirector（MIT）已验证的无回调形式 `camera.autoFocus(null)`；
  A7R2 的录像包装层在 R00685E2 中 11 次请求 0 次回调，不再依赖回调。单飞、
  450 ms 合并、常时 AF 只回收自己的请求、退出前停止巡检并取消，全部保留。
- 拍照路径半按现在会真正对焦：auto-preview READY 后先跑 capture guard，再在
  串行控制线程上 `cancelAutoFocus()` + `autoFocus(null)`；S1 松开取消。这同时清掉
  录像遗留的 AF 请求，避免 Sony 直通快门在 S2 后 0.6–0.9 秒以 `status=1` 取消。
- 相机打开后在 preview 已启动时再取消一次对焦（打开时 preview 之前的取消在
  A7R2 上会抛 `cancelAutoFocus failed`）。新增 `camera_focus_state` 只读日志，
  记录 `focus-mode/af-mode/focus-area/shooting-preview/scene`，便于对照录像前后。
- **真 LUT 半按不再恢复原色。** 新的顺序是：S1 按下仍先恢复基线并
  `startDirectShutter`，随后立刻重新应用近似矩阵/Gamma（半按取景保持近似色）；
  S2 到达、Sony 收到按键之前再次恢复基线，保证写入的原片/显影不继承预览色。
  松开 S1 或拍完后照旧回到近似取景。录像继续始终近似；配方取景本来就是配方色。
- 若 Sony 在近似色状态下拒绝直通快门（首张 `status=1`），该会话自动退回“半按原色”
  的旧流程（日志 `half_color_disabled`），保证仍能拍照；重新进入取景器后恢复近似色。
- 版本 `0.9.0-beta.5-a7r2.53` / code 149。本地维护者构建（含 `local-assets/`
  示例 LUT 与摆姿包）APK 6,652,367 B，SHA-256
  `8e11cd0db2f1810794491bc483be9e43b789aeee5cb80b4f2d6fa8f7b9a9052e`。本版为本地
  测试签名，不对外分发。公开源码树不含示例 LUT/摆姿包，构建出的 APK 会更小且没有
  内置胶片，详见 README 的 “Assets and redistribution”。
  真机验证：真 LUT 连拍（半按应保持近似色）→ 录像待机/录制半按对焦 → 返回后
  真 LUT 连拍 → 配方连拍；重点日志 `focus_canceled origin=half_release`、
  `lut_focus_start`、`camera_focus_state`、`manual_pre_shot preview=off`、
  `shutter status`。

## 0.9.0-beta.5-a7r2.52 — 录像对焦与录像后拍照（A7R2 本地测试版）

- 修复“录像可以录，但无法对焦”。R00685E2 的 11 次 `camera.autoFocus` 全部没有
  回调：A7R2 一次半按会同时上报 scan 516 和 517，旧代码把一次按键当成两次请求，
  且 S1 松开时从不 `cancelAutoFocus()`，后续请求被厂商对焦执行器丢弃或排队。
  a7r2.52 改为单飞：约 450 ms 内重叠的按下合并；半按松开立即取消并释放执行器；
  用户半按优先于 1.6 s 的常时 AF 巡检，巡检只回收自己的请求；退出录像时停止巡检，
  并在恢复参数/关闭预览前取消尚未完成的对焦。
- 修复“录像返回后真 LUT 无法拍照”。R0079E5F 中两次全按都落在 AutoPreviewGate
  的 WAITING 窗口，S1/S2 被门控消费，半按守卫没有执行，`shooting-preview` 仍是
  `iris_ss_iso_aeunlock`，Sony 直通快门以 `status=1` 取消。现在彩色预览 READY 时
  （UI 线程）和全按进入 `arm()` 前都会补齐 `ensureManualCaptureGuard()`，保证
  ISO/快门/WB/EV 与 `shooting-preview=off` 在直通快门前写回。
- 修复“录像返回后配方模式不写文件”。录像会话可能把未完成的 AF 请求留在相机服务；
  真 LUT 与配方拍照现在在打开相机后和释放 CameraEx 前各调用一次
  `cancelAutoFocus()`（日志 `focus_canceled origin=open/close`），避免共享的对焦
  执行器把新的 `takePicture()` 拖住。
- 版本 `0.9.0-beta.5-a7r2.52` / code 148。相机 APK 6,651,619 B，SHA-256
  `d5e641397d40a8d731cc5306fec5808094347db159e9cb1a494fdd9d4e16b36c`。本版为本地
  测试签名，不对外分发。
  真机验证：真 LUT 连拍 → 录像（待机半按对焦、录制中半按/方向键移动对焦点）→
  返回后真 LUT 的 S/M 档连拍 → 配方模式连拍；每段至少两张照片并保存日志。

## 0.9.0-beta.5-a7r2.51 — 修复拍后切换模式无法再拍（A7R2 本地测试版）

- 修复“拍完一张后切换真 LUT / 录像→照片 / 近似配方模式后无法再启动拍照”。SD 日志
  显示两类独立原因：
  - 人脸检测把 `setFaceFrameRendering(true)` 写给第三方 CameraEx 会话后，真 LUT
    直通快门全部以 `ShutterListener.status=1` 取消；唯一启用该设置的配方会话
    `takePicture()` 立即返回但 15 秒内没有写入文件。A7R2 的
    `face-detection-mode` 本来就是 `auto`，所以旧代码实际只增加了厂商人脸框渲染。
    a7r2.51 不再请求人脸框渲染，也不重复写入相同的模式；人脸优先对焦保持相机原值，
    拍照路径不再多出参数事务。
  - 录像返回后的 2.5 秒等待期里货架可操作，用户手动启动拍照与延迟自动启动同时
    打开两个 PhotoActivity，一个成功、一个被取消。现在等待期保持 busy，手动启动、
    离开页面或新的启动都会使旧的延迟启动失效；胶片动画/取景选择回调也加了
    启动令牌，PhotoActivity/VideoActivity 增加单飞保护。
- 版本 `0.9.0-beta.5-a7r2.51` / code 147。相机 APK 6,650,129 B，SHA-256
  `29607aa1a26bd02dcd6c053b39366bf3d6d1aee49359cf481f1792a3c4c582b3`。人脸检测
  开关仍可开启相机原生人脸优先对焦，但不再强制显示厂商人脸框（日志写
  `face_detection_applied ... rendering=suppressed_capture_safety`）。本版为本地
  测试签名，真机验证请覆盖文末的模式切换清单。

## 0.9.0-beta.5-a7r2.50 — 手机传片、录像勾选、人脸检测（A7R2 本地测试版）

- 相机端新增 Wi-Fi Direct 传输服务（主进程前台服务、`START_NOT_STICKY`）和纯 Java
  HTTP 服务：token 鉴权、强 ETag、`Range`/`If-Range`、断点续传；照片源只读
  `SONYLUT/PHOTOS` 回执，真 LUT 只发送 `graded`，配方只发送相机直出，原片/RAW
  永不进清单。
- “更多 → 手机传输”：开关、SSID、WPA2 密码、配对码、地址与服务状态；录像需在
  “选择录像”中勾选，手机端再逐条确认下载。服务在取景器期间保持运行，退出相机
  App 前同步关闭端口、Wi-Fi Direct、Wi-Fi 状态与前台通知。
- 新增配套 App **SonyLutRemote 1.0**（`org.hongen.sonylutremote`，minSdk 23 /
  targetSdk 28，纯 Java）：一键加入相机 Wi-Fi、自动同步新照片、首次同步跳过旧照片、
  显式录像下载、`.part` + ETag 续传、MediaStore/公共目录保存。Android 10+ 的系统
  Wi-Fi 确认框如实提示且无法绕过。
- “更多 → 人脸检测”：调用 Sony 原生 `setFaceDetectionMode("auto")` 与
  `setFaceFrameRendering(true)` 负责人脸优先对焦/取景框，不自绘假框、不注册产品
  人脸回调；关闭时恢复原参数。首次开启拍照写只读 `SONYLUT/EYEPROBE.TXT`，其中
  `STILL_IMAGE_EYE_START_AF` 明确标注为快门启动 AF，不是被摄者 Eye AF；A7R2 未
  发现可确认的 Eye AF 接口，因此不提供假“眼控”开关。
- 共享传输协议/服务器/照片源/录像选择/台账主机测试；相机 release 包检查新增
  `TransferService`（禁止导出、随任务停止）、传输权限与共享类校验；新增手机包
  检查与 v1+v2 测试签名构建。
- 版本 `0.9.0-beta.5-a7r2.50` / code 146。相机 APK 6,649,247 B，SHA-256
  `acb3239a98dad9b7da58309445badd41ae7438bf9d43bda172710604bb79d154`；手机 APK
  115,642 B，SHA-256 `c04c8032170e900aa545b5dec25f3a5750bbe5df5ffd50c15979e42ecbc4fdff`。
  相机与手机均为本地测试签名，不对外分发。真机传输、系统确认框、断点续传、录像下载、
  人脸框显示尚待 A7R2 实机验证。

## 0.9.0-beta.5 — updated reference photograph

- Replace the LUT selector reference with the maintainer-selected IMG_7554.jpeg, oriented and converted to an sRGB thumbnail with the full portrait composition.

## 0.9.0-beta.4 — curated LUT library

- Preload the ten selected Fuji, Ilford, Kodak and Ricoh LUTs; default to Fuji C200.
- Remove the example and identity entries from the product; upgrades retire their exact content identities and caches while preserving other imports and valid selections.
- Generate mathematical test fixtures independently of the product catalog.

## 0.9.0-beta.3 — first release candidate

- Camera-side full 3D LUT processing before JPEG encoding; separate original/graded 24MP EXTRA FINE files.
- Automatic matrix/Gamma preview fitting for imported 2–65³ CUBE LUTs; preview is isolated from capture on half-press.
- Photographic LUT-library preview, automatic exposure with EV, paired review and adjacent-photo caching.
- Correct Sony display precision for smooth gradients and full-composition portrait reference layout.
- Separate release packaging: debugging disabled, production Activity entries only, no legacy test photos/profiles.
- GPL-3.0-only original code; third-party assets documented separately.

Verified hardware scope: A6300 firmware 2.01. See `docs/RELEASE.md` for public downloads and release/acceptance status.
