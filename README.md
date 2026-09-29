# ColorOS 免归档 · ArchiveToOpen

[![libxposed API](https://img.shields.io/badge/libxposed-API%20102-brightgreen)](https://github.com/libxposed/api)
[![Platform](https://img.shields.io/badge/ColorOS-17-1a73e8)](#环境要求)
[![Android](https://img.shields.io/badge/Android-17%20(API%2037)-3ddc84)](#环境要求)

在 ColorOS 17 上关掉 Android 17 新增的应用归档与闲置应用自动处理，把应用详情页的第一个按钮还给「打开」。

Android 17 引入了应用归档：应用可以被「卸载但保留数据」。ColorOS 17 把这项能力和闲置应用管理一起做成了应用详情页上的入口，并且按地区分成两套文案——同一份设置 APK，运行时通过 `CustomizeFeatureUtils.isExpVersion()` 判断地区。海外版第一个按钮是「归档」，中国版则是「自动释放应用空间」开关默认开启。本模块不替换系统逻辑，只把这两个入口的默认值翻转。

## 功能
<img width="7546" height="8192" alt="IMG_20260929_190744" src="https://github.com/user-attachments/assets/c5e26571-a715-42fd-808e-d706bb2a866b" />

| 场景 | 原始行为 | 模块行为 |
| --- | --- | --- |
| 海外版 · 应用详情页 | 第一个按钮是「归档」，点击弹出归档确认框 | 变回「打开」，点击直接启动应用 |
| 中国版 · 应用详情页 | 「自动释放应用空间」默认开启 | 默认关闭，需要时仍可手动打开 |

海外版一侧让 `AppInfoFeature.isSupportArchingFeature()` 恒返回 `false`，一次同时作用于「隐藏归档按钮」和「显示打开按钮」两处。中国版一侧改写 AppOp `android:auto_revoke_permissions_if_unused`，且只在应用从未被设置过（`MODE_DEFAULT`）时关闭，用户手动开过的不再覆盖。

## 环境要求

- ColorOS 17（Android 17 / API 37）。
- LSPosed 等支持 libxposed API 102 的框架。
- 需要 Root（KernelSU / Magisk 均可）。

## 安装

1. 安装 `ArchiveToOpen-x.y.z.apk`。
2. 在 LSPosed 中启用模块，作用域勾选：

   ```
   com.android.settings
   ```

3. 重启设置进程或重启设备。

## 使用

模块没有界面，启用即生效。

- **海外版**：打开任意应用的应用信息页，第一个按钮已是「打开」。
- **中国版**：打开设置后，所有已安装应用会被后台扫一遍，「自动释放应用空间」默认关闭。需要某个应用参与闲置管理时，手动打开该开关即可，模块不会再覆盖。

## 已知限制

- **新装应用有延迟**：模块只在设置进程存活期间工作，新装的应用要等下次打开设置才会被处理。
- **写入是持久的**：关闭操作真实写入了系统状态，禁用或卸载模块不会自动回滚。恢复方式：手动打开开关，或

  ```bash
  adb shell appops set <包名> android:auto_revoke_permissions_if_unused default
  ```

- **只覆盖经典应用详情页**：ColorOS 的应用信息入口（`android.settings.APPLICATION_DETAILS_SETTINGS`）指向经典页，SPA 版页面在本 ROM 上没有入口。
- 系统应用不参与闲置管理，模块也不会去动它们。

## 排错

模块日志 TAG 为 `ArchiveToOpen`，在 LSPosed 日志里按进程筛选即可。

| 记录 | 含义 |
| --- | --- |
| `hooked ...AppInfoFeature#isSupportArchingFeature` | 海外版归档按钮接管成功 |
| `hooked ...OplusHibernationSwitchPreferenceController#updateState` | 中国版页面兜底接管成功 |
| `hooked ...Instrumentation#callApplicationOnCreate` | 全量扫描接管成功 |
| `bulk_default_off_done changed=X kept=Y total=Z` | 扫描完成：X 个被关闭，Y 个保留用户设置，共 Z 个应用 |
| `auto_release_space_default_off_failed` | 单页兜底异常，不影响其他应用 |
| `bulk_default_off_scan_failed` | 扫描整体失败，请附日志反馈 |

## 从源码构建

需要 JDK 17 与 Android SDK（`compileSdk 36`），工程使用 Gradle 8.13 wrapper 与 AGP 8.13.2。

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。

## 版本

- **1.0.0** — 海外版应用详情页「归档」改为「打开」。
- **1.1.0** — 中国版「自动释放应用空间」默认关闭（打开详情页时生效）。
- **1.2.0** — 中国版改为全局扫描，所有已安装应用默认关闭。

## 免责声明

本项目仅供学习与研究。修改系统应用管理行为存在风险，请自行评估并做好备份。
