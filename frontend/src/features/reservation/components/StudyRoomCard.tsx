import { Link } from 'react-router-dom';
import { useRef } from 'react';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Badge } from '../../../shared/ui/Badge';
import { Card } from '../../../shared/ui/Card';
import { bookedSlots, slotCount } from '../data/time';
import { type StudyRoom } from '../data/reservationData';
import { AvailabilityBars } from './AvailabilityBars';
import styles from './StudyRoomCard.module.css';

type StudyRoomCardProps = {
  room: StudyRoom;
  timebarScrollLeft: number;
  onTimebarScroll: (scrollLeft: number) => void;
  date?: string;
};

export function StudyRoomCard({ room, timebarScrollLeft, onTimebarScroll, date }: StudyRoomCardProps) {
  const safePath = useSafeAreaPath();
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null);
  const pointerMovedRef = useRef(false);
  const freeCount = slotCount - bookedSlots(room).size;
  const busy = freeCount < slotCount * 0.45;
  const roomPath = date ? `/reservations/${room.id}?date=${date}` : `/reservations/${room.id}`;

  return (
    <Link
      className={styles.link}
      onClick={(event) => {
        if (pointerMovedRef.current) {
          event.preventDefault();
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
          <Badge tone={busy ? 'orange' : 'green'}>{busy ? '혼잡' : '여유'}</Badge>
        </div>
        <AvailabilityBars date={date} onScrollLeftChange={onTimebarScroll} room={room} scrollLeft={timebarScrollLeft} />
      </Card>
    </Link>
  );
}
