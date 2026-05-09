#!/usr/bin/env bash
# 一次性生成 PWA / iOS 主屏图标 PNG(改了 SVG 后重新跑)
# 用法: bash deploy/gen-icons.sh
set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/src/main/resources/static/img/apple-touch-icon.svg"
DIR="$ROOT/src/main/resources/static/img"

if ! command -v rsvg-convert &> /dev/null; then
  echo "✗ rsvg-convert not found. Install:  sudo apt install -y librsvg2-bin"
  exit 1
fi

if [[ ! -f "$SRC" ]]; then
  echo "✗ Source not found: $SRC"
  exit 1
fi

rsvg-convert -w 180 -h 180 "$SRC" -o "$DIR/apple-touch-icon-180.png"
rsvg-convert -w 192 -h 192 "$SRC" -o "$DIR/icon-192.png"
rsvg-convert -w 512 -h 512 "$SRC" -o "$DIR/icon-512.png"

echo "✓ 3 PNGs generated. Now: git add + commit + redeploy."
ls -la "$DIR"/{apple-touch-icon-180,icon-192,icon-512}.png
