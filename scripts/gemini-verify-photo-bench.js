#!/usr/bin/env node
// 인증샷 진위 판별에 Gemini를 도입하기 전, 실제 지연 시간과 판정 정확도를 측정한다.
// 서버(VerifyPhotoApplicationService)가 보낼 요청과 동일한 형태(프롬프트, JSON 스키마, JPEG 이미지)로
// 호출하므로 여기서 측정된 값이 사용자가 실제로 기다리게 될 시간의 근사치가 된다.
//
// 사용법:
//   GEMINI_API_KEY=... node scripts/gemini-verify-photo-bench.js <이미지경로...>
//
// 환경변수:
//   GEMINI_API_KEY  (필수) Google AI Studio에서 발급한 키
//   GEMINI_MODELS   비교할 모델 목록(쉼표 구분). 기본: gemini-2.5-flash-lite,gemini-3.5-flash-lite
//   RUNS            이미지·모델당 측정 횟수. 기본: 5 (이와 별개로 워밍업 1회를 먼저 돌리고 제외한다)
//   DUMP_RAW=1      첫 응답의 원본 JSON을 통째로 출력한다. 토큰 사용량 필드명 확인용.
//   NO_SCHEMA=1     response_format(JSON 스키마 강제)을 빼고 호출한다. 스키마 관련 400이 날 때 우회용.
//   THINKING_LEVEL  추론 강도. 기본 minimal. flash 계열은 minimal을 거부하므로 low를 넘겨야 한다.
//                   (ENDPOINT=interactions 일 때만 쓰인다)
//   REPORT          결과를 HTML 리포트로 저장할 경로. 사진과 판정을 나란히 보여준다.
//                   이미지는 상대 경로로 참조하므로 리포트를 사진과 같은 디렉터리에 두어야 한다.
//   PROMPT_EXTRA    프롬프트 끝에 덧붙일 문장. 이미지 예시보다 훨씬 싸므로 먼저 시도할 것.
//   FEWSHOT_OK      인정 사례로 프롬프트에 끼워 넣을 이미지 경로들(쉼표 구분).
//                   1장당 입력 토큰 약 1,070 증가 = 매 요청마다 비용과 지연이 늘어난다.
//   ENDPOINT        generate(기본) | interactions
//                   generate = models/{model}:generateContent. 실측상 interactions보다 2배 이상 빠르다.
const fs = require('fs');
const path = require('path');

const API_KEY = process.env.GEMINI_API_KEY;
const MODELS = (process.env.GEMINI_MODELS || 'gemini-2.5-flash-lite,gemini-3.5-flash-lite')
  .split(',')
  .map((model) => model.trim())
  .filter(Boolean);
const RUNS = Number(process.env.RUNS || 5);
const DUMP_RAW = process.env.DUMP_RAW === '1';
const NO_SCHEMA = process.env.NO_SCHEMA === '1';
const THINKING_LEVEL = process.env.THINKING_LEVEL || 'minimal';
const ENDPOINT = process.env.ENDPOINT || 'generate';
const REPORT = process.env.REPORT || '';
const FEWSHOT_OK = process.env.FEWSHOT_OK || '';
const PROMPT_EXTRA = process.env.PROMPT_EXTRA || '';
const IMAGES = process.argv.slice(2);

if (!API_KEY) {
  console.error('[gemini-bench] GEMINI_API_KEY 환경변수가 필요합니다.');
  process.exit(1);
}
if (IMAGES.length === 0) {
  console.error('[gemini-bench] 측정할 이미지 경로를 1개 이상 넘겨주세요.');
  console.error('  예: GEMINI_API_KEY=... node scripts/gemini-verify-photo-bench.js ./photos/ok1.jpg ./photos/selfie.jpg');
  process.exit(1);
}

const PROMPT = [
  '너는 대학교 스터디룸 예약 시스템의 인증샷 검사기다.',
  '주어진 사진이 스터디룸 내부에서 방금 촬영된 인증샷인지 판단해라.',
  '',
  '스터디룸 인증샷의 특징: 실내, 책상과 의자, 화이트보드나 모니터, 실내 조명, 방 번호판.',
  '인증샷이 아닌 것: 셀카, 실외 사진, 컴퓨터나 휴대폰 화면을 찍은 사진, 스크린샷,',
  '음식 사진, 인물 위주 사진, 사진을 다시 촬영한 사진, 강의실이나 카페 등 다른 공간.',
  '',
  '판단이 애매하면 isStudyRoom을 true로 두고 confidence를 낮춰라. 정상 이용자를 막는 것이 더 큰 손해다.',
  '',
  ...(PROMPT_EXTRA ? [PROMPT_EXTRA, ''] : []),
  '{"isStudyRoom": boolean, "confidence": 0~1 사이 숫자, "reason": "한 문장 한국어 근거"} 형태의 JSON만 출력해라.',
].join('\n');

const RESPONSE_SCHEMA = {
  type: 'object',
  properties: {
    isStudyRoom: { type: 'boolean' },
    confidence: { type: 'number' },
    reason: { type: 'string' },
  },
  required: ['isStudyRoom', 'confidence', 'reason'],
};

function endpointUrl(model) {
  if (ENDPOINT === 'interactions') return 'https://generativelanguage.googleapis.com/v1beta/interactions';

  return `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
}

// FEWSHOT_OK에 지정한 이미지들을 "인정 사례"로 프롬프트 앞에 붙인다.
// 예시 1장당 입력 토큰이 약 1,070씩 늘어나므로 장당 비용과 지연이 함께 증가한다.
function loadFewshot() {
  if (!FEWSHOT_OK) return [];

  return FEWSHOT_OK.split(',').map((entry) => entry.trim()).filter(Boolean).map((imagePath) => {
    const extension = path.extname(imagePath).toLowerCase();
    const mimeType = extension === '.png' ? 'image/png' : extension === '.webp' ? 'image/webp' : 'image/jpeg';
    return { mimeType, base64: fs.readFileSync(imagePath).toString('base64'), name: path.basename(imagePath) };
  });
}

const FEWSHOT = loadFewshot();

function fewshotParts(inlineKey) {
  if (FEWSHOT.length === 0) return [];

  const parts = [{ text: `다음 ${FEWSHOT.length}장은 모두 인정된 스터디룸 인증샷 예시다. 이 수준이면 isStudyRoom은 true다.` }];
  for (const example of FEWSHOT) {
    parts.push({ [inlineKey]: { mime_type: example.mimeType, data: example.base64 } });
  }
  parts.push({ text: '예시는 여기까지다. 이제 아래 사진 한 장을 판단해라.' });

  return parts;
}

function buildBody(model, image) {
  if (ENDPOINT === 'generate') {
    const generationConfig = { temperature: 0 };
    if (!NO_SCHEMA) {
      generationConfig.responseMimeType = 'application/json';
      generationConfig.responseSchema = RESPONSE_SCHEMA;
    }

    return JSON.stringify({
      contents: [{
        parts: [
          { text: PROMPT },
          ...fewshotParts('inline_data'),
          { inline_data: { mime_type: image.mimeType, data: image.base64 } },
        ],
      }],
      generationConfig,
    });
  }

  const body = {
    model,
    input: [
      { type: 'text', text: PROMPT },
      ...FEWSHOT.flatMap((example) => [{ type: 'image', data: example.base64, mime_type: example.mimeType }]),
      { type: 'image', data: image.base64, mime_type: image.mimeType },
    ],
    generation_config: { temperature: 0 },
  };

  // 2.5 계열은 추론이 기본으로 꺼져 있어 건드리지 않는다.
  // 3.x는 기본이 켜짐이라 최소치로 낮춰야 지연 비교가 공정해진다.
  // 단 flash-lite가 아닌 flash 계열은 minimal을 거부하므로 THINKING_LEVEL=low로 돌려야 한다.
  if (!model.startsWith('gemini-2.5')) {
    body.generation_config.thinking_level = THINKING_LEVEL;
  }
  if (!NO_SCHEMA) {
    body.response_format = { type: 'text', mime_type: 'application/json', schema: RESPONSE_SCHEMA };
  }

  return JSON.stringify(body);
}

// 응답에서 생성 텍스트를 꺼낸다.
// generateContent는 candidates[].content.parts[].text, interactions는 output_text 또는 steps.
function extractText(payload) {
  const parts = payload.candidates?.[0]?.content?.parts;
  if (Array.isArray(parts)) {
    const joined = parts.map((part) => part.text).filter((text) => typeof text === 'string').join('');
    if (joined) return joined;
  }
  if (typeof payload.output_text === 'string') return payload.output_text;
  if (typeof payload.outputText === 'string') return payload.outputText;

  const chunks = [];
  const walk = (node) => {
    if (node === null || typeof node !== 'object') return;
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (typeof node.text === 'string') chunks.push(node.text);
    Object.values(node).forEach(walk);
  };
  walk(payload.steps);

  return chunks.join('');
}

// Interactions API의 usage 필드 구조 (실측으로 확인):
//   { total_input_tokens, total_output_tokens, total_thought_tokens,
//     input_tokens_by_modality: [{ modality: 'text'|'image', tokens }] }
function extractUsage(payload) {
  if (payload.usageMetadata) {
    const usage = payload.usageMetadata;
    const details = usage.promptTokensDetails || [];
    const image = details.find((entry) => entry.modality === 'IMAGE')?.tokenCount ?? null;
    return {
      input: usage.promptTokenCount ?? null,
      output: usage.candidatesTokenCount ?? null,
      thought: usage.thoughtsTokenCount ?? 0,
      imageTokens: image,
      found: (usage.promptTokenCount ?? null) !== null,
    };
  }

  const usage = payload.usage || {};
  const input = usage.total_input_tokens ?? null;
  const output = usage.total_output_tokens ?? null;
  const thought = usage.total_thought_tokens ?? 0;
  const byModality = usage.input_tokens_by_modality || [];
  const imageTokens = byModality.find((entry) => entry.modality === 'image')?.tokens ?? null;

  return { input, output, thought, imageTokens, found: input !== null };
}

async function callOnce(model, image) {
  const startedAt = process.hrtime.bigint();
  const response = await fetch(endpointUrl(model), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': API_KEY },
    body: buildBody(model, image),
  });
  const raw = await response.text();
  const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6;

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${raw.slice(0, 500)}`);
  }

  const payload = JSON.parse(raw);
  const text = extractText(payload);
  if (!text) {
    throw new Error(`응답에서 텍스트를 찾지 못했습니다. DUMP_RAW=1로 확인하세요: ${raw.slice(0, 500)}`);
  }

  let verdict;
  try {
    // 스키마 없이 호출하면 ```json 펜스가 붙어 올 수 있다.
    verdict = JSON.parse(text.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim());
  } catch {
    verdict = { raw: text.slice(0, 200) };
  }

  return { elapsedMs, verdict, usage: extractUsage(payload), rawPayload: raw };
}

function percentile(sorted, ratio) {
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1);
  return sorted[Math.max(0, index)];
}

function ms(value) {
  return `${value.toFixed(0)}ms`;
}

// 2026-09 기준 공식 단가(USD / 100만 토큰).
const PRICING = {
  'gemini-2.5-flash-lite': { input: 0.10, output: 0.40 },
  'gemini-2.5-flash': { input: 0.30, output: 2.50 },
  'gemini-3.5-flash-lite': { input: 0.30, output: 2.50 },
  'gemini-3.5-flash': { input: 1.50, output: 9.00 },
};

function formatCost(model, usage) {
  const price = PRICING[model];
  if (!usage.found) return '토큰 사용량 필드를 못 찾음 (DUMP_RAW=1로 확인)';

  const detail = usage.imageTokens === null ? '' : ` (이미지 ${usage.imageTokens})`;
  const base = `입력 ${usage.input}${detail} / 출력 ${usage.output ?? '?'} / 추론 ${usage.thought}`;
  if (!price || usage.output === null) return base;

  const usd = (usage.input * price.input + (usage.output + usage.thought) * price.output) / 1e6;
  return `${base} → 장당 $${usd.toFixed(6)} (약 ${(usd * 1400).toFixed(3)}원)`;
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// 파일명 접두사 ok-/ng-를 정답 라벨로 읽는다. 접두사가 없으면 채점하지 않는다.
function expectedLabel(name) {
  if (/^ok[-_]/i.test(name)) return true;
  if (/^ng[-_]/i.test(name)) return false;
  return null;
}

function writeReport(rows) {
  const graded = rows.filter((row) => row.expected !== null);
  const correct = graded.filter((row) => row.expected === row.verdict.isStudyRoom);
  const summary = graded.length === 0
    ? `총 ${rows.length}장 (파일명에 ok-/ng- 접두사가 없어 자동 채점 안 함)`
    : `총 ${rows.length}장 · 채점 ${graded.length}장 · 정답 ${correct.length} · 오답 ${graded.length - correct.length}`;

  const cards = rows.map((row) => {
    const yes = row.verdict.isStudyRoom;
    const wrong = row.expected !== null && row.expected !== yes;
    const badge = yes ? '인정' : '비인정';
    const mark = row.expected === null ? '' : wrong ? '<span class="mark bad">오답</span>' : '<span class="mark good">정답</span>';
    return `<figure class="card${wrong ? ' wrong' : ''}">
  <img src="${escapeHtml(row.file)}" alt="${escapeHtml(row.name)}" loading="lazy">
  <figcaption>
    <div class="head">
      <span class="idx">${escapeHtml(row.name)}</span>
      <span class="badge ${yes ? 'yes' : 'no'}">${badge}</span>
      ${mark}
    </div>
    <div class="conf">confidence ${escapeHtml(row.verdict.confidence)} · ${escapeHtml(row.latency.toFixed(0))}ms</div>
    <p>${escapeHtml(row.verdict.reason ?? JSON.stringify(row.verdict))}</p>
  </figcaption>
</figure>`;
  }).join('\n');

  const html = `<!doctype html>
<html lang="ko">
<meta charset="utf-8">
<title>인증샷 Gemini 판정 리포트</title>
<style>
  body { margin: 0; padding: 24px; background: #14161a; color: #e6e8eb;
         font: 14px/1.6 system-ui, -apple-system, "Segoe UI", sans-serif; }
  h1 { font-size: 18px; margin: 0 0 4px; }
  .summary { color: #9aa3ad; margin-bottom: 24px; }
  .grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
  .card { margin: 0; background: #1c1f24; border: 1px solid #2a2f36; border-radius: 10px; overflow: hidden; }
  .card.wrong { border-color: #b4432f; }
  .card img { width: 100%; height: 260px; object-fit: cover; display: block; background: #000; }
  figcaption { padding: 10px 12px 14px; }
  .head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
  .idx { font-weight: 600; font-size: 13px; }
  .badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; font-weight: 600; }
  .badge.yes { background: #1f4d33; color: #7ee2a8; }
  .badge.no { background: #4d2320; color: #ff9e91; }
  .mark { font-size: 11px; padding: 2px 8px; border-radius: 999px; }
  .mark.good { background: #23303f; color: #8fc6f5; }
  .mark.bad { background: #5a2018; color: #ffb4a6; }
  .conf { color: #8b939d; font-size: 12px; margin-bottom: 6px; }
  p { margin: 0; color: #c6ccd3; font-size: 13px; }
</style>
<h1>인증샷 Gemini 판정 리포트</h1>
<div class="summary">${escapeHtml(summary)}</div>
<div class="grid">
${cards}
</div>
</html>`;

  fs.writeFileSync(REPORT, html, 'utf8');
  console.log(`
[gemini-bench] 리포트 저장: ${REPORT}`);
  console.log(`[gemini-bench] ${summary}`);
}

async function main() {
  const images = IMAGES.map((imagePath) => {
    const buffer = fs.readFileSync(imagePath);
    const extension = path.extname(imagePath).toLowerCase();
    const mimeType = extension === '.png' ? 'image/png' : extension === '.webp' ? 'image/webp' : 'image/jpeg';
    return { name: path.basename(imagePath), path: imagePath, bytes: buffer.length, mimeType, base64: buffer.toString('base64') };
  });

  if (FEWSHOT.length > 0) {
    console.log(`[gemini-bench] 인정 사례 ${FEWSHOT.length}장 첨부: ${FEWSHOT.map((example) => example.name).join(', ')}`);
  }
  console.log(`[gemini-bench] 엔드포인트 ${ENDPOINT} / 이미지 ${images.length}장 / 모델 ${MODELS.length}개 / 측정 ${RUNS}회 (워밍업 1회 제외)`);
  for (const image of images) {
    console.log(`  - ${image.name}: 원본 ${(image.bytes / 1024).toFixed(0)}KB, base64 ${(image.base64.length / 1024).toFixed(0)}KB`);
  }

  let dumped = false;
  const reportRows = [];

  for (const model of MODELS) {
    console.log(`\n[gemini-bench] === ${model} ===`);
    const allTimings = [];

    for (const image of images) {
      let warmup;
      try {
        warmup = await callOnce(model, image);
      } catch (error) {
        console.error(`  ${image.name}: 실패 - ${error.message}`);
        continue;
      }

      if (DUMP_RAW && !dumped) {
        console.log('  [원본 응답]');
        console.log(warmup.rawPayload);
        dumped = true;
      }

      const timings = [];
      for (let i = 0; i < RUNS; i += 1) {
        try {
          timings.push((await callOnce(model, image)).elapsedMs);
        } catch (error) {
          console.error(`  ${image.name}: ${i + 1}회차 실패 - ${error.message}`);
        }
      }
      if (timings.length === 0) continue;
      allTimings.push(...timings);

      const sorted = [...timings].sort((a, b) => a - b);
      reportRows.push({
        name: image.name.replace(/\.[^.]+$/, ''),
        file: path.basename(image.path),
        expected: expectedLabel(image.name),
        verdict: warmup.verdict,
        latency: percentile(sorted, 0.5),
      });

      console.log(`  ${image.name}`);
      console.log(`    지연  min ${ms(sorted[0])} / p50 ${ms(percentile(sorted, 0.5))} / p95 ${ms(percentile(sorted, 0.95))} / max ${ms(sorted[sorted.length - 1])} (워밍업 ${ms(warmup.elapsedMs)})`);
      console.log(`    토큰  ${formatCost(model, warmup.usage)}`);
      console.log(`    판정  ${JSON.stringify(warmup.verdict)}`);
    }

    if (allTimings.length > 0) {
      const sorted = [...allTimings].sort((a, b) => a - b);
      console.log(`  [종합 ${sorted.length}회] p50 ${ms(percentile(sorted, 0.5))} / p95 ${ms(percentile(sorted, 0.95))} / max ${ms(sorted[sorted.length - 1])}`);
    }
  }

  if (REPORT) writeReport(reportRows);
}

main().catch((error) => {
  console.error(`[gemini-bench] ${error.message}`);
  process.exit(1);
});
