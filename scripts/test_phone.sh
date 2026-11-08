#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf out/phone-tests
mkdir -p out/phone-tests
javac -encoding UTF-8 --release 7 -Xlint:-options -d out/phone-tests \
  shared/transfer/src/org/hongen/sonylut/transfer/MediaEntry.java \
  shared/transfer/src/org/hongen/sonylut/transfer/MediaManifest.java \
  shared/transfer/src/org/hongen/sonylut/transfer/ManifestCodec.java \
  shared/transfer/src/org/hongen/sonylut/transfer/TransferProtocol.java \
  shared/transfer/src/org/hongen/sonylut/transfer/HttpDate.java \
  shared/transfer/src/org/hongen/sonylut/transfer/RangeSpec.java \
  shared/transfer/src/org/hongen/sonylut/transfer/TransferServer.java \
  phone/src/org/hongen/sonylutremote/LedgerStore.java \
  phone/src/org/hongen/sonylutremote/CameraClient.java \
  phone/src/org/hongen/sonylutremote/SyncStatus.java \
  phone-tests/PhoneSyncTest.java
java -cp out/phone-tests org.hongen.sonylutremote.PhoneSyncTest
