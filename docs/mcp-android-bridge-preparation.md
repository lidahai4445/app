# Android MCP 接手准备（2026-09-28）

## 连接与环境

- ShunCode Bridge `0.7.6` 已连接；工作区为 `C:/Users/21671/Desktop/workbody`，Android 工程在 `app/`。
- MCP 提供 15 个工具：目录/文件搜索、文本/图片读取、补丁修改、终端命令及输出/输入/取消、诊断、LSP、任务计划和进度报告。
- Android CLI `1.0.16406183` 位于 `C:/Users/21671/.android/bin/android-cli.exe`，**未加入 PATH**；已用绝对路径验证 `--help`、`--version`。执行具体子命令前须先查 `--help`。
- SDK 在 `C:/Users/21671/AppData/Local/Android/Sdk`，JDK 17 在 `C:/Users/21671/AppData/Local/Programs/PaperlyToolchain/jdk17.0.20_12`；ADB 已发现设备 `d8a4be7c`。

## 项目与操作边界

- `app/开发操作日志.md` 记载项目目标：面向考研真题 PDF 的做题/辅助阅读，笔迹本地保存，OCR 选词翻译及 AI 解题，兼顾 Android 平板。较新的界面重做记录见 `做题软件/交互日志.md` 第 8 轮。
- 当前 Android 源码在 `app/app/src/main/java/com/paperly/mobile/`；最新日志称 2.0.0 的界面重做已编译，等待用户真机验证。`app/README.md` 的 1.2.0 描述已过时，不能作为最新版状态依据。
- 既有用户要求真机调试由用户执行；后续若需安装/设备操作，先以当次用户指令确认边界。前次提出的空指针与页面适配问题尚未诊断或修复。
- 使用工具时先定位再读取，修改优先 `apply_patch` 并带文件版本；修改后查诊断、构建与相关测试。多步任务用 Bridge `set_todos`，结束前提交终态。Windows Git Bash 构建前清除遗留的 `MSYS_NO_PATHCONV`。
- 本次仅完成连接、日志和环境的只读盘点，没有修改 Android 源码、构建或安装 APK。
