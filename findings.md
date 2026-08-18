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

## 终端首次回车问题

- 实机前台为 Termux `com.termux/.app.TermuxActivity`，当前 `EditorInfo` 为 `inputType=0x0`、`imeOptions=0x02000000`。
- Hook 将 `UpdatePreedit` 差量直接提交给 `InputConnection`，但仍让 `commitForEnter(boolean, boolean, boolean, boolean)` 完整进入 native。
- Termux 的终端内容不暴露给 UI Automator，复现信号改为执行一条创建 `/sdcard` 唯一目录的命令：最后一个字母由豆包软键盘输入，只点一次软键盘回车后检查目录是否存在。
- 初始假设（后续已证伪）：native 仍保存被 hook 直接提交的英文 preedit；第一次回车只提交/清理 native preedit，而对应 `DoCommit` 又被过滤，第二次回车才发送终端回车事件。
- 红色基线：软键盘输入命令最后一个 `e` 后，第一次点换行，`/sdcard/doubao_enter_repro_e` 不存在；第二次点换行后目录存在。
- 回调序列显示该场景没有 `UpdatePreedit` / `commitForEnter`。第一次换行只有通用的 `DoFunctionKey(44)`；第二次才出现真正执行编辑器动作的 `DoFunctionKey(2)`。`44` 的 Java 实现仅清理语音错误展示状态。
- 临时放行 `lastPreedit` 为空时的单字符 `DoCommit` 后，首次换行仍失败，第二次执行且目录名只有一个 `e`。因此宽泛 `DoCommit` 过滤不是本问题根因。
- 对候选回调增加日志后，最小场景中没有任何 candidate callback，排除候选栏短路导致首回车延迟。
- `KeyboardJni.finishPreeditNative(boolean, boolean)` 是可用的 native 状态清理入口；现有 Java 调用在关闭/切换场景使用 `false` 丢弃 preedit，适合在字符已由 hook 上屏后清理残留 native 状态。
- 实测 `finishPreeditNative(false, false)` 虽成功执行，但首次回车仍被延迟，因此 native preedit 清理不是根治点。
- 触摸级日志确认：字母触摸仅建立待提交状态；第一次回车触摸先产生单字符 `DoCommit`，但没有 `DoFunctionKey(2)`；第二次同坐标触摸才产生 `DoFunctionKey(2)`。两次回车局部坐标均为 `(1135,748)`，视图为 `1220x893`。
- 第一版补偿错误地把英文布局资源名 `key_26` 当成运行时 board 标识，因此 `isFullKeyboard()` 始终返回 false，早期验收结果不可复现。最终诊断直接记录 `KeyboardJni.getBoardEventName()`，实机值为 `key_eng`。
- 正式修复在 `KeyboardView.onTouchEvent` 返回后判断：仅 `TYPE_NULL`、`key_eng`、按下/抬起均在右下回车区域、且本次 native 未派发 `DoFunctionKey(2)` 时补发一次。这样 native 先提交最后一个字符，再执行回车，并避免正常回车重复。
- 最终签名版覆盖安装后，命令末尾软键盘输入 `e`，第一次回车即创建 `/sdcard/dbfixed_e`。`cat > /sdcard/dbguard` 一次回车后文件大小为 `0`，证明未发送第二个换行；29 个单元测试全部通过。

## 已实现模块

- `EnglishInputHook` 在目标包主进程安装；英文 `UpdatePreedit` 通过 `PreeditDelta` 转为 `deleteSurroundingText` + `commitText`，不调用原 `setComposingText`。
- 空格和双击空格保留 native 清理路径，在 `DoCommit` 回调中把 ASCII 候选替换为空格/英文句号空格；无回调时在 native 方法返回后补交。
- `notifyCandidateBarSnapshot`、`notifyMoreCandidateSnapshot`、`notifyCandidateBarPinyin` 在英文模式短路。
- Xposed classic API 仅以 compile-only stub JAR 参与编译，最终 APK dex 不包含 `de.robv.android.xposed` stub 类。
- 为让 LSPosed Manager 默认推荐豆包输入法，Manifest 增加 `xposedscope=com.bytedance.android.doubaoime`；这是 classic Xposed 模块的作用域声明格式。
