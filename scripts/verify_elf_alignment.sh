#!/usr/bin/env bash
# Verify that every ELF LOAD segment in an APK is aligned for 16 KiB pages,
# including the GNU_RELRO page-boundary safety condition.
#
# Usage: scripts/verify_elf_alignment.sh path/to/app.apk
set -euo pipefail
export LC_ALL=C

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <apk>" >&2
  exit 2
fi

apk="$1"
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi

if command -v readelf >/dev/null 2>&1; then
  readelf_bin="$(command -v readelf)"
elif command -v llvm-readelf >/dev/null 2>&1; then
  readelf_bin="$(command -v llvm-readelf)"
else
  echo "readelf or llvm-readelf is required" >&2
  exit 1
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/progress1-elf.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

mkdir -p "$work_dir/libs"
unzip -q "$apk" 'lib/*/*.so' -d "$work_dir/libs"

library_count="$(find "$work_dir/libs" -type f -name '*.so' | wc -l | tr -d ' ')"
if [[ "$library_count" == "0" ]]; then
  echo "APK contains no native libraries to verify: $apk" >&2
  exit 1
fi

failed=0
while IFS= read -r -d '' library; do
  relative="${library#"$work_dir/libs/"}"
  headers="$("$readelf_bin" -lW "$library")"
  segment_count=0
  relro_count=0
  min_alignment=0

  # readelf may print flags as either "RW" or "R E".  Normalize them while
  # retaining offset, virtual address, file size, memory size and alignment.
  load_rows="$(
    awk '
      $1 == "LOAD" {
        flags = $(NF - 1)
        if ($(NF - 2) ~ /^[RWE]+$/ && $(NF - 1) ~ /^[RWE]+$/) flags = $(NF - 2) $(NF - 1)
        gsub(/[[:space:]]/, "", flags)
        print $2 "|" $5 "|" $3 "|" $6 "|" flags "|" $NF
      }
    ' <<<"$headers"
  )"

  while IFS='|' read -r file_offset file_size virt_addr mem_size flags alignment; do
    [[ -n "$virt_addr" ]] || continue
    segment_count=$((segment_count + 1))
    if ! [[ "$alignment" =~ ^0x[0-9a-fA-F]+$ ]]; then
      echo "ERROR: $relative has invalid LOAD alignment '$alignment'" >&2
      failed=1
      continue
    fi
    if (( alignment < 0x4000 )); then
      echo "ERROR: $relative has LOAD alignment $alignment; 0x4000 is required" >&2
      failed=1
    fi
    if (( (alignment & (alignment - 1)) != 0 )); then
      echo "ERROR: $relative has non-power-of-two LOAD alignment $alignment" >&2
      failed=1
    fi
    if (( alignment % 0x4000 != 0 )); then
      echo "ERROR: $relative has LOAD alignment $alignment which is not a multiple of 0x4000" >&2
      failed=1
    fi
    if [[ "$file_offset" =~ ^0x[0-9a-fA-F]+$ && "$virt_addr" =~ ^0x[0-9a-fA-F]+$ ]]; then
      load_offset_value=$((file_offset))
      load_vaddr_value=$((virt_addr))
      if (( load_vaddr_value % alignment != load_offset_value % alignment )); then
        echo "ERROR: $relative has inconsistent LOAD offset/address for alignment $alignment" >&2
        failed=1
      fi
    else
      echo "ERROR: $relative has invalid LOAD offset or virtual address" >&2
      failed=1
    fi
    if (( min_alignment == 0 || alignment < min_alignment )); then
      min_alignment=$alignment
    fi
  done <<<"$load_rows"

  if [[ "$segment_count" == "0" ]]; then
    echo "ERROR: $relative contains no ELF LOAD segment" >&2
    failed=1
  fi

  relro_rows="$(awk '$1 == "GNU_RELRO" { print $2 "|" $5 "|" $3 "|" $6 }' <<<"$headers")"
  relro_safe=true
  relro_status="no GNU_RELRO"
  while IFS='|' read -r file_offset file_size virt_addr mem_size; do
    [[ -n "$virt_addr" ]] || continue
    relro_count=$((relro_count + 1))
    if [[ ! "$file_offset" =~ ^0x[0-9a-fA-F]+$ ||
          ! "$file_size" =~ ^0x[0-9a-fA-F]+$ ||
          ! "$virt_addr" =~ ^0x[0-9a-fA-F]+$ ||
          ! "$mem_size" =~ ^0x[0-9a-fA-F]+$ ]]; then
      echo "ERROR: $relative has invalid GNU_RELRO address or size" >&2
      failed=1
      relro_safe=false
      relro_status="invalid GNU_RELRO"
      continue
    fi

    relro_start=$((virt_addr))
    relro_end=$((virt_addr + mem_size))
    if (( relro_end % 0x4000 == 0 )); then
      # Official condition 2: the protected range ends on a page boundary.
      continue
    fi

    # Official condition 3: GNU_RELRO occupies the entire remainder of its
    # containing LOAD segment, so no writable data follows it on that page.
    found_safe_load=false
    while IFS='|' read -r load_offset load_size load_vaddr load_mem_size flags alignment; do
      [[ -n "$load_vaddr" ]] || continue
      [[ "$load_offset" =~ ^0x[0-9a-fA-F]+$ ]] || continue
      [[ "$load_size" =~ ^0x[0-9a-fA-F]+$ ]] || continue
      [[ "$load_vaddr" =~ ^0x[0-9a-fA-F]+$ ]] || continue
      [[ "$load_mem_size" =~ ^0x[0-9a-fA-F]+$ ]] || continue
      load_start_value=$((load_vaddr))
      load_end_value=$((load_vaddr + load_mem_size))
      if (( relro_start >= load_start_value &&
            relro_end <= load_end_value &&
            load_end_value == relro_end )); then
        found_safe_load=true
        break
      fi
    done <<<"$load_rows"

    if [[ "$found_safe_load" != "true" ]]; then
      echo "ERROR: $relative has writable data after GNU_RELRO on the same 16 KiB page" >&2
      failed=1
      relro_safe=false
      relro_status="unsafe GNU_RELRO boundary"
    fi
  done <<<"$relro_rows"

  if [[ "$relro_count" -gt 0 && "$relro_safe" == "true" ]]; then
    if [[ "$relro_status" == "no GNU_RELRO" || "$relro_status" == "invalid GNU_RELRO" ]]; then
      relro_status="GNU_RELRO has a safe 16 KiB boundary"
    fi
  fi

  printf -v min_alignment_text '0x%x' "$min_alignment"
  echo "$relative: $segment_count LOAD segment(s), minimum alignment $min_alignment_text; $relro_status"
done < <(find "$work_dir/libs" -type f -name '*.so' -print0)

if [[ "$failed" -ne 0 ]]; then
  echo "ELF 16 KiB alignment verification failed for $apk" >&2
  exit 1
fi

echo "ELF LOAD and GNU_RELRO 16 KiB alignment verified for $library_count native libraries in $apk"
