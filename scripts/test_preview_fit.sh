#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
python="${PYTHON:-python3}"
compiler="${CXX:-}"
if [[ -z "$compiler" ]]; then
  if command -v clang++ >/dev/null 2>&1; then compiler=clang++
  elif command -v g++ >/dev/null 2>&1; then compiler=g++
  else echo "C++ compiler not found; set CXX." >&2; exit 1; fi
fi
"$compiler" -O3 -std=c++11 -pthread -Inative native/core.cpp native/preview_fit.cpp native/preview_fit_cli.cpp -o out/preview-fit-host
"$python" scripts/test_preview_fit.py out/preview-fit-host
