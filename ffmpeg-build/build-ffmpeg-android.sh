#!/bin/bash
# Build FFmpeg for Android with live-streaming optimizations
# Requires: Docker (recommended) OR Linux with Android NDK r27
set -e

FFMPEG_VERSION="7.1.5"
PATCHES_DIR="$(dirname "$0")/patches"
OUTPUT_DIR="$(dirname "$0")/output"
NDK_VERSION="27.0.12077973"

# Detect NDK
if [ -z "$ANDROID_NDK_HOME" ]; then
    ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/$NDK_VERSION"
    if [ ! -d "$ANDROID_NDK_HOME" ]; then
        echo "NDK not found. Set ANDROID_NDK_HOME or install NDK $NDK_VERSION"
        exit 1
    fi
fi

# ARM64 optimizations for live streaming
FFMPEG_OPTIMIZATIONS="
--enable-small
--enable-optimizations
--disable-doc
--disable-programs
--disable-debug
--disable-autodetect
--enable-cross-compile
--enable-pic
--enable-shared
--disable-static
--enable-network
--enable-protocol=http
--enable-protocol=https
--enable-protocol=hls
--enable-protocol=tcp
--enable-protocol=udp
--enable-protocol=rtmp
--enable-demuxer=hls
--enable-demuxer=mpegts
--enable-demuxer=mpegvideo
--enable-demuxer=flv
--enable-demuxer=mov
--enable-demuxer=matroska
--enable-parser=h264
--enable-parser=hevc
--enable-parser=aac
--enable-parser=mp3
--enable-parser=mpeg4video
--enable-decoder=h264
--enable-decoder=hevc
--enable-decoder=aac
--enable-decoder=mp3
--enable-decoder=mpeg4
--enable-decoder=mpeg2video
--enable-bsf=h264_mp4toannexb
--enable-bsf=hevc_mp4toannexb
--enable-bsf=aac_adtstoasc
--enable-muxer=mp4
--enable-muxer=mpegts
--disable-avdevice
--disable-postproc
--enable-avfilter
--disable-filter=*
--enable-filter=aresample
--enable-filter=scale
"

build_arch() {
    local arch=$1
    local target=$2
    local cpu=$3
    local toolchain_prefix=$4

    echo "Building FFmpeg for $arch..."

    local prefix="$OUTPUT_DIR/$arch"
    mkdir -p "$prefix"

    local toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
    if [ "$(uname)" = "Darwin" ]; then
        toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
    fi

    export CC="$toolchain/bin/${target}${toolchain_prefix}-clang"
    export CXX="$toolchain/bin/${target}${toolchain_prefix}-clang++"
    export AR="$toolchain/bin/llvm-ar"
    export NM="$toolchain/bin/llvm-nm"
    export RANLIB="$toolchain/bin/llvm-ranlib"
    export STRIP="$toolchain/bin/llvm-strip"
    export CFLAGS="-O2 -fPIC -DANDROID -DNDEBUG"
    export LDFLAGS="-Wl,-soname,libffmpeg.so -Wl,--no-undefined"

    ./configure \
        --prefix="$prefix" \
        --arch="$arch" \
        --target-os=android \
        --cc="$CC" \
        --cxx="$CXX" \
        --ar="$AR" \
        --nm="$NM" \
        --ranlib="$RANLIB" \
        --strip="$STRIP" \
        $FFMPEG_OPTIMIZATIONS \
        --extra-cflags="$CFLAGS" \
        --extra-ldflags="$LDFLAGS" \
        --enable-cross-compile

    make -j$(nproc)
    make install
    make clean

    echo "FFmpeg build complete for $arch"
    ls -la "$prefix/lib/"
}

echo "Building FFmpeg $FFMPEG_VERSION with streaming optimizations"
echo "=============================================="

cd "$(dirname "$0")"

# Download FFmpeg source if needed
if [ ! -f "ffmpeg-$FFMPEG_VERSION.tar.xz" ]; then
    echo "Downloading FFmpeg $FFMPEG_VERSION..."
    curl -L -o "ffmpeg-$FFMPEG_VERSION.tar.xz" \
        "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz"
fi

# Extract
if [ ! -d "ffmpeg-$FFMPEG_VERSION" ]; then
    tar xf "ffmpeg-$FFMPEG_VERSION.tar.xz"
fi

cd "ffmpeg-$FFMPEG_VERSION"

# Apply patches
if [ -d "$PATCHES_DIR" ]; then
    for patch in "$PATCHES_DIR"/*.patch; do
        if [ -f "$patch" ]; then
            echo "Applying patch: $patch"
            git apply "$patch" 2>/dev/null || patch -p1 < "$patch" 2>/dev/null || true
        fi
    done
fi

# Build for all architectures
build_arch "arm64-v8a" "aarch64-linux-android" "armv8-a" "21"
build_arch "armeabi-v7a" "armv7a-linux-androideabi" "armv7-a" "16"
build_arch "x86_64" "x86_64-linux-android" "x86-64" "21"

echo "=============================================="
echo "All FFmpeg builds complete!"
echo "Binaries in: $OUTPUT_DIR"
echo ""
echo "Files to include in the app:"
for arch in arm64-v8a armeabi-v7a x86_64; do
    echo "  $arch:"
    echo "    - $OUTPUT_DIR/$arch/lib/libavcodec.so"
    echo "    - $OUTPUT_DIR/$arch/lib/libavformat.so"
    echo "    - $OUTPUT_DIR/$arch/lib/libavutil.so"
    echo "    - $OUTPUT_DIR/$arch/lib/libswresample.so"
    echo "    - $OUTPUT_DIR/$arch/lib/libavfilter.so"
    echo "    - $OUTPUT_DIR/$arch/lib/libswscale.so"
done
