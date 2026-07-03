import { Link } from 'react-router-dom';
import { useCallback, useEffect, useRef, useState } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { IconButton } from '../../../shared/ui/IconButton';
import { Icon } from '../../../shared/ui/Icon';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { appStorage } from '../../../shared/storage/appStorage';
import { reservationRepository } from '../api/reservationRepository';
import { roomSummaryToStudyRoom } from '../api/reservationMappers';
import { todayString } from '../data/dates';
import { type StudyRoom } from '../data/reservationData';
import { DateStrip } from './DateStrip';
import { DatePickerDialog } from './DatePickerDialog';
import { StudyRoomCard } from './StudyRoomCard';
import styles from './ReservationHome.module.css';

export function ReservationHome() {
  const safePath = useSafeAreaPath();
  const [selectedDate, setSelectedDate] = useState(
    () => sessionStorage.getItem('reservation_selected_date') ?? todayString()
  );
  const [pickerOpen, setPickerOpen] = useState(false);
  const [timebarScrollLeft, setTimebarScrollLeft] = useState<number | undefined>(undefined);
  const [rooms, setRooms] = useState<StudyRoom[]>([]);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(true);
  const screenRef = useRef<HTMLDivElement>(null);
  const scrollersRef = useRef<Set<HTMLDivElement>>(new Set());
  // Tracks each scroller's last-known position so syncing never has to read
  // `el.scrollLeft` back from the DOM: reading right after writing forces a
  // synchronous layout recalculation, and doing that across several sibling
  // cards every frame was the main source of dropped frames while dragging.
  const lastKnownScrollRef = useRef<Map<HTMLDivElement, number>>(new Map());
  const scrollPersistTimeoutRef = useRef<number | undefined>(undefined);

  const registerTimebarScroller = useCallback((el: HTMLDivElement) => {
    scrollersRef.current.add(el);
    lastKnownScrollRef.current.set(el, el.scrollLeft);
    return () => {
      scrollersRef.current.delete(el);
      lastKnownScrollRef.current.delete(el);
    };
  }, []);

  const syncTimebarScroll = useCallback((scrollLeft: number, source: HTMLDivElement) => {
    lastKnownScrollRef.current.set(source, scrollLeft);

    scrollersRef.current.forEach((el) => {
      if (el === source || lastKnownScrollRef.current.get(el) === scrollLeft) {
        return;
      }
      el.scrollLeft = scrollLeft;
      lastKnownScrollRef.current.set(el, scrollLeft);
    });

    window.clearTimeout(scrollPersistTimeoutRef.current);
    scrollPersistTimeoutRef.current = window.setTimeout(() => {
      setTimebarScrollLeft(scrollLeft);
    }, 200);
  }, []);

  useEffect(() => {
    return () => window.clearTimeout(scrollPersistTimeoutRef.current);
  }, []);

  // 스크롤 위치 복원: 데이터 로드 완료 후 복원
  useEffect(() => {
    if (!loading) {
      const saved = sessionStorage.getItem('reservation_scroll');
      if (saved) {
        requestAnimationFrame(() => {
          screenRef.current?.scrollTo({ top: Number(saved) });
          sessionStorage.removeItem('reservation_scroll');
        });
      }
    }
  }, [loading]);

  useEffect(() => {
    let mounted = true;

    async function loadProfile() {
      const profile = await appStorage.getProfile();
      if (profile?.name && mounted) {
        const n = profile.name;
        setName(n.slice(-Math.min(n.length - 1, 3)));
      }
    }

    async function refreshRooms(showLoading: boolean) {
      if (showLoading) {
        setLoading(true);
      }

      const result = await reservationRepository.listRooms(selectedDate);
      if (mounted && result.ok) {
        setRooms(result.data.rooms.map(roomSummaryToStudyRoom));
      }
      if (showLoading && mounted) {
        setLoading(false);
      }
    }

    void loadProfile();
    void refreshRooms(true);

    const intervalId = window.setInterval(() => {
      void refreshRooms(false);
    }, 1000);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
    };
  }, [selectedDate]);

  function pickDate(date: string) {
    sessionStorage.setItem('reservation_selected_date', date);
    sessionStorage.removeItem('reservation_scroll');
    setSelectedDate(date);
    setTimebarScrollLeft(undefined);
  }

  return (
    <div
      className={styles.screen}
      ref={screenRef}
      onScroll={(e) => sessionStorage.setItem('reservation_scroll', String((e.currentTarget as HTMLDivElement).scrollTop))}
    >
      <BrandHeader
        action={
          <Link to={safePath('/reservations/history')}>
            <IconButton aria-label="예약 내역"><Icon name="refresh" /></IconButton>
          </Link>
        }
        sticky
        title="스터디룸 예약"
      />
      <section className={styles.hero}>
        <h1>{name}님, 어디서 공부할까요?</h1>
        <p>실시간으로 빈 시간을 확인하고 바로 예약할 수 있어요</p>
      </section>
      <DateStrip onOpenPicker={() => setPickerOpen(true)} onPickDate={pickDate} selectedDate={selectedDate} />
      <section className={styles.list}>
        {loading ? <LoadingState label="스터디룸 현황을 불러오는 중" /> : null}
        {!loading ? rooms.map((room) => (
          <StudyRoomCard
            key={room.id}
            date={selectedDate}
            onTimebarScroll={syncTimebarScroll}
            registerScroller={registerTimebarScroller}
            room={room}
            timebarScrollLeft={timebarScrollLeft}
          />
        )) : null}
        {!loading && rooms.length === 0 ? (
          <div className={styles.empty}>
            <div className={styles.emptyIcon} aria-hidden="true">
              <Icon name="alertTriangle" />
            </div>
            <strong>이용 가능한 스터디룸이 없어요</strong>
            <p>다른 시설을 이용해주세요</p>
          </div>
        ) : null}
      </section>
      {pickerOpen ? (
        <DatePickerDialog
          onClose={() => setPickerOpen(false)}
          onPick={(date) => {
            pickDate(date);
            setPickerOpen(false);
          }}
          selectedDate={selectedDate}
        />
      ) : null}
    </div>
  );
}
