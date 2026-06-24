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

export const emptyStudyRoom: StudyRoom = {
  id: '',
  name: '',
  capacity: '',
  location: '',
  amenities: [],
  thumbnail: '',
  heroImage: '',
  bookings: [],
};

export const startHour = 6;
export const endHour = 22;
