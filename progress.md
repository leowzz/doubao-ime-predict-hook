# 执行日志

## 2026-08-18

- 恢复项目上下文：仓库仅有 `README.md` 和 `apk/base.apk`，目标是 LSPosed 静态逆向定位。
- 确认 APK 文件约 163 MB，系统可用 `/opt/homebrew/bin/jadx`。
- 创建本次分析的计划、证据和进度文件。
- 通过 `adb` 核对已安装目标：包名、版本名、版本号和 `arm64-v8a` 均匹配用户提供信息。
- JADX 1.5.6 处理三个 DEX 后生成产物，但因 134 个局部方法错误以退出码 3 结束；已记录目标相关错误，后续转用 DEX/smali 交叉检查。
- 已定位 Java/native 边界：`KeyboardView.nativeTouch` -> `libkeyboard.so`；native 回调 `KeyboardJni.UpdatePreedit` 使用 `setComposingText`，`KeyboardJni.DoCommit` 使用 `commitText`。
- native 符号保留英文路径和候选提交相关 C++ 方法，已抽取 `libkeyboard.so` 到 `/tmp/doubao-ime-predict-hook-native` 供后续静态检查。
- 候选入口已确认：`notifyCandidateBarSnapshot` / `notifyMoreCandidateSnapshot` 是 Java static 候选栏回调；`InputBoardType.kEnglish26` 值为 `2`，并由 native `IsEnglishKeyboard()` 做模式判定。
- native `commitForSpace()` 反汇编确认 typing 分支会先尝试 `CommitCand`，验证了空格入口短路的必要性；回车保留 native action，并在 `DoCommit` 入口过滤英文 `keyboard_callback` ASCII 候选词。
- 已创建最小 LSPosed classic hook 模块和 `PreeditDelta` 单元测试；首次构建被本机 Gradle 8.1.1 + Java 21 环境阻断，待用 Gradle 8.5 重跑。
- Gradle 8.5 已能启动，但首次重跑因未配置 Android SDK 路径停止；本机 SDK 已确认位于 `/Users/leo/Library/Android/sdk`。
- APK 初检发现 compile-only project 误把 `de.robv.android.xposed` stub 打入 dex；已改为 compile-only JAR 依赖，待重新构建确认运行时实现不会被覆盖。
- 最终构建通过：Gradle 8.5 + Java 21，4 个 `PreeditDelta` 测试通过，APK package=`com.leo.doubaoimehook`，`xposed_init` 存在，dex 无 `de.robv.android.xposed`，zipalign 与 v2 签名验证通过。
- 当前唯一未完成项为手机侧 LSPosed 安装/作用域/重启和真实输入验收；未执行安装，未盲操作设备输入。
- 已增加 classic LSPosed 的 `xposedscope` 元数据，目标包为 `com.bytedance.android.doubaoime`；待重建并覆盖安装后由 Manager 读取推荐作用域。
- 新 APK 已通过 `adb install -r` 返回 `Success` 覆盖安装；本地 APK Manifest 回读确认作用域值正确，但安装后设备立即断开，手机端 `pm` 回读和 LSPosed Manager UI 验证为 Not Run。
