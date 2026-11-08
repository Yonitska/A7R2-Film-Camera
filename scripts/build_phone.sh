#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"

sdk="${ANDROID_SDK:-}"
if [[ -z "$sdk" ]]; then
  for candidate in "$HOME/Android/Sdk" /home/topeet/Android/Sdk \
      /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    if [[ -x "$candidate/build-tools/36.0.0/aapt2" ]]; then sdk="$candidate"; break; fi
  done
fi
if [[ -z "$sdk" || ! -x "$sdk/build-tools/36.0.0/aapt2" ]]; then
  echo "Android SDK with build-tools 36.0.0 not found; set ANDROID_SDK." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  else
    for candidate in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk; do
      if [[ -x "$candidate/bin/javac" ]]; then JAVA_HOME="$candidate"; break; fi
    done
  fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo "JDK 17 not found; set JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

bt="$sdk/build-tools/36.0.0"
jar="$sdk/platforms/android-36/android.jar"
if [[ ! -f "$jar" ]]; then echo "Missing $jar" >&2; exit 1; fi
if [[ ! -f out/test.keystore ]]; then
  echo "Release signing identity missing: supply the existing out/test.keystore." >&2
  exit 1
fi

rm -rf out/phone/classes out/phone/dex out/phone/assets out/phone/res.zip
mkdir -p out/phone/classes out/phone/dex out/phone/assets out/phone/package dist
{
  find phone/src shared/transfer/src -name '*.java' | sort
} > out/phone/sources.txt
# The phone only needs the wire model, not the camera's HTTP server.
grep -v 'TransferServer.java' out/phone/sources.txt > out/phone/sources.filtered
mv out/phone/sources.filtered out/phone/sources.txt
cp LICENSE THIRD_PARTY_NOTICES.md out/phone/assets/

"$bt/aapt2" compile --dir phone-res -o out/phone/res.zip
"$bt/aapt2" link -o out/phone/unsigned.apk \
  --manifest phone/AndroidManifest.xml -I "$jar" \
  --min-sdk-version 23 --target-sdk-version 28 \
  -A out/phone/assets out/phone/res.zip
javac -encoding UTF-8 --release 7 -Xlint:-options -cp "$jar" \
  -d out/phone/classes @out/phone/sources.txt
find out/phone/classes -name '*.class' > out/phone/classes.txt
java -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 23 --lib "$jar" \
  --output out/phone/dex @out/phone/classes.txt
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('out/phone/unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    z.write('out/phone/dex/classes.dex','classes.dex')
PY
"$bt/zipalign" -f 4 out/phone/unsigned.apk out/phone/package/aligned.apk
"$bt/apksigner" sign --ks out/test.keystore --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true \
  --v3-signing-enabled false --out dist/SonyLutRemote.apk out/phone/package/aligned.apk
"$bt/apksigner" verify --min-sdk-version 23 --verbose dist/SonyLutRemote.apk
python3 scripts/check_phone_package.py
sha256sum dist/SonyLutRemote.apk
echo "Built dist/SonyLutRemote.apk"
