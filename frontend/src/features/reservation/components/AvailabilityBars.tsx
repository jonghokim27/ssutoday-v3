import { useEffect, useRef } from 'react';
import { isToday } from '../data/dates';
import { bookedSlots, getNowPositionRatio, hourTicks, smallSlotGap, smallSlotWidth, slotCount } from '../data/time';
import { type StudyRoom, type TimeBooking } from '../data/reservationData';
import { triggerHaptic } from '../../../shared/native/nativeBridge';
import styles from './AvailabilityBars.module.css';

type AvailabilityBarsProps = {
  room: StudyRoom;
  date?: string;
  large?: boolean;
  selectedStart?: number | null;
  selectedEnd?: number | null;
  onSlotClick?: (index: number, booking?: TimeBooking) => void;
  onCurrentSlotClick?: () => void;
  scrollLeft?: number;
  onScrollLeftChange?: (scrollLeft: number) => void;
};

export function AvailabilityBars({
  room,
  date,
  large = false,
  selectedStart = null,
  selectedEnd = null,
  onSlotClick,
  onCurrentSlotClick,
  scrollLeft,
  onScrollLeftChange,
}: AvailabilityBarsProps) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const booked = bookedSlots(room);
  const width = large ? 58 : smallSlotWidth;
  const gap = large ? 4 : smallSlotGap;
  const trackWidth = slotCount * width + (slotCount - 1) * gap;
  const nowPositionRatio = getNowPositionRatio();

  useEffect(() => {
    if (!scrollerRef.current) {
      return;
    }

    if (scrollLeft === undefined) {
      scrollerRef.current.scrollLeft = isToday(date) ? Math.max(0, nowPositionRatio * trackWidth - scrollerRef.current.clientWidth / 2) : 0;
      return;
    }

    if (Math.abs(scrollerRef.current.scrollLeft - scrollLeft) > 1) {
      scrollerRef.current.scrollLeft = scrollLeft;
    }
  }, [date, nowPositionRatio, scrollLeft, trackWidth]);

  useEffect(() => {
    const el = scrollerRef.current;
    if (!el || large) return;

    let timer: number;
    function handleScroll() {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        onScrollLeftChange?.(el!.scrollLeft);
      }, 32);
    }

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      el.removeEventListener('scroll', handleScroll);
      window.clearTimeout(timer);
    };
  }, [large, onScrollLeftChange]);

  return (
    <div
      className={[styles.scroller, large ? styles.largeScroller : ''].join(' ')}
      ref={scrollerRef}
    >
      <div className={styles.track} style={{ width: trackWidth }}>
        <div className={large ? styles.largeBars : styles.bars}>
          {Array.from({ length: slotCount }, (_, index) => {
            const isBooked = booked.has(index);
            const isMine = Boolean(booked.get(index)?.isMine);
            const slotState = getSlotTimeState(date, index);
            const selected = selectedStart !== null && selectedEnd !== null && index >= selectedStart && index <= selectedEnd;
            const className = [
              large ? styles.largeBar : styles.bar,
              isBooked ? styles.booked : styles.available,
              isMine ? styles.mine : '',
              slotState === 'past' ? styles.past : '',
              slotState === 'current' ? styles.current : '',
              selected ? styles.selected : '',
            ]
              .filter(Boolean)
              .join(' ');

            if (large) {
              return (
                <button
                  className={className}
                  disabled={slotState === 'past' && !isBooked}
                  key={index}
                  onClick={() => {
                    triggerHaptic('heavy');
                    if (slotState === 'current') {
                      onCurrentSlotClick?.();
                    }
                    onSlotClick?.(index, booked.get(index));
                  }}
                  style={{ width }}
                  type="button"
                />
              );
            }

            return <span className={className} key={index} style={{ width }} />;
          })}
        </div>
        {isToday(date) ? <span className={styles.now} style={{ left: `${nowPositionRatio * 100}%` }} /> : null}
        <div className={styles.ticks}>
          {hourTicks.map((tick) => (
            <span key={tick}>{tick}</span>
          ))}
        </div>
      </div>
    </div>
  );
}

function getSlotTimeState(date: string | undefined, index: number) {
  if (!date) {
    return 'future';
  }

  const selected = startOfDay(new Date(date));
  const today = startOfDay(new Date());
  if (selected < today) {
    return 'past';
  }

  if (selected > today) {
    return 'future';
  }

  const start = new Date(selected);
  start.setHours(6, index * 30, 0, 0);
  const end = new Date(start.getTime() + 30 * 60 * 1000);
  const now = new Date();
  if (end <= now) {
    return 'past';
  }

  if (start <= now && now < end) {
    const minutesPassed = (now.getTime() - start.getTime()) / (60 * 1000);
    return minutesPassed >= 15 ? 'past' : 'current';
  }

  return 'future';
}

function startOfDay(date: Date) {
  const value = new Date(date);
  value.setHours(0, 0, 0, 0);
  return value;
}
