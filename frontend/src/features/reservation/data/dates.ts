const weekdayLabels = ['일', '월', '화', '수', '목', '금', '토'];

export type DateStripItem = {
  date: string;
  day: string;
  dow: string;
  label: string;
};

export function todayString() {
  return formatDate(new Date());
}

export function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function parseDate(value: string | null | undefined) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return new Date();
  }

  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return Number.isNaN(date.getTime()) ? new Date() : date;
}

export function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function isSameDate(left: string | undefined, right: string | undefined) {
  return Boolean(left && right && left === right);
}

export function isToday(date: string | undefined) {
  return isSameDate(date, todayString());
}

export function formatDateLabel(dateString: string) {
  const date = parseDate(dateString);
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일(${weekdayLabels[date.getDay()]})`;
}

export function getDateStrip(selectedDate: string, length = 7): DateStripItem[] {
  const today = parseDate(todayString());
  const selected = parseDate(selectedDate);
  const daysFromToday = Math.floor((startOfDay(selected).getTime() - startOfDay(today).getTime()) / 86_400_000);
  const startOffset = daysFromToday >= 0 && daysFromToday < length ? 0 : daysFromToday - Math.floor(length / 2);
  const startDate = addDays(today, startOffset);

  return Array.from({ length }, (_, index) => {
    const date = addDays(startDate, index);
    const formatted = formatDate(date);
    return {
      date: formatted,
      day: String(date.getDate()),
      dow: weekdayLabels[date.getDay()],
      label: formatDateLabel(formatted),
    };
  });
}

function startOfDay(date: Date) {
  const value = new Date(date);
  value.setHours(0, 0, 0, 0);
  return value;
}
