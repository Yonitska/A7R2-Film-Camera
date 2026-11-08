#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/transfer-tests
javac -encoding UTF-8 --release 7 -Xlint:-options -d out/transfer-tests \
  $(find shared/transfer/src -name '*.java') \
  android/storage/LutLibrary.java android/storage/PhotoCatalog.java \
  android-tests/TransferTest.java
java -cp out/transfer-tests org.hongen.sonylutprobe.TransferTest
