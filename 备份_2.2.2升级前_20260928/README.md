# 纸间 Paperly · Android 2.0.1

面向考研真题 PDF 的原生 Android 做题 / 辅助阅读 App。技术栈：Kotlin、Jetpack Compose、Material 3、SQLite、PdfRenderer、ML Kit。包名 `com.paperly.mobile`；版本 `2.0.1`（versionCode 5），minSdk 26、targetSdk 36。

## 当前功能

- 通过文件选择器导入 PDF：复制至应用私有目录，不修改原始文件；资料库支持分类、收藏、重命名、最近打开与继续阅读。
- 做题模式：逐页显示 PDF，可用笔 / 荧光笔、橡皮擦、撤销重做；笔迹按页保存，双指缩放与平移。
- 辅助模式：ML Kit 离线 OCR（英文、可选中文）；点词 / 划句翻译、圈题确认后请求 AI 解题；结果与 OCR 本地缓存。翻译支持用户自行配置兼容接口，也提供联网的免费翻译回退。
- 计时运行时隐藏用时，结束后显示统计。手机使用紧凑双行顶栏和可横向滚动的工具胶囊；足够宽的平板阅读区显示侧栏。设置中的 API Key 由 Android Keystore 加密保存。

注意：笔迹和译文是 App 的本地叠加层，不写入原 PDF；暂未实现导出带批注的 PDF。外部应用“用纸间打开”PDF 的完整流程仍待设备验证。AI 服务只有在选择翻译或确认圈题时才会收到所选文本 / 区域图像；免费翻译需要联网。

项目沿用已有数据库并通过 v1→v2 迁移保留旧资料。不要通过卸载 / 清除数据来更新 App；使用同签名的 APK 原位升级。

## 构建与试用

在 Windows CMD 中进入本目录后执行：

```bat
set "JAVA_HOME=%LOCALAPPDATA%\Programs\PaperlyToolchain\jdk17.0.20_12"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat :app:assembleDebug
```

Gradle 输出 APK：`app\build\outputs\apk\debug\app-debug.apk`；当前试装副本为本目录的 `Paperly-debug.apk`（Debug 签名，57,666,501 字节，SHA-256 `750eec38d7472cc2457415e66c9fe60eb2f415499ac8d93325a2c18b2e03b7c3`）。旧的 2.0.0 副本保留为 `Paperly-debug-2.0.0-pre-fix.apk`。请勿卸载已有 App，以免删除本地数据。

## 构建检查

2026-09-28：`:app:assembleDebug` 成功且无编译警告；`:app:testDebugUnitTest` 为 `NO-SOURCE`（当前没有单元测试）。`aapt` 核验 2.0.1 / vc5 / minSdk 26 / targetSdk 36，构建 APK 与试装副本逐字节一致。连接的手机当时锁屏，且无 AVD / 系统镜像，所以**本次没有安装、启动或实际 UI 验证**；先在解锁设备上原位安装并检查阅读页、竖横屏及分屏。详细修复和未验证范围见 `docs/mcp-paperly-stability-adaptive.md`、`开发操作日志.md`。
