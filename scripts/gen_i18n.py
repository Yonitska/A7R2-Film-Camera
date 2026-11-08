#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the five-language UI catalog for the A7R II build.

Inputs:
  scripts/i18n_data.py     curated en/ja/ko translations keyed by the
                           Simplified Chinese source literal
  android/**/*.java        source literals, scanned for coverage only
  opencc                   zh-TW conversion (phrase table s2twp)

Outputs:
  android-assets/I18N.TSV                 tab separated, 5 columns
  android/i18n/TraditionalChinese.java    char-level fallback converter

The coverage report lists UI literals that would still fall back to Chinese
after the exact, rule and compound lookups, so the table can be completed
before release. Use --strict to fail when product UI literals are missing.
"""

import argparse
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'scripts', 'i18n_data.py')
ANDROID = os.path.join(ROOT, 'android')
TSV = os.path.join(ROOT, 'android-assets', 'I18N.TSV')
TC_JAVA = os.path.join(ANDROID, 'i18n', 'TraditionalChinese.java')

# Files whose text is engineering diagnostics rather than product UI.
SKIP_FILES = {
    'PreJpegProbeActivity.java',
    'PreviewFitProbeActivity.java',
    'VideoProbeActivity.java',
    'HeartbeatProbe.java',
}
SEPARATORS = set(' ：，、/·（）()「」“”\n\t')
HAN_RANGES = ((0x3400, 0x4DBF), (0x4E00, 0x9FFF), (0xF900, 0xFAFF))


def has_han(text):
    return any(a <= ord(ch) <= b for ch in text for a, b in HAN_RANGES)


def load_data():
    sys.path.insert(0, os.path.join(ROOT, 'scripts'))
    import i18n_data
    return dict(i18n_data.T)


def scan_literals():
    literals = {}
    pattern = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
    skip_dirs = {'diagnostics', 'recipe', 'i18n'}
    for base, dirs, files in os.walk(ANDROID):
        dirs[:] = sorted(d for d in dirs
                         if d not in ('build', 'gen', 'bin') and d not in skip_dirs)
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            path = os.path.join(base, name)
            rel = os.path.relpath(path, ROOT)
            with open(path, encoding='utf-8') as fh:
                text = fh.read()
            # Drop comments before scanning so documentation examples do not
            # masquerade as UI strings.
            text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
            text = re.sub(r'//[^\n]*', '', text)
            for match in pattern.finditer(text):
                value = unescape_java(match.group(1))
                if not has_han(value):
                    continue
                literals.setdefault(value, rel)
    return literals


def unescape_java(value):
    def repl(match):
        ch = match.group(1)
        return {'n': '\n', 't': '\t', 'r': '\r', '\\': '\\',
                '"': '"', "'": "'"}.get(ch, ch)
    return re.sub(r'\\(.)', repl, value)


def escape_tsv(value):
    return (value.replace('\\', '\\\\').replace('\t', '\\t')
            .replace('\n', '\\n').replace('\r', '\\r'))


def opencc_converter():
    try:
        from opencc import OpenCC
    except ImportError:
        return None
    return OpenCC('s2twp')


def to_traditional(cc, value):
    if cc is None:
        return value
    return cc.convert(value)


def rule_match(pattern, text):
    """Mirror Lang.Rule: literal parts separated by {} capture anything."""
    parts = pattern.split('{}')
    regex = '^' + '(.*?)'.join(re.escape(p) for p in parts) + '$'
    match = re.match(regex, text, re.S)
    if not match:
        return None
    return match


def apply_rule(rule_out, match, table, rules, target, depth):
    pieces = rule_out.split('{}')
    groups = match.groups()
    result = pieces[0]
    for i, group in enumerate(groups):
        if has_han(group) and depth > 0:
            group = dynamic(group, table, rules, target, depth - 1)
        result += group + (pieces[i + 1] if i + 1 < len(pieces) else '')
    return result


def dynamic(text, table, rules, target, depth):
    values = table.get(text)
    if values is not None:
        return values[target]
    for pattern, rule in rules:
        match = rule_match(pattern, text)
        if match:
            return apply_rule(rule[target], match, table, rules, target, depth)
    return compound(text, table, rules, target)


def translate(text, table, rules, target):
    """Mirror Lang.tr for the coverage report (en/ja/ko only)."""
    values = table.get(text)
    if values is not None:
        return values[target]
    for pattern, rule in rules:
        match = rule_match(pattern, text)
        if match:
            return apply_rule(rule[target], match, table, rules, target, 4)
    result = compound(text, table, rules, target)
    if result is not None:
        return result
    values = table.get(text)
    if values is not None:
        return values[target]
    return None


def compound(text, table, rules, target):
    out, translated, start = [], 0, 0
    for i in range(len(text) + 1):
        end = i == len(text)
        sep = not end and text[i] in SEPARATORS
        if not end and not sep:
            continue
        if i > start:
            part = text[start:i]
            value = piece(part, table, rules, target)
            if value is None:
                if has_han(part):
                    return None
                value = part
            else:
                translated += 1
            out.append(value)
        if not end:
            out.append(text[i])
        start = i + 1
    if translated == 0:
        return None
    return ''.join(out)


def piece(part, table, rules, target):
    if not has_han(part):
        return None
    values = table.get(part)
    if values is not None:
        return values[target]
    for pattern, rule in rules:
        match = rule_match(pattern, part)
        if match:
            return apply_rule(rule[target], match, table, rules, target, 4)
    return None


def write_tsv(table, cc):
    rows = []
    for source in sorted(table):
        en, ja, ko = table[source]
        tw = to_traditional(cc, source)
        rows.append([source, tw, en, ja, ko])
    os.makedirs(os.path.dirname(TSV), exist_ok=True)
    with open(TSV, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('# Simplified Chinese\tTraditional Chinese\tEnglish\tJapanese\tKorean\n')
        fh.write('# Generated by scripts/gen_i18n.py from scripts/i18n_data.py.\n')
        for row in rows:
            fh.write('\t'.join(escape_tsv(cell) for cell in row) + '\n')
    return len(rows)


def write_traditional(cc, sources):
    mapping = {}
    if cc is not None:
        for ch in sorted(set(''.join(sources))):
            if not has_han(ch):
                continue
            try:
                trad = cc.convert(ch)
            except Exception:
                continue
            if len(trad) == 1 and trad != ch:
                mapping[ch] = trad
    chunks = [list(mapping.items())[i:i + 400]
              for i in range(0, len(mapping), 400)]
    lines = [
        'package org.hongen.sonylutprobe;',
        '',
        'import java.util.HashMap;',
        'import java.util.Map;',
        '',
        '/** Simplified-to-Traditional character fallback for labels that are not',
        ' * in assets/I18N.TSV. Generated by scripts/gen_i18n.py; do not edit. */',
        'final class TraditionalChinese {',
        '    private static final Map<Character,Character> MAP=',
        '            new HashMap<Character,Character>();',
        '    static {',
    ]
    for i in range(len(chunks)):
        lines.append('        fill%d();' % i)
    lines.append('    }')
    lines.append('    private TraditionalChinese(){}')
    for i, chunk in enumerate(chunks):
        lines.append('    private static void fill%d(){' % i)
        for simp, trad in chunk:
            lines.append("        MAP.put('%s','%s');" % (java_char(simp), java_char(trad)))
        lines.append('    }')
    lines += [
        '',
        '    static String convert(String source){',
        '        if(source==null||source.length()==0)return source;',
        '        StringBuilder out=null;',
        '        for(int i=0;i<source.length();i++){',
        '            char ch=source.charAt(i);',
        '            Character trad=MAP.get(Character.valueOf(ch));',
        '            if(trad!=null){',
        '                if(out==null){',
        '                    out=new StringBuilder(source.length());',
        '                    out.append(source,0,i);',
        '                }',
        '                out.append(trad.charValue());',
        '            }else if(out!=null){',
        '                out.append(ch);',
        '            }',
        '        }',
        '        return out==null?source:out.toString();',
        '    }',
        '}',
        '',
    ]
    os.makedirs(os.path.dirname(TC_JAVA), exist_ok=True)
    with open(TC_JAVA, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(lines))
    return len(mapping)


def java_char(ch):
    if ch in ('\\', '\'', '"'):
        return '\\' + ch
    if ord(ch) < 0x20 or ord(ch) > 0xFFFD:
        return '\\u%04x' % ord(ch)
    return ch


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--strict', action='store_true',
                        help='exit non-zero when product UI literals are missing')
    parser.add_argument('--report', default='',
                        help='write the missing-literal report to this path')
    args = parser.parse_args()

    table = load_data()
    errors = []
    for pattern, values in table.items():
        if '{}' not in pattern:
            continue
        if not has_han(pattern):
            errors.append('rule has no Han anchor: %r' % pattern)
            continue
        expected = pattern.count('{}')
        for index, name in enumerate(('en', 'ja', 'ko')):
            if values[index].count('{}') != expected:
                errors.append('%s: %s placeholder count differs from source %r' %
                              (name, pattern, values[index]))
    if errors:
        for error in errors:
            print('error: ' + error, file=sys.stderr)
        return 1
    cc = opencc_converter()
    if cc is None:
        print('warning: opencc unavailable; zh-TW left empty', file=sys.stderr)

    literals = scan_literals()
    rules = [(k, v) for k, v in table.items() if '{}' in k]

    missing = []
    for text in sorted(literals):
        file = literals[text]
        if os.path.basename(file) in SKIP_FILES:
            continue
        gap = [lang for lang in (0, 1, 2)
               if not translate(text, table, rules, lang)]
        if gap:
            missing.append((text, file, gap))

    count = write_tsv(table, cc)
    chars = write_traditional(cc, list(table) + list(literals))

    print('I18N.TSV: %d entries (%d rules)' %
          (count, sum(1 for k in table if '{}' in k)))
    print('TraditionalChinese.java: %d character pairs' % chars)
    if args.report:
        with open(args.report, 'w', encoding='utf-8') as fh:
            for text, file, gap in missing:
                fh.write('%s\t%s\t%s\n' % (file, ','.join('en ja ko'.split()[g] for g in gap),
                                          text.replace('\n', '\\n')))
    if missing:
        print('coverage: %d product literals still untranslated' % len(missing))
        for text, file, gap in missing[:40]:
            flag = ','.join('en ja ko'.split()[g] for g in gap)
            print('  [%s] %s  (%s)' % (flag, text.replace('\n', '\\n'),
                                       os.path.basename(file)))
        if len(missing) > 40:
            print('  ... %d more' % (len(missing) - 40))
    else:
        print('coverage: all scanned product literals translate')
    if args.strict and missing:
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
