export const RESERVATION_START_HOUR = 6;
export const RESERVATION_END_HOUR = 22;
export const MAX_RESERVATION_BLOCKS = 4;

export function timeToBlock(time: string) {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 2 + (minute >= 30 ? 1 : 0);
}

export function blockToTime(block: number) {
  const hour = Math.floor(block / 2);
  const minute = block % 2 === 0 ? '00' : '30';
  return `${String(hour).padStart(2, '0')}:${minute}`;
}

export function isValidBlockRange(startBlock: number, endBlock: number) {
  if (endBlock < startBlock) {
    return false;
  }

  return endBlock - startBlock + 1 <= MAX_RESERVATION_BLOCKS;
}
