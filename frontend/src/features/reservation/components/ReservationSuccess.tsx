import { Link, useLocation } from 'react-router-dom';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import { formatDateLabel, todayString } from '../data/dates';
import { studyRooms, type StudyRoom } from '../data/reservationData';
import styles from './ReservationSuccess.module.css';

type ReservationResultState = {
  ok?: boolean;
  room?: StudyRoom;
  date?: string;
  time?: string;
  message?: string;
};

export function ReservationSuccess() {
  const safePath = useSafeAreaPath();
  const { state } = useLocation();
  const result = (state ?? {}) as ReservationResultState;
  const ok = result.ok ?? true;
  const room = result.room ?? studyRooms[0];
  const dateLabel = formatDateLabel(result.date ?? todayString());

  return (
    <div className={styles.screen}>
      <div className={styles.confetti} aria-hidden="true">
        {Array.from({ length: 14 }, (_, index) => <span key={index} />)}
      </div>
      <div className={styles.check}><Icon name={ok ? 'check' : 'x'} width="70" height="70" /></div>
      <p className={styles.eyebrow}>{ok ? 'RESERVED' : 'FAILED'}</p>
      <h1>{result.message ?? (ok ? '예약이 완료됐어요' : '예약에 실패했어요')}</h1>
      <section className={styles.summary}>
        <div className={styles.room}>
          <img alt={room.name} src={room.thumbnail} />
          <div>
            <strong>{room.name}</strong>
            <span>{room.location}</span>
          </div>
        </div>
        <dl>
          <div><dt>날짜</dt><dd>{dateLabel}</dd></div>
          <div><dt>시간</dt><dd>{result.time ?? '17:30 ~ 18:30'}</dd></div>
        </dl>
      </section>
      <div className={styles.buttons}>
        {ok ? <Link to={safePath('/reservations/history')}><Button>예약 내역 보기</Button></Link> : null}
        <Link to={safePath('/reservations')}><Button variant="secondary">예약 화면으로</Button></Link>
      </div>
    </div>
  );
}
