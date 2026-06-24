import { Link, useLocation } from 'react-router-dom';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import styles from './ReservationFailed.module.css';

type ReservationFailedState = {
  message?: string;
  statusCode?: string;
  roomId?: string;
  date?: string;
};

export function ReservationFailed() {
  const safePath = useSafeAreaPath();
  const { state } = useLocation();
  const result = (state ?? {}) as ReservationFailedState;
  const retryPath = result.roomId
    ? safePath(`/reservations/${result.roomId}${result.date ? `?date=${result.date}` : ''}`)
    : safePath('/reservations');

  return (
    <div className={styles.screen}>
      <div className={styles.mark}><Icon name="alertTriangle" width="56" height="56" /></div>
      <p className={styles.eyebrow}>{result.statusCode ?? 'FAILED'}</p>
      <h1>예약 실패</h1>
      <p className={styles.guide}>{result.message ?? '예약 처리 중 문제가 발생했어요'}</p>
      <div className={styles.buttons}>
        <Link to={retryPath}><Button>다시 예약하기</Button></Link>
        <Link to={safePath('/reservations')}><Button variant="secondary">예약 화면으로</Button></Link>
      </div>
    </div>
  );
}
