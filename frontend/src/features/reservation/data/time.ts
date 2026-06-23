import { currentMinute, endHour, startHour, type StudyRoom, type TimeBooking } from './reservationData';

export const slotCount = (endHour - startHour) * 2;

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

export const nowPositionRatio = (currentMinute - startHour * 60) / ((endHour - startHour) * 60);
