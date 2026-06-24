import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { Toast } from '../../../shared/ui/Toast';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { reserveToHistoryView } from '../api/reservationMappers';
import { reservationRepository } from '../api/reservationRepository';
import styles from './ReservationHistory.module.css';

type HistoryViewItem = ReturnType<typeof reserveToHistoryView>;

export function ReservationHistory() {
  const safePath = useSafeAreaPath();
  const [tab, setTab] = useState<'active' | 'done'>('active');
  const [items, setItems] = useState<HistoryViewItem[]>([]);
  const [cancelTarget, setCancelTarget] = useState<HistoryViewItem | null>(null);
  const [doneTarget, setDoneTarget] = useState<HistoryViewItem | null>(null);
  const [toast, setToast] = useState('');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const shown = items.filter((item) => item.kind === tab);

  useEffect(() => {
    void loadItems();
  }, []);

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  async function loadItems() {
    setLoading(true);
    const [active, done] = await Promise.all([reservationRepository.listReserves(1, 1), reservationRepository.listReserves(0, 1)]);
    const next = [
      ...(active.ok ? active.data.reserves.map(reserveToHistoryView) : []),
      ...(done.ok ? done.data.reserves.map(reserveToHistoryView) : []),
    ];
    setItems(next);
    setLoading(false);
  }

  async function cancelReserve(item: HistoryViewItem) {
    setActionLoading(true);
    const result = await reservationRepository.cancelReserve(item.id);
    setCancelTarget(null);
    if (!result.ok) {
      setActionLoading(false);
      flash(cancelFailureMessage(result.statusCode, result.message));
      return;
    }

    await loadItems();
    setActionLoading(false);
    flash('예약이 취소됐어요');
  }

  async function doneReserve(item: HistoryViewItem) {
    setActionLoading(true);
    const result = await reservationRepository.doneReserve(item.id);
    setDoneTarget(null);
    if (!result.ok) {
      setActionLoading(false);
      flash(doneFailureMessage(result.statusCode, result.message));
      return;
    }

    await loadItems();
    setActionLoading(false);
    flash('이용을 종료했어요');
  }

  async function shootPhoto(item: HistoryViewItem) {
    setActionLoading(true);
    const result = await reservationRepository.uploadVerifyPhoto(item.id);
    flash(result.ok ? '인증샷을 업로드했어요' : result.message);
    await loadItems();
    setActionLoading(false);
  }

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <Link to={safePath('/reservations')}><IconButton><Icon name="arrowLeft" /></IconButton></Link>
        <h1>예약 내역</h1>
      </header>
      <div className={styles.tabs}>
        <button className={tab === 'active' ? styles.active : ''} onClick={() => setTab('active')} type="button">
          이용 중 · 대기 {items.filter((item) => item.kind === 'active').length}
        </button>
        <button className={tab === 'done' ? styles.active : ''} onClick={() => setTab('done')} type="button">
          이용 완료 {items.filter((item) => item.kind === 'done').length}
        </button>
      </div>
      <section className={styles.list}>
        {loading ? <LoadingState label="예약 내역을 불러오는 중" /> : null}
        {shown.map((item) => (
          <article className={styles.historyCard} key={item.id}>
            <div className={styles.cardBody}>
              <div className={styles.cardHead}>
                <span className={styles.calendar}><Icon name="calendar" width="16" height="16" /></span>
                <strong>{item.date}</strong>
              </div>
              <dl className={styles.details}>
                <div><dt>시설명</dt><dd>{item.room}</dd></div>
                <div><dt>이용 시간</dt><dd>{item.time} <span>({item.duration})</span></dd></div>
                {item.deletedReason ? <div><dt>취소 구분</dt><dd>{item.deletedReason.replace('(', '\n(')}</dd></div> : null}
              </dl>
            </div>
            <div className={styles.actions}>
              <div className={styles.statusCell}>
                <Badge tone={statusTone(item.state)}>{item.status}</Badge>
                {item.deletedAtLabel ? <span>{item.deletedAtLabel}</span> : null}
              </div>
              {item.canDone ? (
                <button className={styles.primaryAction} onClick={() => setDoneTarget(item)} type="button">
                  이용 종료<span>종료하려면 선택</span>
                </button>
              ) : null}
              {item.canShootPhoto ? (
                <button className={styles.shotButton} onClick={() => void shootPhoto(item)} type="button">
                  <span className={styles.blinkAction}>인증샷 촬영<i /></span>
                  <span>촬영 기한: {item.shotDeadline} 까지</span>
                </button>
              ) : null}
              {item.photoExempted ? (
                <div className={styles.actionInfo}>
                  인증샷 촬영<span>촬영이 면제됨</span>
                </div>
              ) : null}
              {item.canViewPhoto ? (
                <button className={styles.shotButton} onClick={() => void nativeBridge.openExternalUrl(item.verifyPhotoUrl ?? '')} type="button">
                  인증샷 보기<span>{item.verifyPhotoCreatedAt}</span>
                </button>
              ) : null}
              {item.photoMissing ? (
                <div className={styles.actionInfo}>
                  인증샷 보기<span>촬영되지 않음</span>
                </div>
              ) : null}
              {item.cancelable ? <button className={styles.danger} onClick={() => setCancelTarget(item)} type="button">예약 취소</button> : null}
              {item.canRebook ? <Link className={styles.rebook} to={safePath(`/reservations/${item.roomNo}?date=${item.rawDate}`)}>다시 예약하기</Link> : null}
            </div>
          </article>
        ))}
        {!loading && shown.length === 0 ? <div className={styles.empty}>표시할 예약이 없어요</div> : null}
      </section>
      {actionLoading ? (
        <div className={styles.loadingOverlay}>
          <LoadingState compact label="요청을 처리하는 중" />
        </div>
      ) : null}
      {cancelTarget ? (
        <ConfirmDialog
          confirmLabel="예약 취소"
          details={[
            { label: '스터디룸', value: cancelTarget.room },
            { label: '날짜', value: cancelTarget.date },
            { label: '시간', value: cancelTarget.time },
          ]}
          icon="x"
          message="취소한 예약은 되돌릴 수 없어요."
          onCancel={() => setCancelTarget(null)}
          onConfirm={() => {
            void cancelReserve(cancelTarget);
          }}
          title="예약을 취소할까요?"
          tone="danger"
        />
      ) : null}
      {doneTarget ? (
        <ConfirmDialog
          confirmLabel="이용 종료"
          details={[
            { label: '스터디룸', value: doneTarget.room },
            { label: '날짜', value: doneTarget.date },
            { label: '시간', value: doneTarget.time },
          ]}
          icon="check"
          message="종료 후에는 취소할 수 없어요. 최소 30분 이상 이용한 경우에만 종료할 수 있어요."
          onCancel={() => setDoneTarget(null)}
          onConfirm={() => {
            void doneReserve(doneTarget);
          }}
          title="이용을 종료할까요?"
        />
      ) : null}
      <Toast message={toast} />
    </div>
  );
}

function statusTone(state: HistoryViewItem['state']) {
  if (state === 'cancelled') return 'red';
  if (state === 'waiting') return 'purple';
  if (state === 'using') return 'blue';
  return 'green';
}

function cancelFailureMessage(statusCode: string, fallback: string) {
  if (statusCode === 'SSU4141' || statusCode === 'SSU4142') return '이미 이용이 완료된 예약이에요.';
  if (statusCode === 'SSU4143') return '현재 이용중인 예약이에요.';
  return fallback;
}

function doneFailureMessage(statusCode: string, fallback: string) {
  if (statusCode === 'SSU4231' || statusCode === 'SSU4232') return '이미 이용이 완료된 예약이에요.';
  if (statusCode === 'SSU4233') return '아직 이용을 시작하지 않은 예약이에요.';
  if (statusCode === 'SSU4234') return '이용 종료는 최소 30분 이상 이용하신 경우에만 가능해요.';
  if (statusCode === 'SSU4235') return '인증샷을 먼저 촬영해주세요.';
  if (statusCode === 'SSU4236') return '이용 종료 시간에 근접하여 이용을 종료할 수 없어요.';
  return fallback;
}
