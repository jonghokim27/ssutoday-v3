const roomThumb = '';
const roomHero = '';

export type TimeBooking = {
  idx?: number;
  start: string;
  end: string;
  name: string;
  studentId: string;
  department: string;
  people: number;
  isMine?: boolean;
  verifyPhotoUrl?: string | null;
};

export type StudyRoom = {
  id: string;
  name: string;
  capacity: string;
  location: string;
  amenities: string[];
  thumbnail: string;
  heroImage: string;
  bookings: TimeBooking[];
};

export type ReservationHistoryItem = {
  id: number;
  room: string;
  location: string;
  date: string;
  time: string;
  duration: string;
  status: '이용 중' | '이용 대기' | '이용 완료';
  kind: 'active' | 'done';
  shotDeadline?: string;
  cancelable?: boolean;
};

export const studyRooms: StudyRoom[] = [
  {
    id: '2A',
    name: '스터디룸 2A',
    capacity: '10인실',
    location: '2층 교수연구동',
    amenities: ['10인실', '2층 교수연구동', '콘센트 6구', '칠판', 'TV 모니터'],
    thumbnail: roomThumb,
    heroImage: roomHero,
    bookings: [
      { start: '10:00', end: '11:30', name: '김민서', studentId: '20211043', department: '컴퓨터학부', people: 6 },
      { start: '13:00', end: '14:00', name: '이서연', studentId: '20193387', department: '경영학과', people: 4 },
      { start: '17:00', end: '18:30', name: '박도윤', studentId: '20221125', department: '전자정보공학', people: 8 },
      { start: '19:30', end: '20:30', name: '정하윤', studentId: '20204461', department: '영어영문학과', people: 3 },
    ],
  },
  {
    id: '2B',
    name: '스터디룸 2B',
    capacity: '10인실',
    location: '2층 중앙',
    amenities: ['10인실', '2층 중앙', '콘센트 4구', '화이트보드'],
    thumbnail: roomThumb,
    heroImage: roomHero,
    bookings: [
      { start: '09:30', end: '11:00', name: '최지우', studentId: '20198802', department: '화학공학과', people: 5 },
      { start: '11:30', end: '13:00', name: '강시우', studentId: '20221760', department: '기계공학부', people: 7 },
      { start: '14:00', end: '15:30', name: '오서준', studentId: '20205534', department: '미디어경영', people: 4 },
      { start: '17:00', end: '19:00', name: '한지민', studentId: '20193019', department: '산업정보시스템', people: 9 },
      { start: '21:00', end: '22:00', name: '서예린', studentId: '20226628', department: '건축학부', people: 3 },
    ],
  },
  {
    id: '2C',
    name: '스터디룸 2C',
    capacity: '8인실',
    location: '2층 계단 앞',
    amenities: ['8인실', '2층 계단 앞', '콘센트 4구'],
    thumbnail: roomThumb,
    heroImage: roomHero,
    bookings: [
      { start: '15:00', end: '16:00', name: '오수빈', studentId: '20214455', department: '통계학과', people: 5 },
    ],
  },
];

export const dateStrip = [
  { dow: '수', day: '6' },
  { dow: '목', day: '7' },
  { dow: '금', day: '8' },
  { dow: '토', day: '9' },
  { dow: '일', day: '10' },
  { dow: '월', day: '11' },
];

export const reservationHistory: ReservationHistoryItem[] = [
  {
    id: 1,
    room: '스터디룸 2A',
    location: '2층 교수연구동',
    date: '2023년 11월 14일 화',
    time: '17:30 ~ 18:00',
    duration: '30분',
    status: '이용 중',
    kind: 'active',
    shotDeadline: '17:40',
  },
  {
    id: 2,
    room: '스터디룸 2B',
    location: '2층 중앙',
    date: '2023년 11월 14일 화',
    time: '20:00 ~ 21:00',
    duration: '1시간',
    status: '이용 대기',
    kind: 'active',
    cancelable: true,
  },
  {
    id: 3,
    room: '스터디룸 2C',
    location: '2층 계단 앞',
    date: '2023년 11월 10일 금',
    time: '15:00 ~ 17:00',
    duration: '2시간',
    status: '이용 완료',
    kind: 'done',
  },
  {
    id: 4,
    room: '스터디룸 2B',
    location: '2층 중앙',
    date: '2023년 11월 6일 월',
    time: '14:00 ~ 16:00',
    duration: '2시간',
    status: '이용 완료',
    kind: 'done',
  },
];

export const startHour = 6;
export const endHour = 22;
