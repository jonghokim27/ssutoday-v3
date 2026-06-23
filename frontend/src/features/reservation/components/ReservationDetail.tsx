import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from '../../../shared/ui/Badge';
import { Button } from '../../../shared/ui/Button';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { Toast } from '../../../shared/ui/Toast';
import { studyRooms, type StudyRoom, type TimeBooking } from '../data/reservationData';
import { slotLabel } from '../data/time';
import { AvailabilityBars } from './AvailabilityBars';
import { DatePickerDialog } from './DatePickerDialog';
import { ReservedUserSheet } from './ReservedUserSheet';
import styles from './ReservationDetail.module.css';

type ReservationDetailProps = {
  roomId?: string;
};

export function ReservationDetail({ roomId }: ReservationDetailProps) {
  const navigate = useNavigate();
  const room = useMemo(() => studyRooms.find((item) => item.id === roomId) ?? studyRooms[0], [roomId]);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>({ start: 17, end: 18 });
  const [selectedDay, setSelectedDay] = useState(8);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reservedBooking, setReservedBooking] = useState<TimeBooking | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [toast, setToast] = useState('');

  const summary = selection ? `${slotLabel(selection.start)} ~ ${slotLabel(selection.end + 1)}` : '시간대를 선택하세요';

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  function handleSlotClick(index: number, booking?: TimeBooking) {
    if (booking) {
      setReservedBooking(booking);
      return;
    }

    if (!selection) {
      setSelection({ start: index, end: index });
      return;
    }

    if (selection.start === selection.end && selection.start !== index) {
      setSelection({ start: Math.min(selection.start, index), end: Math.max(selection.start, index) });
      return;
    }

    setSelection({ start: index, end: index });
  }

  return (
    <div className={styles.screen}>
      <RoomHero room={room} onBack={() => navigate('/reservations')} />
      <section className={styles.content}>
        <div className={styles.amenities}>
          {room.amenities.map((amenity) => (
            <Badge key={amenity}>{amenity}</Badge>
          ))}
        </div>

        <div className={styles.dateRow}>
          <button className={styles.dateButton} onClick={() => setPickerOpen(true)} type="button">
            2023년 9월 {selectedDay}일(금) <Icon name="calendar" width="17" height="17" />
          </button>
          <span className={styles.live}><i /> 지금 18:40</span>
        </div>
        <p className={styles.guide}>한 칸은 30분입니다. 예약된 시간은 선택할 수 없어요.</p>

        <AvailabilityBars large onSlotClick={handleSlotClick} room={room} selectedEnd={selection?.end ?? null} selectedStart={selection?.start ?? null} />

        <div className={styles.legend}>
          <span><i className={styles.booked} /> 예약됨</span>
          <span><i className={styles.free} /> 빈 시간</span>
          <span><i className={styles.selected} /> 선택</span>
        </div>

        <div className={styles.summary}>
          <div className={styles.summaryBox}>
            <span>{selection ? '선택된 시간' : '시간 선택'}</span>
            <strong>{summary}</strong>
          </div>
          <button className={styles.reset} onClick={() => setSelection(null)} type="button">
            <Icon name="refresh" />
            <span>초기화</span>
          </button>
        </div>

        <div className={styles.rules}>
          <strong>이용 규칙</strong>
          <ul>
            <li>최소 이용 인원은 3명입니다.</li>
            <li>예약 취소는 이용 시작 전에만 가능합니다.</li>
            <li>종료 5분 전에는 자리를 정리해 주세요.</li>
          </ul>
        </div>
      </section>

      <div className={styles.cta}>
        <Button
          disabled={!selection}
          onClick={() => (selection ? setConfirmOpen(true) : flash('시간을 선택하세요'))}
          type="button"
        >
          {selection ? '이 시간으로 예약하기' : '시간을 선택하세요'}
        </Button>
      </div>
      {reservedBooking ? <ReservedUserSheet booking={reservedBooking} onClose={() => setReservedBooking(null)} /> : null}
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
      {confirmOpen && selection ? (
        <ConfirmDialog
          confirmLabel="예약 확정"
          details={[
            { label: '스터디룸', value: room.name },
            { label: '날짜', value: `2023년 9월 ${selectedDay}일(금)` },
            { label: '시간', value: summary },
          ]}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={() => {
            setConfirmOpen(false);
            navigate('/reservations/success');
          }}
          title="이 시간으로 예약할까요?"
        />
      ) : null}
      <Toast message={toast} />
    </div>
  );
}

type RoomHeroProps = {
  room: StudyRoom;
  onBack: () => void;
};

function RoomHero({ room, onBack }: RoomHeroProps) {
  return (
    <header className={styles.hero}>
      <img alt={room.name} src={room.heroImage} />
      <div className={styles.heroOverlay} />
      <IconButton className={styles.back} onClick={onBack} type="button"><Icon name="arrowLeft" /></IconButton>
      <h1>{room.name}</h1>
    </header>
  );
}
