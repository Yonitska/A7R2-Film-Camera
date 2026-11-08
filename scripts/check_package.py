#!/usr/bin/env python3
"""Inspect the offline artifacts; no camera connection."""
import argparse,datetime,hashlib,json,os,pathlib,re,subprocess,zipfile
from audit_release_assets import audit_apk
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--variant", choices=["debug","release"], default="debug")
variant=parser.parse_args().variant
root=pathlib.Path(__file__).resolve().parents[1]
ndk=pathlib.Path(os.environ['ANDROID_NDK']) if os.environ.get('ANDROID_NDK') else pathlib.Path()
if not (ndk/'build/cmake/android.toolchain.cmake').is_file():
    for candidate in (root/'vendor/toolchains/android-ndk-r16b',
                      pathlib.Path.home()/'Android/android-ndk-r16b',
                      pathlib.Path('/home/topeet/Android/android-ndk-r16b')):
        if (candidate/'build/cmake/android.toolchain.cmake').is_file():
            ndk=candidate
            break
assert (ndk/'build/cmake/android.toolchain.cmake').is_file(),'Android NDK r16b not found; set ANDROID_NDK'
sdk=pathlib.Path(os.environ['ANDROID_SDK']) if os.environ.get('ANDROID_SDK') else pathlib.Path()
if not (sdk/'build-tools/36.0.0/aapt2').is_file():
    for candidate in (pathlib.Path.home()/'Android/Sdk',pathlib.Path('/home/topeet/Android/Sdk'),
                      pathlib.Path('/opt/homebrew/share/android-commandlinetools'),
                      pathlib.Path.home()/'Library/Android/sdk'):
        if (candidate/'build-tools/36.0.0/aapt2').is_file():
            sdk=candidate
            break
assert (sdk/'build-tools/36.0.0/aapt2').is_file(),'android build-tools 36.0.0 not found'
readelf=ndk/('toolchains/arm-linux-androideabi-4.9/prebuilt/' + ('darwin-x86_64' if os.uname().sysname == 'Darwin' else 'linux-x86_64') + '/bin/arm-linux-androideabi-readelf')
apk=root/'dist/SonyLutTest.apk'
def elf(args):return subprocess.check_output([str(readelf)]+[str(x) for x in args],text=True)
report={'checked_at':datetime.datetime.now().astimezone().isoformat(),
        'variant':variant,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'apk_bytes':apk.stat().st_size,'native':{}}
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    dex=z.read('classes.dex')
    assert dex[:8]==b'dex\n035\0'
    for required in (b'Lorg/hongen/sonylutprobe/TransferService;',
                     b'Lorg/hongen/sonylutprobe/TransferRuntime;',
                     b'Lorg/hongen/sonylutprobe/PhotoTransferSource;',
                     b'Lorg/hongen/sonylutprobe/FaceDetectionController;',
                     b'Lorg/hongen/sonylut/transfer/TransferServer;'):
        assert required in dex, 'missing product class: %r' % required
    catalog=json.loads(z.read('assets/CATALOG.JSN'))
    if catalog:
        assert sum(item.get('default',False) for item in catalog)==1
    for item in catalog:
        assert re.fullmatch(r'[A-Z0-9_]{1,8}\.CUB',item['asset'])
        assert hashlib.sha256(z.read('assets/'+item['asset'])).hexdigest()==item['sha256']
        assert 2<=item['grid']<=65
    assert len({item['asset'] for item in catalog})==len(catalog)
    maps={'MAP24R1.BIN':(1860,'843b70399744074e9544d69982d8b37964747f9c93f907389c3c7b9efb759ee0'),
          'MAP42R1.BIN':(1980,'a869d22be91332d494f0fe26f6285f998938f4480bf24efe2db86bb37305a621')}
    for name,(size,digest) in maps.items():
        data=z.read('assets/'+name)
        assert len(data)==size and hashlib.sha256(data).hexdigest()==digest, name
    assert 'assets/A7R2-MAP.txt' in z.namelist()
    pose_assets={n for n in z.namelist() if n.startswith('assets/POSE/')}
    pose_count=int(z.read('assets/POSE-COUNT.TXT'))
    assert pose_count==len(pose_assets), (pose_count,len(pose_assets))
    assert pose_count in (0,176), pose_count
    if pose_count:
        assert pose_assets=={'assets/POSE/c%03d.jpg'%i for i in range(1,177)}, sorted(pose_assets)[:3]
    font=json.loads((root/'android-assets/FONTINFO.json').read_text())
    assert hashlib.sha256(z.read('assets/UIFONT.TTF')).hexdigest()==font['sha256']
    assert 'assets/FONT-OFL.txt' in z.namelist()
    assert 'assets/FONT-NOTICE.md' in z.namelist()
    i18n=z.read('assets/I18N.TSV')
    assert i18n==(root/'android-assets/I18N.TSV').read_bytes()
    rows=[line for line in i18n.decode('utf-8').splitlines()
          if line and not line.startswith('#')]
    assert rows and all(line.count('\t')==4 for line in rows), 'I18N.TSV must have 5 columns'
    assert len(rows)>=700, 'I18N.TSV unexpectedly small'
    assert z.read('assets/PREVIEW.JPG') == (root/'android-assets/PREVIEW.JPG').read_bytes()
    camera_cards={n for n in z.namelist() if n.startswith('assets/recipe/cameras/')}
    assert len(camera_cards)==12 and all(n.endswith('.jpg') for n in camera_cards),sorted(camera_cards)
    assert {n.rsplit('/',1)[1] for n in camera_cards}=={
        'sony.jpg','fuji-sim.jpg','fuji-film.jpg','kodak.jpg','cine.jpg','ricoh-gr.jpg',
        'leica.jpg','hasselblad.jpg','canon-nikon.jpg','pana-olympus.jpg','other-stocks.jpg','ilford.jpg'}

    for notice in ['LICENSE','THIRD_PARTY_NOTICES.md','ASSET_LICENSES.md']:
        assert z.read('assets/'+notice)==(root/notice).read_bytes()
    if variant=='release':
        allowed_cubes={'assets/'+e['asset'] for e in catalog}
        assert {n for n in z.namelist() if n.endswith('.CUB')}==allowed_cubes
        assert not any(n.startswith('assets/native-preview/') or n.endswith('TEST24.JPG') for n in z.namelist())
        assert b'GraphicsDepthProbeActivity' not in z.read('classes.dex')
        assert b'PhotoActivity' in z.read('classes.dex') and b'PreJpegProbeActivity' in z.read('classes.dex')
    report['dex']='035'
    for abi,folder in [('armeabi','android-armv5'),('armeabi-v7a','android')]:
        data=z.read('lib/'+abi+'/libsonylut.so')
        assert data[:5]==b'\x7fELF\x01' and data[18:20]==b'\x28\0'
        library=root/'out'/folder/'libsonylut.so'
        attr=elf(['-A',library]);assert 'Advanced_SIMD' not in attr
        objects=[root/'out'/folder/'CMakeFiles/sonylut.dir'/name for name in ['core.cpp.o','jpeg_io.cpp.o','jni.cpp.o']]
        objattr=elf(['-A']+objects)
        expected='v5TE' if abi=='armeabi' else 'v7'
        assert re.findall(r'Tag_CPU_arch: (\S+)',objattr)==[expected]*3
        assert 'Advanced_SIMD' not in objattr
        report['native'][abi]={'bytes':len(data),'source_object_arch':expected,
            'linked_attributes':attr,'needed':re.findall(r'Shared library: \[(.*?)\]',elf(['-d',library]))}
    # OpenMemories Tweak prebuilts must be in the ABI directory the device
    # actually selects. An armeabi-only copy is invisible when v7a is primary.
    tweak=root/'third_party/openmemories-tweak/armeabi'
    for abi in ('armeabi','armeabi-v7a'):
        for name in ('libtweak.so','libadbd.so'):
            entry='lib/'+abi+'/'+name
            assert entry in z.namelist(), entry
            data=z.read(entry)
            assert data[:5]==b'\x7fELF\x01' and data[18:20]==b'\x28\0', entry
            assert hashlib.sha256(data).hexdigest()==hashlib.sha256((tweak/name).read_bytes()).hexdigest(), entry
    for name in ['assets/OPENMEMORIES-TWEAK-MIT.txt','assets/ADBD-APACHE-2.0.txt',
                 'assets/OPENMEMORIES-TWEAK.txt',
                 'assets/JPEG-LICENSE.txt','assets/JPEG-IJG.txt','assets/GNU-STL-NOTICE.txt']:
        assert name in z.namelist()
report['badging']=subprocess.check_output([str(sdk/'build-tools/36.0.0/aapt2'),'dump','badging',str(apk)],text=True)
assert re.search(r"(?:minSdkVersion|sdkVersion):'10'",report['badging'])
for permission in ('android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE',
                   'android.permission.ACCESS_WIFI_STATE','android.permission.CHANGE_WIFI_STATE',
                   'android.permission.FOREGROUND_SERVICE'):
    assert "name='%s'" % permission in report['badging'],permission
if variant=='release':
    audit_apk(apk)
    manifest=subprocess.check_output([str(sdk/'build-tools/36.0.0/aapt2'),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True)
    assert re.search(r'android:debuggable[^\n]*=false',manifest), 'Release APK must not be debuggable'
    activities=re.findall(r'android:name[^\n]*="(\.[^"]*Activity)"',manifest)
    assert set(activities)=={'.LibraryActivity','.PhotoActivity','.GalleryActivity','.VideoActivity','.UiMotionDemoActivity'},activities
    service_block=manifest[manifest.index('E: service'):]
    assert re.search(r'name[^\n]*="\.TransferService"',service_block), 'TransferService missing'
    assert re.search(r'exported[^\n]*=false',service_block), 'TransferService must not be exported'
    assert re.search(r'stopWithTask[^\n]*=true',service_block), 'TransferService must stop with the task'
    report['release_activities']=activities
report['armeabi_attribute_note']='NDK14 shared platform stubs contribute v7 attributes; source objects are v5TE. See HANDOFF.md.'
(root/'dist/BUILDINFO.json').write_text(json.dumps(report,indent=2))
print('PASS APK: zip, dex035, minSDK10, ARM32, v5TE/v7 source objects, no NEON attribute, licenses')
print('APK bytes:',report['apk_bytes'],'SHA256:',report['apk_sha256'])
