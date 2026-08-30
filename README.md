# BetterPowerMenu

BetterPowerMenu 是一个面向 ColorOS SystemUI 的 LSPosed 模块：在长按电源键打开的系统电源菜单中，保留原版关机/重启滑条，并增加一条可配置的双向动作滑条。

## 功能

- 在原版电源菜单右侧增加独立滑条。
- 上滑和下滑可以分别绑定不同动作。
- 支持隐藏原版界面底部的“紧急呼叫”入口。
- 支持分别设置上滑、下滑滑条的颜色。
- 注入失败或不兼容时会主动放弃注入。

### 可配置动作

| 动作 | 是否需要 Root | 说明 |
| --- | --- | --- |
| 锁屏 | 否 | 锁定屏幕 |
| 息屏 | 否 | 关闭屏幕，行为接近电源键短按 |
| 免打扰 | 否 | 免打扰 |
| 软重启 | 否 | 仅重启 `com.android.systemui` 进程 |
| 飞行模式 | 否 | 开关系统飞行模式 |
| 重启到 Recovery | 是 | 通过 Root 执行系统重启命令 |
| 重启到 EDL | 是 | 通过 Root 执行系统重启命令 |
| 重启到 Fastboot | 是 | 通过 Root 执行系统重启命令 |

默认配置为：上滑切换飞行模式，下滑切换免打扰。Root 未授权时，三个高级重启动作不会出现在动作选择列表中。

## 使用要求

- Coloros15/16
- ColorOS SystemUI，目标包名为 `com.android.systemui`。
- 已安装并正常工作的 LSPosed 。
- 在 LSPosed 中启用本模块，并将作用域勾选为 SystemUI。
- Recovery、EDL、Fastboot 动作需要设备已获得 Root，并允许本模块执行 `su`。

本模块无法保证覆盖所有 ColorOS 版本。

## 安装

1. 构建或获取APK。
2. 在 Android 设备上安装 APK。
3. 打开 LSPosed，启用 `BetterPowerMenu`。
4. 将作用域设置为 `com.android.systemui`。
5. 重启 SystemUI 或重启设备。
6. 打开 BetterPowerMenu 设置页，按需配置动作、颜色和紧急呼叫显示状态。
7. 长按电源键打开电源菜单，向上或向下滑动新增滑条至阈值即可触发动作。

Root 状态检测会执行 `su -c id` 并确认返回 `uid=0`。请允许然后重新检测即可。

## 构建

项目使用 Gradle Wrapper，主要构建环境如下：

- Gradle：9.2.0
- Android Gradle Plugin：9.0.1
- compileSdk / targetSdk：35
- minSdk：26
- Java：17 或更高版本
- Xposed API：`de.robv.android.xposed:api:82`（仅编译期依赖）

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

生成的调试 APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以在 Android Studio 中导入项目。

### GitHub Actions

项目包含 `.github/workflows/build.yml`：

## 日志与故障排查

日志 TAG 以 `BetterPowerMenu` 开头，关键日志也会写入 LSPosed 日志。可以使用：

```bash
adb logcat -s BetterPowerMenu
```

常见检查项：

- 没有新增滑条：确认模块已启用、作用域包含 `com.android.systemui`，然后重启 SystemUI。
- 设置页存在但电源菜单无变化：检查 LSPosed 是否报告 Hook 错误。
- Root 动作不可选：在设置页重新检测 Root 。
- 动作执行后没有关闭菜单：查看日志，确认系统服务或 Root 命令是否返回成功。
- 升级系统后失效：厂商可能修改了 SystemUI 内部类、字段或布局结构，需要根据日志适配对应版本。

## 安全与风险提示

- 本模块会修改 SystemUI 进程行为。启用前确保可以救砖。
- 软重启只结束 SystemUI 进程，不等同于整机重启。
- 本项目未承诺兼容性保证。

## 许可证

本项目使用 [MIT License](LICENSE)。
