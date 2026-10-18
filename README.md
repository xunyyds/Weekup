# xwake · 原生 Android 课程表

纯 Kotlin + Jetpack Compose；不包含 Flutter/Dart 引擎或插件。Android Studio 打开 `android` 目录。

项目仓库：https://github.com/xunyyds/xwake 。应用内仓库入口统一由 `core/platform/AppLinks.REPOSITORY` 定义；不预设仓库存在 LICENSE 页面。

## 构建

需要 JDK 21、Android SDK 36；在 `android/local.properties` 设置 `sdk.dir`。

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Release 使用原有本地 xwake 签名（`D:/dev/xwake-signing`）；密钥和密码不进仓库。构建配置只保留 `arm64-v8a`，开启 R8 和资源缩减；输出 `android/app/build/outputs/apk/release/app-release.apk`。最低 Android 8.0/API26。

## 功能

- 本地课表、周次切换、课程增删改、颜色及单双周。
- 主题、三种底栏、字体/密度、学校/学期/星期显示和作息编辑。
- 系统文件选择器导入/导出旧版 version=1 JSON 备份。
- WebView 本人登录教务，离线解析课表，预览确认；不上传或保存登录凭据。
- 桌面模式用桌面版 UA 与宽布局视口打开教务；页面自身的 XHR/fetch/表单 POST 会被复制后由应用网络栈重放，避免 WebView UA 与 `X-Requested-With` 指纹导致登录或课表页返回移动端页面。
- AlarmManager 非精确上课提醒；权限、开机、时间/时区变化恢复，最近300项滚动排期。
- 统一 Compose 页面切换、弹簧滑动底栏、卡片按压及系统减少动画支持。

## 数据与兼容

原 applicationId `com.xu.wake` 保持不变。旧版 `app_flutter/课表.json` 首次启动读取，写入原生 `files/课表.json` 时使用 AtomicFile，旧源不删除。损坏文件拒绝静默覆盖；明确导入有效备份可恢复并保留损坏副本。

## 分层与平台工具复用

源码根目录为 `android/app/src/main/kotlin/com/xu/wake/`：

- `ui/`：Compose 页面、展示状态与用户交互。`About.kt` 不直接读文件、资产或 PackageManager，也不创建 Intent；只调用平台层 API。`ui/web/` 收纳教务浏览器的桌面模式请求桥接：`XwakeDesktopChannel` 负责页面侧脚本装载与报文体暂存，`XwakeDesktopTransport` 负责用 `HttpURLConnection` 重放请求，`WebReplayCodec` 是纯函数辅助（有单测）。页面侧脚本是 `app/src/main/assets/web/xwake_post_bridge.js`，可用 `node tools/verify_post_bridge.mjs` 在 V8 里跑通它的拦截行为。
- `core/platform/`：可供各页面复用的 Android 平台能力，不依赖 Compose 或具体页面。`AppLinks` 统一链接常量；`AppPlatform` 提供包/版本/设备元信息、应用图标、本地第三方许可证读取与链接打开；读取操作使用 IO dispatcher；`ApkSharing` 负责已安装 APK 的复制与系统分享。
- `core/prefs/`：本地偏好存储，如设置搜索历史 `SearchHistory`（最多8条，仅本机）。
- `application/`：`ScheduleService` 是保存与提醒排期的唯一入口，内部互斥串行，UI 不再直接做文件 IO。
- `domain/`：`ScheduleActions` 为纯业务变更（保存/删除/导入课程），无 Android 与存储副作用。
- `data/`：存储、备份编解码、HTML 解析、提醒计划与数据模型。

APK 分享保留 `.ui.ApkShareProvider` 的 Manifest 兼容入口和 `ui.shareInstalledApk` 的旧页面委托，实际逻辑集中在 core。仅共享缓存 `shared-apk/` 内的完整副本，保留 FileProvider 的只读 URI 授权和 ClipData，进程内原子并发保护防止重复复制；split 安装包明确拒绝分享，避免把不完整 base APK 当作完整安装包。Manifest 与 provider 路径配置无需变更。

## 依赖边界

直接生产依赖仅 Activity Compose、Compose UI/Foundation/Animation/Material3、Jsoup、AndroidX WebKit（仅在支持 `DOCUMENT_START_SCRIPT` 时用于文档起始注入，否则回退到页面加载后注入）。通知、文件、时区、网络（`HttpURLConnection`）和WebView使用平台API，无 Flutter插件、Room、Ktor 等网络客户端、图标全集或额外动效库。JUnit 与 org.json JVM adapter 仅用于测试。

参考 mikcb 的动效语言，不复制其 GPL 组件。液态底栏按 AndroidLiquidGlass 的 Apache-2.0 最小镜头算法作 Android-only 适配：Android13+硬件加速时采样背景并折射/高光，旧系统回退材质；保留完整许可证及 THIRD_PARTY_NOTICES.md。

2.1 新增：可拖动底栏、六套主题预设/Android12+莫奈取色、分级设置、截图风格关于页与系统APK分享、桌面UA教务及学校自动识别、HSV/透明度调色盘与严格#AARRGGBB实时预览、日期/时间/数字选择器。

2.2 新增：课表与设置滚动收缩吸顶标题、设置分类标题同栏替换、设置沉浸搜索、自定义动效速度/减少动画/转场风格、浏览器刷新与横竖屏切换、WebView 会话保持与清除、`core/platform` + `application` + `domain` 分层。

2.3 新增：浏览器 chrome 可收起以适配 CAS 二维码登录页并移除强制桌面 viewport、设置搜索仅首页显示且子页标题固定、搜索历史与关键词高亮、悬浮磨砂搜索框、底栏菜单矢量勾选、启动动画。

2.4 新增：桌面模式的教务请求重放桥接（`ui/web/` 与 `assets/web/xwake_post_bridge.js`），桌面 UA 下改走宽布局视口与初始缩放，`Node` 脚本 `tools/verify_post_bridge.mjs` 覆盖拦截行为，`WebReplayCodecTest` 覆盖标记与 MIME 解析。

2.5 新增：请求重放对空报文体、`Blob`/`ArrayBuffer` 与 `Request` 对象的分流处理，重定向改由应用侧跟随（XHR 遇 3xx 不再回落原生栈重发 POST），响应体改为带上限的缓冲流，`@JavascriptInterface` 增加 R8 保留规则。

## 验证边界

JVM 测试覆盖备份兼容、校验、保守HTML解析、提醒日期/DST及存储故障契约，并覆盖桌面模式重放的标记剥离、跳转解析与 MIME/charset 判定；`tools/verify_post_bridge.mjs` 在无头 V8 里覆盖页面侧拦截（XHR/fetch/Request/Blob/表单）。真实教务登录、网络重放后的会话与跳转行为、通知送达、设备流畅度和AtomicFile断电恢复仍需真机验证。
