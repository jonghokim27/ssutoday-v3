import { useEffect, useRef } from 'react';
import { bookedSlots, hourTicks, nowPositionRatio, slotCount } from '../data/time';
import { type StudyRoom, type TimeBooking } from '../data/reservationData';
import styles from './AvailabilityBars.module.css';

type AvailabilityBarsProps = {
  room: StudyRoom;
  large?: boolean;
  selectedStart?: number | null;
  selectedEnd?: number | null;
  onSlotClick?: (index: number, booking?: TimeBooking) => void;
  scrollLeft?: number;
  onScrollLeftChange?: (scrollLeft: number) => void;
};

export function AvailabilityBars({
  room,
  large = false,
  selectedStart = null,
  selectedEnd = null,
  onSlotClick,
  scrollLeft,
  onScrollLeftChange,
}: AvailabilityBarsProps) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const booked = bookedSlots(room);
  const width = large ? 58 : 17;

  useEffect(() => {
    if (large || scrollLeft === undefined || !scrollerRef.current) {
      return;
    }

    if (Math.abs(scrollerRef.current.scrollLeft - scrollLeft) > 1) {
      scrollerRef.current.scrollLeft = scrollLeft;
    }
  }, [large, scrollLeft]);

  return (
    <div
      className={[styles.scroller, large ? styles.largeScroller : ''].join(' ')}
      onScroll={(event) => {
        if (!large) {
          onScrollLeftChange?.(event.currentTarget.scrollLeft);
        }
      }}
      ref={scrollerRef}
    >
      <div className={styles.track} style={{ width: large ? 1612 : 521 }}>
        <div className={large ? styles.largeBars : styles.bars}>
          {Array.from({ length: slotCount }, (_, index) => {
            const isBooked = booked.has(index);
            const selected = selectedStart !== null && selectedEnd !== null && index >= selectedStart && index <= selectedEnd;
            const className = [
              large ? styles.largeBar : styles.bar,
              isBooked ? styles.booked : styles.available,
              selected ? styles.selected : '',
            ]
              .filter(Boolean)
              .join(' ');

            if (large) {
              return (
                <button
                  className={className}
                  disabled={false}
                  key={index}
                  onClick={() => onSlotClick?.(index, booked.get(index))}
                  style={{ width }}
                  type="button"
                />
              );
            }

            return <span className={className} key={index} style={{ width }} />;
          })}
        </div>
        <span className={styles.now} style={{ left: `${nowPositionRatio * 100}%` }} />
        <div className={styles.ticks}>
          {hourTicks.map((tick) => (
            <span key={tick}>{tick}</span>
          ))}
        </div>
      </div>
    </div>
  );
}
