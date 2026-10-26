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

未引入：Room、Ktor/OkHttp、图片加载库、图标全集。通知、文件、时区、网络（`HttpURLConnection`）、WebView、xlsx 解包与生成、Markdown 渲染均走平台 API 或自写实现。
UI 侧仅多一个第三方依赖 `io.github.kyant0:backdrop`（Apache 2.0，即「AndroidLiquidGlass」同源库），液态底栏的折射材质与阻尼手感直接复用它，
其余玻璃材质（毛玻璃面板、渐变描边、散景壁纸）均为手写。

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
               Theme.kt   设计令牌：形状/间距/海拔、渐变壁纸配色、排版、明暗配色
               GlassSheets.kt  玻璃底部卡片、玻璃对话框、忙碌遮罩
               glass/     液态玻璃透镜（RenderEffect + RuntimeShader）与毛玻璃面板
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

## 视觉规范

定位是「iOS Liquid Glass 材质 + Android 原生 Compose 实现」：只借鉴材质、配色、
空间层次与圆角体系，不照搬 iOS 的交互行为。

| 层 | 实现 |
|---|---|
| 壁纸 | `KelaileBackdrop`：三段纵向渐变 + 三团大半径径向光斑，浅色偏蓝→紫→杏，深色为深蓝灰 |
| 分组卡片 | `Section`：分类标题在卡片外，组内共用一张 26dp 圆角磨砂卡，行间内缩发丝分隔线 |
| 毛玻璃面板 | `glassPanel` / `glassSheen`：半透明填充 + 自上而下高光描边 + 顶部内侧提亮 |
| 液态玻璃 | `liquidGlass`（卡片级，采样壁纸）；底栏走 kyant backdrop 的 vibrancy + blur + lens，采样「壁纸 + 正文」内容层，滚动内容会真的透过玻璃 |
| 顶栏/底栏 | 悬浮玻璃胶囊；底栏三档（默认/悬浮/液态玻璃），液态档带拖拽挤压、速度形变与色散 |
| 浮层 | `GlassSheet` / `KelaileDialog` 走独立 Dialog 窗口 + 自绘遮罩，替换 Material 弹窗 |
| 课程卡 | 课程色作为透光色（浅色更实、深色更透），再压白色高光与 1dp 描边 |
| 动效 | 弹性回落 + 淡入位移，全部受「减少动画」「动画速度」与系统动画开关约束 |

## 更新

更新走 GitHub Release：`api.github.com/repos/xunyyds/kelaile/releases/latest`，只认正式版，
`tag_name` 去掉 `v` 后与当前版本比较。安装包从 `assets` 中优先选 `.apk`；下载先直连，
失败后按序尝试镜像（`gh.07150721.xyz`、`ghpxy.hwinzniej.top`、`ghf.无名氏.top`、
`gh.chjina.com`、`js.jiangss.shop`、`ghproxy.icu`，主机名做 `IDN.toASCII`）。

启动时静默检查一次：失败只在「关于与更新」页显示红字，不弹窗；发现新版本则弹更新弹窗。
「此版本不再提示」最多延迟 3 个正式版本，达到阈值后强制更新且不可跳过。规则集中在
`VersionRules`，安装包选取与镜像集中在 `UpdateSource`。

