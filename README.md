# xwake

原生 Android 课程表应用。纯 Kotlin + Jetpack Compose，无 Flutter 引擎、无自建后端，数据全部保存在本机。

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

需在 `android/local.properties` 指定 `sdk.dir`。Release 使用本地密钥 `D:/dev/xwake-signing`（密钥与密码不入库），开启 R8 与资源缩减。

产物：`dist/xwake-release.apk`。

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
core/          concurrent/XwakeExecutors、platform/（APK 分享、链接、元数据）、prefs/
```

### 几个实现要点

**周次与日期**：`AppSettings.start` 是第 1 周第一天，第 N 周即从它起第 `(N-1)*7` 天。表头日期、周区间标签、今天高亮全部由 `WeekDates` 推算，不另设第二套算法。

**课表手势**：单指滚动、双指缩放、拖过左右边界切换周次，全部集中在一处。手势结束一定吸附——缩放吸到「屏幕上正好放下整数列」的档位，偏移吸到整格边界，所以停下来看到的永远是完整格子。

**作息**：默认一天 20 节（8:00 起、40 分钟一节、间隔 5 分钟）。`resizePeriods` 扩容时沿用现有间隔外推，越过午夜则拒绝；`adjustPeriods` 顺延越界时给出可读提示。

**教务导入**：WebView 走原生网络栈，不拦截、不重放请求。脚本递归遍历同源 iframe 收集 `table`，剔除脚本与媒体节点后交给 Jsoup。页面上可能同时存在课表预览小部件与真正的课表，解析器逐张尝试、取课程最多的一张。

**表格导入**：`.xlsx` 是 zip + XML，用平台自带的 `ZipInputStream` 与 SAX 解析，不引入 POI。`XlsxTimetable` 的星期、节次、周次判定与网页导入共用同一套规则。

**并发**：`XwakeExecutors` 提供两个池——`io`（阻塞 IO，核心数×2+2，有界队列 + CallerRuns）与 `compute`（CPU 密集，核心数封顶 6）。提醒排期刻意保持单线程：`sync()` 先撤销全部闹钟再重设，并发会交出错乱的闹期。

**动效**：`XwakeMotion` / `XwakeShapes` / `XwakeSpacing` / `XwakeElevation` 四组 token 统一时长、缓动、圆角、间距与阴影。同心圆角由 `nested(radius, inset)` 推导。所有动画读取 `xwakeMotionEnabled()`，关闭时直接跳到终态。

**液态玻璃**：Android 13+ 硬件加速时用 `RuntimeShader` 做边缘折射与高光，采样背景图层；SDK<33 回退纯色材质。`RenderEffect` 按几何尺寸缓存，避免每帧重建。

## 测试

JVM 单元测试 107 项，覆盖备份编解码、数据校验、课表解析（含真实页面结构夹具）、xlsx 解包、周次与日期换算、作息增删、排课移动与冲突、提醒日期/DST、搜索管线、高亮区间合并与课表投影等价性。

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

真机性能基线见 `android/benchmark/README.md`（Macrobenchmark，需连接设备）。
