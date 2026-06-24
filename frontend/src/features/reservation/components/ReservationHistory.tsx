import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { Toast } from '../../../shared/ui/Toast';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { reserveToHistoryView } from '../api/reservationMappers';
import { reservationRepository } from '../api/reservationRepository';
import styles from './ReservationHistory.module.css';

type HistoryViewItem = ReturnType<typeof reserveToHistoryView>;

export function ReservationHistory() {
  const [tab, setTab] = useState<'active' | 'done'>('active');
  const [items, setItems] = useState<HistoryViewItem[]>([]);
  const [cancelTarget, setCancelTarget] = useState<HistoryViewItem | null>(null);
  const [toast, setToast] = useState('');
  const shown = items.filter((item) => item.kind === tab);

  useEffect(() => {
    void loadItems();
  }, []);

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  async function loadItems() {
    const [active, done] = await Promise.all([reservationRepository.listReserves(1, 1), reservationRepository.listReserves(0, 1)]);
    const next = [
      ...(active.ok ? active.data.reserves.map(reserveToHistoryView) : []),
      ...(done.ok ? done.data.reserves.map((item) => ({ ...reserveToHistoryView(item), kind: 'done' as const, status: '이용 완료' as const })) : []),
    ];
    setItems(next);
  }

  async function cancelReserve(item: HistoryViewItem) {
    const result = await reservationRepository.cancelReserve(item.id);
    setCancelTarget(null);
    if (!result.ok) {
      flash(result.message);
      return;
    }

    await loadItems();
    flash('예약이 취소됐어요');
  }

  async function shootPhoto(item: HistoryViewItem) {
    const result = await reservationRepository.uploadVerifyPhoto(item.id);
    flash(result.ok ? '인증샷을 업로드했어요' : result.message);
    await loadItems();
  }

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <Link to="/reservations"><IconButton><Icon name="arrowLeft" /></IconButton></Link>
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
              </dl>
            </div>
            <div className={styles.actions}>
              <div className={styles.statusCell}>
                <Badge tone={item.status === '이용 대기' ? 'purple' : 'gray'}>{item.status}</Badge>
              </div>
              {item.kind === 'active' && !item.verifyPhotoUrl && !item.isContinuous ? (
                <button className={styles.shotButton} onClick={() => void shootPhoto(item)} type="button">
                  인증샷 촬영<span>촬영 기한: 곧 마감</span>
                </button>
              ) : null}
              {item.verifyPhotoUrl ? (
                <button className={styles.shotButton} onClick={() => void nativeBridge.openExternalUrl(item.verifyPhotoUrl ?? '')} type="button">
                  인증샷 보기
                </button>
              ) : null}
              {item.cancelable ? <button className={styles.danger} onClick={() => setCancelTarget(item)} type="button">예약 취소</button> : null}
              {item.kind === 'done' ? <Link className={styles.rebook} to="/reservations">다시 예약</Link> : null}
            </div>
          </article>
        ))}
        {shown.length === 0 ? <div className={styles.empty}>표시할 예약이 없어요</div> : null}
      </section>
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
      <Toast message={toast} />
    </div>
  );
}
