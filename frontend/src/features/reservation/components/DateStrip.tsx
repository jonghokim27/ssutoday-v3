import { Icon } from '../../../shared/ui/Icon';
import { dateStrip } from '../data/reservationData';
import styles from './DateStrip.module.css';

type DateStripProps = {
  selectedDay: number;
  onOpenPicker: () => void;
  onPickDay: (day: number) => void;
};

export function DateStrip({ selectedDay, onOpenPicker, onPickDay }: DateStripProps) {
  const selectedIndex = dateStrip.findIndex((date) => Number(date.day) === selectedDay);
  const activeIndex = selectedIndex >= 0 ? selectedIndex : 2;

  return (
    <div className={styles.wrap}>
      <div className={styles.dates}>
        {dateStrip.map((date, index) => (
          <button
            className={[styles.date, index === activeIndex ? styles.active : ''].join(' ')}
            key={date.day}
            onClick={() => onPickDay(Number(date.day))}
            type="button"
          >
            <span>{date.dow}</span>
            <strong>{date.day}</strong>
          </button>
        ))}
      </div>
      <div className={styles.meta}>
        <button className={styles.dateFull} onClick={onOpenPicker} type="button">
          <Icon name="calendar" /> 2023년 9월 {selectedDay}일({dateStrip[activeIndex]?.dow ?? '금'})
          <Icon name="chevronDown" width="14" height="14" />
        </button>
        <span className={styles.live}><i /> 실시간 현황</span>
      </div>
    </div>
  );
}
