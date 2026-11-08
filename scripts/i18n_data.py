# -*- coding: utf-8 -*-
"""Reviewable UI translations for the A7R II five-language build.

Source strings are the Simplified Chinese literals used by the Java UI.
Traditional Chinese is generated with OpenCC (s2twp) so this table only
carries English, Japanese and Korean. An entry may be an exact UI string or a
`{}` pattern; both feed assets/I18N.TSV. Diagnostic probe screens are left in
Chinese on purpose: their logs are engineering text, not product UI.
"""

# source: (English, Japanese, Korean)
T = {}


def add(source, en, ja, ko):
    T[source] = (en, ja, ko)


# ---------------------------------------------------------------- language
add("语言", "Language", "言語", "언어")
add("语言设置", "Language", "言語設定", "언어 설정")
add("五国语言", "5 languages", "5言語", "5개 언어")
add("当前", "Current", "現在", "현재")
add("选择", "Select", "選択", "선택")
add("返回", "Back", "戻る", "뒤로")
add("保留当前语言设置", "Keep the current language", "現在の言語を保持", "현재 언어 유지")
add("选择界面语言 · 更改立即生效", "Choose the interface language · Applies immediately",
    "表示言語を選択 · すぐに反映", "인터페이스 언어 선택 · 즉시 적용")
add("语言偏好保存在应用内 · 不会改变相机系统语言",
    "Saved in this app · The camera system language is unchanged",
    "アプリ内に保存 · カメラ本体の言語は変更しません",
    "앱에 저장됨 · 카메라 시스템 언어는 변경되지 않습니다")
add("语言已切换", "Language changed", "言語を切り替えました", "언어가 변경되었습니다")

# ---------------------------------------------------------------- brand / common
add("A7R2胶片相机", "A7R2 Film Camera", "A7R2フィルムカメラ", "A7R2 필름 카메라")
add("A7R2 胶片相机", "A7R2 Film Camera", "A7R2フィルムカメラ", "A7R2 필름 카메라")
add("胶卷库", "Film Library", "フィルムライブラリ", "필름 라이브러리")
add("胶卷", "Film", "フィルム", "필름")
add("胶片", "Film", "フィルム", "필름")
add("选择胶卷", "Choose Film", "フィルムを選択", "필름 선택")
add("相机", "Camera", "カメラ", "카메라")
add("照片", "Photos", "写真", "사진")
add("配方", "Recipe", "レシピ", "레시피")
add("录像", "Video", "動画", "동영상")
add("回放", "Playback", "再生", "재생")
add("取景", "Finder", "ファインダー", "파인더")
add("预览", "Preview", "プレビュー", "미리보기")
add("对焦", "Focus", "フォーカス", "포커스")
add("曝光", "Exposure", "露出", "노출")
add("白平衡", "White Balance", "ホワイトバランス", "화이트 밸런스")
add("色彩", "Color", "カラー", "색상")
add("后期", "Retouch", "編集", "보정")
add("辅助", "Tools", "ツール", "도구")
add("稳定", "Stability", "安定性", "안정성")
add("界面", "Interface", "表示", "인터페이스")
add("动效", "Motion", "モーション", "모션")
add("信息", "Info", "情報", "정보")
add("关于", "About", "このアプリについて", "앱 정보")
add("致谢", "Credits", "クレジット", "크레딧")
add("许可", "License", "ライセンス", "라이선스")
add("开源", "Open Source", "オープンソース", "오픈 소스")
add("移植", "Port", "移植", "이식")
add("使用说明", "Help", "使い方", "사용 안내")
add("导入", "Import", "読み込み", "가져오기")
add("导入胶卷", "Import Film", "フィルムを読み込む", "필름 가져오기")
add("扫描", "Scan", "スキャン", "스캔")
add("重新扫描", "Scan Again", "再スキャン", "다시 스캔")
add("重试", "Retry", "再試行", "다시 시도")
add("关闭", "Off", "オフ", "끔")
add("确认", "Confirm", "決定", "확인")
add("切换", "Switch", "切替", "전환")
add("退出", "Exit", "終了", "종료")
add("退出应用", "Exit App", "アプリを終了", "앱 종료")
add("保存", "Save", "保存", "저장")
add("完成", "Done", "完了", "완료")
add("失败", "Failed", "失敗", "실패")
add("提示", "Notice", "お知らせ", "알림")
add("错误", "Error", "エラー", "오류")
add("加载中", "Loading", "読み込み中", "불러오는 중")
add("载入中", "Loading", "読み込み中", "불러오는 중")
add("请稍候", "Please wait", "お待ちください", "잠시 기다려 주세요")
add("处理中", "Working", "処理中", "처리 중")
add("操作未完成", "Not completed", "操作未完了", "작업 미완료")
add("正在准备", "Preparing", "準備中", "준비 중")
add("正在准备拍摄", "Preparing capture", "撮影を準備中", "촬영 준비 중")
add("正在装卷", "Loading film", "フィルム装填中", "필름 장전 중")
add("显影", "Develop", "現像", "현상")
add("调色", "Grade", "調色", "색보정")
add("拍摄", "Capture", "撮影", "촬영")
add("写入", "Write", "書き込み", "기록")
add("拍照", "Photo", "写真", "사진")
add("全部", "All", "すべて", "전체")
add("自定义胶卷", "Custom Film", "カスタムフィルム", "사용자 필름")
add("空库", "Empty", "空です", "비어 있음")
add("还没有胶卷", "No film yet", "フィルムがありません", "필름이 없습니다")
add("还没有照片", "No photos yet", "写真がありません", "사진이 없습니다")
add("拍下第一张照片后，在这里回看", "Your first shot will appear here",
    "最初の1枚を撮るとここに表示されます", "첫 사진을 촬영하면 여기에 표시됩니다")
add("效果参考", "Preview", "プレビュー", "미리보기")
add("预览不可用", "Preview unavailable", "プレビュー不可", "미리보기 사용 불가")
add("载入中…", "Loading…", "読み込み中…", "불러오는 중…")
add("请稍候，正在处理…", "Please wait…", "お待ちください…", "잠시 기다려 주세요…")
add("相机直出", "Camera JPEG", "カメラJPEG", "카메라 JPEG")
add("直出", "JPEG", "JPEG", "JPEG")
add("原片", "Original", "オリジナル", "원본")
add("冲洗成片", "Developed", "現像済み", "현상 결과")
add("成片", "Result", "完成写真", "결과")
add("配方照片", "Recipe photo", "レシピ写真", "레시피 사진")
add("配方直出", "Recipe JPEG", "レシピJPEG", "레시피 JPEG")

# ---------------------------------------------------------------- library home
add("编译时间", "Build", "ビルド", "빌드")
add("选择相机", "Choose Camera", "カメラを選択", "카메라 선택")
add("选择工作方式", "Choose Workflow", "撮影方法を選択", "촬영 방식 선택")
add("拍摄模式", "Capture Mode", "撮影モード", "촬영 모드")
add("照片美化", "Beauty", "写真補正", "사진 보정")
add("人像轻处理", "Light Portrait", "ポートレート補正", "인물 보정")
add("皮肤平滑", "Skin Smooth", "肌補正", "피부 보정")
add("降噪", "Noise Reduction", "ノイズ低減", "노이즈 감소")
add("色度降噪", "Chroma NR", "色ノイズ低減", "색 노이즈 감소")
add("不处理", "Off", "処理なし", "처리 안 함")
add("仅肤色区域", "Skin tones only", "肌色のみ", "피부색만")
add("弱", "Low", "弱", "약")
add("强", "High", "強", "강")
add("拍摄之外", "Beyond Shooting", "撮影以外", "촬영 외")
add("加入新的胶卷", "Add a New Film", "フィルムを追加", "새 필름 추가")
add("拍过的胶卷", "Shot Film", "撮影したフィルム", "촬영한 필름")
add("从装卷到成片", "From Load to Print", "装填から完成まで", "장전부터 완성까지")
add("选择胶卷 → 半按对焦 → 全按拍摄 → 回看成片",
    "Choose film → half-press focus → full-press shoot → review",
    "フィルム選択 → 半押しAF → 全押し撮影 → 再生",
    "필름 선택 → 반누름 초점 → 완전 누름 촬영 → 재생")
add("效果参考图不是实时取景；成片保持相机全尺寸 · 每页 4 条，继续按 ↓ 查看",
    "Reference images are not live view; results keep the full camera size · 4 rows/page, press ↓ for more",
    "参考画像はライブビューではありません。完成写真はカメラのフルサイズです · 1ページ4件、↓で続き",
    "참고 이미지는 라이브 뷰가 아니며 결과는 카메라 전체 크기입니다 · 페이지당 4개, ↓로 더 보기")
add("原作者、移植与开源致谢", "Authors, Port and Credits", "作者・移植・クレジット", "원작자·이식·크레딧")
add("原作者  瓦尔基里 · 小红薯 ID", "Original author  Valkyrie · Xiaohongshu ID",
    "原作者  ヴァルキリー · 小紅書ID", "원작자  발키리 · 샤오홍슈 ID")
add("移植  Yonitska（小破站ID）", "Port  Yonitska (Bilibili ID)",
    "移植  Yonitska（Bilibili ID）", "이식  Yonitska (빌리빌리 ID)")
add("配方数据  配方实验室 Recipe Lab", "Recipe data  Recipe Lab",
    "レシピデータ  Recipe Lab", "레시피 데이터  Recipe Lab")
add("Recipe Lab 项目地址", "Recipe Lab project", "Recipe Lab プロジェクト", "Recipe Lab 프로젝트")
add("本项目同样开源", "This Project Is Also Open Source", "このプロジェクトもオープンソース",
    "이 프로젝트도 오픈 소스입니다")
add("特别感谢", "Special Thanks", "スペシャルサンクス", "특별 감사")
add("原作者、移植者与上述开源项目贡献者", "The original author, the porter and all project contributors",
    "原作者、移植者、各プロジェクトの貢献者", "원작자, 이식자 및 모든 프로젝트 기여자")
add("感谢 Recipe Lab 开放配方数据；感谢原作者、移植者与所有参考项目",
    "Thanks to Recipe Lab for open recipe data, and to the original author, porter and reference projects",
    "Recipe Labのレシピデータ、原作者、移植者、参考プロジェクトに感謝します",
    "Recipe Lab의 레시피 데이터와 원작자, 이식자, 참고 프로젝트에 감사드립니다")
add("77 个近似胶片配方 · 相机参数映射来源",
    "77 approximate film recipes · Camera parameter mapping source",
    "近似フィルムレシピ77件 · カメラ設定の対応元", "근사 필름 레시피 77개 · 카메라 설정 매핑 출처")
add("GPL-3.0-only · 源码随发行公开，欢迎继续改进",
    "GPL-3.0-only · Source is published with releases; improvements welcome",
    "GPL-3.0-only · ソースはリリースで公開、改良を歓迎します",
    "GPL-3.0-only · 소스는 배포와 함께 공개되며 개선을 환영합니다")
add("参考  理光模组", "Reference  Ricoh module", "参考  リコーモジュール", "참고  리코 모듈")
add("借鉴  M-Film（佳能）", "Inspired by  M-Film (Canon)", "参考  M-Film（キヤノン）", "참고  M-Film (Canon)")
add("借鉴  胶片工坊 Film Studio", "Inspired by  Film Studio", "参考  Film Studio", "참고  Film Studio")
add("借鉴  拍摄导演台 ShotDirector", "Inspired by  ShotDirector", "参考  ShotDirector", "참고  ShotDirector")
add("HNGSeries 作者与贡献者 · 胶片界面参考 · GPL-2.0",
    "HNGSeries authors and contributors · Film UI reference · GPL-2.0",
    "HNGSeries 作者と貢献者 · フィルムUI参考 · GPL-2.0",
    "HNGSeries 작성자 및 기여자 · 필름 UI 참고 · GPL-2.0")
add("A5100 Film Studio 作者与贡献者 · 冲洗流程",
    "A5100 Film Studio authors and contributors · Develop flow",
    "A5100 Film Studio 作者と貢献者 · 現像フロー",
    "A5100 Film Studio 작성자 및 기여자 · 현상 흐름")
add("项目作者与贡献者 · 摆姿/遥控交互 · MIT",
    "Project authors and contributors · Pose/remote interaction · MIT",
    "プロジェクト作者と貢献者 · ポーズ/リモート操作 · MIT",
    "프로젝트 작성자 및 기여자 · 포즈/원격 조작 · MIT")
add("bonyback1 · 机内色彩矩阵方法 · Apache-2.0",
    "bonyback1 · In-camera color matrix method · Apache-2.0",
    "bonyback1 · 機内カラーマトリクス手法 · Apache-2.0",
    "bonyback1 · 카메라 내장 색상 매트릭스 방식 · Apache-2.0")
add("A7R2 适配 · 胶片界面与冲洗流程", "A7R2 port · Film UI and develop flow",
    "A7R2 移植 · フィルムUIと現像フロー", "A7R2 이식 · 필름 UI 및 현상 흐름")
add("Yonitska 移植 · 原作者：瓦尔基里（小红薯 ID） · GitHub: sony-lut-camera",
    "Port by Yonitska · Original author: Valkyrie (Xiaohongshu) · GitHub: sony-lut-camera",
    "移植 Yonitska · 原作者：ヴァルキリー（小紅書） · GitHub: sony-lut-camera",
    "이식 Yonitska · 원작자: 발키리 (샤오홍슈) · GitHub: sony-lut-camera")
add("Yonitska 移植 · v", "Port by Yonitska · v", "Yonitska 移植 · v", "Yonitska 이식 · v")
add(" · 仅支持 A7R2", " · A7R2 only", " · A7R2専用", " · A7R2 전용")
add("基于 Recipe Lab by André Domingues", "Based on Recipe Lab by André Domingues",
    "Based on Recipe Lab by André Domingues", "Based on Recipe Lab by André Domingues")

# ---------------------------------------------------------------- shoot mode
add("真 LUT 模式", "Real LUT", "リアルLUT", "리얼 LUT")
add("近似胶片配方模式", "Approx. Recipe", "近似レシピ", "근사 레시피")
add("先选胶卷 · RAW 冲洗 + 完整 3D LUT", "Pick a film first · RAW develop + full 3D LUT",
    "先にフィルムを選択 · RAW現像 + 完全3D LUT", "필름 선택 · RAW 현상 + 전체 3D LUT")
add("选相机直接取景 · C3 实时换胶片 · 相机直出",
    "Camera finder · C3 switches film live · Camera JPEG",
    "カメラのファインダー · C3でフィルムを即時切替 · カメラJPEG",
    "카메라 파인더 · C3로 필름 실시간 전환 · 카메라 JPEG")
add("保留当前模式", "Keep the current mode", "現在のモードを保持", "현재 모드 유지")
add("模式会记忆 · 两种方式都可随时切回",
    "The mode is remembered · Switch back at any time",
    "モードは記憶されます · いつでも切替可能", "모드는 기억됩니다 · 언제든 전환 가능")
add("真 LUT：一次快门保存原片与冲洗成片 · 配方：只保存一张相机 JPEG",
    "Real LUT: one shutter saves original and developed · Recipe: one camera JPEG",
    "リアルLUT：1回でオリジナルと現像済みを保存 · レシピ：カメラJPEG1枚",
    "리얼 LUT: 한 번에 원본과 현상 결과 저장 · 레시피: 카메라 JPEG 한 장")
add("已切换到真 LUT 模式", "Switched to Real LUT", "リアルLUTに切り替えました", "리얼 LUT로 전환됨")
add("已切换到近似胶片配方模式", "Switched to Approx. Recipe", "近似レシピに切り替えました",
    "근사 레시피로 전환됨")
add("请先在“拍摄模式”切换到真 LUT 模式", "Switch to Real LUT in Capture Mode first",
    "先に「撮影モード」でリアルLUTに切り替えてください",
    "먼저 촬영 모드에서 리얼 LUT로 전환하세요")
add("配方会直接写入照片，不使用取景拟合",
    "Recipes are written directly to the photo; finder fitting is not used",
    "レシピは写真に直接書き込まれ、ファインダー補正は使いません",
    "레시피는 사진에 직접 기록되며 파인더 보정을 사용하지 않습니다")
add("这个相机没有可用配方", "No recipes for this camera", "このカメラのレシピがありません",
    "이 카메라에 사용할 레시피가 없습니다")

# ---------------------------------------------------------------- import
add("扫描 LUTS 文件夹", "Scan LUTS folder", "LUTSフォルダーをスキャン", "LUTS 폴더 스캔")
add("检查文件并显示可导入的胶卷", "Check files and list importable film",
    "ファイルを確認して読み込めるフィルムを表示", "파일을 확인하고 가져올 필름 표시")
add("返回胶卷库", "Back to Library", "ライブラリに戻る", "라이브러리로 돌아가기")
add("已导入的胶卷可以继续使用", "Imported film remains available",
    "読み込んだフィルムは引き続き使えます", "가져온 필름은 계속 사용할 수 있습니다")
add("支持 2–65³ 独立 3D CUBE · 相机不支持中文/长文件名",
    "Standalone 3D CUBE, 2–65³ · The camera does not support Chinese or long names",
    "独立3D CUBE 2–65³対応 · カメラは中国語/長いファイル名に非対応",
    "독립 3D CUBE 2–65³ 지원 · 카메라는 중국어/긴 파일명 미지원")
add("导入完成", "Import complete", "読み込み完了", "가져오기 완료")
add("使用导入的胶卷", "Use imported film", "読み込んだフィルムを使う", "가져온 필름 사용")
add("返回后已定位到新增胶卷", "The new film is selected after returning",
    "戻ると追加したフィルムが選択されます", "돌아가면 새 필름이 선택됩니다")
add("完整校验后加入卡内胶卷库", "Verified and added to the on-camera library",
    "検証後にカメラ内ライブラリへ追加", "검증 후 카메라 라이브러리에 추가")
add("可全部导入，也可只选择其中一个", "Import all or choose one",
    "すべて、または1件だけ読み込めます", "전체 또는 하나만 가져올 수 있습니다")
add("等待导入", "Waiting", "待機中", "대기 중")
add("已处理", "Done", "処理済み", "처리됨")
add("查看原因", "Details", "理由を見る", "이유 보기")
add("这个文件未能导入", "This file could not be imported", "このファイルは読み込めません",
    "이 파일을 가져올 수 없습니다")
add("新增、重复和失败文件已分别核对", "New, duplicate and failed files were checked",
    "新規・重複・失敗ファイルを確認しました", "새 파일, 중복, 실패 파일을 확인했습니다")
add("新增", "New", "新規", "신규")
add("已存在", "Exists", "既存", "있음")
add("已导入", "Imported", "読み込み済み", "가져옴")
add("预览已生成", "Preview ready", "プレビュー生成済み", "미리보기 생성됨")
add("预览待重试", "Preview retry needed", "プレビュー再試行", "미리보기 재시도 필요")
add("正在导入，请勿取出 SD 卡", "Importing · Do not remove the SD card",
    "読み込み中 · SDカードを抜かないでください", "가져오는 중 · SD 카드를 빼지 마세요")
add("正在生成近似预览 · 仅首次需要", "Generating approx. preview · First time only",
    "近似プレビュー生成中 · 初回のみ", "근사 미리보기 생성 중 · 최초 1회만")
add("正在扫描 SD 卡", "Scanning SD card", "SDカードをスキャン中", "SD 카드 스캔 중")
add("正在校验并导入胶卷", "Verifying and importing film", "検証して読み込み中", "검증 및 가져오는 중")
add("CUBE 文件放 SD 根目录；文件名用英文 8.3，如 NEUTRAL.CUB",
    "Put CUBE files in the SD root; use 8.3 English names, e.g. NEUTRAL.CUB",
    "CUBEはSDルートへ。8.3形式の英数字名（例 NEUTRAL.CUB）",
    "CUBE 파일은 SD 루트에 두고 8.3 영문 이름 사용 (예: NEUTRAL.CUB)")
add("CUBE 放 SD 根目录 LUTS 文件夹", "Put CUBE files in LUTS on the SD root",
    "CUBEはSDルートのLUTSフォルダーへ", "CUBE 파일은 SD 루트의 LUTS 폴더에")
add("相机读不到 CUBE：请改成英文 8.3 短名（如 NEUTRAL.CUB）后重插卡",
    "The camera cannot read the CUBE. Rename it to an 8.3 English name (e.g. NEUTRAL.CUB) and reinsert the card",
    "カメラがCUBEを読めません。8.3形式の英語名（例 NEUTRAL.CUB）に変更してカードを挿し直してください",
    "카메라가 CUBE를 읽을 수 없습니다. 8.3 영문 이름(예: NEUTRAL.CUB)으로 바꾸고 카드를 다시 삽입하세요")
add("找到 0 个：CUBE 需在 ", "Found 0: CUBE files must be in ", "0件：CUBEはここに必要です ",
    "0개: CUBE 파일 위치 ")
add("（详见 SONYLUT/LUTSCAN.TXT）", " (see SONYLUT/LUTSCAN.TXT)",
    "（詳細 SONYLUT/LUTSCAN.TXT）", " (자세히 SONYLUT/LUTSCAN.TXT)")

# ---------------------------------------------------------------- LUT errors
add("数据行数与阶数不符，请重新导出 CUBE", "Row count does not match the grid; export the CUBE again",
    "行数とグリッド数が一致しません。CUBEを再出力してください",
    "행 수와 그리드 크기가 맞지 않습니다. CUBE를 다시 내보내세요")
add("只支持独立 3D LUT，请导出为 3D CUBE", "Only standalone 3D LUTs are supported; export a 3D CUBE",
    "独立3D LUTのみ対応。3D CUBEで出力してください",
    "독립 3D LUT만 지원합니다. 3D CUBE로 내보내세요")
add("支持 2–65 阶 3D LUT，请检查 LUT_3D_SIZE", "Supports 2–65 grid 3D LUTs; check LUT_3D_SIZE",
    "2–65グリッドの3D LUTに対応。LUT_3D_SIZEを確認してください",
    "2–65 그리드 3D LUT 지원. LUT_3D_SIZE를 확인하세요")
add("输入范围无效，请检查 DOMAIN_MIN / MAX", "Invalid input range; check DOMAIN_MIN / MAX",
    "入力範囲が無効です。DOMAIN_MIN / MAXを確認してください",
    "입력 범위가 잘못되었습니다. DOMAIN_MIN / MAX를 확인하세요")
add("CUBE 数值格式不正确，请重新导出", "Invalid CUBE values; export again",
    "CUBEの数値形式が正しくありません。再出力してください",
    "CUBE 값 형식이 잘못되었습니다. 다시 내보내세요")
add("存储空间不足，请检查 SD 卡剩余空间", "Not enough space; check free SD card space",
    "空き容量不足です。SDカードの残量を確認してください",
    "저장 공간이 부족합니다. SD 카드 여유 공간을 확인하세요")
add("内置胶卷阶数不符", "Built-in film grid mismatch", "内蔵フィルムのグリッド不一致",
    "내장 필름 그리드 불일치")
add("内置胶卷与内容清单不符", "Built-in film does not match the manifest",
    "内蔵フィルムがマニフェストと一致しません", "내장 필름이 목록과 일치하지 않습니다")
add("这个文件未能导入", "This file could not be imported", "このファイルは読み込めません",
    "이 파일을 가져올 수 없습니다")
add("胶卷不可用", "Film unavailable", "フィルムを使用できません", "필름을 사용할 수 없습니다")
add("当前没有可用胶卷", "No film available", "利用可能なフィルムがありません",
    "사용 가능한 필름이 없습니다")
add("胶卷参考图无效", "Invalid film reference image", "フィルム参考画像が無効です",
    "필름 참고 이미지가 잘못되었습니다")
add("未命名胶卷", "Untitled Film", "名称未設定フィルム", "이름 없는 필름")
add("自定义胶卷", "Custom Film", "カスタムフィルム", "사용자 필름")

# ---------------------------------------------------------------- finder preview
add("取景预览", "Finder Preview", "ファインダープレビュー", "파인더 미리보기")
add("预览与成片可能有较明显差异", "Preview may differ noticeably from the result",
    "プレビューと完成写真が大きく異なる場合があります", "미리보기와 결과가 크게 다를 수 있습니다")
add("近似预览暂未生成", "Approx. preview not generated", "近似プレビュー未生成",
    "근사 미리보기가 아직 생성되지 않았습니다")
add("只改变取景方式，不改变照片效果", "Changes the finder only, not the photo",
    "ファインダーのみ変更し、写真には影響しません", "파인더만 변경되며 사진에는 영향을 주지 않습니다")
add("重新生成近似预览", "Regenerate approx. preview", "近似プレビューを再生成", "근사 미리보기 다시 생성")
add("继续使用近似预览", "Keep using approx. preview", "近似プレビューを使い続ける",
    "근사 미리보기 계속 사용")
add("近似色彩预览", "Approx. color preview", "近似カラープレビュー", "근사 색상 미리보기")
add("使用机内自动拟合的矩阵与曲线", "Use the camera-fitted matrix and curve",
    "カメラ内で自動フィットしたマトリクスとカーブを使用",
    "카메라에서 자동 피팅한 매트릭스와 커브 사용")
add("使用原色取景拍摄", "Shoot with original-color finder", "原色ファインダーで撮影",
    "원색 파인더로 촬영")
add("原色取景", "Original color finder", "原色ファインダー", "원색 파인더")
add("照片仍按完整胶卷冲洗", "Photos still receive the full film develop",
    "写真は引き続きフィルム通りに現像されます", "사진은 계속 필름 전체 현상이 적용됩니다")
add("保留当前选择", "Keep the current choice", "現在の選択を保持", "현재 선택 유지")
add("预览偏好无法读取，请重新选择", "Preview preference could not be read; choose again",
    "プレビュー設定を読めません。選択し直してください",
    "미리보기 설정을 읽을 수 없습니다. 다시 선택하세요")
add("近似效果差异较大，仍可自由选择使用",
    "The approximation differs noticeably, but you may still use it",
    "近似差が大きいですが、使用することはできます",
    "근사 차이가 크지만 계속 사용할 수 있습니다")
add("半按对焦时恢复原色 · 成片按完整胶卷冲洗",
    "Half-press restores original color · Results get the full film develop",
    "半押しで原色に戻る · 完成写真はフィルム通りに現像",
    "반누름 시 원색 복원 · 결과는 필름 전체 현상 적용")
add("正在准备近似预览", "Preparing approx. preview", "近似プレビューを準備中",
    "근사 미리보기 준비 중")
add("正在保存取景方式", "Saving finder setting", "ファインダー設定を保存中",
    "파인더 설정 저장 중")
add("正在读取预览设置", "Reading preview setting", "プレビュー設定を読み込み中",
    "미리보기 설정 불러오는 중")
add("预览偏好损坏，请重新选择预览方式", "Preview preference is damaged; choose again",
    "プレビュー設定が破損しています。選び直してください",
    "미리보기 설정이 손상되었습니다. 다시 선택하세요")
add("没有可用的近似预览", "No approx. preview available", "利用可能な近似プレビューがありません",
    "사용 가능한 근사 미리보기가 없습니다")

# ---------------------------------------------------------------- library notices / exit
add("请检查后重试", "Check and try again", "確認して再試行してください", "확인 후 다시 시도하세요")
add("已有胶卷和照片均已保留", "Existing film and photos are kept", "既存のフィルムと写真は保持されます",
    "기존 필름과 사진은 보존됩니다")
add("已有照片和胶卷保持不变", "Photos and film are unchanged", "写真とフィルムはそのままです",
    "사진과 필름은 그대로 유지됩니다")
add("相机未响应退出请求，可重试或打开测试连接。",
    "The camera did not acknowledge exit. Retry or open the debug helper.",
    "カメラが終了要求に応答しません。再試行するかテスト接続を開いてください。",
    "카메라가 종료 요청에 응답하지 않습니다. 다시 시도하거나 테스트 연결을 여세요.")
add("退出未完成", "Exit Not Completed", "終了未完了", "종료 미완료")
add("重试退出", "Retry Exit", "終了を再試行", "종료 다시 시도")
add("重新结束当前应用会话", "End the current app session again", "現在のアプリセッションを終了し直す",
    "현재 앱 세션을 다시 종료")
add("打开测试连接", "Open Debug Helper", "テスト接続を開く", "테스트 연결 열기")
add("恢复连接以便检查与修复", "Restore the connection for checking and repair",
    "確認と修復のために接続を復元", "확인 및 복구를 위해 연결 복원")
add("测试连接", "Debug Helper", "テスト接続", "테스트 연결")
add("测试连接未能打开，请检查安装状态。", "Could not open the debug helper; check the installation.",
    "テスト接続を開けません。インストール状態を確認してください。",
    "테스트 연결을 열 수 없습니다. 설치 상태를 확인하세요.")
add("打开已安装的相机调试工具", "Open the installed camera debug tool",
    "インストール済みのカメラデバッグツールを開く", "설치된 카메라 디버그 도구 열기")
add("拍照准备超时，请重试", "Capture preparation timed out; try again",
    "撮影準備がタイムアウトしました。再試行してください", "촬영 준비 시간이 초과되었습니다. 다시 시도하세요")
add("拍照模式启动失败", "Could not start photo mode", "写真モードを開始できません",
    "사진 모드를 시작할 수 없습니다")
add("录像模式启动失败", "Could not start video mode", "動画モードを開始できません",
    "동영상 모드를 시작할 수 없습니다")
add("拍照模式未就绪，请再试一次", "Photo mode was not ready; try again",
    "写真モードの準備ができませんでした。もう一度お試しください",
    "사진 모드가 준비되지 않았습니다. 다시 시도하세요")
add("配方拍摄模式启动失败", "Could not start recipe capture", "レシピ撮影モードを開始できません",
    "레시피 촬영 모드를 시작할 수 없습니다")
add("配方录像模式启动失败", "Could not start recipe video", "レシピ動画モードを開始できません",
    "레시피 동영상 모드를 시작할 수 없습니다")
add("屏幕初始化失败", "Display initialization failed", "画面の初期化に失敗しました",
    "화면 초기화 실패")
add("正在返回相机", "Returning to Camera", "カメラに戻っています", "카메라로 돌아가는 중")
add("正在结束应用会话", "Ending app session", "アプリセッションを終了中", "앱 세션 종료 중")
add("正在切换模式", "Switching mode", "モードを切替中", "모드 전환 중")
add("正在切换到拍照模式", "Switching to Photo Mode", "写真モードに切替中", "사진 모드로 전환 중")
add("正在等待录像接口释放", "Waiting for the video interface", "動画インターフェースの解放を待機中",
    "동영상 인터페이스 해제 대기 중")
add("请勿操作相机", "Do not touch the camera", "カメラを操作しないでください",
    "카메라를 조작하지 마세요")
add("录像接口已释放，正在打开拍照", "Video released; opening photo mode",
    "動画を解放しました。写真モードを開いています", "동영상 해제됨. 사진 모드 여는 중")
add("正在打开胶卷库", "Opening Film Library", "フィルムライブラリを開いています",
    "필름 라이브러리를 여는 중")
add("正在打开照片", "Opening photos", "写真を開いています", "사진을 여는 중")
add("正在加载胶卷", "Loading film", "フィルムを読み込み中", "필름 불러오는 중")
add("正在加载照片", "Loading photos", "写真を読み込み中", "사진 불러오는 중")
add("正在返回胶卷库…", "Returning to library…", "ライブラリに戻っています…", "라이브러리로 돌아가는 중…")
add("正在返回拍摄…", "Returning to capture…", "撮影に戻っています…", "촬영으로 돌아가는 중…")

# ---------------------------------------------------------------- help rows
add("左右键或转盘切换 · 中央键开始拍摄", "Arrow keys or dial switch · Center starts shooting",
    "左右キーまたはダイヤルで切替 · 中央で撮影開始", "좌우 키 또는 다이얼 전환 · 중앙 버튼 촬영 시작")
add("选中后自动播放装卷、拉片与快门帘", "Auto-plays load, leader pull and shutter curtain",
    "選択後に装填・リーダー送り・シャッター幕を自動再生",
    "선택 시 장전, 리더 인출, 셔터 커튼 자동 재생")
add("胶片载入动画", "Film Load Animation", "フィルム装填アニメ", "필름 장전 애니메이션")
add("全局取景设置", "Finder Settings", "ファインダー設定", "파인더 설정")
add("MENU → 更多 → 当前胶卷的取景", "MENU → More → Finder preview", "MENU → その他 → ファインダー",
    "MENU → 더 보기 → 파인더 미리보기")
add("取景接近成片色调 · 每个胶卷单独记忆", "Finder approximates the result · Remembered per film",
    "ファインダーが完成色に近似 · フィルムごとに記憶", "파인더가 결과 색상에 근접 · 필름별 기억")
add("取景保持原色 · 成片仍按完整胶卷冲洗", "Finder stays original · Result still gets the full develop",
    "ファインダーは原色 · 完成写真はフィルム通りに現像",
    "파인더는 원색 · 결과는 필름 전체 현상 적용")
add("MENU → 更多 → 拍摄模式 · 真 LUT / 近似配方", "MENU → More → Capture mode · Real LUT / Approx. Recipe",
    "MENU → その他 → 撮影モード · リアルLUT / 近似レシピ",
    "MENU → 더 보기 → 촬영 모드 · 리얼 LUT / 근사 레시피")
add("选相机直接取景 · 相机直出单张 JPEG", "Camera finder · One camera JPEG",
    "カメラのファインダー · カメラJPEG1枚", "카메라 파인더 · 카메라 JPEG 한 장")
add("近似胶片配方", "Approx. Recipe", "近似レシピ", "근사 레시피")
add("配方实时换胶片", "Switch Recipes Live", "レシピを即時切替", "레시피 실시간 전환")
add("取景中按 C3 选择 · 再按 C3 确认 · 效果实时更新",
    "C3 opens selection in the finder · C3 confirms · Live update",
    "ファインダーでC3選択 · C3で決定 · リアルタイム反映",
    "파인더에서 C3 선택 · C3 확인 · 실시간 반영")
add("导入 3D LUT", "Import 3D LUT", "3D LUTを読み込む", "3D LUT 가져오기")
add("CUBE 放 SD 根目录 LUTS 文件夹", "Put CUBE files in LUTS on the SD root",
    "CUBEはSDルートのLUTSフォルダーへ", "CUBE 파일은 SD 루트의 LUTS 폴더에")
add("LUT 文件命名", "LUT File Names", "LUTファイル名", "LUT 파일 이름")
add("英文 8.3 名，如 KODAK.CUB · 支持 2–65³", "8.3 English name, e.g. KODAK.CUB · Supports 2–65³",
    "8.3形式の英語名（例 KODAK.CUB）· 2–65³対応", "8.3 영문 이름(예: KODAK.CUB) · 2–65³ 지원")
add("半按对焦，全按拍摄", "Half-press Focus, Full-press Shoot", "半押しAF、全押し撮影",
    "반누름 초점, 완전 누름 촬영")
add("半按合焦 · 全按完成一次拍摄", "Half-press focuses · Full-press takes one shot",
    "半押しで合焦 · 全押しで1枚撮影", "반누름 초점 · 완전 누름으로 한 장 촬영")
add("对焦框始终显示", "Always Show AF Frame", "AF枠を常時表示", "AF 프레임 항상 표시")
add("拍照与录像取景都常驻显示对焦框", "AF frame stays visible in photo and video finders",
    "写真・動画のファインダーでAF枠を常時表示", "사진 및 동영상 파인더에 AF 프레임 상시 표시")
add("选择对焦点", "Select AF Point", "AFポイントを選択", "AF 포인트 선택")
add("下键进入 · 方向键移动 · 中央键确认", "Down enters · Arrows move · Center confirms",
    "下キーで開始 · 方向キーで移動 · 中央で決定", "아래로 시작 · 방향키 이동 · 중앙 확인")
add("取消对焦选择", "Cancel AF Point", "AFポイント選択を取消", "AF 포인트 선택 취소")
add("MENU 或 BACK 取消并恢复原对焦点", "MENU or BACK cancels and restores the AF point",
    "MENUまたはBACKで取消し、元のAFポイントに戻す", "MENU 또는 BACK으로 취소하고 원래 AF 포인트 복원")
add("曝光模式切换", "Exposure Mode", "露出モード切替", "노출 모드 전환")
add("C1 在 M → A → S → M 之间循环", "C1 cycles M → A → S → M", "C1で M → A → S → M を循環",
    "C1은 M → A → S → M 순환")
add("M 档拨轮", "M-Mode Dials", "Mモードのダイヤル", "M 모드 다이얼")
add("前拨轮光圈 · 后拨轮快门 · 转盘 ISO", "Front dial aperture · Rear dial shutter · Dial ISO",
    "前ダイヤル絞り · 後ダイヤルシャッター · ダイヤルISO",
    "앞 다이얼 조리개 · 뒤 다이얼 셔터 · 다이얼 ISO")
add("A/S 档拨轮", "A/S-Mode Dials", "A/Sモードのダイヤル", "A/S 모드 다이얼")
add("拨轮调快门或光圈 · 转盘调曝光补偿", "Dial adjusts shutter/aperture · Control dial adjusts exposure comp.",
    "ダイヤルでシャッター/絞り · コントロールダイヤルで露出補正",
    "다이얼로 셔터/조리개 · 컨트롤 다이얼로 노출 보정")
add("自动 ISO 读数", "Auto ISO Readout", "オートISO表示", "자동 ISO 표시")
add("AUTO 时显示实测值，例如 A1600", "AUTO shows the measured value, e.g. A1600",
    "AUTO時は実測値（例 A1600）を表示", "AUTO 시 실측값 표시 (예: A1600)")
add("取景时左右键调 EV · 中央键归零", "Left/right adjusts EV in the finder · Center resets",
    "ファインダーで左右キーがEV · 中央でリセット", "파인더에서 좌우 키 EV · 중앙 버튼 초기화")
add("右键打开 WB 菜单 · 方向键选择 · 中央键确认",
    "Right opens WB menu · Arrows choose · Center confirms",
    "右キーでWBメニュー · 方向キーで選択 · 中央で決定",
    "오른쪽 키로 WB 메뉴 · 방향키 선택 · 중앙 확인")
add("摆姿导演", "Pose Director", "ポーズディレクター", "포즈 디렉터")
add("C2 打开摆姿提示 · MENU 或返回键关闭", "C2 opens pose hints · MENU or Back closes",
    "C2でポーズ表示 · MENUまたは戻るで閉じる", "C2로 포즈 표시 · MENU 또는 뒤로 닫기")
add("一次快门，两张 JPEG", "One Shutter, Two JPEGs", "1回のシャッターでJPEG2枚", "한 번 셔터로 JPEG 2장")
add("原片与冲洗成片都保存在 DCIM", "Original and developed JPEGs are saved in DCIM",
    "オリジナルと現像済みをDCIMに保存", "원본과 현상 결과를 DCIM에 저장")
add("暗房冲洗动画", "Darkroom Develop Animation", "暗室現像アニメ", "암실 현상 애니메이션")
add("拍摄后显示显影进度 · 取景保持可操作", "Shows develop progress after capture · Finder stays usable",
    "撮影後に現像進行を表示 · ファインダーは操作可能", "촬영 후 현상 진행 표시 · 파인더 조작 가능")
add("MENU → 更多 → 照片美化 · 皮肤平滑/降噪", "MENU → More → Beauty · Skin smooth/NR",
    "MENU → その他 → 写真補正 · 肌補正/ノイズ低減", "MENU → 더 보기 → 사진 보정 · 피부/노이즈")
add("按回放键打开 · 左右翻图 · 中央键对照原片",
    "Playback key opens · Left/right browse · Center compares original",
    "再生キーで開く · 左右で送り · 中央で元画像と比較",
    "재생 키로 열기 · 좌우 탐색 · 중앙으로 원본 비교")
add("回放相册", "Playback Gallery", "再生ギャラリー", "재생 갤러리")
add("回放时按下键打开 4×3 缩略图", "Press Down in playback for a 4×3 contact sheet",
    "再生中に下キーで4×3サムネイル", "재생 중 아래 키로 4×3 썸네일")
add("接触印相总览", "Contact Sheet", "コンタクトシート", "컨택트 시트")
add("回放放大与平移", "Playback Zoom & Pan", "再生ズームと移動", "재생 확대 및 이동")
add("C3 切换 1×/2× · 放大后方向键平移画面", "C3 toggles 1×/2× · Arrows pan when zoomed",
    "C3で1×/2×切替 · 拡大時は方向キーで移動", "C3로 1×/2× 전환 · 확대 시 방향키 이동")
add("删除整组", "Delete Set", "セット削除", "세트 삭제")
add("删除键确认后移除原片、成片和缩略图", "Delete key removes original, result and thumbnail after confirmation",
    "削除キーでオリジナル・完成写真・サムネイルを削除",
    "삭제 키로 원본, 결과, 썸네일 삭제")
add("MOVIE 键或快门开始/停止 · 中央键不录制", "MOVIE or shutter starts/stops · Center does not record",
    "MOVIEキーまたはシャッターで開始/停止 · 中央では録画しない",
    "MOVIE 키 또는 셔터로 시작/정지 · 중앙 버튼은 녹화 안 함")
add("视频录制", "Video Recording", "動画撮影", "동영상 촬영")
add("视频格式与色彩", "Video Format & Color", "動画フォーマットと色", "동영상 형식 및 색상")
add("MENU 选择格式 · 取景始终为近似胶卷色", "MENU chooses format · Finder always shows approx. film color",
    "MENUで形式選択 · ファインダーは常に近似フィルム色",
    "MENU에서 형식 선택 · 파인더는 항상 근사 필름 색상")
add("配方写入相机 · 视频直接使用，无需 LUT", "Recipes are written in-camera · Video uses them directly, no LUT",
    "レシピはカメラ内で適用 · 動画はLUT不要", "레시피는 카메라에 기록 · 동영상은 LUT 불필요")
add("配方录像", "Recipe Video", "レシピ動画", "레시피 동영상")
add("空闲时每 1.6 秒对焦一次 · 半按立即合焦", "Focuses every 1.6 s when idle · Half-press focuses instantly",
    "待機中は1.6秒ごとにAF · 半押しで即合焦", "대기 중 1.6초마다 AF · 반누름 시 즉시 초점")
add("录像待机自动对焦", "Standby Autofocus", "待機オートフォーカス", "대기 자동 초점")
add("待机下键选择 · 录制中方向键移动", "Down selects in standby · Arrows move while recording",
    "待機中は下キーで選択 · 録画中は方向キーで移動", "대기 중 아래로 선택 · 녹화 중 방향키 이동")
add("录像对焦点", "Video AF Point", "動画AFポイント", "동영상 AF 포인트")
add("C1 切模式 · 拨轮调整 · 右键白平衡", "C1 changes mode · Dials adjust · Right sets WB",
    "C1でモード切替 · ダイヤル調整 · 右キーでWB", "C1 모드 전환 · 다이얼 조정 · 오른쪽 WB")
add("录像曝光与白平衡", "Video Exposure & WB", "動画露出とWB", "동영상 노출 및 WB")
add("切换前等待录像接口完全释放，避免重启", "Wait for the video interface to release before switching",
    "切替前に動画インターフェースの解放を待機し再起動を防止",
    "전환 전 동영상 인터페이스 해제를 기다려 재시작 방지")
add("录像转拍照保护", "Video-to-Photo Safety", "動画から写真への保護", "동영상-사진 전환 보호")
add("取景与回放的上下面板使用胶片边样式", "Finder and playback panels use film-edge styling",
    "ファインダーと再生の上下パネルにフィルム縁デザイン",
    "파인더와 재생 상하 패널에 필름 가장자리 디자인")
add("胶片齿孔装饰", "Film Perforations", "フィルムパーフォレーション", "필름 퍼포레이션")
add("取景信息开关", "Finder Info Toggle", "ファインダー情報切替", "파인더 정보 전환")
add("上键隐藏或显示取景参数", "Up hides or shows finder readouts", "上キーでファインダー情報を表示/非表示",
    "위 키로 파인더 정보 표시/숨김")
add("MENU → 关于 查看作者与开源致谢", "MENU → About for authors and credits",
    "MENU → このアプリについて で作者とクレジット", "MENU → 앱 정보에서 작성자 및 크레딧")
add("关于与致谢", "About & Credits", "このアプリについて・クレジット", "앱 정보 및 크레딧")
add("货架", "Shelf", "棚", "선반")
add("模式", "Mode", "モード", "모드")
add("保存", "Save", "保存", "저장")

# ---------------------------------------------------------------- gallery / review
add("正在打开照片", "Opening photo", "写真を開いています", "사진을 여는 중")
add("正在打开", "Opening ", "開いています ", "여는 중 ")
add("照片仍在内存卡中，请重新进入回放", "The photo is still on the card; reopen playback",
    "写真はカード内にあります。再生を開き直してください",
    "사진이 카드에 있습니다. 재생을 다시 여세요")
add("照片文件已不在 SD 卡", "The photo is no longer on the SD card", "写真がSDカードにありません",
    "사진이 SD 카드에 없습니다")
add("照片读取失败", "Could not read the photo", "写真を読み込めません", "사진을 읽을 수 없습니다")
add("照片预览超过缓存容量", "Photo preview exceeds the cache", "写真プレビューがキャッシュ容量を超えました",
    "사진 미리보기가 캐시 용량을 초과했습니다")
add("照片方向不支持原图放大", "Full-size zoom is not supported for this orientation",
    "この向きでは原寸拡大に対応していません", "이 방향에서는 원본 확대를 지원하지 않습니다")
add("照片信息暂不可用", "Photo information is unavailable", "写真情報を利用できません",
    "사진 정보를 사용할 수 없습니다")
add("照片已移动或 SD 卡不可用", "The photo was moved or the SD card is unavailable",
    "写真が移動されたかSDカードを利用できません", "사진이 이동되었거나 SD 카드를 사용할 수 없습니다")
add("相机照片索引不可用", "Camera photo index unavailable", "カメラの写真インデックスを利用できません",
    "카메라 사진 인덱스를 사용할 수 없습니다")
add("相机预览暂不可用，请重新打开照片", "Camera preview unavailable; reopen the photo",
    "カメラプレビューを利用できません。写真を開き直してください",
    "카메라 미리보기를 사용할 수 없습니다. 사진을 다시 여세요")
add("内存不足，已停止回放", "Out of memory; playback stopped", "メモリ不足のため再生を停止しました",
    "메모리 부족으로 재생을 중지했습니다")
add("内存不足，无法加载原图", "Out of memory; cannot load the original", "メモリ不足で原画像を読み込めません",
    "메모리 부족으로 원본을 불러올 수 없습니다")
add("内存不足，无法放大原图", "Out of memory; cannot zoom the original", "メモリ不足で原画像を拡大できません",
    "메모리 부족으로 원본을 확대할 수 없습니다")
add("内存不足，无法读取原图", "Out of memory; cannot read the original", "メモリ不足で原画像を読み込めません",
    "메모리 부족으로 원본을 읽을 수 없습니다")
add("内存不足，无法读取预览", "Out of memory; cannot read the preview", "メモリ不足でプレビューを読み込めません",
    "메모리 부족으로 미리보기를 읽을 수 없습니다")
add("内存不足，无法旋转预览", "Out of memory; cannot rotate the preview", "メモリ不足でプレビューを回転できません",
    "메모리 부족으로 미리보기를 회전할 수 없습니다")
add("原图区域解码失败", "Could not decode the original image region", "原画像の領域をデコードできません",
    "원본 이미지 영역을 디코딩할 수 없습니다")
add("原图尺寸读取失败", "Could not read the original size", "原画像サイズを読み込めません",
    "원본 크기를 읽을 수 없습니다")
add("原图解码失败", "Could not decode the original", "原画像をデコードできません",
    "원본을 디코딩할 수 없습니다")
add("照片预览尺寸无效", "Invalid preview size", "プレビューサイズが無効です", "미리보기 크기가 잘못되었습니다")
add("删除整组？原片 + 冲洗成片都会删除", "Delete the set? Original and developed copies will both be deleted",
    "セットを削除しますか？オリジナルと現像済みの両方を削除します",
    "세트를 삭제할까요? 원본과 현상 결과가 모두 삭제됩니다")
add("删除这张配方照片？", "Delete this recipe photo?", "このレシピ写真を削除しますか?",
    "이 레시피 사진을 삭제할까요?")
add("● 删除   MENU/BACK 取消", "● Delete   MENU/BACK Cancel", "● 削除   MENU/BACK キャンセル",
    "● 삭제   MENU/BACK 취소")
add("正在删除照片…", "Deleting photo…", "写真を削除中…", "사진 삭제 중…")
add("正在删除整组…", "Deleting set…", "セットを削除中…", "세트 삭제 중…")
add("MENU 返回", "MENU Back", "MENU 戻る", "MENU 뒤로")
add("MENU 返回照片列表", "MENU Back to Photos", "MENU 写真一覧に戻る", "MENU 사진 목록으로")
add("转盘缩放  方向键平移  C3/放大键还原", "Dial zoom  Arrows pan  C3/zoom restores",
    "ダイヤルズーム  方向キー移動  C3/ズームで戻す",
    "다이얼 확대  방향키 이동  C3/확대 키 복원")
add("转盘缩放  方向键平移  C3/放大键还原  ● 对照", "Dial zoom  Arrows pan  C3/zoom restores  ● Compare",
    "ダイヤルズーム  方向キー移動  C3/ズームで戻す  ● 比較",
    "다이얼 확대  방향키 이동  C3/확대 키 복원  ● 비교")
add("←→/拨轮 翻图  转盘/C3 放大  ↑ 信息  ↓ 印相  删除键删除",
    "←→/dial browse  Dial/C3 zoom  ↑ Info  ↓ Sheet  Delete key",
    "←→/ダイヤル送り  ダイヤル/C3ズーム  ↑ 情報  ↓ シート  削除キー",
    "←→/다이얼 탐색  다이얼/C3 확대  ↑ 정보  ↓ 시트  삭제 키")
add("←→/拨轮 翻图  转盘/C3 放大  ● 对照  ↑ 信息  ↓ 印相  删除键删除",
    "←→/dial browse  Dial/C3 zoom  ● Compare  ↑ Info  ↓ Sheet  Delete key",
    "←→/ダイヤル送り  ダイヤル/C3ズーム  ● 比較  ↑ 情報  ↓ シート  削除キー",
    "←→/다이얼 탐색  다이얼/C3 확대  ● 비교  ↑ 정보  ↓ 시트  삭제 키")
add("显示原片", "Show original", "オリジナルを表示", "원본 표시")
add("显示冲洗成片", "Show developed", "現像済みを表示", "현상 결과 표시")
add("配方照片由相机直出，没有原片对照", "Recipe photos are camera JPEGs and have no original to compare",
    "レシピ写真はカメラJPEGのため比較用オリジナルがありません",
    "레시피 사진은 카메라 JPEG이라 비교할 원본이 없습니다")
add("相机直出 JPEG", "Camera JPEG", "カメラJPEG", "카메라 JPEG")
add(" · 相机直出 JPEG", " · Camera JPEG", " · カメラJPEG", " · 카메라 JPEG")
add(" · 放大 ", " · Zoom ", " · 拡大 ", " · 확대 ")
add("加载中", "Loading", "読み込み中", "불러오는 중")
add("请稍候", "Please wait", "お待ちください", "잠시 기다려 주세요")
add("已保存的冲洗成片", "Saved developed photo", "保存済みの現像写真", "저장된 현상 사진")

# ---------------------------------------------------------------- photo capture / HUD
add("相机正在写入配方 JPEG，请保持开机", "The camera is writing the recipe JPEG; keep it on",
    "カメラがレシピJPEGを書き込み中です。電源を切らないでください",
    "카메라가 레시피 JPEG를 기록 중입니다. 전원을 끄지 마세요")
add("正在保存原片与成片，请保持相机开机", "Saving original and result; keep the camera on",
    "オリジナルと完成写真を保存中。カメラの電源を切らないでください",
    "원본과 결과를 저장 중입니다. 카메라 전원을 끄지 마세요")
add("保存时间较长，请继续等待", "Saving takes longer than usual; please wait",
    "保存に時間がかかっています。そのままお待ちください", "저장에 시간이 걸립니다. 계속 기다려 주세요")
add("拍摄未完成 · ", "Capture incomplete · ", "撮影未完了 · ", "촬영 미완료 · ")
add("原片已保存 · ", "Original saved · ", "オリジナル保存済み · ", "원본 저장됨 · ")
add("请返回胶卷库重新进入拍摄", "Return to the library and reopen capture",
    "ライブラリに戻って撮影を開き直してください", "라이브러리로 돌아가 촬영을 다시 여세요")
add("MENU 返回胶卷库后重试", "MENU Back to library and retry", "MENU ライブラリに戻って再試行",
    "MENU 라이브러리로 돌아가 다시 시도")
add("正在拍摄", "Capturing", "撮影中", "촬영 중")
add("正在显影", "Developing", "現像中", "현상 중")
add("正在保存原片", "Saving original", "オリジナルを保存中", "원본 저장 중")
add("正在准备调色", "Preparing grade", "調色を準備中", "색보정 준비 중")
add("正在保存成片", "Saving result", "完成写真を保存中", "결과 저장 중")
add("正在冲洗照片", "Developing photo", "写真を現像中", "사진 현상 중")
add("正在冲洗照片 · 美肤/降噪", "Developing photo · Beauty/NR", "写真を現像中 · 肌補正/ノイズ",
    "사진 현상 중 · 피부/노이즈")
add("正在应用", "Applying ", "適用中 ", "적용 중 ")
add("正在应用胶片", "Applying film", "フィルムを適用中", "필름 적용 중")
add("正在准备下一张", "Preparing next frame", "次の1枚を準備中", "다음 사진 준비 중")
add("配方就绪", "Recipe ready", "レシピ準備完了", "레시피 준비 완료")
add("曝光控制已就绪 · C1 模式 · 前/后拨轮 EV · 转盘 ISO",
    "Exposure ready · C1 mode · Front/rear dial EV · Dial ISO",
    "露出準備完了 · C1モード · 前/後ダイヤルEV · ダイヤルISO",
    "노출 준비 완료 · C1 모드 · 앞/뒤 다이얼 EV · 다이얼 ISO")
add("正在连接相机", "Connecting camera", "カメラに接続中", "카메라 연결 중")
add("正在恢复曝光设置", "Restoring exposure", "露出設定を復元中", "노출 설정 복원 중")
add("正在恢复对焦", "Restoring focus", "フォーカスを復元中", "초점 복원 중")
add("正在准备取景", "Preparing finder", "ファインダーを準備中", "파인더 준비 중")
add("正在准备调色", "Preparing grade", "調色を準備中", "색보정 준비 중")
add("正在生成近似预览 · 仅首次需要", "Generating approx. preview · First time only",
    "近似プレビュー生成中 · 初回のみ", "근사 미리보기 생성 중 · 최초 1회만")
add("配方不可用。\n请返回胶卷库重新选择。", "Recipe unavailable.\nReturn to the library and choose again.",
    "レシピを利用できません。\nライブラリに戻って選び直してください。",
    "레시피를 사용할 수 없습니다.\n라이브러리로 돌아가 다시 선택하세요.")
add("无法启动拍摄", "Could not start capture", "撮影を開始できません", "촬영을 시작할 수 없습니다")
add("拍摄已取消 · 可重新半按对焦后拍摄", "Capture cancelled · Half-press again to shoot",
    "撮影をキャンセルしました · 再度半押しして撮影できます",
    "촬영이 취소되었습니다 · 다시 반누름하여 촬영하세요")
add("拍摄已取消 · 可重新拍摄", "Capture cancelled · You can shoot again",
    "撮影をキャンセルしました · もう一度撮影できます", "촬영이 취소되었습니다 · 다시 촬영할 수 있습니다")
add("拍摄超时 · 可再次按下快门", "Capture timed out · Press the shutter again",
    "撮影タイムアウト · もう一度シャッターを押してください",
    "촬영 시간 초과 · 셔터를 다시 누르세요")
add("相机写入较慢 · 请稍候", "The camera is writing slowly · Please wait",
    "カメラの書き込みが遅いです · お待ちください", "카메라 기록이 느립니다 · 기다려 주세요")
add("正在保存原片与成片，请保持相机开机", "Saving original and result; keep the camera on",
    "オリジナルと完成写真を保存中。電源を切らないでください",
    "원본과 결과 저장 중. 카메라 전원을 끄지 마세요")
add("原色已恢复 · 现在可半按对焦、全按拍照", "Original color restored · Half-press to focus, full-press to shoot",
    "原色に戻りました · 半押しAF、全押し撮影ができます",
    "원색이 복원되었습니다 · 반누름 초점, 완전 누름 촬영")
add("已恢复拍摄原色 · 按下快门拍摄", "Original color restored · Press the shutter",
    "撮影原色に戻りました · シャッターを押してください",
    "촬영 원색이 복원되었습니다 · 셔터를 누르세요")
add("近似色彩预览 · 半按对焦，按下拍摄", "Approx. color preview · Half-press focus, then shoot",
    "近似カラープレビュー · 半押しAF後に撮影", "근사 색상 미리보기 · 반누름 초점 후 촬영")
add("正在准备拍摄 · 请保持半按", "Preparing capture · Keep half-pressed", "撮影準備中 · 半押しを維持",
    "촬영 준비 중 · 반누름 유지")
add("↓ AF · → WB · 前拨光圈 · 后拨快门 · 转盘 ISO",
    "↓ AF · → WB · Front dial aperture · Rear dial shutter · Dial ISO",
    "↓ AF · → WB · 前ダイヤル絞り · 後ダイヤルシャッター · ダイヤルISO",
    "↓ AF · → WB · 앞 다이얼 조리개 · 뒤 다이얼 셔터 · 다이얼 ISO")
add("拨轮调亮度 · 中央归零 · ↑ 隐藏信息", "Dial adjusts brightness · Center resets · ↑ hides info",
    "ダイヤルで明るさ · 中央でリセット · ↑で情報を隠す",
    "다이얼 밝기 조정 · 중앙 초기화 · ↑ 정보 숨김")
add("C3 胶片 · C1 模式 · ↓ AF · → WB · ← 录像 · C2 摆姿",
    "C3 Film · C1 Mode · ↓ AF · → WB · ← Video · C2 Pose",
    "C3 フィルム · C1 モード · ↓ AF · → WB · ← 動画 · C2 ポーズ",
    "C3 필름 · C1 모드 · ↓ AF · → WB · ← 동영상 · C2 포즈")
add("快门/● 确认 · MENU 取消 · 转盘/←→ 换胶片", "Shutter/● confirm · MENU cancel · Dial/←→ change film",
    "シャッター/● 決定 · MENU キャンセル · ダイヤル/←→ フィルム切替",
    "셔터/● 확인 · MENU 취소 · 다이얼/←→ 필름 전환")
add("实时预览 · ", "Live preview · ", "ライブプレビュー · ", "라이브 미리보기 · ")
add("胶片 ", "Film ", "フィルム ", "필름 ")
add("胶片  ", "Film  ", "フィルム  ", "필름  ")
add("胶片切换失败", "Film switch failed", "フィルム切替失敗", "필름 전환 실패")
add("胶片切换中 · 请再按快门", "Switching film · Press the shutter again",
    "フィルム切替中 · もう一度シャッターを押してください", "필름 전환 중 · 셔터를 다시 누르세요")
add("白平衡预设不可用", "White balance preset unavailable", "ホワイトバランスプリセットを利用できません",
    "화이트 밸런스 프리셋을 사용할 수 없습니다")
add("↑↓ 选择 · ● 确认 · →/MENU 取消", "↑↓ Choose · ● Confirm · →/MENU Cancel",
    "↑↓ 選択 · ● 決定 · →/MENU キャンセル", "↑↓ 선택 · ● 확인 · →/MENU 취소")
add("AF 选择 · 方向键移动 · 中键确认 · MENU 取消",
    "AF select · Arrows move · Center confirms · MENU cancels",
    "AF選択 · 方向キー移動 · 中央で決定 · MENU キャンセル",
    "AF 선택 · 방향키 이동 · 중앙 확인 · MENU 취소")
add("操作未生效：", "No effect: ", "反映されません: ", "적용되지 않음: ")
add("照片已保存在 DCIM；回看记录保存失败", "Photo saved in DCIM; playback record could not be saved",
    "写真はDCIMに保存済み。再生記録の保存に失敗しました",
    "사진은 DCIM에 저장됨. 재생 기록 저장 실패")
add("按快门拍照 · 原片保留 · EXTRA FINE", "Press shutter · Original kept · EXTRA FINE",
    "シャッターで撮影 · オリジナル保持 · EXTRA FINE",
    "셔터로 촬영 · 원본 유지 · EXTRA FINE")
add("自动曝光", "Auto exposure", "自動露出", "자동 노출")
add("检测相机拍摄接口…", "Checking camera capture API…", "カメラ撮影APIを確認中…",
    "카메라 촬영 API 확인 중…")
add("相机拒绝", "Camera refused", "カメラが拒否しました", "카메라가 거부했습니다")
add("参数写入被相机拒绝", "The camera refused the parameter write", "カメラがパラメータ書き込みを拒否しました",
    "카메라가 매개변수 기록을 거부했습니다")
add("正在保存", "Saving", "保存中", "저장 중")
add("正在写入照片", "Writing photo", "写真を書き込み中", "사진 기록 중")
add("拍摄准备中", "Preparing capture", "撮影準備中", "촬영 준비 중")
add("美肤/降噪", "Beauty/NR", "肌補正/ノイズ低減", "피부/노이즈 감소")
add("保存时间较长，请继续等待", "Saving takes longer; please wait", "保存に時間がかかります。お待ちください",
    "저장에 시간이 걸립니다. 기다려 주세요")
add("请稍候，保存完成后可再次录制", "Please wait; you can record again after saving",
    "お待ちください。保存後に再び録画できます", "잠시 기다리면 저장 후 다시 녹화할 수 있습니다")

# ---------------------------------------------------------------- video
add("录像待机", "Video standby", "動画スタンバイ", "동영상 대기")
add("录像中", "Recording", "録画中", "녹화 중")
add("录像不可用", "Video unavailable", "動画を利用できません", "동영상을 사용할 수 없습니다")
add("录像设置", "Video Settings", "動画設定", "동영상 설정")
add("录像格式", "Video format", "動画形式", "동영상 형식")
add("选择录像格式", "Choose Video Format", "動画形式を選択", "동영상 형식 선택")
add("对焦模式", "Focus mode", "フォーカスモード", "초점 모드")
add("曝光模式", "Exposure mode", "露出モード", "노출 모드")
add("曝光补偿", "Exposure Comp.", "露出補正", "노출 보정")
add("取景胶卷", "Finder Film", "ファインダーフィルム", "파인더 필름")
add("近似胶卷", "Approx. film", "近似フィルム", "근사 필름")
add("近似胶卷（拟合矩阵，和照片胶卷不完全相同）",
    "Approx. film (fitted matrix; not identical to photo film)",
    "近似フィルム（フィットしたマトリクス。写真フィルムとは完全一致しません）",
    "근사 필름 (피팅 매트릭스, 사진 필름과 완전히 같지는 않음)")
add("原色（相机原始色彩，和照片取景一致）",
    "Original color (camera color, same as photo finder)",
    "原色（カメラ本来の色。写真ファインダーと同じ）",
    "원색 (카메라 원래 색상, 사진 파인더와 동일)")
add("正在准备录像", "Preparing video", "動画を準備中", "동영상 준비 중")
add("正在准备录像格式", "Preparing video format", "動画形式を準備中", "동영상 형식 준비 중")
add("正在开始录像", "Starting video", "録画を開始中", "녹화 시작 중")
add("正在停止并保存", "Stopping and saving", "停止して保存中", "중지 및 저장 중")
add("正在保存视频", "Saving video", "動画を保存中", "동영상 저장 중")
add("已保存", "Saved", "保存済み", "저장됨")
add("正在准备下一段录像…", "Preparing the next clip…", "次の動画を準備中…", "다음 동영상 준비 중…")
add("正在切换格式", "Switching format", "形式を切替中", "형식 전환 중")
add("正在停止并返回", "Stopping and returning", "停止して戻っています", "중지 후 돌아가는 중")
add("正在保存并返回", "Saving and returning", "保存して戻っています", "저장 후 돌아가는 중")
add("正在关闭录像接口", "Closing video interface", "動画インターフェースを閉じています",
    "동영상 인터페이스 닫는 중")
add("请稍候，视频保存完成后返回拍照", "Please wait; returning to photo after the video is saved",
    "お待ちください。保存後に写真へ戻ります", "기다려 주세요. 동영상 저장 후 사진으로 돌아갑니다")
add("请稍候，随后返回拍照", "Please wait; returning to photo shortly", "お待ちください。まもなく写真へ戻ります",
    "잠시 후 사진으로 돌아갑니다")
add("请先停止录像并等待保存完成", "Stop recording and wait for saving to finish",
    "録画を停止し、保存完了までお待ちください", "녹화를 중지하고 저장이 끝날 때까지 기다리세요")
add("按 ← 返回拍照", "Press ← for Photo", "← で写真へ戻る", "← 사진으로 돌아가기")
add("请等待当前录像操作完成后再换胶片", "Wait for the current video operation before changing film",
    "現在の動画操作が終わってからフィルムを切り替えてください",
    "현재 동영상 작업이 끝난 후 필름을 전환하세요")
add("请等待当前操作完成", "Wait for the current operation to finish", "現在の操作の完了をお待ちください",
    "현재 작업이 끝날 때까지 기다리세요")
add("选择胶片", "Choose Film", "フィルムを選択", "필름 선택")
add("C3 确认 · MENU 取消 · 拨轮/←→ 换胶片", "C3 Confirm · MENU Cancel · Dial/←→ Change film",
    "C3 決定 · MENU キャンセル · ダイヤル/←→ フィルム切替",
    "C3 확인 · MENU 취소 · 다이얼/←→ 필름 전환")
add("快门 停止 · 方向键 切换焦点 · MENU 设置", "Shutter Stop · Arrows Move AF · MENU Settings",
    "シャッター 停止 · 方向キー AF移動 · MENU 設定",
    "셔터 중지 · 방향키 초점 이동 · MENU 설정")
add("快门 录制 · C3 胶片 · MENU 设置 · ↓ AF · ← 拍照",
    "Shutter Record · C3 Film · MENU Settings · ↓ AF · ← Photo",
    "シャッター 録画 · C3 フィルム · MENU 設定 · ↓ AF · ← 写真",
    "셔터 녹화 · C3 필름 · MENU 설정 · ↓ AF · ← 사진")
add("快门 录制 · MENU 设置 · ↓ AF · ", "Shutter Record · MENU Settings · ↓ AF · ",
    "シャッター 録画 · MENU 設定 · ↓ AF · ", "셔터 녹화 · MENU 설정 · ↓ AF · ")
add(" · ← 拍照", " · ← Photo", " · ← 写真", " · ← 사진")
add("前 光圈 · 后 快门 · 增益轮 ISO", "Front Aperture · Rear Shutter · Gain ISO",
    "前 絞り · 後 シャッター · ゲインISO", "앞 조리개 · 뒤 셔터 · 게인 ISO")
add("增益轮 光圈 · 前后轮 EV", "Gain dial Aperture · Both dials EV", "ゲインダイヤル 絞り · 前後ダイヤル EV",
    "게인 다이얼 조리개 · 앞뒤 다이얼 EV")
add("增益轮 快门 · 前后轮 EV", "Gain dial Shutter · Both dials EV", "ゲインダイヤル シャッター · 前後ダイヤル EV",
    "게인 다이얼 셔터 · 앞뒤 다이얼 EV")
add("增益轮 ISO · 前后轮 EV", "Gain dial ISO · Both dials EV", "ゲインダイヤル ISO · 前後ダイヤル EV",
    "게인 다이얼 ISO · 앞뒤 다이얼 EV")
add("↑↓ 选择 · ● 应用 · MENU/← 返回", "↑↓ Choose · ● Apply · MENU/← Back",
    "↑↓ 選択 · ● 適用 · MENU/← 戻る", "↑↓ 선택 · ● 적용 · MENU/← 뒤로")
add("↑↓ 选择 · ● 进入 · MENU/← 返回", "↑↓ Choose · ● Open · MENU/← Back",
    "↑↓ 選択 · ● 開く · MENU/← 戻る", "↑↓ 선택 · ● 열기 · MENU/← 뒤로")
add("↑↓/拨轮 选择 · ● 应用 · MENU 取消", "↑↓/dial Choose · ● Apply · MENU Cancel",
    "↑↓/ダイヤル 選択 · ● 適用 · MENU キャンセル", "↑↓/다이얼 선택 · ● 적용 · MENU 취소")
add("↑↓ 选择 · ● 切换   MENU 返回", "↑↓ Choose · ● Switch   MENU Back",
    "↑↓ 選択 · ● 切替   MENU 戻る", "↑↓ 선택 · ● 전환   MENU 뒤로")
add("↑↓ 选择   ● 应用   MENU 返回", "↑↓ Choose   ● Apply   MENU Back",
    "↑↓ 選択   ● 適用   MENU 戻る", "↑↓ 선택   ● 적용   MENU 뒤로")
add("↑↓ 选择   ● 确认   MENU 返回", "↑↓ Choose   ● Confirm   MENU Back",
    "↑↓ 選択   ● 決定   MENU 戻る", "↑↓ 선택   ● 확인   MENU 뒤로")
add("↑↓ 选择   ● 打开   MENU 返回", "↑↓ Choose   ● Open   MENU Back",
    "↑↓ 選択   ● 開く   MENU 戻る", "↑↓ 선택   ● 열기   MENU 뒤로")
add("↑↓ 选择   ● 确认   → 重扫   MENU 返回", "↑↓ Choose   ● Confirm   → Rescan   MENU Back",
    "↑↓ 選択   ● 決定   → 再スキャン   MENU 戻る", "↑↓ 선택   ● 확인   → 다시 스캔   MENU 뒤로")
add("↑↓ 选择   MENU 返回", "↑↓ Choose   MENU Back", "↑↓ 選択   MENU 戻る", "↑↓ 선택   MENU 뒤로")
add("←→ 选择   ● 拍摄   回放键 相册   MENU 更多",
    "←→ Choose   ● Shoot   Playback key Gallery   MENU More",
    "←→ 選択   ● 撮影   再生キー ギャラリー   MENU その他",
    "←→ 선택   ● 촬영   재생 키 갤러리   MENU 더 보기")
add("←→↑↓ 选择   ● 进入拍摄   回放键 相册   MENU 更多",
    "←→↑↓ Choose   ● Open capture   Playback key Gallery   MENU More",
    "←→↑↓ 選択   ● 撮影を開く   再生キー ギャラリー   MENU その他",
    "←→↑↓ 선택   ● 촬영 열기   재생 키 갤러리   MENU 더 보기")
add("←→ / 转盘选择 · 中央键开始拍摄", "←→ / dial choose · Center starts shooting",
    "←→ / ダイヤル選択 · 中央で撮影開始", "←→ / 다이얼 선택 · 중앙 촬영 시작")
add("左右键或转盘切换 · 中央键开始拍摄", "Arrow keys or dial switch · Center starts shooting",
    "左右キーまたはダイヤルで切替 · 中央で撮影開始", "좌우 키 또는 다이얼 전환 · 중앙 촬영 시작")
add("无格式", "No format", "形式なし", "형식 없음")
add("不支持", "Unsupported", "非対応", "지원 안 됨")
add("AF-A 自动", "AF-A Auto", "AF-A オート", "AF-A 자동")
add("AF-S 单次", "AF-S Single", "AF-S シングル", "AF-S 싱글")
add("AF-C 连续", "AF-C Continuous", "AF-C コンティニュアス", "AF-C 연속")
add("MOVIE 连续 AF", "MOVIE Continuous AF", "MOVIE コンティニュアスAF", "MOVIE 연속 AF")
add("DMF 全时手动", "DMF Direct Manual", "DMF ダイレクトマニュアル", "DMF 직접 수동")
add("MF 手动", "MF Manual", "MF マニュアル", "MF 수동")
add("P 自动", "P Auto", "P オート", "P 자동")
add("A 光圈优先", "A Aperture", "A 絞り優先", "A 조리개 우선")
add("S 快门优先", "S Shutter", "S シャッター優先", "S 셔터 우선")
add("M 手动", "M Manual", "M マニュアル", "M 수동")
add("白平衡预设不可用", "White balance preset unavailable", "ホワイトバランスプリセットを利用できません",
    "화이트 밸런스 프리셋을 사용할 수 없습니다")
add("相机外部媒体不可用", "Camera external media unavailable", "カメラの外部メディアを利用できません",
    "카메라 외부 미디어를 사용할 수 없습니다")
add("相机没有报告可用的录像格式", "The camera did not report any video format",
    "カメラが利用可能な動画形式を報告しません", "카메라가 사용 가능한 동영상 형식을 보고하지 않았습니다")
add("无法读取录像参数", "Could not read video parameters", "動画パラメータを読み込めません",
    "동영상 매개변수를 읽을 수 없습니다")
add("摄像机不可用", "Camera unavailable", "カメラを利用できません", "카메라를 사용할 수 없습니다")
add("曝光控制不可用", "Exposure control unavailable", "露出制御を利用できません",
    "노출 제어를 사용할 수 없습니다")
add("相机没有报告白平衡预设", "The camera did not report WB presets",
    "カメラがWBプリセットを報告しません", "카메라가 WB 프리셋을 보고하지 않았습니다")
add("相机没有报告对焦模式", "The camera did not report focus modes",
    "カメラがフォーカスモードを報告しません", "카메라가 초점 모드를 보고하지 않았습니다")
add("相机没有报告曝光补偿", "The camera did not report exposure compensation",
    "カメラが露出補正を報告しません", "카메라가 노출 보정을 보고하지 않았습니다")
add("录像中或准备中不能切换格式", "Cannot change format while recording or preparing",
    "録画中・準備中は形式を変更できません", "녹화 중 또는 준비 중에는 형식을 변경할 수 없습니다")
add("准备尚未完成，正在重试，请稍后再按录像",
    "Preparation is not complete; retrying. Press record again shortly.",
    "準備が未完了です。再試行中。しばらくしてから再度録画を押してください。",
    "준비가 끝나지 않았습니다. 다시 시도 중입니다. 잠시 후 녹화를 다시 누르세요.")
add("对焦未成功", "Focus did not lock", "フォーカスできませんでした", "초점이 맞지 않았습니다")
add("对焦模式回读", "Focus mode readback", "フォーカスモード読み戻し", "초점 모드 재확인")
add("录像错误 ", "Video error ", "動画エラー ", "동영상 오류 ")
add("取景色彩切换失败：", "Finder color switch failed: ", "ファインダー色切替失敗: ",
    "파인더 색상 전환 실패: ")
add("胶片切换失败：", "Film switch failed: ", "フィルム切替失敗: ", "필름 전환 실패: ")
add("对焦模式设置失败：", "Focus mode failed: ", "フォーカスモード設定失敗: ", "초점 모드 설정 실패: ")
add("白平衡设置失败：", "White balance failed: ", "ホワイトバランス設定失敗: ",
    "화이트 밸런스 설정 실패: ")
add("曝光模式切换失败：", "Exposure mode switch failed: ", "露出モード切替失敗: ",
    "노출 모드 전환 실패: ")
add("曝光模式设置失败：", "Exposure mode setting failed: ", "露出モード設定失敗: ",
    "노출 모드 설정 실패: ")
add("曝光补偿设置失败：", "Exposure compensation failed: ", "露出補正設定失敗: ",
    "노출 보정 설정 실패: ")
add("曝光调整失败：", "Exposure adjustment failed: ", "露出調整失敗: ", "노출 조정 실패: ")
add("实时对焦点切换失败：", "Live AF point switch failed: ", "ライブAFポイント切替失敗: ",
    "라이브 AF 포인트 전환 실패: ")
add("对焦点确认失败：", "AF point confirmation failed: ", "AFポイント決定失敗: ",
    "AF 포인트 확인 실패: ")
add("对焦恢复失败：", "Focus restore failed: ", "フォーカス復元失敗: ", "초점 복원 실패: ")
add("对焦点选择失败：", "AF point selection failed: ", "AFポイント選択失敗: ", "AF 포인트 선택 실패: ")
add("请稍候，视频保存完成后返回拍照", "Please wait; returning to photo after saving",
    "お待ちください。保存後に写真へ戻ります", "기다려 주세요. 저장 후 사진으로 돌아갑니다")
add("正在读取相机格式", "Reading camera format", "カメラ形式を読み込み中", "카메라 형식 읽는 중")
add("保存中", "Saving", "保存中", "저장 중")
add("待机", "Standby", "スタンバイ", "대기")
add("正在保存并返回", "Saving and returning", "保存して戻っています", "저장 후 돌아가는 중")

# ---------------------------------------------------------------- pose director
add("摆姿导演台", "Pose Director", "ポーズディレクター", "포즈 디렉터")
add("姿势库读取失败", "Could not read the pose library", "ポーズライブラリを読み込めません",
    "포즈 라이브러리를 읽을 수 없습니다")
add("姿势包是空的", "The pose pack is empty", "ポーズパックが空です", "포즈 팩이 비어 있습니다")
add("没有姿势卡", "No pose cards", "ポーズカードがありません", "포즈 카드가 없습니다")
add("这个分类还没有姿势卡", "No pose cards in this category", "このカテゴリにはポーズカードがありません",
    "이 카테고리에는 포즈 카드가 없습니다")
add("（这张卡没有姿势要点）", "(No pose notes on this card)", "（このカードにポーズ要点はありません）",
    "(이 카드에는 포즈 요점이 없습니다)")
add("机位  ", "Camera  ", "カメラ位置  ", "카메라 위치  ")
add("光位  ", "Light  ", "光の位置  ", "조명 위치  ")
add("关键词  ", "Keywords  ", "キーワード  ", "키워드  ")
add("正在载入…", "Loading…", "読み込み中…", "불러오는 중…")
add("无图片", "No image", "画像なし", "이미지 없음")
add("POSE 图片无法解码", "Could not decode the POSE image", "POSE画像をデコードできません",
    "POSE 이미지를 디코딩할 수 없습니다")
add("POSE 索引失败: ", "POSE index failed: ", "POSEインデックス失敗: ", "POSE 인덱스 실패: ")
add("SD/POSE 不可读且无内置图 · 见 SONYLUT/POSE.TXT",
    "SD/POSE unreadable and no built-in image · See SONYLUT/POSE.TXT",
    "SD/POSEが読めず内蔵画像もありません · SONYLUT/POSE.TXT参照",
    "SD/POSE를 읽을 수 없고 내장 이미지도 없음 · SONYLUT/POSE.TXT 참조")
add("未找到 ", "Not found: ", "見つかりません: ", "찾을 수 없음: ")
add(" 图片（SD 与内置）· 见 SONYLUT/POSE.TXT", " image (SD and built-in) · See SONYLUT/POSE.TXT",
    " 画像（SD・内蔵）· SONYLUT/POSE.TXT参照", " 이미지 (SD 및 내장) · SONYLUT/POSE.TXT 참조")
add("C2 关闭", "C2 Close", "C2 閉じる", "C2 닫기")
add("C2 关闭 · 左右 换分类", "C2 Close · Left/right Category", "C2 閉じる · 左右 カテゴリ",
    "C2 닫기 · 좌우 카테고리")
add("C2 关闭 · 上下/拨轮 翻卡 · 左右 换分类 · 快门可拍",
    "C2 Close · Up/down/dial Cards · Left/right Category · Shutter shoots",
    "C2 閉じる · 上下/ダイヤル カード · 左右 カテゴリ · シャッター撮影",
    "C2 닫기 · 상하/다이얼 카드 · 좌우 카테고리 · 셔터 촬영")

# ---------------------------------------------------------------- white balance / contact sheet
add("WB 日光", "WB Daylight", "WB 昼光", "WB 주광")
add("WB 阴影", "WB Shade", "WB 日陰", "WB 그늘")
add("WB 阴天", "WB Cloudy", "WB 曇天", "WB 흐림")
add("WB 白炽灯", "WB Incandescent", "WB 白熱灯", "WB 백열등")
add("WB 闪光", "WB Flash", "WB フラッシュ", "WB 플래시")
add("WB 色温", "WB Color Temp.", "WB 色温度", "WB 색온도")
add("WB 自定义", "WB Custom", "WB カスタム", "WB 사용자 설정")
add("WB 荧光灯", "WB Fluorescent", "WB 蛍光灯", "WB 형광등")
add("WB 水下", "WB Underwater", "WB 水中", "WB 수중")
add("0 张", "0 photos", "0枚", "0장")
add("拍摄后在这里查看接触印相", "Your contact sheet appears here after shooting",
    "撮影後にここでコンタクトシートを確認できます", "촬영 후 여기에서 컨택트 시트를 볼 수 있습니다")
add("←→↑↓ 选择   ● 打开   MENU/BACK 关闭", "←→↑↓ Choose   ● Open   MENU/BACK Close",
    "←→↑↓ 選択   ● 開く   MENU/BACK 閉じる", "←→↑↓ 선택   ● 열기   MENU/BACK 닫기")
add("第 ", "Frame ", "コマ ", "프레임 ")
add(" 帧", " Frame", "コマ", "프레임")
add(" 张", " photos", "枚", "장")
add("张", " photos", "枚", "장")
add("卷 ", "Roll ", "ロール ", "롤 ")
add("已拍 ", "Shot ", "撮影済み ", "촬영 ")
add(" 个胶卷", " films", "件", "개 필름")
add(" 个 · ", " · ", " · ", " · ")
add(" 秒", " s", " 秒", "초")
add("秒", " s", "秒", "초")
add(" 配方", " recipes", "レシピ", "개 레시피")
add("配方", "Recipe", "レシピ", "레시피")
add("片", "Film", "フィルム", "필름")
add(" · 当前", " · Current", " · 現在", " · 현재")
add(" · 失败 ", " · Failed ", " · 失敗 ", " · 실패 ")
add(" · 已存在 ", " · Exists ", " · 既存 ", " · 있음 ")
add(" · 已导入 ", " · Imported ", " · 読み込み済み ", " · 가져옴 ")
add("已保存 · ", "Saved · ", "保存済み · ", "저장됨 · ")
add("已启用 ", "Enabled ", "有効 ", "활성화 ")
add("已选择 ", "Selected ", "選択 ", "선택 ")
add("正在应用 ", "Applying ", "適用中 ", "적용 중 ")
add(" · 相机直出", " · Camera JPEG", " · カメラJPEG", " · 카메라 JPEG")
add(" · 正在应用", " · Applying", " · 適用中", " · 적용 중")
add("胶卷库 · ", "Film Library · ", "フィルムライブラリ · ", "필름 라이브러리 · ")
add("近似配方 · ", "Approx. recipes · ", "近似レシピ · ", "근사 레시피 · ")
add("A7R2胶片相机 编译时间：", "A7R2 Film Camera  Build: ", "A7R2フィルムカメラ  ビルド: ",
    "A7R2 필름 카메라  빌드: ")

# ---------------------------------------------------------------- dynamic rules
add("胶卷已更新 · 共 {} 张", "Film updated · {} photos", "フィルム更新 · {}枚",
    "필름 업데이트 · {}장")
add("发现 {} 个胶卷", "Found {} films", "フィルム{}件", "필름 {}개 발견")
add("{} 张照片 · 配方直出为单张 JPEG，真 LUT 保留原片与成片",
    "{} photos · Recipes save one camera JPEG; Real LUT keeps original and result",
    "写真{}枚 · レシピはカメラJPEG1枚、リアルLUTはオリジナルと完成写真を保持",
    "사진 {}장 · 레시피는 카메라 JPEG 1장, 리얼 LUT는 원본과 결과 유지")
add("新增 {} · 已存在 {} · 失败 {}", "New {} · Existing {} · Failed {}",
    "新規{} · 既存{} · 失敗{}", "신규 {} · 기존 {} · 실패 {}")
add("找到 {} 个 · {}", "Found {} · {}", "{}件 · {}", "{}개 · {}")
add("{} · 放大 {}", "{} · Zoom {}", "{} · 拡大 {}", "{} · 확대 {}")
add("已删除照片 · 还剩 {} 张", "Photo deleted · {} left", "写真を削除 · 残り{}枚",
    "사진 삭제됨 · {}장 남음")
add("已删除整组 · 还剩 {} 张", "Set deleted · {} left", "セットを削除 · 残り{}枚",
    "세트 삭제됨 · {}장 남음")
add("{} 组拍摄记录失效（DCIM 被清理或照片被移动）",
    "{} capture records are stale (DCIM cleaned or photos moved)",
    "{}件の撮影記録が無効です（DCIM消去または写真移動）",
    "촬영 기록 {}건이 유효하지 않습니다 (DCIM 정리 또는 사진 이동)")
add("正在打开{}", "Opening {}", "{}を開いています", "{} 여는 중")
add("已保存 · {} · 可直接使用", "Saved · {} · Ready to use", "保存済み · {} · 使用できます",
    "저장됨 · {} · 바로 사용 가능")
add("已启用 {} · 相机直出", "Enabled {} · Camera JPEG", "{} を有効化 · カメラJPEG",
    "{} 활성화 · 카메라 JPEG")
add("已选择 {} · 正在应用", "Selected {} · Applying", "{} を選択 · 適用中",
    "{} 선택 · 적용 중")
add("正在应用 {} · 相机直出", "Applying {} · Camera JPEG", "{} を適用中 · カメラJPEG",
    "{} 적용 중 · 카메라 JPEG")
add("胶片切换失败 · {}", "Film switch failed · {}", "フィルム切替失敗 · {}",
    "필름 전환 실패 · {}")
add("胶片切换失败：{}", "Film switch failed: {}", "フィルム切替失敗: {}",
    "필름 전환 실패: {}")
add("取景色彩切换失败：{}", "Finder color switch failed: {}", "ファインダー色切替失敗: {}",
    "파인더 색상 전환 실패: {}")
add("对焦模式设置失败：{}", "Focus mode failed: {}", "フォーカスモード設定失敗: {}",
    "초점 모드 설정 실패: {}")
add("白平衡设置失败：{}", "White balance failed: {}", "ホワイトバランス設定失敗: {}",
    "화이트 밸런스 설정 실패: {}")
add("曝光模式切换失败：{}", "Exposure mode switch failed: {}", "露出モード切替失敗: {}",
    "노출 모드 전환 실패: {}")
add("曝光模式设置失败：{}", "Exposure mode setting failed: {}", "露出モード設定失敗: {}",
    "노출 모드 설정 실패: {}")
add("曝光补偿设置失败：{}", "Exposure compensation failed: {}", "露出補正設定失敗: {}",
    "노출 보정 설정 실패: {}")
add("曝光调整失败：{}", "Exposure adjustment failed: {}", "露出調整失敗: {}",
    "노출 조정 실패: {}")
add("实时对焦点切换失败：{}", "Live AF point switch failed: {}", "ライブAFポイント切替失敗: {}",
    "라이브 AF 포인트 전환 실패: {}")
add("对焦点确认失败：{}", "AF point confirmation failed: {}", "AFポイント決定失敗: {}",
    "AF 포인트 확인 실패: {}")
add("对焦恢复失败：{}", "Focus restore failed: {}", "フォーカス復元失敗: {}",
    "초점 복원 실패: {}")
add("对焦点选择失败：{}", "AF point selection failed: {}", "AFポイント選択失敗: {}",
    "AF 포인트 선택 실패: {}")
add("录像错误 {}", "Video error {}", "動画エラー {}", "동영상 오류 {}")
add("操作未生效：{}", "No effect: {}", "反映されません: {}", "적용되지 않음: {}")
add("拍摄未完成 · {}", "Capture incomplete · {}", "撮影未完了 · {}", "촬영 미완료 · {}")
add("原片已保存 · {}", "Original saved · {}", "オリジナル保存済み · {}", "원본 저장됨 · {}")
add("皮肤平滑 {} · 降噪 {}", "Skin Smooth {} · NR {}", "肌補正 {} · ノイズ低減 {}",
    "피부 보정 {} · 노이즈 감소 {}")
add("皮肤平滑 {} · {}", "Skin Smooth {} · {}", "肌補正 {} · {}", "피부 보정 {} · {}")
add("成片 {} 秒", "Result {} s", "完成 {} 秒", "결과 {}초")
add("成片 {} 秒 · 已保存原片 + 冲洗成片", "Result {} s · Original + developed saved",
    "完成 {} 秒 · オリジナルと現像済みを保存", "결과 {}초 · 원본 + 현상 결과 저장됨")
add("成片 {} 秒 · 下张可拍 {} 秒", "Result {} s · Next shot in {} s",
    "完成 {} 秒 · 次の撮影まで {} 秒", "결과 {}초 · 다음 촬영 {}초")
add("接口等待超过 {} 秒\n请保留当前页面，读取日志。",
    "Interface wait exceeded {} s\nKeep this page open and read the log.",
    "インターフェース待機が{}秒を超えました\nこの画面を開いたままログを確認してください。",
    "인터페이스 대기 {}초 초과\n이 화면을 유지하고 로그를 확인하세요.")
add("验证成功：{} 原片已保存\n保存 {} 秒；核验 {} 秒\n请保留此页，读取测试结果。",
    "Verified: {} original saved\nSave {} s; verify {} s\nKeep this page for the test result.",
    "検証成功: {} オリジナル保存済み\n保存{}秒、検証{}秒\nこの画面を開いたまま結果を確認してください。",
    "검증 성공: {} 원본 저장됨\n저장 {}초, 검증 {}초\n이 화면을 유지하고 결과를 확인하세요.")
add("第 {} 帧", "Frame {}", "コマ {}", "프레임 {}")
add("卷 {} · {} 张", "Roll {} · {} photos", "ロール{} · {}枚", "롤 {} · {}장")
add("已拍 {} 张", "Shot {} photos", "撮影済み {}枚", "촬영 {}장")
add("摆姿导演台    {}/{}", "Pose Director    {}/{}", "ポーズディレクター    {}/{}",
    "포즈 디렉터    {}/{}")

# ---------------------------------------------------------------- remaining product UI
add(" 期望 ", " expected ", " 期待値 ", " 기대값 ")
add("对焦模式回读 {} 期望 {}", "Focus mode readback {} expected {}", "フォーカスモード読み戻し {} 期待値 {}",
    "초점 모드 재확인 {} 기대값 {}")
add("亮度 ", "Brightness ", "明るさ ", "밝기 ")
add("胶卷类型 ", "Film type ", "フィルム種別 ", "필름 종류 ")
add("配方照片由相机直出，不会经过美颜或降噪 · ",
    "Recipe photos come straight from the camera, with no beauty or noise reduction · ",
    "レシピ写真はカメラから直接出力され、美肌・ノイズ低減はかかりません · ",
    "레시피 사진은 카메라에서 바로 출력되며 피부 보정·노이즈 감소가 없습니다 · ")
add("配方照片由相机直出", "Recipe photos come straight from the camera",
    "レシピ写真はカメラから直接出力されます", "레시피 사진은 카메라에서 바로 출력됩니다")
add("不会经过美颜或降噪", "No beauty or noise reduction", "美肌・ノイズ低減なし",
    "피부 보정·노이즈 감소 없음")
add("42MP 全尺寸机内处理，可能增加数秒；默认关闭，不做 AI 磨皮",
    "Full-size 42MP in-camera processing adds a few seconds; off by default, no AI smoothing",
    "42MPフルサイズを機内処理するため数秒かかります。既定はオフ、AI美肌なし",
    "42MP 전체 크기 카메라 내 처리로 몇 초가 걸릴 수 있습니다. 기본 꺼짐, AI 보정 없음")
add("GitHub: sony-lut-camera · 原项目源码与说明 · 开源 GPL-3.0-only",
    "GitHub: sony-lut-camera · Source and docs · Open source GPL-3.0-only",
    "GitHub: sony-lut-camera · ソースと説明 · オープンソース GPL-3.0-only",
    "GitHub: sony-lut-camera · 소스 및 설명 · 오픈소스 GPL-3.0-only")
add("github.com/voxivoid/recipe-lab · 开放配方数据",
    "github.com/voxivoid/recipe-lab · Open recipe data",
    "github.com/voxivoid/recipe-lab · オープンレシピデータ",
    "github.com/voxivoid/recipe-lab · 오픈 레시피 데이터")
add("MENU → 导入胶卷 · 中央键确认", "MENU → Import films · Center to confirm",
    "MENU → フィルム読み込み · 中央で決定", "MENU → 필름 가져오기 · 중앙 확인")
add("UI 动画预览", "UI Motion Demo", "UIモーション試作", "UI 모션 데모")
add("动画原型无法初始化：\n", "Could not initialize the motion prototype:\n",
    "モーション試作を初期化できません:\n", "모션 프로토타입을 초기화할 수 없습니다:\n")
add("动画原型无法初始化：", "Could not initialize the motion prototype: ",
    "モーション試作を初期化できません: ", "모션 프로토타입을 초기화할 수 없습니다: ")
add("基准", "Baseline", "基準", "기준")
add("测光", "Metering", "測光", "측광")
add("纯色", "Solid", "単色", "단색")
add("快门", "Shutter", "シャッター", "셔터")
add("胶片货架/快门/测光动效测试", "Film shelf / shutter / metering motion test",
    "フィルム棚・シャッター・測光モーション", "필름 선반·셔터·측광 모션 테스트")
add("相机列表", "Camera List", "カメラ一覧", "카메라 목록")
add("回到", "Back to ", "戻る: ", "돌아가기: ")
add("回到相机原生拍摄界面", "Return to the camera's native shooting screen",
    "カメラ本来の撮影画面に戻る", "카메라 기본 촬영 화면으로 돌아가기")
add("保留当前设置", "Keep current settings", "現在の設定を保持", "현재 설정 유지")
add("全部功能、按键与保存方式 · 37 条", "All features, keys and saving · 37 items",
    "全機能・キー・保存方法 · 37項目", "모든 기능·키·저장 방식 · 37개")
add("全部导入", "Import All", "すべて読み込む", "모두 가져오기")
add("查看", "View", "表示", "보기")
add("更多", "More", "その他", "더 보기")
add("打开", "Open", "開く", "열기")
add("遇到", "Found ", "検出 ", "발견 ")
add("设置", "Settings", "設定", "설정")
add("视频", "Video", "動画", "동영상")
add("日期未设置", "Date not set", "日付未設定", "날짜 미설정")
add("继续选择或拍摄", "Keep choosing or shooting", "選択または撮影を続けてください",
    "계속 선택하거나 촬영하세요")
add("没有可用配方", "No recipes available", "利用できるレシピがありません",
    "사용 가능한 레시피가 없습니다")
add("请检查应用安装是否完整", "Check that the app installation is complete",
    "アプリのインストールが完全か確認してください", "앱 설치가 완전한지 확인하세요")
add("从 SD 卡 LUTS 文件夹加入胶卷", "Add films from the SD card LUTS folder",
    "SDカードのLUTSフォルダからフィルムを追加", "SD 카드 LUTS 폴더에서 필름 추가")
add("当前胶卷的取景", "This film's finder look", "このフィルムのファインダー表示",
    "이 필름의 파인더 화면")
add("只作用于真 LUT 冲洗成片 · 原片保持不动",
    "Applies only to Real LUT results · Originals stay untouched",
    "リアルLUTの完成写真のみに適用 · オリジナルは変更しません",
    "리얼 LUT 결과에만 적용 · 원본은 그대로 유지")
add("真 LUT 成片使用 · 配方直出不受影响",
    "Used by Real LUT results · Recipe JPEGs are unaffected",
    "リアルLUT完成写真で使用 · レシピ直出には影響しません",
    "리얼 LUT 결과에 사용 · 레시피 JPEG에는 영향 없음")
add("真 LUT 模式使用 · 近似色彩或原色", "Used in Real LUT mode · Approx. color or original",
    "リアルLUTモードで使用 · 近似色または原色", "리얼 LUT 모드에서 사용 · 근사 색상 또는 원색")
add("真 LUT · 先选胶卷", "Real LUT · Choose a film first", "リアルLUT · 先にフィルムを選択",
    "리얼 LUT · 먼저 필름 선택")
add("真 LUT 成片与配方直出照片", "Real LUT results and recipe JPEGs", "リアルLUT完成写真とレシピ直出写真",
    "리얼 LUT 결과 및 레시피 JPEG")
add("近似胶片配方 · 相机直出 · C3 换胶片",
    "Approx. film recipes · Camera JPEG · C3 changes film",
    "近似フィルムレシピ · カメラJPEG · C3でフィルム切替",
    "근사 필름 레시피 · 카메라 JPEG · C3 필름 전환")
add("选相机直接进入取景 · 取景中按 C3 换胶片",
    "Picking a camera opens the finder · C3 changes film there",
    "カメラを選ぶとファインダーへ · ファインダーでC3を押すとフィルム切替",
    "카메라를 고르면 파인더로 이동 · 파인더에서 C3로 필름 전환")
add("配方由 Recipe Lab 提供 · 直接写入相机 JPEG · 进入后 C3 可实时换胶片",
    "Recipes by Recipe Lab · Written directly into camera JPEGs · C3 changes film live",
    "レシピはRecipe Lab提供 · カメラJPEGに直接記録 · 起動後C3でリアルタイム切替",
    "레시피 제공: Recipe Lab · 카메라 JPEG에 직접 기록 · 실행 후 C3로 실시간 필름 전환")
add("配方数据 v1.11.1 · GPL-3.0 · 特别感谢", "Recipe data v1.11.1 · GPL-3.0 · Special thanks",
    "レシピデータ v1.11.1 · GPL-3.0 · 特別感謝", "레시피 데이터 v1.11.1 · GPL-3.0 · 특별 감사")
add("发现 ", "Found ", "検出 ", "발견 ")
add("找到 ", "Found ", "検出 ", "발견 ")
add("已删除照片 · 还剩 ", "Photo deleted · ", "写真を削除 · 残り", "사진 삭제됨 · 남은 ")
add("已删除整组 · 还剩 ", "Set deleted · ", "セットを削除 · 残り", "세트 삭제됨 · 남은 ")
add("胶卷已更新 · 共 ", "Film updated · ", "フィルム更新 · ", "필름 업데이트 · 총 ")
add("装卷", "Loading", "装填中", "장전 중")
add("日光", "Daylight", "昼光", "주광")
add("白炽", "Incandescent", "白熱灯", "백열등")
add("阴影", "Shade", "日陰", "그늘")
add("阴天", "Cloudy", "曇天", "흐림")
add("荧光", "Fluorescent", "蛍光灯", "형광등")
add("闪光", "Flash", "フラッシュ", "플래시")
add("水下", "Underwater", "水中", "수중")
add("自动", "Auto", "オート", "자동")
add("自定义", "Custom", "カスタム", "사용자 설정")
add("色温", "Color Temp.", "色温度", "색온도")
add("正在生成效果参考", "Rendering effect reference", "効果リファレンスを生成中",
    "효과 참조 이미지 생성 중")
add("正在读取", "Reading", "読み込み中", "읽는 중")
add("无法读取胶卷库", "Could not read the film library", "フィルムライブラリを読み込めません",
    "필름 라이브러리를 읽을 수 없습니다")
add("胶卷索引损坏", "Film index is damaged", "フィルム索引が破損しています",
    "필름 인덱스가 손상되었습니다")
add("选择的胶卷不存在", "The selected film no longer exists", "選択したフィルムがありません",
    "선택한 필름이 없습니다")
add("导入未完成：无法保存胶卷", "Import incomplete: could not save the film",
    "読み込み未完了: フィルムを保存できません", "가져오기 미완료: 필름을 저장할 수 없습니다")
add("无法保存当前胶卷", "Could not save the current film", "現在のフィルムを保存できません",
    "현재 필름을 저장할 수 없습니다")
add("无法创建胶卷导入目录", "Could not create the film import folder",
    "フィルム読み込みフォルダを作成できません", "필름 가져오기 폴더를 만들 수 없습니다")
add("无法创建存储目录", "Could not create the storage folder", "保存フォルダを作成できません",
    "저장 폴더를 만들 수 없습니다")
add("无法初始化存储目录", "Could not initialize the storage folder", "保存フォルダを初期化できません",
    "저장 폴더를 초기화할 수 없습니다")
add("无法读取 SD 卡 LUTS 文件夹", "Could not read the SD card LUTS folder",
    "SDカードのLUTSフォルダを読み込めません", "SD 카드 LUTS 폴더를 읽을 수 없습니다")
add("无法清理旧示例胶卷", "Could not clean up old sample films", "古いサンプルフィルムを整理できません",
    "이전 샘플 필름을 정리할 수 없습니다")
add("无法清理旧示例胶卷 源缓存", "Could not clean up old sample film source cache",
    "古いサンプルフィルムのソースキャッシュを整理できません",
    "이전 샘플 필름 원본 캐시를 정리할 수 없습니다")
add("无法移除旧示例胶卷", "Could not remove old sample films", "古いサンプルフィルムを削除できません",
    "이전 샘플 필름을 제거할 수 없습니다")
add("文件为空或超过 64 MB", "File is empty or larger than 64 MB", "ファイルが空か64MBを超えています",
    "파일이 비어 있거나 64MB를 초과합니다")
add("文件超过 64 MB", "File is larger than 64 MB", "ファイルが64MBを超えています",
    "파일이 64MB를 초과합니다")
add("LUT 超过 64 MB", "LUT is larger than 64 MB", "LUTが64MBを超えています",
    "LUT가 64MB를 초과합니다")
add("LUT 内容已变化，请从 LUTS 重新导入", "LUT content changed; import it again from LUTS",
    "LUTの内容が変わりました。LUTSから再読み込みしてください",
    "LUT 내용이 변경되었습니다. LUTS에서 다시 가져오세요")
add("不支持的 3D LUT 阶数", "Unsupported 3D LUT size", "未対応の3D LUTサイズ", "지원되지 않는 3D LUT 크기")
add("内置 LUT 必须有一个默认项", "Built-in LUTs need a default entry", "内蔵LUTには既定項目が必要です",
    "내장 LUT에는 기본 항목이 필요합니다")
add("内置 LUT 文件名重复", "Duplicate built-in LUT filename", "内蔵LUTのファイル名が重複しています",
    "내장 LUT 파일 이름이 중복됩니다")
add("内置 LUT 清单无效", "Invalid built-in LUT list", "内蔵LUTリストが無効です",
    "내장 LUT 목록이 유효하지 않습니다")
add("内置 LUT 清单过大", "Built-in LUT list is too large", "内蔵LUTリストが大きすぎます",
    "내장 LUT 목록이 너무 큽니다")
add("无法保存预览偏好", "Could not save the preview preference", "プレビュー設定を保存できません",
    "미리보기 설정을 저장할 수 없습니다")
add("无法保存预览缓存", "Could not save the preview cache", "プレビューキャッシュを保存できません",
    "미리보기 캐시를 저장할 수 없습니다")
add("无法更新预览缓存", "Could not update the preview cache", "プレビューキャッシュを更新できません",
    "미리보기 캐시를 업데이트할 수 없습니다")
add("无法读取照片记录", "Could not read photo records", "写真記録を読み込めません",
    "사진 기록을 읽을 수 없습니다")
add("照片记录保存失败", "Could not save photo records", "写真記録を保存できません",
    "사진 기록 저장 실패")
add("照片记录损坏：", "Photo records damaged: ", "写真記録が破損: ", "사진 기록 손상: ")
add("照片记录文件重名，请重试", "Photo record filename collision; try again",
    "写真記録のファイル名が重複しています。再試行してください",
    "사진 기록 파일 이름이 중복됩니다. 다시 시도하세요")
add("照片路径无效", "Invalid photo path", "写真パスが無効です", "사진 경로가 유효하지 않습니다")
add("原片与成片必须独立保存", "Original and result must be saved separately",
    "オリジナルと完成写真は別々に保存する必要があります",
    "원본과 결과는 별도로 저장해야 합니다")
add("测试连接不可用", "Test connection unavailable", "テスト接続を利用できません",
    "테스트 연결을 사용할 수 없습니다")
add("测试连接未安装", "Test connection is not installed", "テスト接続がインストールされていません",
    "테스트 연결이 설치되지 않았습니다")
add("请先更新测试连接到 0.5，再退出应用",
    "Update the test connection to 0.5 before exiting", "テスト接続を0.5に更新してから終了してください",
    "테스트 연결을 0.5로 업데이트한 후 종료하세요")
add("调试连接应用签名不匹配，未执行退出", "Debug helper signature mismatch; exit was not performed",
    "デバッグ接続の署名が一致しないため終了しませんでした",
    "디버그 연결 서명이 일치하지 않아 종료하지 않았습니다")
add("调试连接清理未完成，已停止退出", "Debug helper cleanup incomplete; exit stopped",
    "デバッグ接続の整理が未完了のため終了を中止しました",
    "디버그 연결 정리가 끝나지 않아 종료를 중단했습니다")
add("无法启动调试连接清理", "Could not start debug helper cleanup", "デバッグ接続の整理を開始できません",
    "디버그 연결 정리를 시작할 수 없습니다")
add("无法访问测试连接退出服务", "Could not reach the test connection exit service",
    "テスト接続の終了サービスに接続できません", "테스트 연결 종료 서비스에 접근할 수 없습니다")
add("退出回执不是系统 Parcelable 类型", "Exit receipt is not a system Parcelable",
    "終了レシートがシステムParcelableではありません", "종료 영수증이 시스템 Parcelable이 아닙니다")
add("可重试或按 ← 返回拍照", "Retry, or press ← to return to photo", "再試行するか、←で写真に戻ります",
    "다시 시도하거나 ←로 사진으로 돌아가세요")
add("发现 {} 个胶卷", "Found {} films", "フィルム{}件", "필름 {}개 발견")
add("简体中文", "Simplified Chinese", "簡体字中国語", "간체 중국어")
add("繁體中文", "Traditional Chinese", "繁体字中国語", "번체 중국어")
add("日本語", "Japanese", "日本語", "일본어")
add("한국어", "Korean", "韓国語", "한국어")
add("关闭", "Off", "オフ", "끔")
add("开", "On", "オン", "켬")
add("关", "Off", "オフ", "끔")
add("弱", "Weak", "弱", "약하게")
add("强", "Strong", "強", "강하게")
add("低", "Low", "低", "낮음")
add("中", "Mid", "中", "중간")
add("高", "High", "高", "높음")
add("标准", "Standard", "標準", "표준")
add("不处理", "No processing", "処理なし", "처리 안 함")
add("仅肤色区域", "Skin tones only", "肌色のみ", "피부색 영역만")
add("色度降噪", "Chroma NR", "色ノイズ低減", "색 노이즈 감소")
add("正在打开照片", "Opening photo", "写真を開いています", "사진 여는 중")
add("正在打开胶卷库", "Opening film library", "フィルムライブラリを開いています",
    "필름 라이브러리 여는 중")
add("正在打开胶卷", "Opening film", "フィルムを開いています", "필름 여는 중")
add("正在打开拍照", "Opening camera", "撮影画面を開いています", "촬영 화면 여는 중")
add("配方照片由相机直出，不会经过美颜或降噪 · 皮肤平滑 {} · 降噪 {}",
    "Recipe photos come straight from the camera; no beauty or NR · Skin Smooth {} · NR {}",
    "レシピ写真はカメラから直接出力。美肌・ノイズ低減なし · 肌補正 {} · ノイズ低減 {}",
    "레시피 사진은 카메라에서 바로 출력; 피부 보정·노이즈 감소 없음 · 피부 보정 {} · 노이즈 감소 {}")

# ---------------------------------------------------------------- phone transfer
add("手机传输", "Phone Transfer", "スマホ転送", "휴대폰 전송")
add("传输", "Transfer", "転送", "전송")
add("手机自动传片", "Automatic Phone Transfer", "スマホへの自動転送", "휴대폰 자동 전송")
add("手机自动接收照片", "Phone Receives Photos Automatically",
    "スマホが写真を自動受信", "휴대폰이 사진을 자동 수신")
add("手机连接相机", "Connect Phone to Camera", "スマホをカメラに接続", "휴대폰을 카메라에 연결")
add("录像传输", "Video Transfer", "動画転送", "동영상 전송")
add("选择录像", "Select Videos", "動画を選択", "동영상 선택")
add("扫描录像", "Scanning Videos", "動画をスキャン中", "동영상 스캔 중")
add("正在扫描录像", "Scanning videos", "動画をスキャン中", "동영상 스캔 중")
add("刷新", "Refresh", "更新", "새로 고침")
add("刷新列表", "Refresh List", "リストを更新", "목록 새로 고침")
add("全选", "Select All", "すべて選択", "모두 선택")
add("全选已完成", "Select All Done", "完了分をすべて選択", "완료 항목 모두 선택")
add("清空", "Clear", "クリア", "지우기")
add("清空选择", "Clear Selection", "選択をクリア", "선택 지우기")
add("清空录像选择", "Clear Video Selection", "動画の選択をクリア", "동영상 선택 지우기")
add("勾选", "Check", "チェック", "선택")
add("已勾选", "Checked", "チェック済み", "선택됨")
add("已勾选 ", "Checked ", "チェック済み ", "선택됨 ")
add("未勾选", "Not checked", "未選択", "선택 안 됨")
add("已选", "Selected", "選択済み", "선택됨")
add("写入中", "Writing", "書き込み中", "기록 중")
add("名称", "Name", "名前", "이름")
add("密码", "Password", "パスワード", "비밀번호")
add("开启", "On", "オン", "켜기")
add("已关闭", "Stopped", "オフ", "꺼짐")
add("配对", "Pair", "ペアリング", "페어링")
add("配对码", "Pairing Code", "ペアリングコード", "페어링 코드")
add("网络", "Network", "ネットワーク", "네트워크")
add("地址与端口", "Address & Port", "アドレスとポート", "주소 및 포트")
add(" · 端口 ", " · Port ", " · ポート ", " · 포트 ")
add(" 个 · 手机端点按下载", " selected · Tap download on the phone",
    "件 · スマホ側でタップしてダウンロード", "개 · 휴대폰에서 눌러 다운로드")
add(" 个已勾选 · 取消勾选后手机立即不可见",
    " selected · Unchecked videos disappear from the phone immediately",
    "件を選択 · 解除するとスマホ側からすぐに消えます",
    "개 선택됨 · 선택을 해제하면 휴대폰에서 즉시 사라집니다")
add("只传已勾选并写完的录像 · 手机端仍需点按下载",
    "Only checked, finished videos · Tap download on the phone",
    "チェック済みで書き込み完了した動画のみ · スマホ側でタップしてダウンロード",
    "선택하고 기록이 끝난 동영상만 전송 · 휴대폰에서 눌러 다운로드")
add("把当前列表中的已完成录像加入传输",
    "Add all finished videos in this list to transfer",
    "このリストの完了した動画をすべて転送対象に追加",
    "현재 목록의 완료된 동영상을 모두 전송에 추가")
add("手机端录像列表会同时清空", "The video list on the phone is cleared too",
    "スマホ側の動画リストも同時に消去されます", "휴대폰의 동영상 목록도 함께 지워집니다")
add("手机端不再显示任何录像", "No videos remain visible on the phone",
    "スマホ側に動画は表示されなくなります", "휴대폰에 더 이상 동영상이 표시되지 않습니다")
add("录像仍在写入，请稍后再选", "Video is still being written; select it later",
    "動画を書き込み中です。しばらくしてから選択してください",
    "동영상 아직 기록 중입니다. 잠시 후 선택하세요")
add("仍在写入，暂不能勾选", "Still writing; cannot be selected yet",
    "書き込み中のため、まだ選択できません", "아직 기록 중이라 선택할 수 없습니다")
add("录像列表尚未载入", "Video list is not loaded yet",
    "動画リストがまだ読み込まれていません", "동영상 목록이 아직 로드되지 않았습니다")
add("录像还没有写完", "Video is not finished yet", "動画の書き込みが完了していません",
    "동영상 기록이 아직 끝나지 않았습니다")
add("没有找到录像 · 请确认录像已保存在 DCIM 或 PRIVATE",
    "No videos found · Make sure they are saved under DCIM or PRIVATE",
    "動画が見つかりません · DCIMまたはPRIVATEに保存されているか確認してください",
    "동영상을 찾지 못했습니다 · DCIM 또는 PRIVATE에 저장되었는지 확인하세요")
add("正在读取 SD 卡 · 不启动相机", "Reading the SD card · Camera is not started",
    "SDカードを読み込み中 · カメラは起動しません", "SD 카드 읽는 중 · 카메라를 시작하지 않음")
add("正在保存录像选择", "Saving video selection", "動画の選択を保存中", "동영상 선택 저장 중")
add("正在清空录像选择", "Clearing video selection", "動画の選択をクリア中", "동영상 선택 지우는 중")
add("无法保存录像选择", "Could not save the video selection",
    "動画の選択を保存できません", "동영상 선택을 저장할 수 없습니다")
add("无法更新录像选择", "Could not update the video selection",
    "動画の選択を更新できません", "동영상 선택을 업데이트할 수 없습니다")
add("重新扫描 DCIM、MP_ROOT 与 PRIVATE", "Rescan DCIM, MP_ROOT and PRIVATE",
    "DCIM、MP_ROOT、PRIVATEを再スキャン", "DCIM, MP_ROOT 및 PRIVATE 다시 스캔")
add("开启手机传输", "Turn On Phone Transfer", "スマホ転送をオン", "휴대폰 전송 켜기")
add("关闭手机传输", "Turn Off Phone Transfer", "スマホ転送をオフ", "휴대폰 전송 끄기")
add("手机传输已开启，正在准备相机 Wi-Fi",
    "Phone transfer turned on; preparing camera Wi-Fi",
    "スマホ転送をオンにしました。カメラWi-Fiを準備中",
    "휴대폰 전송을 켰습니다. 카메라 Wi-Fi 준비 중")
add("手机传输已关闭", "Phone transfer turned off", "スマホ転送をオフにしました",
    "휴대폰 전송을 껐습니다")
add("手机传输已停止", "Phone transfer stopped", "スマホ転送を停止しました",
    "휴대폰 전송이 중지되었습니다")
add("服务状态", "Service Status", "サービス状態", "서비스 상태")
add("相机 Wi-Fi 与配对", "Camera Wi-Fi & Pairing", "カメラWi-Fiとペアリング",
    "카메라 Wi-Fi 및 페어링")
add("关闭 · 手机自动接收新照片", "Off · Phone receives new photos automatically",
    "オフ · スマホが新しい写真を自動受信", "끔 · 휴대폰이 새 사진 자동 수신")
add("开启后只在新照片拍完时后台发送",
    "Once on, new photos are sent in the background after capture",
    "オンにすると、新しく撮影した写真だけをバックグラウンドで送信します",
    "켜면 새 사진이 촬영 후 백그라운드로 전송됩니다")
add("保持本页或取景器打开 · 手机连接相机本地 Wi-Fi",
    "Keep this page or the finder open · Connect your phone to the camera's local Wi-Fi",
    "この画面かファインダーを開いたままにしてください · スマホをカメラのローカルWi-Fiに接続",
    "이 화면이나 뷰파인더를 열어 두세요 · 휴대폰을 카메라 로컬 Wi-Fi에 연결")
add("Wi-Fi 名称(SSID)", "Wi-Fi Name (SSID)", "Wi-Fi名(SSID)", "Wi-Fi 이름(SSID)")
add("Wi-Fi 密码", "Wi-Fi Password", "Wi-Fiパスワード", "Wi-Fi 비밀번호")
add("等待相机创建 Wi-Fi Direct 组",
    "Waiting for the camera to create the Wi-Fi Direct group",
    "カメラがWi-Fi Directグループを作成するのを待機中",
    "카메라가 Wi-Fi Direct 그룹을 만들기를 기다리는 중")
add("等待相机生成一次性密码",
    "Waiting for the camera to generate a one-time password",
    "カメラがワンタイムパスワードを生成するのを待機中",
    "카메라가 일회용 비밀번호를 만들기를 기다리는 중")
add("● 开启服务 · 手机即可扫描相机 Wi-Fi",
    "● Start service · Your phone can then find the camera Wi-Fi",
    "● サービス開始 · スマホからカメラWi-Fiを検索できます",
    "● 서비스 켜기 · 휴대폰에서 카메라 Wi-Fi 검색 가능")
add("● 关闭服务并恢复相机 Wi-Fi",
    "● Stop service and restore camera Wi-Fi",
    "● サービス停止とカメラWi-Fiの復元", "● 서비스 끄고 카메라 Wi-Fi 복원")
add("等待 SD 卡", "Waiting for SD card", "SDカードを待機中", "SD 카드 대기")
add("开启后显示", "Shown after enabling", "オンにすると表示", "켜면 표시")
add("未开启 · 不会启动 Wi-Fi", "Off · Wi-Fi will not start",
    "オフ · Wi-Fiは起動しません", "꺼짐 · Wi-Fi를 시작하지 않습니다")
add("自动绑定 · 常用地址 192.168.122.1:",
    "Automatic bind · Common address 192.168.122.1:",
    "自動バインド · よく使うアドレス 192.168.122.1:",
    "자동 바인딩 · 일반 주소 192.168.122.1:")
add("等待服务启动", "Waiting for service", "サービス起動待ち", "서비스 시작 대기")
add("服务设置保持不变", "Service settings are kept", "サービス設定は保持されます",
    "서비스 설정은 유지됩니다")
add("返回手机传输", "Back to Phone Transfer", "スマホ転送に戻る", "휴대폰 전송으로 돌아가기")
add("已开启 · 等待服务启动", "On · Waiting for the service",
    "オン · サービス起動待ち", "켜짐 · 서비스 시작 대기")
add("启动失败 · ", "Start failed · ", "起動失敗 · ", "시작 실패 · ")
add("读取失败 · ", "Read failed · ", "読み込み失敗 · ", "읽기 실패 · ")
add("配对信息不可用", "Pairing information unavailable",
    "ペアリング情報を利用できません", "페어링 정보를 사용할 수 없습니다")
add("配对信息未能读取 · 请检查 SD 卡写入",
    "Could not read pairing information · Check SD card writes",
    "ペアリング情報を読み込めません · SDカードへの書き込みを確認してください",
    "페어링 정보를 읽을 수 없습니다 · SD 카드 쓰기를 확인하세요")
add("等待手机连接 · 首次需在手机输入配对码",
    "Waiting for phone · Enter the pairing code on first use",
    "スマホの接続を待機中 · 初回はスマホにペアリングコードを入力",
    "휴대폰 연결 대기 · 처음에는 휴대폰에 페어링 코드를 입력하세요")
add("手机已连接 · 新照片会自动出现在手机相册",
    "Phone connected · New photos appear in the phone gallery",
    "スマホ接続済み · 新しい写真はスマホのギャラリーに自動で表示されます",
    "휴대폰 연결됨 · 새 사진이 휴대폰 갤러리에 자동으로 표시됩니다")
add("真 LUT 只传成片 · 配方只传相机直出 · 原片永不发送",
    "True LUT sends only results · Recipes send camera JPEGs · Originals are never sent",
    "トゥルーLUTは完成写真のみ · レシピはカメラJPEGのみ · オリジナルは送信しません",
    "트루 LUT는 결과 사진만 · 레시피는 카메라 JPEG만 · 원본은 절대 전송하지 않습니다")
add("已刷新传输状态", "Transfer status refreshed", "転送状態を更新しました",
    "전송 상태를 새로 고쳤습니다")
add("手机已连接", "Phone Connected", "スマホ接続済み", "휴대폰 연결됨")
add("等待手机连接", "Waiting for phone", "スマホの接続を待機中", "휴대폰 연결 대기")
add("A7R2胶片相机 · 手机传输", "A7R2 Film Camera · Phone Transfer",
    "A7R2フィルムカメラ · スマホ転送", "A7R2 필름 카메라 · 휴대폰 전송")
add("配套 App 内一键加入相机 Wi-Fi · 首次输入配对码",
    "Join camera Wi-Fi in one tap in the companion app · Enter the pairing code once",
    "専用アプリでワンタップ接続 · 初回はペアリングコードを入力",
    "전용 앱에서 한 번 눌러 카메라 Wi-Fi 연결 · 처음 한 번 페어링 코드 입력")
add("手机传输页勾选已完成录像 · 手机端点按下载",
    "Check finished videos in Phone Transfer · Tap download on the phone",
    "スマホ転送画面で完了した動画をチェック · スマホ側でタップしてダウンロード",
    "휴대폰 전송 화면에서 완료된 동영상 선택 · 휴대폰에서 눌러 다운로드")
add("MENU → 更多 → 手机传输 · App 打开期间新照片后台发送",
    "MENU → More → Phone Transfer · New photos sent while this app is open",
    "MENU → その他 → スマホ転送 · アプリ起動中に新しい写真をバックグラウンド送信",
    "MENU → 더 보기 → 휴대폰 전송 · 앱이 열려 있는 동안 새 사진 백그라운드 전송")
add("A7R2 适配 · 胶片界面与冲洗流程 · 手机传片与人脸检测",
    "A7R2 port · Film UI and develop flow · Phone transfer and face detection",
    "A7R2対応 · フィルムUIと現像フロー · スマホ転送と顔検出",
    "A7R2 대응 · 필름 UI와 현상 흐름 · 휴대폰 전송 및 얼굴 검출")
add("移植增强  Yonitska（Bilibili）", "Enhanced Port  Yonitska (Bilibili)",
    "移植強化  Yonitska（Bilibili）", "이식 강화  Yonitska(Bilibili)")
add("Yonitska（Bilibili）移植增强 · v", "Yonitska (Bilibili) Enhanced Port · v",
    "Yonitska（Bilibili）移植強化 · v", "Yonitska(Bilibili) 이식 강화 · v")
add("全部功能、按键与保存方式 · 42 条",
    "All features, controls and saving · 42 items",
    "すべての機能・ボタン・保存方法 · 42項目",
    "모든 기능, 버튼 및 저장 방식 · 42개")
add("↑↓ 选择   ● 勾选   MENU 返回", "↑↓ Select   ● Check   MENU Back",
    "↑↓ 選択   ● チェック   MENU 戻る", "↑↓ 선택   ● 선택   MENU 뒤로")

# ---------------------------------------------------------------- face detection
add("人脸检测", "Face Detection", "顔検出", "얼굴 검출")
add("人脸优先对焦已开启 · 下次拍照生效",
    "Face-priority AF on · Applies to the next photo",
    "顔優先AFをオン · 次回の撮影から適用",
    "얼굴 우선 AF 켜짐 · 다음 사진부터 적용")
add("人脸检测已关闭 · 下次拍照恢复相机设置",
    "Face detection off · Camera setting restored on the next photo",
    "顔検出をオフ · 次回の撮影でカメラ設定に戻します",
    "얼굴 검출 꺼짐 · 다음 사진 촬영 시 카메라 설정 복원")
add("开启 · 相机原生人脸优先对焦",
    "On · Native face-priority AF",
    "オン · カメラ本来の顔優先AF", "켜짐 · 카메라 기본 얼굴 우선 AF")
add("关闭 · 使用相机原设置", "Off · Use the camera setting",
    "オフ · カメラの元の設定を使用", "끔 · 카메라 원래 설정 사용")
add("MENU → 更多 → 人脸检测 · 使用相机原生人脸优先对焦",
    "MENU → More → Face Detection · Uses native face-priority AF",
    "MENU → その他 → 顔検出 · カメラ本来の顔優先AFを使用",
    "MENU → 더 보기 → 얼굴 검출 · 카메라 기본 얼굴 우선 AF 사용")

# ---------------------------------------------------------------- transfer service
add("服务已就绪 · 等待手机连接", "Service ready · Waiting for the phone",
    "サービス準備完了 · スマホの接続を待機中", "서비스 준비 완료 · 휴대폰 연결 대기")
add("正在准备配对与 Wi-Fi Direct", "Preparing pairing and Wi-Fi Direct",
    "ペアリングとWi-Fi Directを準備中", "페어링 및 Wi-Fi Direct 준비 중")
add("正在启动 Wi-Fi Direct", "Starting Wi-Fi Direct", "Wi-Fi Directを起動中",
    "Wi-Fi Direct 시작 중")
add("正在创建 Wi-Fi Direct 群组", "Creating Wi-Fi Direct group",
    "Wi-Fi Directグループを作成中", "Wi-Fi Direct 그룹 생성 중")
add("群组已建立，正在启动传输服务", "Group ready; starting transfer service",
    "グループ確立 · 転送サービスを起動中", "그룹 준비 완료, 전송 서비스 시작 중")
add("群组已建立，等待手机连接", "Group ready; waiting for the phone",
    "グループ確立 · スマホの接続を待機中", "그룹 준비 완료, 휴대폰 연결 대기")
add("手机已连接 · 正在同步", "Phone connected · Syncing",
    "スマホ接続済み · 同期中", "휴대폰 연결됨 · 동기화 중")
add("手机已断开 · 群组仍在等待连接",
    "Phone disconnected · Group still waiting for a connection",
    "スマホが切断されました · グループは接続待機中",
    "휴대폰 연결 끊김 · 그룹이 계속 연결을 기다리는 중")
add("正在启动相机 Wi-Fi", "Starting camera Wi-Fi", "カメラWi-Fiを起動中",
    "카메라 Wi-Fi 시작 중")
add("正在启动相机 Wi-Fi…", "Starting camera Wi-Fi…", "カメラWi-Fiを起動中…",
    "카메라 Wi-Fi 시작 중…")
add("正在关闭手机传输", "Stopping phone transfer", "スマホ転送を停止中",
    "휴대폰 전송 중지 중")
add("正在关闭传输", "Stopping transfer", "転送を停止中", "전송 중지 중")
add("正在关闭传输…", "Stopping transfer…", "転送を停止中…", "전송 중지 중…")
add("手机已连接 · 新照片会自动传输", "Phone connected · New photos transfer automatically",
    "スマホ接続済み · 新しい写真は自動転送されます",
    "휴대폰 연결됨 · 새 사진이 자동 전송됩니다")
add("连接失败 · 打开应用查看", "Connection failed · Open the app for details",
    "接続に失敗 · アプリを開いて確認してください",
    "연결 실패 · 앱을 열어 확인하세요")
add("相机与手机之间的照片和已勾选录像传输",
    "Photo and checked-video transfer between camera and phone",
    "カメラとスマホ間の写真と選択済み動画の転送",
    "카메라와 휴대폰 간 사진 및 선택된 동영상 전송")
add("传输任务无法启动", "Could not start the transfer task",
    "転送タスクを開始できません", "전송 작업을 시작할 수 없습니다")
add("传输服务启动失败，请重试", "Transfer service failed to start; try again",
    "転送サービスの起動に失敗しました。再試行してください",
    "전송 서비스 시작 실패, 다시 시도하세요")
add("传输服务无法启动", "Transfer service could not start",
    "転送サービスを開始できません", "전송 서비스를 시작할 수 없습니다")
add("无法保存配对信息，请检查 SD 卡",
    "Could not save pairing information; check the SD card",
    "ペアリング情報を保存できません。SDカードを確認してください",
    "페어링 정보를 저장할 수 없습니다. SD 카드를 확인하세요")
add("无法保存配对信息", "Could not save pairing information",
    "ペアリング情報を保存できません", "페어링 정보를 저장할 수 없습니다")
add("无法更新配对信息", "Could not update pairing information",
    "ペアリング情報を更新できません", "페어링 정보를 업데이트할 수 없습니다")
add("正在关闭手机传输并结束会话",
    "Stopping phone transfer and ending the session",
    "スマホ転送を停止してセッションを終了中",
    "휴대폰 전송을 중지하고 세션을 종료 중")
add("无法读取 Wi-Fi 服务", "Could not access the Wi-Fi service",
    "Wi-Fiサービスを利用できません", "Wi-Fi 서비스를 사용할 수 없습니다")
add("无法开启 Wi-Fi Direct", "Could not enable Wi-Fi Direct",
    "Wi-Fi Directを有効にできません", "Wi-Fi Direct를 켤 수 없습니다")
add("无法监听 Wi-Fi Direct 状态", "Could not monitor Wi-Fi Direct status",
    "Wi-Fi Directの状態を監視できません", "Wi-Fi Direct 상태를 모니터링할 수 없습니다")
add("无法读取 Wi-Fi Direct 网络编号", "Could not read the Wi-Fi Direct network ID",
    "Wi-Fi DirectのネットワークIDを読み込めません",
    "Wi-Fi Direct 네트워크 ID를 읽을 수 없습니다")
add("无法读取 Wi-Fi Direct 网络参数", "Could not read Wi-Fi Direct network details",
    "Wi-Fi Directのネットワーク情報を読み込めません",
    "Wi-Fi Direct 네트워크 정보를 읽을 수 없습니다")
add("相机没有可用的 Wi-Fi Direct 配置",
    "The camera has no Wi-Fi Direct configuration",
    "カメラに利用可能なWi-Fi Direct設定がありません",
    "카메라에 사용 가능한 Wi-Fi Direct 구성이 없습니다")
add("这台相机没有提供 Wi-Fi Direct 服务",
    "This camera does not provide Wi-Fi Direct",
    "このカメラはWi-Fi Directサービスを提供していません",
    "이 카메라는 Wi-Fi Direct 서비스를 제공하지 않습니다")
add("启动 Wi-Fi Direct 群组失败", "Could not start the Wi-Fi Direct group",
    "Wi-Fi Directグループを起動できません", "Wi-Fi Direct 그룹 시작 실패")
add("Wi-Fi Direct 启动超时，请重试", "Wi-Fi Direct start timed out; try again",
    "Wi-Fi Directの起動がタイムアウトしました。再試行してください",
    "Wi-Fi Direct 시작 시간 초과, 다시 시도하세요")
add("Wi-Fi Direct 群组创建超时，请重试",
    "Wi-Fi Direct group creation timed out; try again",
    "Wi-Fi Directグループの作成がタイムアウトしました。再試行してください",
    "Wi-Fi Direct 그룹 생성 시간 초과, 다시 시도하세요")
add("Wi-Fi Direct 群组创建失败（", "Wi-Fi Direct group creation failed (",
    "Wi-Fi Directグループの作成に失敗（", "Wi-Fi Direct 그룹 생성 실패 (")
add("Wi-Fi Direct 未返回网络名称", "Wi-Fi Direct did not return a network name",
    "Wi-Fi Directがネットワーク名を返しません",
    "Wi-Fi Direct가 네트워크 이름을 반환하지 않았습니다")
add("Wi-Fi Direct 群组已建立，但无法读取网络参数",
    "Wi-Fi Direct group started, but network details are unavailable",
    "Wi-Fi Directグループは確立しましたが、ネットワーク情報を読み込めません",
    "Wi-Fi Direct 그룹은 만들어졌지만 네트워크 정보를 읽을 수 없습니다")
