#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HEV_DIR="$ROOT/third_party/hev-socks5-tunnel"
JNI_LIBS="$ROOT/app/src/main/jniLibs"
PKGNAME="com/smartxray/client/data/xray"
CLSNAME="HevTunnelBridge"

if [[ -z "${NDK_HOME:-}" ]]; then
  if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    NDK_HOME="$ANDROID_NDK_HOME"
  elif [[ -d "$HOME/Android/Sdk/ndk" ]]; then
    NDK_HOME="$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1)"
  fi
fi

if [[ -z "${NDK_HOME:-}" || ! -x "$NDK_HOME/ndk-build" ]]; then
  echo "Android NDK not found. Set NDK_HOME or ANDROID_NDK_HOME." >&2
  exit 1
fi

if [[ ! -d "$HEV_DIR" ]]; then
  git clone --recursive --depth 1 https://github.com/heiher/hev-socks5-tunnel.git "$HEV_DIR"
else
  git -C "$HEV_DIR" submodule update --init --recursive
fi

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

mkdir -p "$TMPDIR/jni"
echo 'include $(call all-subdir-makefiles)' > "$TMPDIR/jni/Android.mk"
ln -s "$HEV_DIR" "$TMPDIR/jni/hev-socks5-tunnel"

pushd "$TMPDIR" >/dev/null
"$NDK_HOME/ndk-build" \
  NDK_PROJECT_PATH=. \
  APP_BUILD_SCRIPT=jni/Android.mk \
  APP_ABI="armeabi-v7a arm64-v8a x86 x86_64" \
  APP_PLATFORM=android-26 \
  NDK_LIBS_OUT="$TMPDIR/libs" \
  NDK_OUT="$TMPDIR/obj" \
  APP_CFLAGS="-O3 -DANDROID -DPKGNAME=$PKGNAME -DCLSNAME=$CLSNAME" \
  APP_LDFLAGS="-Wl,--build-id=none -Wl,--hash-style=gnu"
popd >/dev/null

for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  if [[ -f "$TMPDIR/libs/$abi/libhev-socks5-tunnel.so" ]]; then
    mkdir -p "$JNI_LIBS/$abi"
    cp "$TMPDIR/libs/$abi/libhev-socks5-tunnel.so" "$JNI_LIBS/$abi/"
    echo "Installed $abi"
  fi
done

echo "Done. libhev-socks5-tunnel.so installed to app/src/main/jniLibs/"
