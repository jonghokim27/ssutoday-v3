import { Link } from 'react-router-dom';
import { useRef } from 'react';
import { triggerHaptic } from '../../../shared/native/nativeBridge';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Badge } from '../../../shared/ui/Badge';
import { Card } from '../../../shared/ui/Card';
import { bookedSlots, slotCount, toSlot } from '../data/time';
import { type StudyRoom } from '../data/reservationData';
import { AvailabilityBars } from './AvailabilityBars';
import styles from './StudyRoomCard.module.css';

const NOON_SLOT = toSlot('12:00');

type StudyRoomCardProps = {
  room: StudyRoom;
  timebarScrollLeft?: number;
  onTimebarScroll: (scrollLeft: number) => void;
  registerScroller?: (el: HTMLDivElement) => () => void;
  date?: string;
};

export function StudyRoomCard({ room, timebarScrollLeft, onTimebarScroll, registerScroller, date }: StudyRoomCardProps) {
  const safePath = useSafeAreaPath();
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null);
  const pointerMovedRef = useRef(false);
  const afternoonSlotCount = slotCount - NOON_SLOT;
  const bookedAfternoonCount = Array.from(bookedSlots(room).keys()).filter((slot) => slot >= NOON_SLOT).length;
  const freeRatio = afternoonSlotCount > 0 ? (afternoonSlotCount - bookedAfternoonCount) / afternoonSlotCount : 0;
  const congestion = congestionLevel(freeRatio);
  const roomPath = date ? `/reservations/${room.id}?date=${date}` : `/reservations/${room.id}`;

  return (
    <Link
      className={styles.link}
      onClick={(event) => {
        if (pointerMovedRef.current) {
          event.preventDefault();
        } else {
          triggerHaptic('medium');
        }
      }}
      onPointerDown={(event) => {
        pointerStartRef.current = { x: event.clientX, y: event.clientY };
        pointerMovedRef.current = false;
      }}
      onPointerMove={(event) => {
        if (!pointerStartRef.current) {
          return;
        }

        const deltaX = Math.abs(event.clientX - pointerStartRef.current.x);
        const deltaY = Math.abs(event.clientY - pointerStartRef.current.y);
        if (deltaX > 8 || deltaY > 8) {
          pointerMovedRef.current = true;
        }
      }}
      onPointerUp={() => {
        pointerStartRef.current = null;
      }}
      to={safePath(roomPath)}
    >
      <Card>
        <div className={styles.head}>
          <img alt={room.name} className={styles.thumb} src={room.thumbnail} />
          <div className={styles.body}>
            <div className={styles.badges}>
              <Badge tone="blue">{room.capacity}</Badge>
              <Badge>{room.location}</Badge>
            </div>
            <strong className={styles.name}>{room.name}</strong>
          </div>
          <Badge tone={congestion.tone}>{congestion.label}</Badge>
        </div>
        <AvailabilityBars date={date} onScrollLeftChange={onTimebarScroll} registerScroller={registerScroller} room={room} scrollLeft={timebarScrollLeft} />
      </Card>
    </Link>
  );
}

function congestionLevel(freeRatio: number): { label: string; tone: 'green' | 'orange' | 'red' } {
  if (freeRatio >= 0.55) return { label: '여유', tone: 'green' };
  if (freeRatio >= 0.25) return { label: '보통', tone: 'orange' };
  return { label: '혼잡', tone: 'red' };
}
