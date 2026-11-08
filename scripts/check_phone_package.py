#!/usr/bin/env python3
"""Inspect the phone companion APK without a device."""
import argparse, datetime, hashlib, json, os, pathlib, re, subprocess, zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--apk", default="dist/SonyLutRemote.apk")
args = parser.parse_args()

root = pathlib.Path(__file__).resolve().parents[1]
apk = pathlib.Path(args.apk)
if not apk.is_absolute():
    apk = root / apk
sdk = pathlib.Path(os.environ["ANDROID_SDK"]) if os.environ.get("ANDROID_SDK") else pathlib.Path()
if not (sdk / "build-tools/36.0.0/aapt2").is_file():
    for candidate in (pathlib.Path.home() / "Android/Sdk", pathlib.Path("/home/topeet/Android/Sdk"),
                      pathlib.Path("/opt/homebrew/share/android-commandlinetools"),
                      pathlib.Path.home() / "Library/Android/sdk"):
        if (candidate / "build-tools/36.0.0/aapt2").is_file():
            sdk = candidate
            break
aapt2 = sdk / "build-tools/36.0.0/aapt2"
apksigner = sdk / "build-tools/36.0.0/apksigner"
assert aapt2.is_file(), "android build-tools 36.0.0 not found"
assert apk.is_file(), str(apk)

with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    names = set(z.namelist())
    dex = z.read("classes.dex")
    assert dex[:8] == b"dex\n035\0", dex[:8]
    for forbidden in (b"org/hongen/sonylutprobe", b"com/sony/scalar",
                      b"com/sony/", b"Lorg/hongen/sonylut/transfer/TransferServer;"):
        assert forbidden not in dex, "unexpected camera code in phone APK: %r" % forbidden
    for required in (b"Lorg/hongen/sonylutremote/MainActivity;",
                     b"Lorg/hongen/sonylutremote/SyncService;",
                     b"Lorg/hongen/sonylutremote/CameraClient;",
                     b"Lorg/hongen/sonylutremote/LedgerStore;",
                     b"Lorg/hongen/sonylutremote/MediaSaverApi29;",
                     b"Lorg/hongen/sonylutremote/WifiSpecifierConnector;",
                     b"Lorg/hongen/sonylut/transfer/TransferProtocol;"):
        assert required in dex, required
    assert not any(name.startswith("lib/") for name in names), "phone app must be pure Java"
    assert z.read("assets/LICENSE") == (root / "LICENSE").read_bytes()
    assert z.read("assets/THIRD_PARTY_NOTICES.md") == (root / "THIRD_PARTY_NOTICES.md").read_bytes()

badging = subprocess.check_output([str(aapt2), "dump", "badging", str(apk)], text=True)
assert re.search(r"package: name='org\.hongen\.sonylutremote' versionCode='1' versionName='1\.0'", badging), badging[:400]
assert re.search(r"minSdkVersion:'23'", badging), "minSdk must be 23"
assert re.search(r"targetSdkVersion:'28'", badging), "targetSdk must be 28"
assert "launchable-activity: name='org.hongen.sonylutremote.MainActivity'" in badging
for permission in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                   "android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_STATE",
                   "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                   "android.permission.FOREGROUND_SERVICE", "android.permission.POST_NOTIFICATIONS"):
    assert "name='%s'" % permission in badging, permission
assert re.search(r"name='android\.permission\.WRITE_EXTERNAL_STORAGE' maxSdkVersion='28'", badging), \
    "legacy storage permission must stop at API 28"

manifest = subprocess.check_output(
    [str(aapt2), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
assert re.search(r"usesCleartextTraffic[^\n]*=true", manifest), "cleartext traffic must be enabled"
assert re.search(r'name[^\n]*="\.SyncService"', manifest)
assert re.search(r'name[^\n]*="\.MainActivity"', manifest)
activity_block = manifest[manifest.index("E: activity"): manifest.index("E: service")]
assert re.search(r"exported[^\n]*=true", activity_block), "launcher activity must be exported"
service_block = manifest[manifest.index("E: service"):]
assert re.search(r'name[^\n]*="\.SyncService"', service_block)
assert re.search(r"exported[^\n]*=false", service_block), "service must not be exported"
assert re.search(r"stopWithTask[^\n]*=true", service_block)

verify = subprocess.check_output([str(apksigner), "verify", "--min-sdk-version", "23",
                                  "--verbose", str(apk)], text=True,
                                 stderr=subprocess.STDOUT)
assert "Verifies" in verify, verify

report = {
    "checked_at": datetime.datetime.now().astimezone().isoformat(),
    "apk": apk.name,
    "apk_bytes": apk.stat().st_size,
    "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "package": "org.hongen.sonylutremote",
    "versionName": "1.0",
    "versionCode": 1,
    "minSdk": 23,
    "targetSdk": 28,
    "signature": "v1+v2 test key",
    "badging": badging,
}
(root / "dist/PHONE-BUILDINFO.json").write_text(json.dumps(report, indent=2))
print("PASS phone APK: minSdk23/targetSdk28, pure Java, cleartext, service, permissions, licenses, signature")
print("Phone APK bytes:", report["apk_bytes"], "SHA256:", report["apk_sha256"])
