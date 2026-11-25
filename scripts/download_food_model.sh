#!/usr/bin/env bash
# Download and verify the on-device Food-101 vision model (Swin Transformer, ONNX).
#
# The ~336 MB model is intentionally NOT stored in git. The download is written to
# a temporary file, checked against the pinned size and SHA-256, then atomically
# moved into place. A partial or corrupt download can therefore never be used by
# a later build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/swin_food101_fp32.onnx"
URL="https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/e5e50bfc6425aa546f3b4421ca8bd79d0dd610b8/onnx/model.onnx"
EXPECTED_SIZE="352389545"
EXPECTED_SHA256="f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "A SHA-256 utility (sha256sum or shasum) is required." >&2
    return 1
  fi
}

verify_model() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  local size
  size="$(stat -c '%s' "$file" 2>/dev/null || stat -f '%z' "$file")"
  [[ "$size" == "$EXPECTED_SIZE" ]] || return 1
  [[ "$(sha256_of "$file")" == "$EXPECTED_SHA256" ]] || return 1
  return 0
}

mkdir -p "$(dirname "$DEST")"

if verify_model "$DEST"; then
  echo "Model already present and verified: $DEST"
  exit 0
fi

if [[ -e "$DEST" ]]; then
  echo "Existing model is invalid or incomplete; downloading a clean copy."
  rm -f "$DEST"
fi

TEMP="$DEST.part.$$"
trap 'rm -f "$TEMP"' EXIT
echo "Downloading Food-101 ONNX model (~336 MB)..."
if command -v curl >/dev/null 2>&1; then
  curl -L --fail --retry 5 --retry-all-errors --retry-delay 2 \
    --continue-at - "$URL" -o "$TEMP"
else
  wget --tries=5 --continue -O "$TEMP" "$URL"
fi

if ! verify_model "$TEMP"; then
  actual_size="$(stat -c '%s' "$TEMP" 2>/dev/null || stat -f '%z' "$TEMP" || echo unknown)"
  echo "Downloaded model failed integrity verification (size: $actual_size)." >&2
  exit 1
fi

mv -f "$TEMP" "$DEST"
trap - EXIT
echo "Saved and verified: $DEST"
