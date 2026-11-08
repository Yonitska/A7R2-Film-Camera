#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
variant="debug"
if [[ "${1:-}" == "--release" && "$#" == 1 ]]; then variant="release"
elif [[ "$#" != 0 ]]; then echo "Usage: $0 [--release]" >&2; exit 2; fi
if [[ "$variant" == "release" && ! -f out/test.keystore ]]; then
  echo "Release signing identity missing: supply the existing out/test.keystore." >&2; exit 1
fi
mkdir -p out
python3 scripts/test_builtin_catalog.py
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
ndk="${ANDROID_NDK:-}"
if [[ -z "$ndk" ]]; then
  for candidate in "$root/vendor/toolchains/android-ndk-r16b" \
      "$HOME/Android/android-ndk-r16b" /home/topeet/Android/android-ndk-r16b; do
    if [[ -f "$candidate/build/cmake/android.toolchain.cmake" ]]; then ndk="$candidate"; break; fi
  done
fi
if [[ -z "$ndk" || ! -f "$ndk/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK r16b not found; set ANDROID_NDK." >&2
  exit 1
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_home="$JAVA_HOME"
elif [[ -x /usr/libexec/java_home ]]; then
  java_home="$(/usr/libexec/java_home -v 17)"
else
  java_home=""
  for candidate in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk; do
    if [[ -x "$candidate/bin/javac" ]]; then java_home="$candidate"; break; fi
  done
fi
if [[ -z "$java_home" || ! -x "$java_home/bin/javac" ]]; then
  echo "JDK 17 not found; set JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH"
bt="$sdk/build-tools/36.0.0"
jar="$sdk/platforms/android-36/android.jar"
cmake -S vendor/libjpeg-turbo -B out/jpeg-android \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-14 -DANDROID_STL=gnustl_static \
  -DWITH_SIMD=OFF -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DCMAKE_BUILD_TYPE=Release \
  '-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG' \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > out/jpeg-android-config.log 2>&1
cmake --build out/jpeg-android --target jpeg-static -j4 > out/jpeg-android-build.log 2>&1
cmake -S native -B out/android -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-14 -DANDROID_STL=gnustl_static \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > out/android-config.log 2>&1
cmake --build out/android -j4 > out/android-build.log 2>&1
# Some PMCA firmware advertises only armeabi. Build a real ARMv5 fallback,
# rather than relabelling an ARMv7 library as a different ABI.
cmake -S vendor/libjpeg-turbo -B out/jpeg-android-armv5 \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi -DANDROID_PLATFORM=android-14 -DANDROID_STL=gnustl_static \
  -DWITH_SIMD=OFF -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DCMAKE_BUILD_TYPE=Release \
  '-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG' \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > out/jpeg-armv5-config.log 2>&1
cmake --build out/jpeg-android-armv5 --target jpeg-static -j4 > out/jpeg-armv5-build.log 2>&1
cmake -S native -B out/android-armv5 -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi -DANDROID_PLATFORM=android-14 -DANDROID_STL=gnustl_static \
  -DJPEG_BUILD="$root/out/jpeg-android-armv5" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > out/android-armv5-config.log 2>&1
cmake --build out/android-armv5 -j4 > out/android-armv5-build.log 2>&1
rm -rf out/apk/classes out/apk/dex out/apk/res
mkdir -p out/apk/assets out/apk/res/drawable out/apk/classes out/apk/dex out/apk/package/lib/armeabi-v7a out/apk/package/lib/armeabi dist
cp -R android-res/. out/apk/res/
python3 scripts/stage_android.py --variant "$variant"
cp vendor/libjpeg-turbo/LICENSE.md out/apk/assets/JPEG-LICENSE.txt
cp vendor/libjpeg-turbo/README.ijg out/apk/assets/JPEG-IJG.txt
cp "$ndk/sources/cxx-stl/gnu-libstdc++/4.9/NOTICE" out/apk/assets/GNU-STL-NOTICE.txt
python3 - <<'PY'
import hashlib
from pathlib import Path
source=Path('vendor/sony-picture-effect-plus/app/src/main/lib/armeabi/libILDCSC_MUSASHI8Gb24m_PictureEffectPlus_00_01.so')
data=source.read_bytes()
if hashlib.sha256(data).hexdigest()!='843b70399744074e9544d69982d8b37964747f9c93f907389c3c7b9efb759ee0':
    raise SystemExit('Reference memory map differs from reviewed exact-platform asset')
Path('out/apk/assets/MAP24R1.BIN').write_bytes(data)
# User-extracted A7R II 42m map; local-only vendor asset, see SOURCE.md.
source=Path('vendor/sony-a7r2-memmap/MAP42R1.BIN')
data=source.read_bytes()
if hashlib.sha256(data).hexdigest()!='a869d22be91332d494f0fe26f6285f998938f4480bf24efe2db86bb37305a621' or len(data)!=1980:
    raise SystemExit('A7R II 42m memory map differs from the captured firmware blob')
Path('out/apk/assets/MAP42R1.BIN').write_bytes(data)
Path('out/apk/assets/A7R2-MAP.txt').write_text(
    'Sony ILCE-7RM2 42m PictureEffectPlus memory configuration\n'
    'Source: /system/etc/memmap/libILDCSC_MUSASHI8Gb42m_PictureEffectPlus_00_00.so\n'
    'Size: 1980 bytes\nSHA-256: a869d22be91332d494f0fe26f6285f998938f4480bf24efe2db86bb37305a621\n'
    'Extracted from a user-owned camera for this local A7R II port. Sony firmware data; not GPL.\n')
from pathlib import Path
import shutil
# Realistic camera icon: a licensed Unsplash Leica crop, already at mdpi/hdpi.
# See ASSET_LICENSES.md; no third-party launcher artwork is fetched at build time.
mdpi=Path('out/apk/res/drawable-mdpi');hdpi=Path('out/apk/res/drawable-hdpi')
mdpi.mkdir(parents=True,exist_ok=True);hdpi.mkdir(parents=True,exist_ok=True)
shutil.copyfile('android-assets/ICON-72.PNG',mdpi/'icon.png')
shutil.copyfile('android-assets/ICON-144.PNG',hdpi/'icon.png')

PY
"$bt/aapt2" compile --dir out/apk/res -o out/apk/res.zip
"$bt/aapt2" link -o out/apk/unsigned.apk --manifest out/apk/AndroidManifest.xml -I "$jar" \
  --min-sdk-version 10 --target-sdk-version 10 -0 TTF -A out/apk/assets out/apk/res.zip
javac -encoding UTF-8 --release 7 -Xlint:-options -cp "$jar" -d out/apk/classes @out/apk/sources.txt
find out/apk/classes -name '*.class' > out/apk/classes.txt
java -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 10 --lib "$jar" --output out/apk/dex @out/apk/classes.txt
cp out/android/libsonylut.so out/apk/package/lib/armeabi-v7a/
case "$(uname -s)" in
  Darwin) host=darwin-x86_64 ;;
  *) host=linux-x86_64 ;;
esac
stripper="$ndk/toolchains/arm-linux-androideabi-4.9/prebuilt/$host/bin/arm-linux-androideabi-strip"
"$stripper" --strip-unneeded out/apk/package/lib/armeabi-v7a/libsonylut.so
cp out/android-armv5/libsonylut.so out/apk/package/lib/armeabi/
"$stripper" --strip-unneeded out/apk/package/lib/armeabi/libsonylut.so
cp out/android/sony-lut dist/sony-lut-armv7
"$stripper" --strip-unneeded dist/sony-lut-armv7
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('out/apk/unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 z.write('out/apk/dex/classes.dex','classes.dex')
 z.write('out/apk/package/lib/armeabi-v7a/libsonylut.so','lib/armeabi-v7a/libsonylut.so')
 z.write('out/apk/package/lib/armeabi/libsonylut.so','lib/armeabi/libsonylut.so')
PY
"$bt/zipalign" -f 4 out/apk/unsigned.apk out/apk/aligned.apk
if [ ! -f out/test.keystore ]; then
  keytool -genkeypair -keystore out/test.keystore -alias lut-test -keyalg RSA -keysize 2048 \
    -validity 3650 -storepass android -keypass android -dname 'CN=Sony LUT Test'
fi
"$bt/apksigner" sign --ks out/test.keystore --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 10 --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false \
  --out dist/SonyLutTest.apk out/apk/aligned.apk
"$bt/apksigner" verify --min-sdk-version 10 --verbose dist/SonyLutTest.apk
python3 scripts/check_package.py --variant "$variant"
echo "Built dist/SonyLutTest.apk and dist/sony-lut-armv7"
