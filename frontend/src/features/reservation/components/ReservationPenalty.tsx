import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { reservationRepository, reserveHistoryTime, type Penalty, type PenaltyStatus } from '../api/reservationRepository';
import { formatDateLabel } from '../data/dates';
import {
  dateLabel,
  daysUntil,
  hoursLabel,
  parseLocalDateTime,
  penaltyReasonText,
  penaltyStepIndex,
  penaltySteps,
  reliefMessage,
} from '../data/penalty';
import styles from './ReservationPenalty.module.css';

export function ReservationPenalty() {
  const safePath = useSafeAreaPath();
  const [status, setStatus] = useState<PenaltyStatus | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;
    void reservationRepository.getPenaltyStatus().then((result) => {
      if (!mounted) return;
      if (result.ok) {
        setStatus(result.data);
      } else {
        setError(result.message);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <Link to={safePath('/reservations')}><IconButton aria-label="뒤로"><Icon name="arrowLeft" /></IconButton></Link>
        <h1>페널티 현황</h1>
      </header>
      {!status && !error ? <LoadingState label="페널티 현황을 불러오는 중" /> : null}
      {error ? <p className={styles.error}>{error}</p> : null}
      {status ? (
        <>
          <PenaltySummary status={status} />
          {status.penalties.length > 0 ? (
            <>
              <div className={styles.sectionTitle}>
                <strong>부여 내역</strong>
                <span>{status.penalties.length}건</span>
              </div>
              <div className={styles.list}>
                {status.penalties.map((penalty) => <PenaltyCard key={penalty.id} penalty={penalty} />)}
              </div>
            </>
          ) : (
            <div className={styles.empty}>
              <div className={styles.emptyIcon} aria-hidden="true"><Icon name="shield" /></div>
              <strong>적용 중인 페널티가 존재하지 않아요</strong>
              <p>페널티가 부여되면 이곳에 표시돼요</p>
            </div>
          )}
          <section className={styles.guide}>
            <ul>
              {status.fraud ? (
                <li>부정 예약이 확인되면 <b>90일간</b> 예약이 정지돼요</li>
              ) : (
                <li>페널티는 부여된 날로부터 <b>30일간</b> 유효해요</li>
              )}
              <li>페널티에 대한 이의제기는 <b>마이페이지 → 지원</b> 또는 각 학부 학생회로 문의해 주세요</li>
            </ul>
          </section>
        </>
      ) : null}
    </div>
  );
}

function PenaltySummary({ status }: { status: PenaltyStatus }) {
  if (status.suspendedUntil) {
    return (
      <section className={[styles.summary, styles.stopped].join(' ')}>
        <span className={styles.summaryLabel}>예약 상태</span>
        <strong className={styles.stoppedTitle}><Icon name="ban" />예약이 정지됐어요</strong>
        <p className={styles.stoppedDesc}>
          <b>{dateLabel(parseLocalDateTime(status.suspendedUntil))}</b>까지 신규 예약이 제한돼요
        </p>
        {status.fraud ? null : <PenaltySteps photoCount={status.photoCount} tone={styles.toneRed} />}
      </section>
    );
  }

  const tone = status.photoCount > 0 ? styles.toneOrange : styles.toneBlue;
  const relief = reliefMessage(status);
  return (
    <section className={[styles.summary, tone].join(' ')}>
      <div className={styles.summaryTop}>
        <div>
          <span className={styles.summaryLabel}>적용 중인 페널티</span>
          <strong className={styles.count}>{status.photoCount}<small>회</small></strong>
        </div>
        <div className={styles.limit}>
          <span>1일 최대 예약 시간</span>
          <strong>{hoursLabel(status.maxDailyMinutes ?? 0)}</strong>
        </div>
      </div>
      <PenaltySteps photoCount={status.photoCount} />
      {relief ? (
        <p className={styles.hint}>{relief}</p>
      ) : null}
    </section>
  );
}

function PenaltySteps({ photoCount, tone }: { photoCount: number; tone?: string }) {
  const current = penaltyStepIndex(photoCount);
  return (
    <div className={[styles.steps, tone].filter(Boolean).join(' ')}>
      {penaltySteps.map((step, index) => (
        <div
          className={[styles.step, index <= current ? styles.filled : '', index === current ? styles.current : ''].filter(Boolean).join(' ')}
          key={step.label}
        >
          <i />
          <b>{step.label}</b>
          <span>{step.limit}</span>
        </div>
      ))}
    </div>
  );
}

function PenaltyCard({ penalty }: { penalty: Penalty }) {
  const fraud = penalty.type === 'FRAUD';
  const grantedAt = parseLocalDateTime(penalty.grantedAt);
  const expiresAt = parseLocalDateTime(penalty.expiresAt);
  const granted = `${dateLabel(grantedAt)} 부여${penalty.reason === 'PHOTO_OUTSIDE' ? ' · 관리자 확인' : ''}`;

  return (
    <article className={styles.card}>
      <div className={styles.cardHead}>
        <span className={[styles.cardIcon, fraud ? styles.red : styles.orange].join(' ')}>
          <Icon name={fraud ? 'ban' : 'camera'} width="18" height="18" />
        </span>
        <div className={styles.cardTitle}>
          <strong>{fraud ? '부정 예약' : '인증샷 미촬영·미인정'}</strong>
          <span>{granted}</span>
        </div>
        <span className={[styles.dday, fraud ? styles.ddayRed : ''].filter(Boolean).join(' ')}>D-{daysUntil(expiresAt)}</span>
      </div>
      <p className={styles.reason}>{penaltyReasonText[penalty.reason]}</p>
      <dl className={styles.details}>
        <div><dt>시설명</dt><dd>{penalty.roomName}</dd></div>
        <div><dt>이용 일자</dt><dd>{formatDateLabel(penalty.date)}</dd></div>
        <div>
          <dt>이용 시간</dt>
          <dd>{reserveHistoryTime(penalty.startBlock, penalty.endBlock)}{penalty.reservationCancelled ? <span> (취소 처리)</span> : null}</dd>
        </div>
        <div>
          <dt>{fraud ? '정지 기한' : '만료 일자'}</dt>
          <dd>{dateLabel(expiresAt)}</dd>
        </div>
      </dl>
    </article>
  );
}
