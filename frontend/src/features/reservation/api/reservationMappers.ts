import roomHero from '../../../../design/uploads/studyroom2abig.jpeg';
import roomThumb from '../../../../design/uploads/studyroom2a.jpeg';
import { type StudyRoom, type TimeBooking } from '../data/reservationData';
import { blockToTime } from './reservationBlocks';
import { type ReserveHistory, type RoomDetail, type RoomSummary } from './reservationRepository';

export function roomSummaryToStudyRoom(room: RoomSummary | RoomDetail): StudyRoom {
  return {
    id: String(room.no),
    name: room.name,
    capacity: `${room.capacity}인실`,
    location: room.location,
    amenities: 'tags' in room && room.tags ? room.tags.split(',').filter(Boolean) : [`${room.capacity}인실`, room.location],
    thumbnail: room.image || roomThumb,
    heroImage: 'bigImage' in room && room.bigImage ? room.bigImage : roomHero,
    bookings: room.reserves.map((reserve): TimeBooking => ({
      idx: reserve.idx,
      start: blockToTime(reserve.startBlock),
      end: blockToTime(reserve.endBlock + 1),
      name: reserve.studentInfo,
      studentId: '',
      department: '',
      people: 0,
      isMine: reserve.isMine,
    })),
  };
}

export function reserveToHistoryView(reserve: ReserveHistory) {
  const time = `${blockToTime(reserve.startBlock)} ~ ${blockToTime(reserve.endBlock + 1)}`;
  return {
    id: reserve.idx,
    room: reserve.roomByRoomNo.name,
    location: String(reserve.roomNo),
    date: reserve.date,
    time,
    duration: '',
    status: reserve.deletedAt ? '이용 완료' : '이용 대기',
    kind: reserve.deletedAt ? 'done' : 'active',
    cancelable: !reserve.deletedAt,
    verifyPhotoUrl: reserve.verifyPhotosByIdx[0]?.url,
    isContinuous: reserve.isContinuous,
  } as const;
}
