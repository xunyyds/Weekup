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

产物：`dist/xwake-2.5.0-release.apk`。

## 依赖

生产依赖仅 6 项，其余全部使用平台 API：

```kotlin
platform("androidx.compose:compose-bom:2026.02.01")
implementation("androidx.activity:activity-compose:1.12.4")
implementation("androidx.compose.ui:ui")            // 由 BOM 管理版本
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.animation:animation")
implementation("androidx.compose.material3:material3")
implementation("androidx.webkit:webkit:1.15.0")     // DOCUMENT_START_SCRIPT 能力检测
implementation("org.jsoup:jsoup:1.18.3")            // 课表 HTML 解析
```

测试依赖：`junit:junit:4.13.2`、`org.json:json`（JVM 适配）。

未引入：Room、Ktor/OkHttp、图片加载库、图标全集、额外动效库。通知、文件、时区、网络（`HttpURLConnection`）、WebView 均走平台 API。

## 架构

源码根 `android/app/src/main/kotlin/com/xu/wake/`，按职责分层，依赖单向向下：

```
ui/           Compose 页面与交互
domain/       纯业务动作（课程增删改、导入合并）
application/  用例编排（加载、保存、备份恢复）
data/         模型、存储、解析、提醒排期
core/         平台能力与并发
```

```
ui/            App 顶层导航、Timetable、Settings、Browser、Motion、Components
               glass/     液态玻璃透镜（RenderEffect + RuntimeShader）
               search/    全屏搜索：SearchIndex 索引、SearchOverlay 面板、SearchPill 入口
               palette/   命令候选条目
domain/        ScheduleActions —— 无 Android 依赖的纯函数
application/   ScheduleService —— 串行化读写 + 提醒同步
data/          Models、Repository、BackupCodec、SchoolParser、Reminders、ReminderPlan
core/          concurrent/XwakeExecutors、platform/（APK 分享、链接、元数据）、prefs/
```

### 几个实现要点

**搜索**：`SearchIndex` 把字段预转小写后建索引，用 `LongArray` 打包 score/order 排序；输入串是上次查询的前缀扩展时只重扫上一轮命中集。空查询短路，不构建候选。

**课表投影**：`projectWeek()` 是纯函数，`(courses, week, firstDay, lastDay, weekStart, periods) → WeekProjection`。Composable 里用 `remember` 以这 6 个输入为 key 缓存；颜色与重叠布局（lane）一并预计算。`LocalDate.now()` 的“今天”高亮留在组合内实时求值，不进缓存，否则跨天不刷新。

**表格导入**：`.xlsx` 本质是 zip + XML（`xl/worksheets/*.xml` 与 `xl/sharedStrings.xml`），用 JDK 自带的 `ZipInputStream` 与 SAX 解析，不引入 POI。`XlsxTimetable` 把网格映射成课程，星期/节次/周次判定与网页导入共用同一套规则。

**教务导入**：WebView 走原生网络栈，不拦截、不重放请求。页面加载后通过 `evaluateJavascript` 提取课表：脚本递归遍历同源 iframe 收集 `table`，剔除脚本/表单/媒体节点后交给 Jsoup。`SchoolParser` 按“星期表头 + 节次行 + 周次表达式”三重结构校验，任何一步不成立即拒绝导入，宁可失败也不写脏数据。

**并发**：`XwakeExecutors` 提供两个池——`io`（阻塞 IO，核心数×2+2，有界队列 + CallerRuns）与 `compute`（CPU 密集，核心数封顶 6）。提醒排期刻意保持单线程：`sync()` 先撤销全部闹钟再重设，并发会交出错乱的闹期。

**动效**：`XwakeMotion` / `XwakeShapes` / `XwakeSpacing` / `XwakeElevation` 四组 token 统一时长、缓动、圆角、间距与阴影。同心圆角由 `nested(radius, inset)` 推导，改外层半径时指示器自动跟随。所有动画读取 `xwakeMotionEnabled()`，关闭时用 `EnterTransition.None` 并跳过位移缩放。

**液态玻璃**：Android 13+ 硬件加速时用 `RuntimeShader` 做边缘折射 + 高光，采样背景图层；SDK<33 回退纯色材质。`RenderEffect` 按几何尺寸缓存，避免每帧重建。

## 测试

JVM 单元测试 64 项，覆盖备份编解码、数据校验、课表解析、提醒日期/DST、搜索管线、高亮区间合并与课表投影等价性（以改动前的逻辑作为 oracle 逐值比对）。

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

真机性能基线见 `android/benchmark/README.md`（Macrobenchmark，需连接设备）。
