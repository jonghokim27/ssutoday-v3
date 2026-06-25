#!/usr/bin/env node
// frontend와 mobile이 각각 보유한 브리지 프로토콜 타입 정의가 동일한지 검사한다.
// mobile/src/bridge/protocol.ts는 frontend/src/shared/native/bridgeProtocol.ts를 수동으로 복제한 파일이므로,
// 한쪽만 수정되어 drift가 발생하면 이 스크립트가 비정상 종료(exit 1)한다.
const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const frontendFile = path.join(repoRoot, 'frontend/src/shared/native/bridgeProtocol.ts');
const mobileFile = path.join(repoRoot, 'mobile/src/bridge/protocol.ts');

function readNormalized(filePath) {
  return fs
    .readFileSync(filePath, 'utf8')
    .split(/\r?\n/)
    .filter((line) => !line.trim().startsWith('//'))
    .join('\n')
    .trim();
}

const frontendContent = readNormalized(frontendFile);
const mobileContent = readNormalized(mobileFile);

if (frontendContent !== mobileContent) {
  console.error('[bridge-protocol-sync] frontend와 mobile의 브리지 프로토콜 정의가 다릅니다.');
  console.error(`  - ${path.relative(repoRoot, frontendFile)}`);
  console.error(`  - ${path.relative(repoRoot, mobileFile)}`);
  console.error('두 파일의 코드(주석 제외)가 동일하도록 수동으로 동기화해주세요.');
  process.exit(1);
}

console.log('[bridge-protocol-sync] OK: 두 파일이 동일합니다.');
