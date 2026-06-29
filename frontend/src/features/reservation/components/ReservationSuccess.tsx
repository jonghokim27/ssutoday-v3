import { Link, useLocation } from 'react-router-dom';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import { formatDateLabel, todayString } from '../data/dates';
import { emptyStudyRoom, type StudyRoom } from '../data/reservationData';
import styles from './ReservationSuccess.module.css';

type ReservationSuccessState = {
  room?: StudyRoom;
  date?: string;
  time?: string;
  message?: string;
};

export function ReservationSuccess() {
  const safePath = useSafeAreaPath();
  const { state } = useLocation();
  const result = (state ?? {}) as ReservationSuccessState;
  const room = result.room ?? emptyStudyRoom;
  const dateLabel = formatDateLabel(result.date ?? todayString());

  return (
    <div className={styles.screen}>
      <div className={styles.confetti} aria-hidden="true">
        {Array.from({ length: 14 }, (_, index) => <span key={index} />)}
      </div>
      <div className={styles.check}><Icon name="check" width="70" height="70" /></div>
      <p className={styles.eyebrow}>RESERVED</p>
      <h1>예약 성공</h1>
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
        <Link to={safePath('/reservations/history')} replace><Button>예약 내역 보기</Button></Link>
        <Link to={safePath('/reservations')} replace><Button variant="secondary">예약 화면으로</Button></Link>
      </div>
    </div>
  );
}
