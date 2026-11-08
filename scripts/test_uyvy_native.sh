#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
cxx="${CXX:-c++}"
"$cxx" -O2 -std=c++11 -pthread -Inative native/core.cpp native/uyvy.cpp native/uyvy_tests.cpp -o out/uyvy-tests
out/uyvy-tests
