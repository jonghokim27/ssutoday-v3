import { Link } from 'react-router-dom';
import { useState } from 'react';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { Toast } from '../../../shared/ui/Toast';
import { reservationHistory, type ReservationHistoryItem } from '../data/reservationData';
import styles from './ReservationHistory.module.css';

export function ReservationHistory() {
  const [tab, setTab] = useState<'active' | 'done'>('active');
  const [items, setItems] = useState(reservationHistory);
  const [cancelTarget, setCancelTarget] = useState<ReservationHistoryItem | null>(null);
  const [toast, setToast] = useState('');
  const shown = items.filter((item) => item.kind === tab);

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
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
                <Badge tone={item.status === '이용 중' ? 'blue' : item.status === '이용 대기' ? 'purple' : 'gray'}>{item.status}</Badge>
              </div>
              {item.shotDeadline ? (
                <button className={styles.shotButton} onClick={() => flash('인증샷 촬영 화면은 준비 중이에요')} type="button">
                  인증샷 촬영<span>촬영 기한: {item.shotDeadline}</span>
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
            setItems((prev) => prev.filter((item) => item.id !== cancelTarget.id));
            setCancelTarget(null);
            flash('예약이 취소됐어요');
          }}
          title="예약을 취소할까요?"
          tone="danger"
        />
      ) : null}
      <Toast message={toast} />
    </div>
  );
}
