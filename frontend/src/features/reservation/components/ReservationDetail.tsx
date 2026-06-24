import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Badge } from '../../../shared/ui/Badge';
import { Button } from '../../../shared/ui/Button';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { Toast } from '../../../shared/ui/Toast';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { appStorage } from '../../../shared/storage/appStorage';
import { studyRooms, type StudyRoom, type TimeBooking } from '../data/reservationData';
import { slotLabel } from '../data/time';
import { usageRules } from '../data/usageRules';
import { roomSummaryToStudyRoom } from '../api/reservationMappers';
import { reservationRepository, type ReserveStatus } from '../api/reservationRepository';
import { isValidBlockRange, timeToBlock } from '../api/reservationBlocks';
import { AvailabilityBars } from './AvailabilityBars';
import { DatePickerDialog } from './DatePickerDialog';
import { ReservedUserSheet } from './ReservedUserSheet';
import styles from './ReservationDetail.module.css';

type ReservationDetailProps = {
  roomId?: string;
};

export function ReservationDetail({ roomId }: ReservationDetailProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const fallbackRoom = useMemo(() => studyRooms.find((item) => item.id === roomId) ?? studyRooms[0], [roomId]);
  const [room, setRoom] = useState<StudyRoom>(fallbackRoom);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null);
  const [selectedDay, setSelectedDay] = useState(8);
  const [selectedDate, setSelectedDate] = useState(searchParams.get('date') ?? '2023-09-08');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reservedBooking, setReservedBooking] = useState<TimeBooking | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);

  const summary = selection ? `${slotLabel(selection.start)} ~ ${slotLabel(selection.end + 1)}` : '시간대를 선택하세요';

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  useEffect(() => {
    let mounted = true;

    async function loadRoom() {
      if (!roomId) {
        return;
      }

      const result = await reservationRepository.getRoom(selectedDate, roomId);
      if (mounted && result.ok) {
        setRoom(roomSummaryToStudyRoom(result.data.room));
      }
    }

    setSelection(null);
    void loadRoom();

    return () => {
      mounted = false;
    };
  }, [roomId, selectedDate]);

  useEffect(() => {
    let mounted = true;
    void appStorage.getProfile().then((profile) => {
      if (mounted) {
        setIsAdmin(Boolean(profile?.isAdmin));
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  function handleSlotClick(index: number, booking?: TimeBooking) {
    if (booking) {
      if (booking.isMine) {
        navigate('/reservations/history');
        return;
      }
      setReservedBooking(booking);
      return;
    }

    if (!selection) {
      setSelection({ start: index, end: index });
      return;
    }

    if (selection.start === selection.end && selection.start !== index) {
      const next = { start: Math.min(selection.start, index), end: Math.max(selection.start, index) };
      if (!isValidBlockRange(slotToBlock(next.start), slotToBlock(next.end))) {
        flash('한 번에 최대 2시간까지만 예약할 수 있어요');
        return;
      }
      setSelection(next);
      return;
    }

    setSelection({ start: index, end: index });
  }

  async function submitReservation() {
    if (!selection || !roomId) {
      flash('시간을 선택하세요');
      return;
    }

    setSubmitting(true);
    setConfirmOpen(false);
    const requested = await reservationRepository.requestReserve({
      roomNo: roomId,
      date: selectedDate,
      startBlock: slotToBlock(selection.start),
      endBlock: slotToBlock(selection.end),
    });
    if (!requested.ok) {
      setSubmitting(false);
      navigate('/reservations/success', { state: { ok: false, message: requested.message } });
      return;
    }

    const status = await pollReserveStatus(requested.data.idx);
    setSubmitting(false);
    navigate('/reservations/success', {
      state: {
        ok: status === 1,
        status,
        room,
        date: selectedDate,
        time: summary,
        message: reserveStatusMessage(status),
      },
    });
  }

  async function reportBooking(booking: TimeBooking) {
    await nativeBridge.openExternalUrl(createReportUrl(room.name, selectedDate, `${booking.start} ~ ${booking.end}`));
  }

  async function runAdminTool(type: 'reserveCancel' | 'photoDelete' | 'photoExecpt', booking: TimeBooking) {
    if (!booking.idx) {
      flash('예약 정보를 찾을 수 없어요');
      return;
    }

    const text = type === 'reserveCancel' ? window.prompt('취소 사유를 입력하세요') : null;
    if (type === 'reserveCancel' && !text) {
      return;
    }

    const result = await reservationRepository.adminTool({ type, idx: booking.idx, text });
    flash(result.ok ? adminResultMessage(result.data) : result.message);
    setReservedBooking(null);
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
            {selectedDate} <Icon name="calendar" width="17" height="17" />
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

        <UsageRules />
      </section>

      <div className={styles.cta}>
        <Button
          disabled={!selection || submitting}
          onClick={() => (selection ? setConfirmOpen(true) : flash('시간을 선택하세요'))}
          type="button"
        >
          {submitting ? '예약 처리 중' : selection ? '이 시간으로 예약하기' : '시간을 선택하세요'}
        </Button>
      </div>
      {reservedBooking ? (
        <ReservedUserSheet
          booking={reservedBooking}
          isAdmin={isAdmin}
          onAdminTool={(type) => void runAdminTool(type, reservedBooking)}
          onClose={() => setReservedBooking(null)}
          onReport={() => void reportBooking(reservedBooking)}
        />
      ) : null}
      {pickerOpen ? (
        <DatePickerDialog
          onClose={() => setPickerOpen(false)}
          onPick={(day) => {
            setSelectedDay(day);
            setSelectedDate(`2023-09-${String(day).padStart(2, '0')}`);
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
            { label: '날짜', value: selectedDate },
            { label: '시간', value: summary },
          ]}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={() => {
            void submitReservation();
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

function UsageRules() {
  return (
    <div className={styles.rules}>
      <strong>이용 규칙</strong>
      <ul>
        {usageRules.map((rule) => (
          <li className={rule.strong ? styles.ruleStrong : ''} key={rule.text}>{rule.text}</li>
        ))}
      </ul>
    </div>
  );
}

function slotToBlock(slot: number) {
  return timeToBlock(slotLabel(slot));
}

async function pollReserveStatus(idx: number): Promise<ReserveStatus> {
  for (let count = 0; count < 20; count += 1) {
    const result = await reservationRepository.getReserveStatus(idx);
    if (result.ok && result.data.status !== 0) {
      return result.data.status;
    }

    await new Promise((resolve) => window.setTimeout(resolve, 500));
  }

  return 0;
}

function reserveStatusMessage(status: ReserveStatus) {
  if (status === 1) return '예약이 완료됐어요';
  if (status === 2) return '지난 날짜는 예약할 수 없어요';
  if (status === 3) return '지난 시간은 예약할 수 없어요';
  if (status === 4) return '예약 가능 시간이 아직 아니에요';
  if (status === 5) return '이미 예약된 시간이에요';
  if (status === 6) return '하루 최대 예약 가능 시간을 초과했어요';
  if (status === 7) return '같은 시간대에 이미 예약한 스터디룸이 있어요';
  return '예약 처리 중 문제가 발생했어요';
}

function createReportUrl(roomName: string, date: string, time: string) {
  const url = new URL('https://docs.google.com/forms/d/e/1FAIpQLSeCYo0oiuoK-3KNzKFnFLPFP43Bp4fRZq7ulTmxgoMUWGWz8g/viewform');
  url.searchParams.set('usp', 'pp_url');
  url.searchParams.set('entry.284506795', roomName);
  url.searchParams.set('entry.46856824', date);
  url.searchParams.set('entry.573216846', time);
  return url.toString();
}

function adminResultMessage(status: ReserveStatus) {
  if (status === 0) return '예약이 존재하지 않아요';
  if (status === 1) return '이미 취소된 예약이에요';
  if (status === 2) return '이미 종료된 예약이에요';
  if (status === 3) return '예약을 취소했어요';
  if (status === 4) return '인증 사진이 촬영되지 않았어요';
  if (status === 5) return '인증 사진을 삭제했어요';
  if (status === 6) return '인증 사진이 이미 촬영됐어요';
  if (status === 7) return '인증 사진 예외 처리했어요';
  return '관리자 작업을 처리했어요';
}
