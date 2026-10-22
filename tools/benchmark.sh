#!/usr/bin/env bash
# 课来了 真机性能基准 —— 可复用运行脚本
# 用法: tools/benchmark.sh [all|startup|scroll|search]
set -euo pipefail

SUITE="${1:-all}"
ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/android"

# JAVA_HOME: 优先环境变量，否则用本机已知的 JDK
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x "/d/dev/jdk-21/jdk-21.0.12.1+1/bin/java.exe" ]; then
    export JAVA_HOME="/d/dev/jdk-21/jdk-21.0.12.1+1"
  else
    echo "ERROR: JAVA_HOME 未设置，且未找到 D:/dev/jdk-21/jdk-21.0.12.1+1" >&2
    exit 1
  fi
fi

ADB="${ANDROID_HOME:-/d/dev/android-sdk}/platform-tools/adb"
[ -x "$ADB" ] || ADB=adb

echo "== 检查设备 =="
DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
if [ -z "$DEVICES" ]; then
  echo "ERROR: 没有可用设备。请连接手机并开启 USB 调试，然后确认 'adb devices' 状态为 device。" >&2
  echo "       模拟器数字不可信，本项目只接受真机结果。" >&2
  exit 1
fi
echo "   设备: $DEVICES"

echo "== 关闭系统动画（否则帧时间被污染）=="
"$ADB" shell settings put global window_animation_scale 0     || true
"$ADB" shell settings put global transition_animation_scale 0 || true
"$ADB" shell settings put global animator_duration_scale 0    || true

case "$SUITE" in
  all)     ARGS="" ;;
  startup) ARGS="-Pandroid.testInstrumentationRunnerArguments.class=com.xu.wake.benchmark.StartupBenchmark" ;;
  scroll)  ARGS="-Pandroid.testInstrumentationRunnerArguments.class=com.xu.wake.benchmark.TimetableScrollBenchmark" ;;
  search)  ARGS="-Pandroid.testInstrumentationRunnerArguments.class=com.xu.wake.benchmark.SearchBenchmark" ;;
  *) echo "用法: $0 [all|startup|scroll|search]" >&2; exit 2 ;;
esac

echo "== 运行基准: $SUITE =="
cd "$ANDROID_DIR"
./gradlew :benchmark:connectedBenchmarkAndroidTest $ARGS

echo ""
echo "== 完成。结果在: =="
find benchmark/build/outputs/connected_android_test_additional_output -name '*.json' 2>/dev/null | head -20 || true
echo ""
echo "提示: 读完 JSON 后清理生成物可执行  tools/benchmark.sh 前先看 benchmark/README.md"
