import { Button } from '../../../shared/ui/Button';
import { type TimeBooking } from '../data/reservationData';
import styles from './ReservedUserSheet.module.css';

type ReservedUserSheetProps = {
  booking: TimeBooking;
  isAdmin?: boolean;
  onClose: () => void;
  onReport?: () => void;
  onAdminTool?: (type: 'reserveCancel' | 'photoDelete' | 'photoExecpt') => void;
  onViewPhoto?: () => void;
};

export function ReservedUserSheet({ booking, isAdmin = false, onClose, onReport, onAdminTool, onViewPhoto }: ReservedUserSheetProps) {
  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.sheet} onClick={(event) => event.stopPropagation()}>
        <div className={styles.handle} />
        <div className={styles.profile}>
          <div className={styles.initial}>{booking.name[0]}</div>
          <div>
            <strong>{booking.name}</strong>
            <span>{booking.department}</span>
          </div>
        </div>
        <dl>
          <div><dt>학번</dt><dd>{booking.studentId}</dd></div>
          <div><dt>이용 시간</dt><dd>{booking.start} ~ {booking.end}</dd></div>
        </dl>
        <p className={styles.help}>개인정보 보호를 위해 이름과 학번 일부가 마스킹되어 표시됩니다.</p>
        <div className={styles.actions}>
          {isAdmin ? (
            <>
              <Button onClick={() => onAdminTool?.('reserveCancel')} type="button" variant="secondary">관리자 취소</Button>
              <Button disabled={!booking.verifyPhotoUrl} onClick={() => onViewPhoto?.()} type="button" variant="secondary">인증샷 보기</Button>
              <Button onClick={() => onAdminTool?.('photoDelete')} type="button" variant="secondary">인증샷 삭제</Button>
              <Button onClick={() => onAdminTool?.('photoExecpt')} type="button" variant="secondary">인증샷 예외</Button>
            </>
          ) : (
            <></>
          )}
        </div>
        <Button onClick={onClose} type="button" variant="secondary">닫기</Button>
      </div>
    </div>
  );
}
