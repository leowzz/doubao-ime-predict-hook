# Doubao IME English Direct Commit Hook

[![Android Release](https://github.com/leowzz/doubao-ime-predict-hook/actions/workflows/release.yml/badge.svg)](https://github.com/leowzz/doubao-ime-predict-hook/actions/workflows/release.yml)
[![Latest Release](https://img.shields.io/github/v/release/leowzz/doubao-ime-predict-hook)](https://github.com/leowzz/doubao-ime-predict-hook/releases/latest)

一个面向豆包输入法的 LSPosed/Xposed 模块，用于关闭英文输入预测候选，并将英文 ASCII 字母直接提交到当前编辑器。

豆包输入法默认会把正在输入的英文放入组合态，再通过候选栏预测完整单词。本模块将英文输入改成更接近传统硬件键盘的行为：输入 `h` 就立即提交 `h`，不需要点击预测候选，也不会因为候选词而改变已经输入的文本。

> 本项目是针对特定输入法版本的实验性 hook，不是豆包输入法本体，也不能脱离 LSPosed 独立运行。

## 功能

- 仅在英文键盘模式下处理 ASCII 字母。
- 根据 preedit 差量直接调用当前编辑器的 `InputConnection` 提交或删除文本。
- 在英文模式下抑制候选栏 snapshot 回调，避免显示英文预测候选。
- 处理空格和双击空格路径，保留空格及英文句号加空格的输入行为。
- 中文、拼音、非 ASCII 字符以及未识别的输入路径继续交给豆包输入法原生逻辑。

## 兼容性

| 项目 | 当前基线 |
| --- | --- |
| 目标包名 | `com.bytedance.android.doubaoime` |
| 目标输入法版本 | 1.4.0 (`100400012`) |
| Hook 框架 | LSPosed，兼容 classic Xposed API |
| 模块最低 Android API | 26 |
| 处理范围 | 豆包输入法英文键盘模式 |

目标输入法的实现包含私有类、native 代码和可能变化的混淆名称。升级豆包输入法后，模块可能失效或需要重新定位 hook 点；上表版本是当前代码的验证基线，不代表对所有版本兼容。

## 安装

### 前置条件

- 已 root 的 Android 设备。
- 已安装并正常运行 LSPosed 或兼容 classic Xposed API 的框架。
- 设备上已安装豆包输入法。

### 步骤

1. 从 [Releases](https://github.com/leowzz/doubao-ime-predict-hook/releases) 下载 APK。
2. 使用系统安装器安装，或通过 adb 安装：

   ```bash
   adb install -r doubao-ime-predict-hook-<version>.apk
   ```

3. 打开 LSPosed Manager，启用本模块，并将作用域勾选为豆包输入法（`com.bytedance.android.doubaoime`）。
4. 重启豆包输入法进程；必要时重启设备。
5. 在豆包输入法中切换到英文模式，输入一段英文确认行为。

如果出现崩溃、无法输入或行为异常，先在 LSPosed Manager 中关闭本模块或移除豆包输入法作用域，再重新启动输入法。

## 从源码构建

### 环境

- JDK 17 或兼容版本。
- Android SDK Platform 36。
- Android SDK Build-Tools 36.0.0。
- 仓库自带 Gradle Wrapper，会使用 Gradle 8.5。

### 常用命令

```bash
# 运行 JVM 单元测试
make test

# 构建 Debug APK
make build
```

Debug APK 输出在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接使用 Gradle Wrapper：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`xposed-api-stub` 只用于编译期提供 Xposed API 类型，最终 APK 不包含这些 stub 的运行时代码；设备上的 Xposed API 由 LSPosed 提供。

## 发布

版本号维护在 [`gradle.properties`](gradle.properties) 中。发布脚本要求工作区干净，会更新版本号、创建提交并创建 `vX.Y.Z` annotated tag：

```bash
# 检查当前仓库版本
make verify-release TAG=v1.0.1

# 自动递增 patch 版本
make release

# 或指定版本
make release V=v1.1.0
```

将生成的 tag 推送到 GitHub 后，Actions 会运行单元测试、构建签名 APK，并发布 APK 和 SHA-256 校验文件：

```bash
git push origin <branch> --follow-tags
```

签名发布需要在仓库 Secrets 中配置 `DOUBAO_IME_KEYSTORE_BASE64`、`DOUBAO_IME_KEYSTORE_PASSWORD`、`DOUBAO_IME_KEY_ALIAS` 和 `DOUBAO_IME_KEY_PASSWORD`。不要将 keystore、密码或本地签名文件提交到仓库。

## 实现概览

模块入口位于 [`HookEntry`](app/src/main/java/com/leo/doubaoimehook/HookEntry.java)，只对目标包安装 hook。核心逻辑位于 [`EnglishInputHook`](app/src/main/java/com/leo/doubaoimehook/EnglishInputHook.java)：

1. hook `KeyboardJni.UpdatePreedit`，计算当前 preedit 与上一次回调的差量。
2. 通过 `InputConnection.deleteSurroundingText` 和 `InputConnection.commitText` 将差量直接写入编辑器。
3. hook `DoCommit` 及候选回调，过滤英文预测词提交和候选栏更新。
4. 在输入开始、结束等生命周期回调中清理内部状态。

差量计算和 ASCII 字母判断位于 [`PreeditDelta`](app/src/main/java/com/leo/doubaoimehook/PreeditDelta.java)，对应测试位于 `app/src/test`。

## 已知限制与安全提示

- CI 覆盖单元测试和 APK 构建；LSPosed 是否在你的设备上成功注入、输入法是否稳定，以及真实按键行为是否符合预期，需要在目标设备上自行验收。
- hook 依赖豆包输入法的私有实现，输入法升级后可能需要调整代码。
- 本模块运行在输入法进程中。输入法天然可以接触用户输入内容，请只安装来自可信来源的 APK，并在使用前审查代码和发布签名。
- 目前没有为所有编辑器、密码框、特殊输入动作和不同 Android ROM 提供完整的兼容性承诺。

## 参与贡献

欢迎提交 Issue 或 Pull Request。报告问题时请尽量附上：

- 豆包输入法版本、Android 版本和 ROM 信息；
- LSPosed 版本、作用域配置和复现步骤；
- 相关日志或最小复现结果。请先删除账号、密码、输入文本和其他敏感信息。

提交代码前运行 `make test` 和 `make build`，并避免提交 `apk/`、`decompile/`、keystore 或其他本地构建产物。

## License

本项目采用 [MIT License](LICENSE) 开源。该协议适用于本仓库的原创代码；第三方组件和依赖仍受其各自许可证约束。
