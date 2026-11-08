#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
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
mkdir -p out/product-ui-tests
"$java_home/bin/javac" -encoding UTF-8 -d out/product-ui-tests \
  android/storage/LutLibrary.java android/storage/PhotoCatalog.java android/capture/PreJpegGate.java android/capture/CaptureProfile.java android/capture/ControlMath.java android/capture/A7R2Keys.java android/capture/WhiteBalanceNames.java android/capture/ManualExposure.java android/capture/VideoExposure.java android/capture/AfPointController.java android/capture/PreviewColorAudit.java android/ui/ReviewGeometry.java android/capture/SimpleExposure.java android/ui/PreviewCache.java android/ui/DevelopTimeline.java android/ui/UiModels.java android/capture/CaptureReadout.java android/preview/NativePreviewProfile.java android/preview/NativePreviewSession.java android/platform/NativeExitGate.java android/preview/PreviewCaptureBarrier.java android/preview/AutoPreviewGate.java android/preview/PreviewProfileStore.java android/ui/PhotoTiles.java android/recipe/RecipeData.java android/recipe/RecipeCameraLook.java android/i18n/TraditionalChinese.java \
  android-tests/I18nTest.java android-tests/BuiltinRetirementTest.java android-tests/ProductStorageTest.java android-tests/PreJpegGateTest.java android-tests/ReviewGeometryTest.java android-tests/SimpleExposureTest.java android-tests/PreviewCacheTest.java android-tests/DevelopTimelineTest.java android-tests/UiModelsTest.java android-tests/CaptureReadoutTest.java android-tests/NativePreviewTest.java android-tests/NativeExitGateTest.java android-tests/PreviewCaptureBarrierTest.java android-tests/AutoPreviewGateTest.java android-tests/PreviewProfileStoreTest.java android-tests/CaptureProfileTest.java android-tests/ManualExposureTest.java android-tests/VideoExposureTest.java android-tests/PreviewColorAuditTest.java android-tests/PhotoTilesTest.java android-tests/RecipeDataTest.java android-tests/RecipeCameraLookTest.java
for suite in I18nTest CaptureProfileTest ManualExposureTest VideoExposureTest PreviewColorAuditTest BuiltinRetirementTest PreviewProfileStoreTest AutoPreviewGateTest PreviewCaptureBarrierTest NativeExitGateTest DevelopTimelineTest ProductStorageTest PreJpegGateTest ReviewGeometryTest SimpleExposureTest PreviewCacheTest UiModelsTest CaptureReadoutTest PhotoTilesTest RecipeDataTest RecipeCameraLookTest; do
  "$java_home/bin/java" -cp out/product-ui-tests "org.hongen.sonylutprobe.$suite"
done
python3 scripts/generate_preview_fixture.py
"$java_home/bin/java" -cp out/product-ui-tests org.hongen.sonylutprobe.NativePreviewTest out/tests/native-preview/IDENT.PRV
