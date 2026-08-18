# 豆包输入法英文预测 Hook 分析计划

## 目标

针对 `com.bytedance.android.doubaoime` `1.4.0` (`100400012`)，从 `apk/base.apk` 定位英文输入“预测单词 + 点候选上屏”的实际代码路径，确认可验证的 LSPosed hook 切入点；只有静态证据足够时才实现模块代码。

## 阶段

- [completed] 1. 固定 APK 元数据、组件和 DEX/native 结构
- [completed] 2. 使用 JADX 1.5.6 反编译并建立可搜索源码
- [completed] 3. 搜索输入连接、组合文本、候选和预测调用链
- [completed] 4. 交叉检查混淆/动态加载/native 边界，确定 hook 方案
- [completed] 5. 实现最小 LSPosed hook，并做离线构建与静态验证
- [completed] 6. 完成手机侧安装、激活和英文混合预编辑实机验收
- [completed] 7. 复现并最小化终端首次回车被延迟的问题
- [completed] 8. 定位回车事件与预编辑会话之间的根因并增加回归测试
- [completed] 9. 实现修复，重建安装并完成终端实机验收

## 成功标准

- 记录 APK 的真实包名、版本、入口和分析范围。
- 至少给出一个由调用链支撑的候选方法，而不是只凭字符串命中。
- 明确哪些结论已验证、哪些必须在已 root 手机上实测。
- 不把 JADX 反编译输出或 APK 原文件误提交为源码改动。

## 错误记录

| 错误 | 尝试 | 处理 |
|---|---:|---|
| JADX 局部反编译错误 134 个，退出码 3 | 1 | 保留已生成源码；对命中目标使用 DEX/smali 交叉验证，不重复盲目重跑同一参数 |
| 对目录直接运行 grep 未递归 | 1 | 改用 `grep -R`，后续只对文件或明确递归目录搜索 |
| `su -c id` 不可用 | 1 | 仅记录设备已连接；不把 root/LSPosed 注入状态当作已验证 |
| `adb logcat -d -T 10m` 不接受该时间格式 | 1 | 改用 `adb logcat -d -t 5000` 做有限日志检查 |
| Gradle 8.1.1 在 Java 21 上解析脚本失败，提示 class file major version 65 | 1 | 使用支持 Java 21 的 Gradle 8.5 发行版重试 |
| Gradle 8.5 找不到 Android SDK | 1 | 使用 git 忽略的本机 `local.properties` 指向已存在 SDK |
| APK 级检查发现 `compileOnly project` 把 Xposed stub 类打入 dex | 1 | 改为生成 stub JAR 后用文件形式 `compileOnly`，并检查 APK dex 不含 stub 包 |
| 覆盖安装后设备从 ADB 断开，无法完成安装后 `pm` 回读 | 1 | 保留安装命令的 `Success` 结果；记录设备回读为 Not Run |
| 对目录直接运行 `rtk grep` 未递归 | 1 | 改用 `rtk proxy rg` 搜索源码目录 |
| `adb shell input text` 中的 `>` 被设备 shell 当作重定向 | 1 | 对设备端 shell 转义 `>` 后再注入 Termux 文本 |
| 最终验收时 Termux 命令行已有残留字符并进入 zsh 纠错提示 | 1 | 用 Ctrl+C 清空，截图确认空提示符后重跑；不把该轮当作产品结果 |
| 将英文布局资源名 `key_26` 误作运行时 board 标识 | 1 | 直接记录 `getBoardEventName()` 返回值，改用实机值 `key_eng` 后重跑验收 |
| `adb shell find` 的分组括号被设备 shell 解析失败 | 1 | 拆成不带分组的只读查询，并按已知精确路径清理测试文件 |

## 当前边界

- Debug APK、单元测试、Manifest、zipalign、v2 签名和 dex 内容均已验证。
- 已完成手机安装、LSPosed 注入确认，以及英文累积预编辑、上滑数字/符号、空格和退格实机验收。
- 当前新增问题限定为终端编辑器：英文模式无候选词，但第一次回车只结束内部状态，第二次才向终端发送回车。
- 已在干净 Termux 提示符完成验收：最后一个软键盘字母后第一次回车即可执行，普通回车不会重复发送。
