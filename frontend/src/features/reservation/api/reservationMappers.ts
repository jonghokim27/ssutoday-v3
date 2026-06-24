import { departmentCodeToName } from '../../../shared/utils/department';
import { type StudyRoom, type TimeBooking } from '../data/reservationData';
import { blockToTime } from './reservationBlocks';
import { type ReserveHistory, type RoomDetail, type RoomSummary } from './reservationRepository';

export type ReserveHistoryState = 'cancelled' | 'waiting' | 'using' | 'completed';

export function roomSummaryToStudyRoom(room: RoomSummary | RoomDetail): StudyRoom {
  return {
    id: String(room.no),
    name: room.name,
    capacity: `${room.capacity}인실`,
    location: room.location,
    amenities: 'tags' in room && room.tags ? room.tags.split(',').filter(Boolean) : [`${room.capacity}인실`, room.location],
    thumbnail: room.image || '',
    heroImage: 'bigImage' in room && room.bigImage ? room.bigImage : room.image || '',
    bookings: room.reserves.map((reserve): TimeBooking => ({
      idx: reserve.idx,
      start: blockToTime(reserve.startBlock),
      end: blockToTime(reserve.endBlock + 1),
      name: reserve.studentInfo.name,
      studentId: reserve.studentInfo.studentId,
      department: departmentCodeToName(reserve.studentInfo.major),
      people: 0,
      isMine: reserve.isMine,
      verifyPhotoUrl: reserve.verifyPhotoUrl ?? null,
    })),
  };
}

export function reserveToHistoryView(reserve: ReserveHistory) {
  const startTime = blockToTime(reserve.startBlock);
  const endTime = blockToTime(reserve.endBlock + 1);
  const time = `${startTime} ~ ${endTime}`;
  const state = getReserveHistoryState(reserve);
  const photo = reserve.verifyPhotosByIdx[0];

  return {
    id: reserve.idx,
    roomNo: reserve.roomNo,
    room: reserve.roomByRoomNo.name,
    location: String(reserve.roomNo),
    rawDate: reserve.date,
    date: formatReserveDate(reserve.date),
    time,
    duration: reserveDurationLabel(reserve.startBlock, reserve.endBlock),
    state,
    status: reserveStatusLabel(state),
    kind: state === 'waiting' || state === 'using' ? 'active' : 'done',
    cancelable: state === 'waiting',
    canDone: state === 'using',
    canRebook: state === 'cancelled',
    canShootPhoto: state === 'using' && !photo && !reserve.isContinuous,
    photoExempted: state === 'using' && !photo && reserve.isContinuous,
    canViewPhoto: (state === 'using' || state === 'completed') && Boolean(photo),
    photoMissing: state === 'completed' && !photo,
    verifyPhotoUrl: photo?.url,
    verifyPhotoCreatedAt: photo?.createdAt ? formatDateTime(photo.createdAt) : null,
    deletedAt: reserve.deletedAt,
    deletedAtLabel: reserve.deletedAt ? formatDateTime(reserve.deletedAt) : null,
    deletedReason: reserve.deletedReason,
    isContinuous: reserve.isContinuous,
    shotDeadline: getShotDeadline(reserve),
  };
}

export function reserveDurationLabel(startBlock: number, endBlock: number) {
  const blocks = endBlock - startBlock + 1;
  if (blocks === 1) return '30분';
  if (blocks === 2) return '1시간';
  if (blocks === 3) return '1시간 30분';
  if (blocks === 4) return '2시간';
  return `${blocks * 30}분`;
}

function getReserveHistoryState(reserve: ReserveHistory): ReserveHistoryState {
  if (reserve.deletedAt !== null) {
    return 'cancelled';
  }

  const now = new Date();
  const start = dateWithBlock(reserve.date, reserve.startBlock);
  const end = dateWithBlock(reserve.date, reserve.endBlock + 1);

  if (now < start) {
    return 'waiting';
  }

  if (now < end) {
    return 'using';
  }

  return 'completed';
}

function reserveStatusLabel(state: ReserveHistoryState) {
  if (state === 'cancelled') return '취소됨';
  if (state === 'waiting') return '이용 대기';
  if (state === 'using') return '이용중';
  return '이용완료';
}

function getShotDeadline(reserve: ReserveHistory) {
  const start = dateWithBlock(reserve.date, reserve.startBlock);
  const createdAt = new Date(reserve.createdAt);
  const base = Number.isNaN(createdAt.getTime()) || createdAt <= start ? start : createdAt;
  return formatTime(new Date(base.getTime() + 10 * 60 * 1000));
}

function dateWithBlock(date: string, block: number) {
  const value = new Date(date);
  const [hour, minute] = blockToTime(block).split(':').map(Number);
  value.setHours(hour, minute, 0, 0);
  return value;
}

function formatReserveDate(date: string) {
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) {
    return date;
  }

  return `${value.getFullYear()}년 ${value.getMonth() + 1}월 ${value.getDate()}일(${dayOfWeekHan(value.getDay())})`;
}

function formatDateTime(date: string) {
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) {
    return date;
  }

  return `${value.getFullYear()}.${String(value.getMonth() + 1).padStart(2, '0')}.${String(value.getDate()).padStart(2, '0')} ${formatTime(value)}:${String(value.getSeconds()).padStart(2, '0')}`;
}

function formatTime(date: Date) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function dayOfWeekHan(day: number) {
  return ['일', '월', '화', '수', '목', '금', '토'][day] ?? '';
}
