import { Link } from 'react-router-dom';
import { useState } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { IconButton } from '../../../shared/ui/IconButton';
import { Icon } from '../../../shared/ui/Icon';
import { studyRooms } from '../data/reservationData';
import { DateStrip } from './DateStrip';
import { DatePickerDialog } from './DatePickerDialog';
import { StudyRoomCard } from './StudyRoomCard';
import styles from './ReservationHome.module.css';

export function ReservationHome() {
  const [selectedDay, setSelectedDay] = useState(8);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [timebarScrollLeft, setTimebarScrollLeft] = useState(0);

  return (
    <div className={styles.screen}>
      <BrandHeader
        action={
          <Link to="/reservations/history">
            <IconButton aria-label="예약 내역"><Icon name="refresh" /></IconButton>
          </Link>
        }
        sticky
        title="스터디룸 예약"
      />
      <section className={styles.hero}>
        <h1>종호님, 어디서 공부할까요?</h1>
        <p>실시간으로 빈 시간을 확인하고 바로 예약할 수 있어요</p>
      </section>
      <DateStrip onOpenPicker={() => setPickerOpen(true)} onPickDay={setSelectedDay} selectedDay={selectedDay} />
      <section className={styles.list}>
        {studyRooms.map((room) => (
          <StudyRoomCard
            key={room.id}
            onTimebarScroll={setTimebarScrollLeft}
            room={room}
            timebarScrollLeft={timebarScrollLeft}
          />
        ))}
      </section>
      {pickerOpen ? (
        <DatePickerDialog
          onClose={() => setPickerOpen(false)}
          onPick={(day) => {
            setSelectedDay(day);
            setPickerOpen(false);
          }}
          selectedDay={selectedDay}
        />
      ) : null}
    </div>
  );
}
