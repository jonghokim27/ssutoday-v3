import { Button } from '../../../shared/ui/Button';
import { type TimeBooking } from '../data/reservationData';
import styles from './ReservedUserSheet.module.css';

type ReservedUserSheetProps = {
  booking: TimeBooking;
  onClose: () => void;
};

export function ReservedUserSheet({ booking, onClose }: ReservedUserSheetProps) {
  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.sheet} onClick={(event) => event.stopPropagation()}>
        <div className={styles.handle} />
        <p className={styles.eyebrow}>예약된 시간 · 개인정보 보호</p>
        <div className={styles.profile}>
          <div className={styles.initial}>{booking.name[0]}</div>
          <div>
            <strong>{maskName(booking.name)}</strong>
            <span>{booking.department}</span>
          </div>
        </div>
        <dl>
          <div><dt>학번</dt><dd>{maskStudentId(booking.studentId)}</dd></div>
          <div><dt>이용 시간</dt><dd>{booking.start} ~ {booking.end}</dd></div>
          <div><dt>이용 인원</dt><dd>{booking.people}명</dd></div>
        </dl>
        <p className={styles.help}>개인정보 보호를 위해 이름과 학번 일부가 마스킹되어 표시됩니다.</p>
        <Button onClick={onClose} type="button" variant="secondary">닫기</Button>
      </div>
    </div>
  );
}

function maskName(name: string) {
  if (name.length <= 2) {
    return `${name[0]}*`;
  }

  return `${name[0]}${'*'.repeat(name.length - 2)}${name[name.length - 1]}`;
}

function maskStudentId(studentId: string) {
  return `${studentId.slice(0, 4)}${'*'.repeat(Math.max(0, studentId.length - 4))}`;
}
