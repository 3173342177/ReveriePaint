#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

# 复制预编译 Android arm64 动态库到 jniLibs
# 默认从仓库内置 third_party/android-native-libs 复制全部 112 个库:
#   Krita 核心库 + 笔刷插件 + Qt for Android + KF6 + NDK 依赖 + 预编译 libreverie_jni.so
# 克隆仓库后直接运行本脚本即可构建 APK, 无需本地 Qt/Krita 环境
#
# 用法:
#   scripts/copy_jni_libs.sh             预编译模式(默认), 复制 arm64-v8a 含 libreverie_jni.so
#   scripts/copy_jni_libs.sh --no-jni    开发者模式(-PbuildNative), 跳过 libreverie_jni.so
#                                        避免与 CMake 重编译产物冲突
#   scripts/copy_jni_libs.sh <目录> [abi] 从自定义目录复制, 可选指定 abi (默认 arm64-v8a)
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKIP_JNI=0
TARGET_ABI="arm64-v8a"
SRC="$REPO_ROOT/third_party/android-native-libs"

while [ $# -gt 0 ]; do
	case "$1" in
		--no-jni)
			SKIP_JNI=1
			shift
			;;
		--abi)
			TARGET_ABI="$2"
			shift 2
			;;
		arm64-v8a|armeabi-v7a)
			TARGET_ABI="$1"
			shift
			;;
		*)
			SRC="$1"
			shift
			;;
	esac
done

if [ ! -d "$SRC" ] || ! ls "$SRC"/*.so >/dev/null 2>&1; then
	echo "未找到 $SRC 或目录内无 .so 文件"
	echo "请先获取依赖库: 克隆完整仓库 (third_party/android-native-libs)"
	exit 1
fi

DST="$REPO_ROOT/app/src/main/jniLibs/$TARGET_ABI"
mkdir -p "$DST"
count=0
for f in "$SRC"/*.so; do
	b="$(basename "$f")"
	if [ "$SKIP_JNI" = "1" ] && [ "$b" = "libreverie_jni.so" ]; then
		rm -f "$DST/$b"
		continue
	fi
	cp -f "$f" "$DST/"
	count=$((count + 1))
done
echo "已复制 $count 个动态库到 $DST"
if [ "$SKIP_JNI" = "1" ]; then
	echo "已跳过 libreverie_jni.so (buildNative 模式由 CMake 重新编译)"
else
	echo "现在可以直接 ./gradlew assembleDebug 构建 (预编译 jni 模式)"
	echo "如需重新编译 C++: ./gradlew assembleDebug -PbuildNative (见 README 构建章节)"
fi
