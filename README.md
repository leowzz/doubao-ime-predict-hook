# doubao-ime-predict-hook

干掉豆包输入法英文「预测单词+点候选上屏」，改成「每按一个键，字母直接上屏，不弹候选」。

## 目标（WHAT）

| 项目 | 内容 |
|------|------|
| 被改应用 | 豆包输入法（字节跳动） |
| 引擎模式 | 英文模式 |
| 当前行为（不喜欢） | 手打字母时，候选栏弹预测单词，必须点候选/空格才上屏 |
| 期望行为 | 每按一个键，当前字母直接 commit 上屏，不弹候选、不进入组合状态 |

## 已确定决策（DECIDED）

- **Hook 框架：LSPosed（Xposed 模块）**
  - 理由：长期稳定、开机常驻、无需每次 adb。比 Frida 更贴合「一劳永逸」需求。
  - 用户的手机**已安装 LSPosed 框架**，电脑能连 adb。
  - （顺带：用户手上无 Frida，但本方案不强依赖它。）
- **手机 root 情况**：已 root（能装 LSPosed 即说明已 root）。

## 尚未确定（TODO — 拿手机后再做）

- [ ] 确认豆包输入法**包名**（`adb shell pm list packages | grep -i doubao`，或 `grep -i baidu`）
- [ ] 拿到 APK：`adb shell pm path <包名>` → `adb pull` 拉回电脑
- [ ] **静态逆向** APK（反编译 `classes.dex`），定位英文预测的代码入口
  - 关键词：`composing` / `commitText` / `InputConnection` / `InputMethodService` / `candidate` / `predict`
  - 工具建议：`jadx`（首选，可直接搜字符串+定位调用链）、`apktool`
- [ ] 判定 hook 切入点属于哪一类（二选一，需分析确认）：
  1. **按键回调**：按下字母键时，不让其进入「组合状态」，直接 `InputConnection.commitText` 把单个字母上屏；
  2. **隐藏内部 flag/设置开关**：该行为可能受一个 flag 控制，强制置 false 更干净。

## 下一步（拿到手机后从这里继续）

按上面 TODO 列表从包名确认开始走。虚线展望：确认切入点后，用 LSPosed 模块骨架（`LSPosed Module` 模板工程）实现并签名，push 到手机通过 LSPosed 激活。

## 进程历史

- 2026-08-17：立项。与用户对齐目标、hook 方式（选定 LSPosed）。建 repo 固化上下文。等待用户拿回手机后开始逆向。