# 分析证据

## 当前范围

- 应用：豆包输入法
- 包名：`com.bytedance.android.doubaoime`
- 版本名称：`1.4.0`
- 版本代号：`100400012`
- 输入场景：英文模式；期望每个字母直接 commit，不进入组合态、不弹预测候选。
- APK：`apk/base.apk`
- JADX：`1.5.6`（用户已确认）

## 证据

- `apk/base.apk` SHA-256：`ead1c749a385ee1a206a25a98acc3399eb5bba98044e4198e74af932587e2e37`。
- 已连接设备上的安装包路径为 `/data/app/.../com.bytedance.android.doubaoime.../base.apk`；`pm` 读到 `versionCode=100400012`、`versionName=1.4.0`、`primaryCpuAbi=arm64-v8a`，与目标一致。
- APK 包含 `classes.dex`、`classes2.dex`、`classes3.dex`，以及 `assets/skin/default/layout/input_kbd_english26.xml` 等英文键盘资源。
- APK 包含 `lib/arm64-v8a/libkeyboard.so`、`libime_net_sdk.so`、`liboime-config.so` 等输入法相关 native 库；预测逻辑可能跨 Java/native 边界。
- JADX 1.5.6 已生成部分源码，但退出码为 3，记录 134 个局部方法反编译错误。目标相关错误清单中出现 `com.bytedance.android.input.x.C.f.invokeSuspend` 与 `com.bytedance.common_biz.tool_bar.view.views.CandidateIdleView.z`，后续需用 DEX/smali 交叉检查。
- `ImeService` 在 manifest 中注册为真正的 IME service；`r()` 返回 `com.bytedance.android.input.editor.a`，该 wrapper 将 `commitText`、`setComposingText`、`finishComposingText` 转发到当前应用的 `InputConnection`。
- `KeyboardView.onTouchEvent` 将键盘触摸坐标直接传给 native `nativeTouch(long,int,int,int,long)`，Java 层没有单独的字母按键回调。
- `KeyboardJni.UpdatePreedit(String)` 当前实现调用 `setComposingText(str, 1)`；`KeyboardJni.DoCommit(String,int,String,String,String)` 当前实现最终调用 wrapper 的 `commitText`。这两个 Java static 方法是 native 到 Java 的可 hook 回调边界。
- `lib/arm64-v8a/libkeyboard.so` 为 stripped ELF，但保留大量动态 C++ 符号。已发现：`keyboard::KeyboardCallbackImpl::UpdatePreedit(std::string const&)`、`keyboard::KeyboardCallbackImpl::DoCommit(std::string const&, int, ...)`、`keyboard::InputModel::OnUpdateEnglish26PreCommit()`、`CommitCurrentEnglishPreedit()`、`CommitDeferredHalfCandidateWithEnglishPreeditIfNeeded(char const*)`，以及 `ui::ButtonEnglishChar::OnButtonEnglishCharClicked()`。
- native 字符串包含 `English26`、`UpdatePreedit OnUpdateEnglish26PreCommit`、`English Clear typing input and preedit string`、`CandidateLink` 等，说明英文预测与组合/候选提交确实在 `libkeyboard.so` 内，而非单纯 UI 候选栏逻辑。

## 候选 Hook 点

- `KeyboardJni.InputBoardType` 的顺序为 `kPinyin26=0`、`kPinyin9=1`、`kEnglish26=2`；native `Jni_IsEnglishKeyboard` 仍保留，因此 Java hook 可以按当前 native 键盘状态限定为英文模式。
- `KeyboardJni.notifyCandidateBarSnapshot(...)` 是 native 候选 snapshot 到候选栏 host 的 Java static 回调；其中 `mode` 直接参与候选栏展示模式选择。native 符号为 `keyboard::KeyboardCallbackImpl::NotifyCandidateBarSnapshot`。
- `KeyboardJni.notifyMoreCandidateSnapshot(...)` 是更多候选页的对应 Java static 回调；native 符号为 `NotifyMoreCandidateSnapshot`。
- 英文空格由 `KeyboardJni.commitForSpace()` 进入 native；静态反汇编显示 typing 分支先尝试 `InputModel::CommitCand`，无候选时才走 `InputModel::CommitString`，所以在 Java native 方法入口返回成功并直接 commit 空格可以阻断候选词上屏。
- `KeyboardJni.commitForEnter(boolean, boolean, boolean, boolean)` 也进入 native。当前方案保留该 native 方法的 action/换行处理，只在 `DoCommit` 的 `source=keyboard_callback` 且处于英文模式时拦截 ASCII 候选词，避免回车触发预测词上屏。
- `KeyboardJni.UpdatePreedit(String)` 原实现只负责把完整字符串传给 `setComposingText`；hook 将 ASCII 字母预编辑与上一次回调做前缀差量，直接通过 `ImeService.r()` 返回的 `InputConnection` wrapper commit/delete，并跳过原实现。

## 未验证项

- LSPosed 是否实际注入目标 IME 进程、实机按键行为和候选栏消失效果。
- `DoCommit` 在不同编辑器 action、候选/回车路径下的完整参数分布；当前仅按来源和 ASCII 文本做保守拦截。

## 已实现模块

- `EnglishInputHook` 在目标包主进程安装；英文 `UpdatePreedit` 通过 `PreeditDelta` 转为 `deleteSurroundingText` + `commitText`，不调用原 `setComposingText`。
- 空格和双击空格保留 native 清理路径，在 `DoCommit` 回调中把 ASCII 候选替换为空格/英文句号空格；无回调时在 native 方法返回后补交。
- `notifyCandidateBarSnapshot`、`notifyMoreCandidateSnapshot`、`notifyCandidateBarPinyin` 在英文模式短路。
- Xposed classic API 仅以 compile-only stub JAR 参与编译，最终 APK dex 不包含 `de.robv.android.xposed` stub 类。
- 为让 LSPosed Manager 默认推荐豆包输入法，Manifest 增加 `xposedscope=com.bytedance.android.doubaoime`；这是 classic Xposed 模块的作用域声明格式。
