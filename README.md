<p align="center">
  <img src="art/logo.svg" alt="Logo" width="120" height="120"/>
</p>

<h1 align="center">呼叫转移排班助手（Call Forward Scheduler）</h1>

<p align="center">
  <img alt="Build & Test" src="https://github.com/AutoPhone-Org/call-forward-scheduler/actions/workflows/build.yml/badge.svg"/>
  <img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-blue.svg"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9-blue"/>
</p>

基于 Shizuku 的「定时倒班切换自动呼叫转移」Android App，无 root 即可按排班表自动把本机来电转给当班人。

> 产品需求见 [`docs/PRD_自动呼叫转移排班助手.md`](docs/PRD_自动呼叫转移排班助手.md) · 使用文档见 [Wiki](https://github.com/AutoPhone-Org/call-forward-scheduler/wiki) · 在线配置见 [GitHub Pages](https://autophone-org.github.io/call-forward-scheduler/)

## 功能特性

- **定时倒班切换**：按排班表在切换节点自动切换呼叫转移目标
- **多班次排班**：内置两班倒 / 三班倒 / 四班两倒 / 四班三倒模板，支持 24h/12h/8h 单岗时长与每日多切换节点
- **排班日历**：App 内月视图 + 网页月视图，直观查看每天当班人员
- **无 root 转移**：经 Shizuku 以 shell 权限下发 USSD（`*21*号#`），无需 root
- **一键取消**：随时 `#21#` 取消全部转移，避免漏接
- **深链导入**：网页配置后通过 `callforward://import` 一键导入 App
- **自动更新**：检测新版本，多镜像下载 APK 并引导安装
- **云端同步**：支持 GitHub Gist 与 Gitee Gist 双平台（国内推荐 Gitee 直连），令牌加密存储（Keystore AES-256-GCM）
- **CSV 导入导出**：人员与排班表一键备份/恢复（UTF-8 BOM，Excel 直接打开）
- **系统日历对接**：只读导入日历事件（标题如「白班 思源」）生成排班
- **通知告警**：切换成功/失败、下载进度均有通知栏提示

## 技术栈

- 语言：Kotlin
- 构建：Gradle 8.7 + AGP 8.5.2
- 目标：Android 11+（`minSdk 24`，`target/compileSdk 34`）；兼容 HarmonyOS 4.x（AOSP 兼容层）
- 权限桥接：Shizuku（`dev.rikka.shizuku:api` / `provider` 13.1.5）
- 调度：AlarmManager（精确闹钟）+ BootReceiver（重启恢复）
- 持久化：SharedPreferences（人员 / 排班 / 设置）
- 更新：多镜像下载（ghfast / ghproxy / moeyy / 官方直连）+ FileProvider 引导安装

## 目录结构

```
call-forward-scheduler/
├── app/
│   ├── build.gradle.kts          # 模块构建与依赖
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/autophone/callforward/
│       │   │   ├── CallForwardApp.kt          # Application 入口
│       │   │   ├── model/Models.kt            # 数据模型 + 默认倒班模板
│       │   │   ├── engine/RosterEngine.kt     # 排班引擎（切换节点展开/当班查询）
│       │   │   ├── executor/CallForwardExecutor.kt  # USSD 执行（经 Shizuku）
│       │   │   ├── scheduler/                 # 定时调度 + 开机恢复 + 闹钟权限引导
│       │   │   ├── data/RosterStore.kt        # 本地 JSON 持久化
│       │   │   ├── deeplink/DeepLinkImporter.kt # 深链配置导入
│       │   │   ├── notify/NotificationHelper.kt  # 通知栏（切换/下载/告警）
│       │   │   ├── update/                    # 更新检测 + 下载 + 安装
│       │   │   └── ui/                        # 主界面 / 人员管理 / 排班管理（含月历）
│       │   └── res/                           # 布局 / 字符串 / 颜色 / 图标 / 主题
│       └── test/.../engine/RosterEngineTest.kt # 排班引擎单元测试
├── pages/                        # 静态官网（配置编辑器 + 排班日历）
├── docs/                         # PRD
├── wiki/                         # 使用文档（GitHub Wiki）
├── art/                          # logo 与组织头像
├── .github/workflows/            # build.yml / pages.yml / auto-release.yml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 构建

> 需要 JDK 17 与 Android SDK（`ANDROID_HOME` 或 `local.properties` 指向 SDK）。

```bash
cd call-forward-scheduler
# 生成 wrapper（如缺失）
gradle wrapper
# 编译 debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 本机若未安装 JDK/Gradle/Android SDK，可直接从 GitHub Actions 的 artifact 或 [Release](https://github.com/AutoPhone-Org/call-forward-scheduler/releases) 下载编译好的 APK。

## 运行（设备侧）

1. 手机安装并启动 [Shizuku](https://shizuku.rikka.app/)，通过无线调试/root 方式激活。
2. 安装本 App，打开后点击「授权 Shizuku」。
3. 授权成功即可「设置转移」「一键取消转移」，或导入网页配置。

## 在线配置

- **GitHub Pages**：https://autophone-org.github.io/call-forward-scheduler/ （配置编辑器 + 排班日历，生成深链导入）
- **GitHub Wiki**：https://github.com/AutoPhone-Org/call-forward-scheduler/wiki （9 页使用文档）

## 云端同步

App 内「云同步」支持双平台，按设备网络环境选择：

| 平台 | API | 适用场景 |
|------|-----|---------|
| GitHub Gist | api.github.com | 国际网络环境 |
| **Gitee Gist（码云）** | gitee.com/api/v5 | 国内直连推荐 |

使用要点：
- 令牌权限仅需 gist（GitHub 为 PAT，Gitee 为「私人令牌」）
- **令牌加密存储**：基于 Android Keystore（AES-256-GCM）加密后落盘，主密钥不可导出；设备备份中不含令牌与排班数据
- Gist ID 留空时首次上传自动创建私密 Gist 并回填
- 令牌与 Gist ID 按平台分别保存，切换平台自动回填
- GitCode 实测暂无 Gist 接口，暂不支持（待办中）
- 同步内容与深链导入格式完全一致，可互相导入

## 自动发布与更新

- 每次 push `master`，`auto-release.yml` 自动递增 patch 版本（如 `v0.1.2 → v0.1.3`）、打 Tag 并发布 Release，产物为 release APK。
- Release 说明内置多镜像下载通道（GitHub 官方 / ghfast / ghproxy / moeyy）。
- App 内 `UpdateChecker` 检测新版本，`ApkDownloader` 多镜像轮询下载，`ApkInstaller` 通过 FileProvider 引导安装。

## 核心执行原理

- 设置转移：经 Shizuku shell 执行 `service call phone <setCode> s16 '*21*<号>#'`
- 查询校验：`service call phone <getCode> s16 '*#21#'`
- 取消：`service call phone <setCode> s16 '#21#'`

> 注意：`setCode`/`getCode` 因 ROM 不同而异。已在 BON-AL00（HarmonyOS 4.0.0.154）验证 `get=2` 可用；**`set` code 需实机枚举确认**（默认按 1）。

## 排班与倒班模板

内置默认模板（见 PRD §3.2.1）：两班倒 / 三班倒 / 四班两倒 / 四班三倒。
单岗时长支持 24h / 12h / 8h，每天可多切换节点。

## 当前进度与待办（TODO）

已实现：

- [x] 工程骨架（Gradle + Manifest + 依赖）
- [x] 数据模型 + 默认倒班模板
- [x] 排班引擎（切换节点展开 / 跨天查询）+ 单元测试
- [x] USSD 执行器（Shizuku 对接，含回读校验）
- [x] 定时调度（AlarmManager）+ 开机恢复
- [x] 通知栏告警（成功/失败）+ 配置持久化
- [x] 完整 UI（主界面 / 人员管理 / 排班管理 + App 月历）
- [x] Deep Link 配置导入
- [x] 客户端自动更新（检测 + 多镜像下载 + 引导安装）
- [x] 精确闹钟权限引导（Android 12+）
- [x] 下载进度通知
- [x] 自动版本递增 + Tag + Release 发布
- [x] 静态官网（配置编辑器 + 排班日历）+ Wiki 文档
- [x] CSV 导入导出（人员 + 排班表）
- [x] 系统日历只读导入排班
- [x] 云端同步（GitHub Gist / Gitee Gist 双平台）

待办：

- [ ] 实机验证：Shizuku 激活 + `set` code 枚举 + `*21*` 生效
- [ ] 正式签名 keystore（当前 release 用 debug 签名，仅测试用）
- [ ] GitCode 同步后端（其暂无 Gist 接口，需基于仓库文件 API 另行实现）
- [ ] 云端自动同步（当前为手动上传/恢复）

## 安全提醒

`*21*` 为无条件全部转移，一旦设置所有来电转走。测试务必使用测试卡/备用号，并随时 `#21#` 取消。
