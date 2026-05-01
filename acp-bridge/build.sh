#!/bin/bash
# Build the ACP bridge as a standalone binary.
# Requires Bun to be installed.
set -e

cd "$(dirname "$0")"

echo "Installing dependencies..."
bun install

echo "Building standalone binary..."
bun build --compile \
  --minify \
  --windows-hide-console \
  --windows-title="Vibe Manager ACP Bridge" \
  --outfile dist/acp-bridge.exe \
  entry.ts

echo "Done! Binary at: dist/acp-bridge.exe"
ls -lh dist/acp-bridge.exe
