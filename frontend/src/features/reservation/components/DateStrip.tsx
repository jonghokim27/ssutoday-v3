import { triggerHaptic } from '../../../shared/native/nativeBridge';
import { Icon } from '../../../shared/ui/Icon';
import { formatDateLabel, getDateStrip, parseDate, todayString } from '../data/dates';
import styles from './DateStrip.module.css';

type DateStripProps = {
  selectedDate: string;
  onOpenPicker: () => void;
  onPickDate: (date: string) => void;
};

export function DateStrip({ selectedDate, onOpenPicker, onPickDate }: DateStripProps) {
  const dates = getDateStrip(selectedDate);
  const selectedMonth = parseDate(selectedDate).getMonth() + 1;
  const today = todayString();

  return (
    <div className={styles.wrap}>
      <div className={styles.dates}>
        {dates.map((date) => {
          const active = date.date === selectedDate;
          const isToday = date.date === today;
          return (
            <button
              className={[styles.date, active ? styles.active : '', isToday && !active ? styles.today : ''].filter(Boolean).join(' ')}
              key={date.date}
              onClick={() => { triggerHaptic('heavy'); onPickDate(date.date); }}
              type="button"
            >
              <span>{date.month !== selectedMonth ? `${date.month}월` : date.dow}</span>
              <strong>{date.day}</strong>
            </button>
          );
        })}
      </div>
      <div className={styles.meta}>
        <button className={styles.dateFull} onClick={() => { triggerHaptic('heavy'); onOpenPicker(); }} type="button">
          <Icon name="calendar" /> {formatDateLabel(selectedDate)}
          <Icon name="chevronDown" width="14" height="14" />
        </button>
        <span className={styles.live}><i /> 실시간 현황</span>
      </div>
    </div>
  );
}
