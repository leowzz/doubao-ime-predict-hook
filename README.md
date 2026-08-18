# doubao-ime-predict-hook

干掉豆包输入法英文「预测单词+点候选上屏」，改成「每按一个键，字母直接上屏，不弹候选」。

## 目标（WHAT）

| 项目 | 内容 |
|------|------|
| 被改应用 | 豆包输入法（字节跳动） |
| 包名 | `com.bytedance.android.doubaoime` |
| 版本 | `1.4.0` / `100400012` |
| 引擎模式 | 英文模式 |
| 当前行为（不喜欢） | 手打字母时，候选栏弹预测单词，必须点候选/空格才上屏 |
| 期望行为 | 每按一个键，当前字母直接 commit 上屏，不弹候选、不进入组合状态 |

## 已确定决策（DECIDED）

- **Hook 框架：LSPosed（Xposed 模块）**
  - 理由：长期稳定、开机常驻、无需每次 adb。比 Frida 更贴合「一劳永逸」需求。
  - 用户的手机**已安装 LSPosed 框架**，电脑能连 adb。
  - （顺带：用户手上无 Frida，但本方案不强依赖它。）
- **手机 root 情况**：已 root（能装 LSPosed 即说明已 root）。

## 当前分析结果

- 已从 `apk/base.apk` 确认版本、`ImeService`、`KeyboardJni.UpdatePreedit`、`KeyboardJni.DoCommit` 和 `libkeyboard.so` 的 native English26 路径。
- 模块在英文模式下将 `UpdatePreedit` 的差量直接提交到当前编辑器，短路英文候选 snapshot，并接管 `commitForSpace`，避免空格优先提交预测候选。
- `DoCommit` 只过滤 `source=keyboard_callback` 的 ASCII 英文候选词；其他来源和非英文模式继续走原逻辑。

## 构建

```bash
make test
make build
```

输出 APK：`app/build/outputs/apk/debug/app-debug.apk`。安装后需在 LSPosed 中勾选豆包输入法作用域并重启豆包输入法进程；本机尚未完成实机注入验收。

## 发布

版本号维护在 `gradle.properties`。发布前确保工作区干净，然后执行：

```bash
make release
```

默认递增 patch 版本，也可以指定版本：

```bash
make release V=v1.0.0
```

推送生成的 `vX.Y.Z` tag 后，GitHub Actions 会运行单元测试、构建签名 APK，并将 APK 和 SHA-256 文件发布到 GitHub Release。

## 进程历史

- 2026-08-17：立项。与用户对齐目标、hook 方式（选定 LSPosed）。建 repo 固化上下文。等待用户拿回手机后开始逆向。
