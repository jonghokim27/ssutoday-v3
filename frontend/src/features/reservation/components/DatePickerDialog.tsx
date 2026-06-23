import { useState } from 'react';
import { Icon } from '../../../shared/ui/Icon';
import styles from './DatePickerDialog.module.css';

type DatePickerDialogProps = {
  selectedDay: number;
  onClose: () => void;
  onPick: (day: number) => void;
};

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const monthNames = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];

export function DatePickerDialog({ selectedDay, onClose, onPick }: DatePickerDialogProps) {
  const [viewMonth, setViewMonth] = useState(8);
  const [viewYear, setViewYear] = useState(2023);
  const [pendingDay, setPendingDay] = useState(selectedDay);
  const [closing, setClosing] = useState(false);

  const firstDay = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const isSelectedMonth = viewYear === 2023 && viewMonth === 8;

  function moveMonth(delta: number) {
    const next = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(next.getFullYear());
    setViewMonth(next.getMonth());
  }

  function pickDay(day: number) {
    setPendingDay(day);
    window.setTimeout(() => {
      setClosing(true);
      window.setTimeout(() => onPick(day), 180);
    }, 180);
  }

  function closeWithAnimation() {
    setClosing(true);
    window.setTimeout(onClose, 180);
  }

  return (
    <div className={[styles.backdrop, closing ? styles.closing : ''].join(' ')} onClick={closeWithAnimation}>
      <div className={[styles.dialog, closing ? styles.dialogClosing : ''].join(' ')} onClick={(event) => event.stopPropagation()}>
        <header>
          <button aria-label="이전 월" onClick={() => moveMonth(-1)} type="button"><Icon name="arrowLeft" /></button>
          <h2>{viewYear}년 {monthNames[viewMonth]}</h2>
          <div className={styles.headerActions}>
            <button aria-label="다음 월" onClick={() => moveMonth(1)} type="button"><Icon name="arrowRight" /></button>
            <button aria-label="닫기" onClick={closeWithAnimation} type="button"><Icon name="x" /></button>
          </div>
        </header>
        <div className={styles.weekdays}>
          {weekdays.map((weekday) => <span key={weekday}>{weekday}</span>)}
        </div>
        <div className={styles.days}>
          {Array.from({ length: firstDay }, (_, index) => <span aria-hidden="true" key={`blank-${index}`} />)}
          {Array.from({ length: daysInMonth }, (_, index) => {
            const day = index + 1;
            const selected = isSelectedMonth && day === pendingDay;
            return (
              <button
                className={selected ? styles.selected : ''}
                key={day}
                onClick={() => pickDay(day)}
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
