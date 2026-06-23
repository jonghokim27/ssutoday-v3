import { Link } from 'react-router-dom';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import { studyRooms } from '../data/reservationData';
import styles from './ReservationSuccess.module.css';

export function ReservationSuccess() {
  const room = studyRooms[0];

  return (
    <div className={styles.screen}>
      <div className={styles.confetti} aria-hidden="true">
        {Array.from({ length: 14 }, (_, index) => <span key={index} />)}
      </div>
      <div className={styles.check}><Icon name="check" width="70" height="70" /></div>
      <p className={styles.eyebrow}>RESERVED</p>
      <h1>예약이 완료됐어요</h1>
      <section className={styles.summary}>
        <div className={styles.room}>
          <img alt={room.name} src={room.thumbnail} />
          <div>
            <strong>{room.name}</strong>
            <span>{room.location}</span>
          </div>
        </div>
        <dl>
          <div><dt>날짜</dt><dd>2023년 9월 8일(금)</dd></div>
          <div><dt>시간</dt><dd>17:30 ~ 18:30</dd></div>
        </dl>
      </section>
      <div className={styles.buttons}>
        <Link to="/reservations/history"><Button>예약 내역 보기</Button></Link>
        <Link to="/reservations"><Button variant="secondary">예약 화면으로</Button></Link>
      </div>
    </div>
  );
}
