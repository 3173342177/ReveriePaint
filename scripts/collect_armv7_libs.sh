#!/usr/bin/env bash
set -e

DEST="/home/lanrhyme/Projects/ReveriePaint-native/third_party/android-native-libs-armeabi-v7a"
mkdir -p "$DEST"

KRITA_BIN="/home/lanrhyme/Projects/krita-source/build-android-armv7"
DEPS="/home/lanrhyme/ReveriePaint-android/deps-armv7"
QT_ARMV7="/home/lanrhyme/Qt6/6.6.3/android_armv7"
NDK="/home/lanrhyme/Android/Sdk/ndk/25.2.9519653"

# 1. Krita libs & plugins
for f in "$KRITA_BIN"/lib/*.so; do
    b="$(basename "$f")"
    if [[ "$b" != lib* ]]; then
        cp -f "$f" "$DEST/lib$b"
    else
        cp -f "$f" "$DEST/$b"
    fi
done

# 2. KF6 libs
cp -f "$DEPS"/lib/libKF6*.so "$DEST/"

# 3. Qt libs (matching arm64-v8a set)
for f in /home/lanrhyme/Projects/ReveriePaint-native/third_party/android-native-libs/libQt6*.so; do
    b="$(basename "$f")"
    armv7_name="${b//arm64-v8a/armeabi-v7a}"
    if [ -f "$QT_ARMV7/lib/$armv7_name" ]; then
        cp -f "$QT_ARMV7/lib/$armv7_name" "$DEST/"
    fi
done

# 4. Other 3rdparty deps
cp -f "$DEPS"/lib/libbz2.so "$DEST/" 2>/dev/null || true
cp -f "$DEPS"/lib/libpng16.so "$DEST/" 2>/dev/null || true
cp -f "$DEPS"/lib/libquazip1-qt6.so "$DEST/" 2>/dev/null || true
cp -f "$DEPS"/lib/libintl.so "$DEST/" 2>/dev/null || true

# 5. libc++_shared.so
cp -f "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" "$DEST/"

echo "Collected $(ls -1 "$DEST"/*.so | wc -l) armv7 shared libraries into $DEST"
