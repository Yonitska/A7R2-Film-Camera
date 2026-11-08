#!/usr/bin/env python3
"""Stage an explicit development or release APK without touching signing keys."""
import argparse
import json
from datetime import datetime
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
from generate_preview_fixture import generate

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / 'local-assets'
ANDROID = '{http://schemas.android.com/apk/res/android}'
PRODUCT_ACTIVITIES = {'.LibraryActivity', '.PhotoActivity', '.GalleryActivity', '.VideoActivity', '.UiMotionDemoActivity'}
PUBLIC_REQUIRED = ['CATALOG.JSN', 'PREVIEW.JPG', 'UIFONT.TTF', 'FONT-OFL.txt', 'FONT-NOTICE.md', 'I18N.TSV']
POSE_FILES = ['POSE.JSN', 'POSE.TTF', 'POSE-NOTICE.md', 'POSE-PROVENANCE.csv', 'POSE-FONT-OFL.txt']


def asset_source(name):
    """Prefer the untracked local overlay for assets whose public
    redistribution is not cleared (sample LUTs, pose pack). A fresh clone has
    only the public android-assets/ tree and builds with an empty LUT catalog."""
    local = LOCAL / 'android-assets' / name
    return local if local.is_file() else ROOT / 'android-assets' / name


def stage(variant):
    out = ROOT / 'out/apk'
    assets = out / 'assets'
    # This is generated staging only, never out/ itself or out/test.keystore.
    if assets.exists():
        shutil.rmtree(assets)
    assets.mkdir(parents=True)
    catalog_path = asset_source('CATALOG.JSN')
    catalog = json.loads(catalog_path.read_text())
    names = [e['asset'] for e in catalog] + PUBLIC_REQUIRED
    for name in names:
        source = asset_source(name)
        if not source.is_file():
            raise SystemExit('missing required asset: %s (public source ships an empty LUT catalog; '
                             'put sample LUTs in local-assets/android-assets/)' % name)
        shutil.copyfile(source, assets / name)
    for name in POSE_FILES:
        source = asset_source(name)
        if source.is_file():
            shutil.copyfile(source, assets / name)
    cameras = assets / 'recipe/cameras'
    cameras.mkdir(parents=True)
    for image in sorted((ROOT / 'android-assets/recipe/cameras').glob('*.jpg')):
        shutil.copyfile(image, cameras / image.name)
    pose_assets = assets / 'POSE'
    pose_assets.mkdir()
    staged_pose = 0
    pose_source = LOCAL / 'POSE'
    if not pose_source.is_dir():
        pose_source = ROOT / 'POSE'
    if pose_source.is_dir():
        # Personal-build fallback: the 176 camera-sized pose diagrams are
        # bundled so the director never falls back to stick figures when the
        # camera's SD listing is unavailable. SD cards still take priority.
        from PIL import Image
        for image in sorted(pose_source.glob('c[0-9][0-9][0-9].jpg')):
            photo = Image.open(image).convert('RGB')
            if photo.size != (190, 320):
                photo = photo.resize((190, 320), Image.LANCZOS)
            photo.save(pose_assets / image.name, 'JPEG', quality=72, optimize=True)
            staged_pose += 1
    (assets / 'POSE-COUNT.TXT').write_text(str(staged_pose), encoding='ascii')
    for name in ['LICENSE', 'THIRD_PARTY_NOTICES.md', 'ASSET_LICENSES.md']:
        shutil.copyfile(ROOT / name, assets / name)
    if variant == 'debug':
        generate(assets / 'native-preview')
        for name in ['ID33.CUB', 'COLOR17.CUB']:
            shutil.copyfile(ROOT / 'out/tests' / name, assets / name)
        shutil.copyfile(ROOT / 'out/tests/IN6000.JPG', assets / 'TEST24.JPG')
    ET.register_namespace('android', ANDROID[1:-1])
    tree = ET.parse(ROOT / 'android/AndroidManifest.xml')
    app = tree.getroot().find('application')
    if variant == 'release':
        app.set(ANDROID + 'debuggable', 'false')
        app.set(ANDROID + 'allowBackup', 'false')
        for activity in list(app.findall('activity')):
            if activity.get(ANDROID + 'name') not in PRODUCT_ACTIVITIES:
                app.remove(activity)
    tree.write(out / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    sources = sorted(ROOT.glob('android/**/*.java'))
    if variant == 'release':
        sources = [p for p in sources if 'diagnostics' not in p.relative_to(ROOT).parts]
    sources.extend(sorted(ROOT.glob('shared/transfer/src/**/*.java')))
    sources = sorted(set(sources))
    now = datetime.now()
    gen = out / 'gen/org/hongen/sonylutprobe'
    gen.mkdir(parents=True, exist_ok=True)
    (gen / 'BuildInfo.java').write_text(
        'package org.hongen.sonylutprobe;\n'
        'final class BuildInfo {\n'
        '    static final String DATE = "%d年%d月%d日";\n'
        '    static final String STAMP = "%04d-%02d-%02d %02d:%02d";\n'
        '}\n' % (now.year, now.month, now.day, now.year, now.month, now.day, now.hour, now.minute),
        encoding='utf-8')
    sources.append(gen / 'BuildInfo.java')
    (out / 'sources.txt').write_text(''.join(str(p.relative_to(ROOT)) + '\n' for p in sources))
    (out / 'variant.json').write_text(json.dumps({'variant': variant, 'source_count': len(sources)}))
    print('Staged', variant, 'APK:', len(sources), 'Java sources;', len(catalog),
          'built-in LUTs;', staged_pose, 'pose images;', 'local overlay' if LOCAL.is_dir() else 'public assets only')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--variant', choices=['debug', 'release'], default='debug')
    stage(parser.parse_args().variant)
