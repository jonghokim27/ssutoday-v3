import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../../../shared/ui/Icon';
import { formatDate, parseDate, todayString } from '../data/dates';
import styles from './DatePickerDialog.module.css';

type DatePickerDialogProps = {
  selectedDate: string;
  onClose: () => void;
  onPick: (date: string) => void;
};

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const monthNames = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];

export function DatePickerDialog({ selectedDate, onClose, onPick }: DatePickerDialogProps) {
  const initialDate = parseDate(selectedDate);
  const [viewMonth, setViewMonth] = useState(initialDate.getMonth());
  const [viewYear, setViewYear] = useState(initialDate.getFullYear());
  const [pendingDate, setPendingDate] = useState(selectedDate);
  const [closing, setClosing] = useState(false);

  const firstDay = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();

  function moveMonth(delta: number) {
    const next = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(next.getFullYear());
    setViewMonth(next.getMonth());
  }

  function pickDay(day: number) {
    const nextDate = formatDate(new Date(viewYear, viewMonth, day));
    setPendingDate(nextDate);
    window.setTimeout(() => {
      setClosing(true);
      window.setTimeout(() => onPick(nextDate), 180);
    }, 180);
  }

  function closeWithAnimation() {
    setClosing(true);
    window.setTimeout(onClose, 180);
  }

  return createPortal(
    <div className={[styles.backdrop, closing ? styles.closing : ''].join(' ')} onClick={closeWithAnimation}>
      <div className={[styles.dialog, closing ? styles.dialogClosing : ''].join(' ')} onClick={(event) => event.stopPropagation()}>
        <header>
          <button aria-label="이전 달" onClick={() => moveMonth(-1)} type="button"><Icon name="chevronLeft" /></button>
          <h2>{viewYear}년 {monthNames[viewMonth]}</h2>
          <div className={styles.headerActions}>
            <button aria-label="다음 달" onClick={() => moveMonth(1)} type="button"><Icon name="chevronRight" /></button>
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
            const date = formatDate(new Date(viewYear, viewMonth, day));
            const selected = date === pendingDate;
            const today = date === todayString();
            return (
              <button
                className={[selected ? styles.selected : '', today && !selected ? styles.today : ''].filter(Boolean).join(' ')}
                key={date}
                onClick={() => pickDay(day)}
                type="button"
              >
                {day}
              </button>
            );
          })}
        </div>
      </div>
    </div>,
    document.body
  );
}
