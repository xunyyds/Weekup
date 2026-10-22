# 课来了

原生 Android 课程表应用，纯 Kotlin + Jetpack Compose

## 命名

| 位置 | 取值 |
|---|---|
| 应用名（桌面图标 / 启动页 / 关于页） | 课来了 |
| 代码标识前缀 | `Kelaile`（`KelaileApp`、`KelaileTheme`、`KelaileExecutors` …） |
| 资源命名 | `ic_kelaile`、`kelaile_post_bridge.js` |
| 包名 / `applicationId` | `com.xu.wake`（保持不改，已装用户可直接覆盖升级） |
| 签名密钥 | `D:/dev/xwake-signing`、`keyAlias = "xwake"`（密钥库未迁移，改别名会导致签名不一致） |

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

需在 `android/local.properties` 指定 `sdk.dir`。Release 使用本地密钥 `D:/dev/xwake-signing`（密钥与密码不入库，沿用原目录与 alias），开启 R8 与资源缩减。

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

未引入：Room、Ktor/OkHttp、图片加载库、图标全集、额外动效库。通知、文件、时区、网络（`HttpURLConnection`）、WebView 与 xlsx 解包均走平台 API。

## 架构

源码根 `android/app/src/main/kotlin/com/xu/wake/`，按职责分层，依赖单向向下：

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
domain/        ScheduleActions —— 无 Android 依赖的纯函数
application/   ScheduleService —— 串行化读写 + 提醒同步
data/          Models、Repository、BackupCodec、SchoolParser、XlsxReader、XlsxTimetable
               WeekDates（周次与真实日期换算）、Reminders、ReminderPlan
core/          concurrent/KelaileExecutors、platform/（APK 分享、链接、元数据）、prefs/
```

