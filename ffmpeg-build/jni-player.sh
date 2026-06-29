#!/bin/bash
# Build the native FFmpeg JNI player (libffmpegJNI.so replacement)
# After ffmpeg libraries are built, compile and link the JNI wrapper
set -e

NDK_VERSION="27.0.12077973"
FFMPEG_OUTPUT="$(dirname "$0")/output"
PLAYER_SRC="../app/src/main/cpp"

if [ -z "$ANDROID_NDK_HOME" ]; then
    ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/$NDK_VERSION"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
if [ "$(uname)" = "Darwin" ]; then
    TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
fi

# Linker flags for FFmpeg
FFMPEG_LIBS="-lavcodec -lavformat -lavutil -lswresample -lswscale -lavfilter"
LDFLAGS="-shared -Wl,-soname,libffmpegJNI.so -Wl,--no-undefined -Wl,--gc-sections"
LDFLAGS="$LDFLAGS -L\$FFMPEG_OUTPUT/\$ARCH/lib $FFMPEG_LIBS -lm -lz -landroid -llog"

build_player() {
    local arch=$1
    local target=$2
    local api=$3
    local toolchain_prefix=$4

    echo "Building JNI player for $arch..."

    local CC="$TOOLCHAIN/bin/${target}${toolchain_prefix}-clang"
    local SYSROOT="$TOOLCHAIN/sysroot"
    local INC="-I$PLAYER_SRC -I$FFMPEG_OUTPUT/$arch/include"
    local CFLAGS="-O2 -fPIC -DANDROID -DNDEBUG $INC"

    $CC $CFLAGS \
        -c "$PLAYER_SRC/string_encryption.c" \
        -o "/tmp/string_encryption_$arch.o"

    $CC $CFLAGS \
        -c "$PLAYER_SRC/native_player.c" \
        -o "/tmp/native_player_$arch.o"

    $CC $CFLAGS \
        -c "$PLAYER_SRC/ffmpeg_player.c" \
        -o "/tmp/ffmpeg_player_$arch.o"

    $CC $CFLAGS \
        -c "$PLAYER_SRC/jni_bridge.c" \
        -o "/tmp/jni_bridge_$arch.o"

    $CC $LDFLAGS \
        "/tmp/jni_bridge_$arch.o" \
        "/tmp/string_encryption_$arch.o" \
        "/tmp/native_player_$arch.o" \
        "/tmp/ffmpeg_player_$arch.o" \
        -o "$FFMPEG_OUTPUT/$arch/libffmpegJNI.so"

    $TOOLCHAIN/bin/llvm-strip "$FFMPEG_OUTPUT/$arch/libffmpegJNI.so"
    echo "Built: $FFMPEG_OUTPUT/$arch/libffmpegJNI.so"
}

echo "Building FFmpeg JNI Player"
echo "=========================="
echo ""
echo "Copy these .so files to your app:"
echo "  app/src/main/jniLibs/<abi>/libffmpegJNI.so"
echo ""
echo "Then in Kotlin: System.loadLibrary(\"ffmpegJNI\")"
echo ""

# Build for each architecture
build_player "arm64-v8a" "aarch64-linux-android" "21" "21"
build_player "armeabi-v7a" "armv7a-linux-androideabi" "16" "16"
build_player "x86_64" "x86_64-linux-android" "21" "21"
