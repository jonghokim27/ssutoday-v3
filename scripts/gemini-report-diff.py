#!/usr/bin/env python
# gemini-verify-photo-bench.js가 만든 리포트 두 개를 읽어 판정을 나란히 비교하는 HTML을 만든다.
# 프롬프트를 바꾼 뒤 어떤 사진의 판정이 뒤집혔는지 한눈에 보려는 용도다.
#
# 사용법:
#   python scripts/gemini-report-diff.py <이전리포트> <이후리포트> <출력경로> [이전라벨] [이후라벨]
import io
import os
import re
import sys

CARD_RE = re.compile(
    r'<figure class="card[^"]*">\s*'
    r'<img src="(?P<file>[^"]+)"[^>]*>\s*'
    r'<figcaption>.*?'
    r'<span class="idx">(?P<name>[^<]+)</span>\s*'
    r'<span class="badge (?P<badge>yes|no)">(?P<label>[^<]*)</span>.*?'
    r'<div class="conf">confidence (?P<conf>[^ ]+) . (?P<latency>[^<]+)</div>\s*'
    r'<p>(?P<reason>.*?)</p>',
    re.S,
)


def parse(path):
    html = io.open(path, encoding='utf-8').read()
    cards = {}
    for match in CARD_RE.finditer(html):
        data = match.groupdict()
        cards[data['name']] = data
    if not cards:
        sys.exit('[report-diff] %s 에서 카드를 찾지 못했습니다.' % path)
    return cards


def verdict_block(card, title):
    if card is None:
        return '<div class="side missing"><b>%s</b><div>결과 없음</div></div>' % title
    tone = 'yes' if card['badge'] == 'yes' else 'no'
    return (
        '<div class="side">'
        '<div class="sidehead"><b>%s</b><span class="badge %s">%s</span></div>'
        '<div class="conf">confidence %s · %s</div>'
        '<p>%s</p>'
        '</div>'
    ) % (title, tone, card['label'], card['conf'], card['latency'], card['reason'])


def main():
    if len(sys.argv) < 4:
        sys.exit('사용법: python scripts/gemini-report-diff.py <이전> <이후> <출력> [이전라벨] [이후라벨]')

    before_path, after_path, out_path = sys.argv[1:4]
    before_label = sys.argv[4] if len(sys.argv) > 4 else 'v1'
    after_label = sys.argv[5] if len(sys.argv) > 5 else 'v2'

    before, after = parse(before_path), parse(after_path)
    names = sorted(set(before) | set(after))

    changed = [n for n in names if n in before and n in after and before[n]['badge'] != after[n]['badge']]

    cards = []
    for name in names:
        old, new = before.get(name), after.get(name)
        flipped = name in changed
        arrow = ''
        if flipped:
            direction = '거부 → 인정' if new['badge'] == 'yes' else '인정 → 거부'
            arrow = '<span class="flag">%s</span>' % direction
        image = (new or old)['file']
        cards.append(
            '<section class="card%s">'
            '<img src="%s" alt="%s" loading="lazy">'
            '<div class="body"><div class="title">%s%s</div><div class="sides">%s%s</div></div>'
            '</section>' % (
                ' flipped' if flipped else '',
                image, name, name, arrow,
                verdict_block(old, before_label), verdict_block(new, after_label),
            )
        )

    def count(cards_map, badge):
        return sum(1 for c in cards_map.values() if c['badge'] == badge)

    summary = (
        '총 %d장 · 판정이 바뀐 사진 %d장<br>'
        '%s: 인정 %d / 거부 %d &nbsp;&nbsp; %s: 인정 %d / 거부 %d'
    ) % (
        len(names), len(changed),
        before_label, count(before, 'yes'), count(before, 'no'),
        after_label, count(after, 'yes'), count(after, 'no'),
    )

    html = """<!doctype html>
<html lang="ko">
<meta charset="utf-8">
<title>인증샷 판정 비교</title>
<style>
  body { margin:0; padding:24px; background:#14161a; color:#e6e8eb;
         font:14px/1.6 system-ui,-apple-system,"Segoe UI",sans-serif; }
  h1 { font-size:18px; margin:0 0 4px; }
  .summary { color:#9aa3ad; margin-bottom:20px; }
  .card { display:flex; gap:16px; background:#1c1f24; border:1px solid #2a2f36;
          border-radius:10px; padding:14px; margin-bottom:14px; }
  .card.flipped { border-color:#c9822f; background:#221d18; }
  .card img { width:200px; height:200px; object-fit:cover; border-radius:8px; background:#000; flex:none; }
  .body { flex:1; min-width:0; }
  .title { font-weight:600; margin-bottom:10px; display:flex; align-items:center; gap:10px; }
  .flag { font-size:11px; font-weight:600; padding:2px 8px; border-radius:999px;
          background:#4a3410; color:#ffcc80; }
  .sides { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
  .side { background:#171a1f; border:1px solid #262b32; border-radius:8px; padding:10px 12px; }
  .side.missing { color:#8b939d; }
  .sidehead { display:flex; align-items:center; gap:8px; margin-bottom:4px; }
  .badge { font-size:11px; padding:2px 8px; border-radius:999px; font-weight:600; }
  .badge.yes { background:#1f4d33; color:#7ee2a8; }
  .badge.no { background:#4d2320; color:#ff9e91; }
  .conf { color:#8b939d; font-size:12px; margin-bottom:6px; }
  p { margin:0; color:#c6ccd3; font-size:13px; }
  @media (max-width:900px) { .sides { grid-template-columns:1fr; } }
</style>
<h1>인증샷 판정 비교 — %s vs %s</h1>
<div class="summary">%s</div>
%s
</html>""" % (before_label, after_label, summary, '\n'.join(cards))

    io.open(out_path, 'w', encoding='utf-8').write(html)
    print('[report-diff] 저장: %s' % out_path)
    print('[report-diff] 판정이 바뀐 사진 %d장: %s' % (len(changed), ', '.join(c[:2] for c in changed)))


if __name__ == '__main__':
    main()
