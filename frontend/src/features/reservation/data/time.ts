import { endHour, startHour, type StudyRoom, type TimeBooking } from './reservationData';
import { isToday } from './dates';

export const slotCount = (endHour - startHour) * 2;
export const smallSlotWidth = 17;
export const smallSlotGap = 3;

export function toSlot(time: string) {
  const [hour, minute] = time.split(':').map(Number);
  return ((hour * 60 + minute) - startHour * 60) / 30;
}

export function slotLabel(index: number) {
  const total = startHour * 60 + index * 30;
  const hour = Math.floor(total / 60);
  const minute = total % 60;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

export function bookedSlots(room: StudyRoom) {
  const slots = new Map<number, TimeBooking>();

  room.bookings.forEach((booking) => {
    for (let index = toSlot(booking.start); index < toSlot(booking.end); index += 1) {
      slots.set(index, booking);
    }
  });

  return slots;
}

export const hourTicks = Array.from({ length: endHour - startHour }, (_, index) => {
  return `${String(startHour + index).padStart(2, '0')}:00`;
});

export function getCurrentMinute() {
  const now = new Date();
  return now.getHours() * 60 + now.getMinutes();
}

export function getNowPositionRatio() {
  const ratio = (getCurrentMinute() - startHour * 60) / ((endHour - startHour) * 60);
  return Math.min(Math.max(ratio, 0), 1);
}

export function getDefaultTimebarScrollLeft(date: string | undefined, viewportWidth = 0) {
  if (!isToday(date)) {
    return 0;
  }

  const trackWidth = slotCount * smallSlotWidth + (slotCount - 1) * smallSlotGap;
  return Math.max(0, getNowPositionRatio() * trackWidth - viewportWidth / 2);
}
