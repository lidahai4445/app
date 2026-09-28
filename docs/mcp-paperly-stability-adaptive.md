# Paperly 2.0.1 稳定性与自适应修复记录

## 范围与根因

用户反馈最新版打开阅读器有空指针、页面在手机 / 平板适配不佳。ADB crash 缓冲区中 2026-09-28 11:55 连续 3 次出现 `MutableState.setValue` 空指针，调用链为 `ReaderViewModel.<init>` → `refreshMarks` → `setStrokePages`。源码在 `init` 中刷新笔迹页，但 `strokePages by mutableStateOf(...)` 当时声明在 `init` 之后；Kotlin 按源码顺序初始化字段，因此委托状态尚未建立。该崩溃不是 PDF 内容或用户数据损坏。

## 已改动

- `app/src/main/java/com/paperly/mobile/ui/ReaderViewModel.kt`：先初始化 `strokePages` 再调用 `refreshMarks`；撤销 / 重做后刷新笔迹页标记；翻译前拒绝失效的 OCR 下标区间。
- `app/src/main/java/com/paperly/mobile/ui/ReaderScreen.kt`：窄屏双行顶栏、工具胶囊可横向滚动、带侧栏的阅读布局从 900dp 起启用；计时结束卡限制宽度、操作按钮竖排。
- `app/src/main/java/com/paperly/mobile/ui/PageView.kt`：极窄分屏下圈题按钮位置不再产生倒置的 `coerceIn` 区间；译文卡限制宽高并允许长文滚动；移除无用的强制非空断言。
- `app/build.gradle.kts`：升级至 2.0.1 / versionCode 5，支持区分旧的 2.0.0 包并原位更新。

## 验证与限制

- Windows 本机 JDK17 下 Gradle `:app:assembleDebug` 最终成功，无 Kotlin 警告。`:app:testDebugUnitTest` 返回 `NO-SOURCE`，不能据此声称有单元测试通过。
- `aapt dump badging`：`com.paperly.mobile`、2.0.1 / vc5、minSdk 26 / targetSdk 36。`app/build/outputs/apk/debug/app-debug.apk` 与仓库根目录 `Paperly-debug.apk` 经 `cmp` 验证一致，均为 57,666,501 字节，SHA-256 `750eec38d7472cc2457415e66c9fe60eb2f415499ac8d93325a2c18b2e03b7c3`。
- 手机 `d8a4be7c` 当时锁屏，前台是其他应用；本次没有安装 / 启动 App、读取锁屏截图或更改手机数据。Android CLI `emulator list --long` 为空，SDK 中也没有已安装系统镜像，因此实际 UI 和崩溃复现复测仍待设备解锁后完成。

## 建议验收

1. 在已解锁设备原位安装 `Paperly-debug.apk`，保留已有 App 数据；打开已有 PDF，确认不再闪退，旧笔迹仍在。
2. 手机竖屏和横屏分别检查顶栏、模式切换、可横滑工具条、圈题确认按钮、辅助面板、结束计时卡；有条件时再检查分屏和 Android 平板。
3. 测试撤销 / 重做后缩略图笔迹标记、OCR 选词与长译文滚动。外部 VIEW / SEND 导入、AI 联网及免费翻译目前未做设备端验收。
