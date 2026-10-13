#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE="$ROOT/ttfx-android/rust"
OUT="$ROOT/ttfx-android/src/main/jniLibs/arm64-v8a"
NDK_VERSION="$(sed -n 's/^NDK_VERSION=//p' "$ROOT/scripts/fetch-proot.sh")"
SDK="${ANDROID_HOME:-}"
if [[ -z "$SDK" && -f "$ROOT/local.properties" ]]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties")"
fi
if [[ -z "$SDK" ]]; then
  echo "Set ANDROID_HOME or sdk.dir in local.properties to build ttfx" >&2
  exit 1
fi
if [[ -z "$NDK_VERSION" ]]; then
  echo "Could not read NDK_VERSION from scripts/fetch-proot.sh" >&2
  exit 1
fi
NDK="$SDK/ndk/$NDK_VERSION"

if ! rustup target list --installed | rg -qx 'aarch64-linux-android'; then
  echo "Rust target aarch64-linux-android is not installed; run: rustup target add aarch64-linux-android" >&2
  exit 1
fi

if [[ ! -d "$NDK" ]]; then
  echo "Android NDK $NDK_VERSION not found at $NDK" >&2
  exit 1
fi

case "$(uname -s)" in
  Linux) HOST_TAG=linux-x86_64 ;;
  Darwin) HOST_TAG=darwin-x86_64 ;;
  *)
    echo "Unsupported build host: $(uname -s)" >&2
    exit 1
    ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
LINKER="$TOOLCHAIN/aarch64-linux-android29-clang"
if [[ ! -x "$LINKER" ]]; then
  echo "Android linker not found at $LINKER" >&2
  exit 1
fi

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$LINKER"
export CC_aarch64_linux_android="$LINKER"
export AR_aarch64_linux_android="$TOOLCHAIN/llvm-ar"

cargo build --release --locked --target aarch64-linux-android --manifest-path "$CRATE/Cargo.toml"
mkdir -p "$OUT"
cp "$CRATE/target/aarch64-linux-android/release/libttfx_android.so" "$OUT/libttfx_android.so"
ls -lh "$OUT/libttfx_android.so"
