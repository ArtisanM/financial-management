#!/usr/bin/env bash
# 一次性生成 4 套预设图标 × 4 尺寸 = 16 PNG。改了 icons/iconN.* 后重跑。
# 用法: bash deploy/gen-presets.sh
set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/tools/PresetIconGen.java"
WORK="/tmp/preset-icon-gen-$$"

if [[ ! -f "$SRC" ]]; then
  echo "✗ $SRC not found"
  exit 1
fi

mkdir -p "$WORK"
javac -d "$WORK" "$SRC"
java -cp "$WORK" PresetIconGen
rm -rf "$WORK"

echo
ls -la "$ROOT/src/main/resources/static/img/presets/" | head -20
echo
echo "✓ done. Now: git add + mvn package + redeploy."
