import { Icon } from '../../../shared/ui/Icon';
import { formatDateLabel, getDateStrip } from '../data/dates';
import styles from './DateStrip.module.css';

type DateStripProps = {
  selectedDate: string;
  onOpenPicker: () => void;
  onPickDate: (date: string) => void;
};

export function DateStrip({ selectedDate, onOpenPicker, onPickDate }: DateStripProps) {
  const dates = getDateStrip(selectedDate);

  return (
    <div className={styles.wrap}>
      <div className={styles.dates}>
        {dates.map((date) => (
          <button
            className={[styles.date, date.date === selectedDate ? styles.active : ''].join(' ')}
            key={date.date}
            onClick={() => onPickDate(date.date)}
            type="button"
          >
            <span>{date.dow}</span>
            <strong>{date.day}</strong>
          </button>
        ))}
      </div>
      <div className={styles.meta}>
        <button className={styles.dateFull} onClick={onOpenPicker} type="button">
          <Icon name="calendar" /> {formatDateLabel(selectedDate)}
          <Icon name="chevronDown" width="14" height="14" />
        </button>
        <span className={styles.live}><i /> 실시간 현황</span>
      </div>
    </div>
  );
}
