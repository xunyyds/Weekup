# 课来了 真机性能基准（Macrobenchmark）

真机帧时间 / 启动耗时 / GPU 开销**只能在这里测**。JVM 单元测试（`app/src/test`）测的是算法与分配，不是帧预算——两者不可互相替代。

## 前置条件

1. **一台真机**（模拟器数字不可信），Android 8.0+（minSdk 26）
2. USB 调试已开，`adb devices` 能看到设备且状态为 `device`（不是 `unauthorized`）
3. 手机**关闭动画**，否则帧时间被系统动画污染：
   ```bash
   adb shell settings put global window_animation_scale 0
   adb shell settings put global transition_animation_scale 0
   adb shell settings put global animator_duration_scale 0
   ```
4. 电量 > 50%，**不要插着充电跑**（充电会触发温控降频）
5. 跑之前把手机静置 1 分钟，避免后台任务干扰

## 跑

```bash
cd android
export JAVA_HOME='D:/dev/jdk-21/jdk-21.0.12.1+1'

# 全部基准
./gradlew :benchmark:connectedBenchmarkAndroidTest

# 只跑某一类（快）
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.xunyyds.kelaile.benchmark.StartupBenchmark
```

或直接用封装脚本（会先帮你检查设备与动画设置）：

```bash
tools/benchmark.sh            # 全部
tools/benchmark.sh startup    # 只跑启动
tools/benchmark.sh scroll     # 只跑课表滚动
tools/benchmark.sh search     # 只跑搜索
```

## 结果在哪

```
benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<设备名>/
```

里面是 JSON + 一份 HTML 摘要。关键指标：

| 指标 | 看什么 | 健康线 |
|---|---|---|
| `frameDurationCpuMs` (P50/P90/P99) | 每帧 CPU 耗时 | P90 < 16.7ms（60fps）/ < 8.3ms（120fps） |
| `frameOverrunMs` | 超出帧预算多少 | P90 接近 0 最好；> 0 就是掉帧 |
| `startupTimingMedianDurationMs` | 冷启动到首帧 | 冷启 < 1000ms 体感可接受 |

## 三类基准覆盖什么

| 文件 | 测什么 | 为什么重要 |
|---|---|---|
| `StartupBenchmark` | 冷启 / 温启 / 有课程数据的冷启 | 首屏是第一印象 |
| `TimetableScrollBenchmark` | 课表滚动、滑动换周 | 绘制最密集的页面（98 个格子 + 重叠课程） |
| `SearchBenchmark` | 打开全屏搜索、逐字输入、滚动结果 | 搜索是交互最频繁的路径 |

## 两个模块的分工（别搞混）

- `:app` 的 **`benchmark` buildType** —— 被测量的目标。它 `initWith(release)`，所以有 R8 混淆 + release 签名，但 `isDebuggable = false`（Macrobenchmark 强制要求，debuggable 会直接报错而不是给错数据）。
- `:benchmark` 模块 —— 测量用的 instrumentation APK，自身必须 debuggable，通过 `targetProjectPath = ":app"` 指向目标。

**两者必须成对存在**，删掉任何一边都会构建失败。

## 常见错误

| 现象 | 原因 | 解决 |
|---|---|---|
| `No connected devices` | 没连手机 / 未授权 | `adb devices` 确认状态是 `device` |
| `benchmark requires a non-debuggable app` | 测的是 debug 包 | 确认跑的是 `benchmark` buildType，别手动装 debug APK |
| `profileable` 相关报错 | manifest 缺 `<profileable android:shell="true"/>` | `app/src/main/AndroidManifest.xml` 的 `<application>` 里必须有（已配） |
| 数字抖动大 | 温控降频 / 后台任务 | 静置降温、关后台、别充电 |
| `Could not resolve androidx.benchmark` | 仓库被 `FAIL_ON_PROJECT_REPOS` 限制 | 仓库只能在 `settings.gradle.kts` 声明，模块内不许加 `repositories` |

## 注意：这里没有跑过的数字

本模块建立时**这台机器没有任何设备**（`adb devices` 空、无 emulator、无 system-image），所以**以上基准一次都没在真机上执行过**。配置与编译已验证（`:app:assembleBenchmark` 与 `:benchmark:assembleBenchmark` 均 BUILD SUCCESSFUL），但**没有任何真实帧时间数据**。

拿到手机后第一次跑，请把这行替换成实际结果。
