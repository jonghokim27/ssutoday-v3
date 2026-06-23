import { Icon } from '../../../shared/ui/Icon';
import styles from './DatePickerDialog.module.css';

type DatePickerDialogProps = {
  selectedDay: number;
  onClose: () => void;
  onPick: (day: number) => void;
};

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

export function DatePickerDialog({ selectedDay, onClose, onPick }: DatePickerDialogProps) {
  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div className={styles.dialog} onClick={(event) => event.stopPropagation()}>
        <header>
          <h2>2023년 9월</h2>
          <button onClick={onClose} type="button"><Icon name="x" /></button>
        </header>
        <div className={styles.weekdays}>
          {weekdays.map((weekday) => <span key={weekday}>{weekday}</span>)}
        </div>
        <div className={styles.days}>
          {Array.from({ length: 5 }, (_, index) => <span aria-hidden="true" key={`blank-${index}`} />)}
          {Array.from({ length: 30 }, (_, index) => {
            const day = index + 1;
            return (
              <button
                className={day === selectedDay ? styles.selected : ''}
                key={day}
                onClick={() => onPick(day)}
                type="button"
              >
                {day}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
