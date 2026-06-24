export function formatKoreanDateTime(value: string | number | Date) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return `${date.getFullYear()}년 ${pad(date.getMonth() + 1)}월 ${pad(date.getDate())}일 ${pad(date.getHours())}시 ${pad(date.getMinutes())}분`;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}
