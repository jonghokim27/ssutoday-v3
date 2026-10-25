import { type PenaltyReason, type PenaltyStatus } from '../api/reservationRepository';
import { formatDate, formatDateLabel } from './dates';

const DAY_MS = 24 * 60 * 60 * 1000;

// 서버 PenaltyReason.label과 같은 문구다.
export const penaltyReasonText: Record<PenaltyReason, string> = {
  PHOTO_MISSING: '인증샷 미촬영 자동 취소',
  PHOTO_REJECTED: '인증샷 2회 미인정 취소',
  PHOTO_OUTSIDE: '스터디룸 외부에서 촬영된 인증샷',
  FRAUD_AUTOMATION: '매크로 등 자동화 프로그램 이용',
  FRAUD_ACCOUNT: '타인 계정 이용 또는 계정 대여',
  FRAUD_PHOTO: '인증샷 재사용 또는 조작',
};

// 서버 PenaltyPolicy.maxDailyBlocks와 같은 단계다.
export const penaltySteps = [
  { label: '1회', limit: '3시간' },
  { label: '2회', limit: '2시간' },
  { label: '3~4회', limit: '1시간' },
  { label: '5회 이상', limit: '예약 정지' },
];

/** 유효 인증샷 페널티 횟수가 속한 단계. 0회면 -1이다. */
export function penaltyStepIndex(photoCount: number) {
  if (photoCount <= 0) return -1;
  if (photoCount === 1) return 0;
  if (photoCount === 2) return 1;
  if (photoCount <= 4) return 2;
  return 3;
}

export function hoursLabel(minutes: number) {
  const hours = Math.floor(minutes / 60);
  return minutes % 60 === 0 ? `${hours}시간` : `${hours}시간 30분`;
}

/** 서버가 보내는 yyyy-MM-ddTHH:mm:ss를 기기 시간대와 무관하게 그대로 읽는다. */
export function parseLocalDateTime(value: string) {
  const [date, time = '00:00:00'] = value.split('T');
  const [year, month, day] = date.split('-').map(Number);
  const [hour, minute, second] = time.split(':').map(Number);
  return new Date(year, month - 1, day, hour, minute, second || 0);
}

/** 다른 예약 화면과 같은 날짜 표기다. 예: 2026년 11월 03일(화) */
export function dateLabel(date: Date) {
  return formatDateLabel(formatDate(date));
}

/** 만료일까지 남은 날짜. 오늘 만료면 0이다. */
export function daysUntil(target: Date, now = new Date()) {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const end = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
  return Math.max(0, Math.round((end - start) / DAY_MS));
}

/** 예: 2026년 12월 05일(토) 1회 만료 시 3시간으로 상향돼요. 늘어날 일이 없으면 null이다. */
export function reliefMessage(status: PenaltyStatus) {
  const relief = status.nextRelief;
  if (!relief) return null;

  const at = parseLocalDateTime(relief.at);
  const expiring = status.penalties.filter(
    (penalty) => penalty.type === 'PHOTO' && parseLocalDateTime(penalty.expiresAt).getTime() <= at.getTime(),
  ).length;
  return `${dateLabel(at)} ${Math.max(expiring, 1)}회 만료 시 ${hoursLabel(relief.maxDailyMinutes)}으로 상향돼요`;
}
