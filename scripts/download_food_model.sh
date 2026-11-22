#!/usr/bin/env bash
# Download the on-device Food-101 vision model (Swin Transformer, ONNX).
#
# The 336 MB model is intentionally NOT stored in git. Run this script once
# before building, or download the file manually and put it here:
#   app/src/main/assets/swin_food101_fp32.onnx
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/swin_food101_fp32.onnx"
URL="https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx"

if [[ -f "$DEST" ]]; then
  echo "Model already present: $DEST"
  exit 0
fi

mkdir -p "$(dirname "$DEST")"
echo "Downloading Food-101 ONNX model (~336 MB)..."
if command -v curl >/dev/null 2>&1; then
  curl -L --fail --progress-bar "$URL" -o "$DEST"
else
  wget -O "$DEST" "$URL"
fi

echo "Saved to $DEST"
