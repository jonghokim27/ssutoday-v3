import { Link } from 'react-router-dom';
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
};

export function StudyRoomCard({ room, timebarScrollLeft, onTimebarScroll }: StudyRoomCardProps) {
  const freeCount = slotCount - bookedSlots(room).size;
  const busy = freeCount < slotCount * 0.45;

  return (
    <Link to={`/reservations/${room.id}`}>
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
        <AvailabilityBars onScrollLeftChange={onTimebarScroll} room={room} scrollLeft={timebarScrollLeft} />
      </Card>
    </Link>
  );
}
