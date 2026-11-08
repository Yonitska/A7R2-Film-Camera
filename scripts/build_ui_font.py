#!/usr/bin/env python3
"""Static TrueType for the camera; covers the five UI languages.

Sources (both pinned, SIL Open Font License 1.1):
  Noto Sans SC VF  - Simplified Chinese, Traditional Chinese, kana, Latin
  Noto Sans KR     - Hangul (and any glyph the SC family does not carry)

The two subsets are merged so every language renders from one TrueType file;
the A7R II firmware predates Android's font-family fallback. Glyph coverage is
driven by the Java UI literals and assets/I18N.TSV, plus GB2312 for LUT names.
No font is fetched during ordinary APK builds.
"""
from pathlib import Path
import hashlib
import json
import re
import tempfile
import unicodedata

from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / 'vendor/font-source'
SOURCE_SC = FONT_DIR / 'NotoSansSC-VF.ttf'
SOURCE_KR = FONT_DIR / 'NotoSansKR-Regular.ttf'
EXPECTED_SC = 'd68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964'
EXPECTED_KR = '84e2b8acadd0a01af9f0de6b0a2d29117fb5b0eabd7c2f8353d9d2da0461d5fa'

assert hashlib.sha256(SOURCE_SC.read_bytes()).hexdigest() == EXPECTED_SC
assert hashlib.sha256(SOURCE_KR.read_bytes()).hexdigest() == EXPECTED_KR


def needed_characters():
    chars = set(chr(c) for c in range(32, 127))
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                chars.add(bytes([hi, lo]).decode('gb2312'))
            except UnicodeDecodeError:
                pass
    for path in (ROOT / 'android').rglob('*.java'):
        chars.update(c for c in path.read_text(encoding='utf-8')
                     if ord(c) >= 32 and c != '﻿')
    i18n = ROOT / 'android-assets/I18N.TSV'
    if i18n.exists():
        chars.update(c for c in i18n.read_text(encoding='utf-8') if ord(c) >= 32)
    # Kana and Japanese punctuation: translations are data-driven, so keep the
    # blocks complete rather than relying on today's literal list.
    for start, end in ((0x3000, 0x303F), (0x3040, 0x30FF), (0xFF66, 0xFF9D)):
        chars.update(chr(c) for c in range(start, end + 1)
                     if unicodedata.category(chr(c)) != 'Cn')
    # Korean compatibility jamo, plus every Hangul syllable used by the TSV.
    chars.update(chr(c) for c in range(0x3131, 0x318F)
                 if unicodedata.category(chr(c)) != 'Cn')
    # The generated traditional fallback may produce characters that do not
    # appear in the Simplified literals, e.g. 體 from 体.
    tc = ROOT / 'android/i18n/TraditionalChinese.java'
    if tc.exists():
        for simp, trad in re.findall(r"MAP\.put\('(.)','(.)'\);",
                                     tc.read_text(encoding='utf-8')):
            chars.update((simp, trad))
    return chars


def options():
    opt = subset.Options()
    opt.layout_features = []
    opt.name_IDs = ['*']
    opt.name_legacy = True
    opt.name_languages = ['*']
    opt.notdef_glyph = True
    opt.notdef_outline = True
    opt.recommended_glyphs = True
    opt.recalc_bounds = True
    return opt


def subset_to(source, text):
    opt = options()
    font = subset.load_font(str(source), opt)
    worker = subset.Subsetter(options=opt)
    worker.populate(text=text)
    worker.subset(font)
    return font, opt


def main():
    chars = needed_characters()
    sc_probe = TTFont(str(SOURCE_SC), lazy=True)
    sc_cmap = sc_probe.getBestCmap()
    sc_chars = {c for c in chars if ord(c) in sc_cmap}
    missing = {c for c in chars if ord(c) not in sc_cmap}

    kr_probe = TTFont(str(SOURCE_KR), lazy=True)
    kr_cmap = kr_probe.getBestCmap()
    kr_chars = {c for c in missing if ord(c) in kr_cmap}
    unresolved = sorted(c for c in missing if ord(c) not in kr_cmap)
    # GB2312 has a few spacing characters absent from Noto; UI text must not.
    ui_only = set(''.join(p.read_text(encoding='utf-8')
                          for p in (ROOT / 'android').rglob('*.java')))
    ui_missing = sorted(c for c in ui_only
                        if ord(c) >= 32 and c != '﻿'
                        and c not in sc_chars and c not in kr_chars)
    assert not ui_missing, ui_missing
    assert not unresolved, 'font sources lack: ' + ''.join(unresolved)

    tmp = Path(tempfile.mkdtemp(prefix='lutfont-'))
    sc, _ = subset_to(SOURCE_SC, ''.join(sorted(sc_chars)))
    instantiateVariableFont(sc, {'wght': 400}, inplace=True)
    sc_path = tmp / 'sc.ttf'
    sc.save(sc_path)

    kr, _ = subset_to(SOURCE_KR, ''.join(sorted(kr_chars)))
    kr_path = tmp / 'kr.ttf'
    kr.save(kr_path)

    if kr_chars:
        font = Merger().merge([str(sc_path), str(kr_path)])
        # Merge can leave duplicate/replaced tables; a final subset keeps only
        # the glyph set the UI can draw and drops merger bookkeeping.
        final_opt = options()
        worker = subset.Subsetter(options=final_opt)
        worker.populate(text=''.join(sorted(chars)))
        worker.subset(font)
    else:
        font = sc
        font = TTFont(str(sc_path))

    family = 'Lut Camera Sans'
    for nid, value in {1: family, 2: 'Regular', 3: family + ' Regular',
                       4: family + ' Regular', 6: 'LutCameraSans-Regular',
                       16: family, 17: 'Regular'}.items():
        font['name'].setName(value, nid, 3, 1, 0x409)
        font['name'].setName(value, nid, 1, 0, 0)

    missing_final = sorted(ord(c) for c in chars
                           if ord(c) not in font.getBestCmap())
    assert not missing_final, missing_final
    out = ROOT / 'android-assets/UIFONT.TTF'
    font.save(out)
    info = {
        'source_sc_sha256': EXPECTED_SC,
        'source_sc_commit': 'f8d157532fbfaeda587e826d4cd5b21a49186f7c',
        'source_kr_sha256': EXPECTED_KR,
        'source_kr_url': 'https://fonts.gstatic.com/s/notosanskr/v40/'
                         'PbyxFmXiEBPT4ITbgNA5Cgms3VYcOA-vvnIzzuoyeLQ.ttf',
        'format': 'static TrueType',
        'coverage': 'zh-CN zh-TW ja ko + Latin/GB2312',
        'glyphs': len(font.getBestCmap()),
        'bytes': out.stat().st_size,
        'sha256': hashlib.sha256(out.read_bytes()).hexdigest(),
    }
    (ROOT / 'android-assets/FONTINFO.json').write_text(
        json.dumps(info, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(out, out.stat().st_size, 'bytes;', info['glyphs'],
          'glyphs; merged', len(sc_chars), 'SC +', len(kr_chars), 'KR')


if __name__ == '__main__':
    main()
