#!/bin/bash
# Build the ACP bridge as a standalone binary.
# Requires Bun to be installed.
set -e

cd "$(dirname "$0")"

OS="$(uname -s)"
case "$OS" in
  Linux*)   EXT=""; PLATFORM_FLAGS=() ;;
  Darwin*)  EXT=""; PLATFORM_FLAGS=() ;;
  MINGW*|MSYS*|CYGWIN*)
            EXT=".exe"
            PLATFORM_FLAGS=(--windows-hide-console --windows-title="Vibe Manager ACP Bridge") ;;
  *)        EXT=""; PLATFORM_FLAGS=() ;;
esac

OUT="dist/acp-bridge${EXT}"

echo "Installing dependencies..."
bun install

echo "Building standalone binary for ${OS}..."
bun build --compile \
  --minify \
  "${PLATFORM_FLAGS[@]}" \
  --outfile "${OUT}" \
  entry.ts

echo "Done! Binary at: ${OUT}"
ls -lh "${OUT}"
