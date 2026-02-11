#!/usr/bin/env bash
set -euo pipefail

# Build QEMU for Android (arm64-v8a) using the Android NDK.
# Usage:
#   ANDROID_NDK_ROOT=/path/to/ndk \
#   ANDROID_API=26 \
#   ./scripts/build-qemu-android.sh /path/to/qemu-src

QEMU_SRC=${1:-}
if [[ -z "$QEMU_SRC" ]]; then
  echo "Usage: ANDROID_NDK_ROOT=... ANDROID_API=26 ./scripts/build-qemu-android.sh /path/to/qemu-src"
  exit 1
fi

: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to your NDK path}"
: "${ANDROID_API:=26}"

HOST_TAG=$(uname -s | tr '[:upper:]' '[:lower:]')
if [[ "$HOST_TAG" == "darwin" ]]; then
  HOST_TAG="darwin-x86_64"
else
  HOST_TAG="linux-x86_64"
fi

TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"

export CC="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang"
export CXX="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export STRIP="$TOOLCHAIN/bin/llvm-strip"

PREFIX="$(pwd)/out-android-arm64"

mkdir -p "$PREFIX"
cd "$QEMU_SRC"

# Minimal features for headless + VNC
./configure \
  --target-list=aarch64-softmmu \
  --prefix="$PREFIX" \
  --disable-werror \
  --disable-sdl \
  --disable-gtk \
  --disable-opengl \
  --disable-virglrenderer \
  --disable-curses \
  --disable-spice \
  --disable-usb-redir \
  --disable-libusb \
  --disable-docs

make -j"$(nproc)"
make install

BIN="$PREFIX/bin/qemu-system-aarch64"
if [[ -f "$BIN" ]]; then
  "$STRIP" "$BIN" || true
  echo "Built: $BIN"
  echo "Copy to: android-vm-app/app/src/main/assets/qemu-system-aarch64"
else
  echo "Build succeeded but binary not found at $BIN"
  exit 1
fi
