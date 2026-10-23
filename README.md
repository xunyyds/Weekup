# 课来了

原生 Android 课程表应用，纯 Kotlin + Jetpack Compose

## 下载

正式版见 [Releases](https://github.com/xunyyds/kelaile/releases/latest)，当前 **v2.5.3**（包名 `com.xunyyds.kelaile`，arm64-v8a 与 x86_64 打在同一包里）。包名与签名都换过，旧版 `com.xu.wake` 不能覆盖升级，需先导出课表、卸载重装再导入。

## 命名

| 位置 | 取值 |
|---|---|
| 应用名（桌面图标 / 启动页 / 关于页） | 课来了 |
| 代码标识前缀 | `Kelaile`（`KelaileApp`、`KelaileTheme`、`KelaileExecutors` …） |
| 资源命名 | `ic_kelaile`（资源名只能是 ASCII 小写） |
| 包名 / `applicationId` | `com.xunyyds.kelaile` |
| 线程 / 日志 / 偏好文件名 | 英文：`kelaile-io`、`kelaile-compute`、`kelaile-reminders`、`KelaileReminders`、`kelaile_search` |
| 只能留 ASCII 的 | Uri scheme `kelaile://reminder/`、WebView 注入节点 `__kelaile_*`、JS 哨兵 `KELAILE_TABLE_TOO_LARGE`、共享元素 key、Gradle `rootProject.name` |
| 签名密钥 | `D:/dev/kelaile-signing`（PKCS12、alias `kelaile`、RSA 4096） |

## 环境

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 2.4.0（含 Compose 编译器插件） |
| 构建 | Gradle 9.3.1 + AGP 9.1.0 |
| JDK | 21 |
| compileSdk / targetSdk | 36 |
| minSdk | 26（Android 8.0） |
| ABI | arm64-v8a、x86_64 |

## 构建

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

需在 `android/local.properties` 指定 `sdk.dir`。Release 使用本地密钥 `D:/dev/kelaile-signing`（密钥与密码不入库），开启 R8 与资源缩减。

产物：`android/app/build/outputs/apk/release/app-release.apk`。

## 依赖

生产依赖仅 8 条，其余全部使用平台 API：

```kotlin
platform("androidx.compose:compose-bom:2026.02.01")
implementation("androidx.activity:activity-compose:1.12.4")
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.animation:animation")
implementation("androidx.compose.material3:material3")
implementation("androidx.webkit:webkit:1.15.0")
implementation("org.jsoup:jsoup:1.18.3")
```

测试依赖：`junit:junit:4.13.2`、`org.json:json`（JVM 适配）。

未引入：Room、Ktor/OkHttp、图片加载库、图标全集、额外动效库。通知、文件、时区、网络（`HttpURLConnection`）、WebView、xlsx 解包与生成、Markdown 渲染均走平台 API 或自写实现。

## 架构

源码根 `android/app/src/main/kotlin/com/xunyyds/kelaile/`，按职责分层，依赖单向向下：

```
ui/           Compose 页面与交互
domain/       纯业务动作（课程增删改、导入合并、排课移动）
application/  用例编排（加载、保存、备份恢复）
data/         模型、存储、解析、日期与周次换算、提醒排期
core/         平台能力与并发
```

```
ui/            App 顶层导航、Timetable、Settings、Browser、TopBar、Motion、Components
               glass/     液态玻璃透镜（RenderEffect + RuntimeShader）
               search/    全屏搜索：SearchIndex 索引、SearchOverlay 面板、SearchPill 入口
               palette/   命令候选条目
               update/    更新弹窗、流程控制器与 Markdown 渲染
domain/        ScheduleActions —— 无 Android 依赖的纯函数
application/   ScheduleService —— 串行化读写 + 提醒同步
data/          Models、Repository、BackupCodec、SchoolParser、XlsxReader、XlsxTimetable、XlsxWriter
               WeekDates（周次与真实日期换算）、Reminders、ReminderPlan
               VersionRules（版本与强制更新）、ReleaseInfo、UpdateSource（包选取与镜像）、TransferMeter
core/          concurrent/KelaileExecutors、platform/（APK 分享、链接、元数据）、prefs/
               update/    UpdateClient（Release 查询与下载）、ApkVerifier、ApkInstaller
```

## 更新

更新走 GitHub Release：`api.github.com/repos/xunyyds/kelaile/releases/latest`，只认正式版，
`tag_name` 去掉 `v` 后与当前版本比较。安装包从 `assets` 中优先选 `.apk`；下载先直连，
失败后按序尝试镜像（`gh.07150721.xyz`、`ghpxy.hwinzniej.top`、`ghf.无名氏.top`、
`gh.chjina.com`、`js.jiangss.shop`、`ghproxy.icu`，主机名做 `IDN.toASCII`）。

启动时静默检查一次：失败只在「关于与更新」页显示红字，不弹窗；发现新版本则弹更新弹窗。
「此版本不再提示」最多延迟 3 个正式版本，达到阈值后强制更新且不可跳过。规则集中在
`VersionRules`，安装包选取与镜像集中在 `UpdateSource`。

