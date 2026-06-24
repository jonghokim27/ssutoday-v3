import { apiClient } from '../../../shared/api/apiClient';
import { apiFailure, apiSuccess, type ApiResult } from '../../../shared/api/types';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { getRecaptchaToken } from '../../../shared/recaptcha/recaptcha';
import { blockToTime, timeToBlock } from './reservationBlocks';

export type ReserveInRoom = {
  idx: number;
  startBlock: number;
  endBlock: number;
  studentInfo: StudentInfo;
  isMine?: boolean;
  verifyPhotoUrl?: string;
  [key: string]: unknown;
};

export type StudentInfo = {
  studentId: string;
  name: string;
  major: string;
}

export type RoomSummary = {
  no: number | string;
  name: string;
  capacity: number;
  location: string;
  image: string;
  reserves: ReserveInRoom[];
};

export type RoomDetail = RoomSummary & {
  tags: string;
  bigImage: string;
};

export type ReserveHistory = {
  idx: number;
  roomNo: number | string;
  date: string;
  startBlock: number;
  endBlock: number;
  createdAt: string;
  deletedAt: string | null;
  deletedReason: string | null;
  isContinuous: boolean;
  roomByRoomNo: { name: string };
  verifyPhotosByIdx: Array<{ url: string; createdAt: string }>;
};

export type ReserveStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7;

export type ReservationRepository = {
  listRooms(date: string): Promise<ApiResult<{ rooms: RoomSummary[] }>>;
  getRoom(date: string, roomNo: number | string): Promise<ApiResult<{ room: RoomDetail }>>;
  requestReserve(params: { recaptchaToken: string; roomNo: number | string; date: string; startBlock: number; endBlock: number }): Promise<ApiResult<{ idx: number }>>;
  getReserveStatus(idx: number): Promise<ApiResult<{ status: ReserveStatus }>>;
  listReserves(type: 0 | 1, page: number): Promise<ApiResult<{ reserves: ReserveHistory[]; totalPages: number }>>;
  cancelReserve(idx: number): Promise<ApiResult<null>>;
  doneReserve(idx: number): Promise<ApiResult<null>>;
  uploadVerifyPhoto(idx: number): Promise<ApiResult<null>>;
  adminTool(params: { type: 'reserveCancel' | 'photoDelete' | 'photoExecpt'; idx: number; text: string | null }): Promise<ApiResult<ReserveStatus>>;
};

export class ApiReservationRepository implements ReservationRepository {
  async listRooms(date: string) {
    return apiClient.post<{ date: string }, { rooms: RoomSummary[] }>('room/list', { date }, { authenticated: true });
  }

  async getRoom(date: string, roomNo: number | string) {
    return apiClient.post<{ date: string; roomNo: number | string }, { room: RoomDetail }>('room/get', { date, roomNo }, { authenticated: true });
  }

  async requestReserve(params: { recaptchaToken: string; roomNo: number | string; date: string; startBlock: number; endBlock: number }) {
    return apiClient.post<typeof params, { idx: number }>('reserve/request', params, { authenticated: true });
  }

  async getReserveStatus(idx: number) {
    return apiClient.post<{ idx: number }, { status: ReserveStatus }>('reserve/status', { idx }, { authenticated: true });
  }

  async listReserves(type: 0 | 1, page: number) {
    return apiClient.post<{ type: 0 | 1; page: number }, { reserves: ReserveHistory[]; totalPages: number }>(
      'reserve/list',
      { type, page },
      { authenticated: true },
    );
  }

  async cancelReserve(idx: number) {
    return apiClient.post<{ idx: number }, null>('reserve/cancel', { idx }, { authenticated: true });
  }

  async doneReserve(idx: number) {
    return apiClient.post<{ idx: number }, null>('reserve/done', { idx }, { authenticated: true });
  }

  async uploadVerifyPhoto(idx: number) {
    const photo = await nativeBridge.captureVerifyPhoto();
    if (!photo) {
      return apiFailure('SSU0000', '사진 촬영이 취소되었습니다.');
    }

    const recaptchaToken = await getRecaptchaToken('verify_photo_upload');

    const formData = new FormData();
    formData.append('recaptchaToken', recaptchaToken);
    formData.append('idx', String(idx));
    formData.append('file', photo.blob ?? new Blob([], { type: photo.type }), photo.name);

    return apiClient.postFormData<null>('reserve/verifyPhoto/upload', formData, { authenticated: true });
  }

  async adminTool(params: { type: 'reserveCancel' | 'photoDelete' | 'photoExecpt'; idx: number; text: string | null }) {
    const device = await nativeBridge.getDeviceInfo();
    const signed = await nativeBridge.signWithBiometrics(`${params.type}:${params.idx}`);
    if (!signed) {
      return apiFailure('SSU0000', '생체 인증을 사용할 수 없습니다.');
    }

    return apiClient.post<
      { type: 'reserveCancel' | 'photoDelete' | 'photoExecpt'; idx: number; text: string | null; osType: string; uuid: string; signature: string },
      ReserveStatus
    >(
      'reserve/adminTools',
      { ...params, osType: device.osType, uuid: device.uuid, signature: signed.signature },
      { authenticated: true },
    );
  }
}

export const reservationRepository: ReservationRepository = new ApiReservationRepository();

export function reserveHistoryTime(startBlock: number, endBlock: number) {
  return `${blockToTime(startBlock)} ~ ${blockToTime(endBlock + 1)}`;
}
