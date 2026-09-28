# 纸间 Paperly · Android 2.2.2

面向考研真题 PDF 的原生 Android 做题 / 辅助阅读 App。技术栈：Kotlin、Jetpack Compose、Material 3、SQLite、PdfRenderer、ML Kit。包名 `com.paperly.mobile`；版本 `2.2.2`（versionCode 6），minSdk 26、targetSdk 36。接手前先读本目录 `最新日志.md`。

## 当前功能

- 通过文件选择器导入 PDF：复制至应用私有目录，不修改原始文件；资料库支持分类、收藏、重命名、最近打开与继续阅读。
- 做题模式：逐页显示 PDF，可用笔 / 荧光笔、橡皮擦、撤销重做；笔迹按页保存，双指缩放与平移。
- 辅助模式：ML Kit 离线 OCR（英文、可选中文）；点词 / 划句翻译、圈题确认后请求 AI 解题；结果与 OCR 本地缓存。翻译支持用户自行配置兼容接口，也提供联网的免费翻译回退。
- 计时运行时隐藏用时，结束后显示统计。手机使用紧凑双行顶栏和可横向滚动的工具胶囊；足够宽的平板阅读区显示侧栏。设置中的 API Key 由 Android Keystore 加密保存。
- 2.2.2 平板优先界面：资料库继续阅读 / 本周专注 / 真实试卷网格与左侧导航；新增学习回顾（真实计时、查词与最近练习）；阅读器提供复盘入口，设置页在平板上双栏显示。手机维持紧凑布局。
- 2.2.2 状态栏修订：资料库根布局按系统安全区域内缩，避免手机版首页“设置”按钮被状态栏覆盖。编译已核验；实际设备显示待用户复测。

注意：笔迹和译文是 App 的本地叠加层，不写入原 PDF；暂未实现导出带批注的 PDF。外部应用“用纸间打开”PDF 的完整流程仍待设备验证。AI 服务只有在选择翻译或确认圈题时才会收到所选文本 / 区域图像；免费翻译需要联网。

项目沿用已有数据库并通过 v1→v2 迁移保留旧资料。不要通过卸载 / 清除数据来更新 App；使用同签名的 APK 原位升级。

## 构建与试用

在 Windows CMD 中进入本目录后执行：

```bat
set "JAVA_HOME=%LOCALAPPDATA%\Programs\PaperlyToolchain\jdk17.0.20_12"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat :app:assembleDebug
```

Gradle 输出 APK：`app\build\outputs\apk\debug\app-debug.apk`；**最新 2.2.2 交付副本**为本目录 `Paperly-2.2.2-debug.apk`（Debug 签名，57,733,843 字节，SHA-256 `86f6eb90cc101d5e5c42646b91e75cf648ebd5eb77716714faae0dc4ba0b3449`）。上一个 2.2.2 APK 已备份到 `备份_2.2.2状态栏修复前_20260928/`；原 `Paperly-debug.apk` 仍为 2.0.1 / vc5，不要误装旧包。新旧包证书 SHA-256 一致，可原位更新。请勿卸载已有 App，以免删除本地数据。

## 构建检查

2026-09-28（2.2.2）：`:app:assembleDebug` 成功、无 Kotlin 编译警告；`:app:testDebugUnitTest` 成功但为 `NO-SOURCE`（当前没有单元测试）；VS Code error / warning 诊断为 0。`aapt` 核验包名、2.2.2 / vc6 / minSdk 26 / targetSdk 36，`apksigner` 验证新旧包证书一致，交付副本与构建产物 `cmp` 相同。**没有安装、启动或真机 UI 验证**；用户自行原位安装并检查资料库、阅读页、回顾、设置及数据保留，特别是外部 VIEW/SEND 导入仍未验证。详细修改和未验证范围见 `最新日志.md`、`开发操作日志.md`。

同日追加：根据用户反馈修复手机版首页设置按钮被状态栏挡住的问题，在资料库根容器加入 `WindowInsets.safeDrawing`。复编 `BUILD SUCCESSFUL in 15s`、无编译警告；新 APK 元信息/签名/哈希重新核验；**手机实际效果仍待用户确认**。
