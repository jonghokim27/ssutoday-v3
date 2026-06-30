import { apiClient } from '../../../shared/api/apiClient';
import { apiFailure, apiSuccess, type ApiResult } from '../../../shared/api/types';
import { nativeBridge, isNativeApp, HandledError } from '../../../shared/native/nativeBridge';
import { getTurnstileToken } from '../../../shared/turnstile/turnstile';
import { blockToTime, timeToBlock } from './reservationBlocks';

function dataUriToBlob(uri: string, fallbackType: string): Blob {
  if (!uri.startsWith('data:')) {
    return new Blob([], { type: fallbackType });
  }
  const [header, base64] = uri.split(',');
  const mime = header.match(/:(.*?);/)?.[1] ?? fallbackType;
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return new Blob([bytes], { type: mime });
}

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
  requestReserve(params: { turnstileToken: string; roomNo: number | string; date: string; startBlock: number; endBlock: number }): Promise<ApiResult<{ idx: number }>>;
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

  async requestReserve(params: { turnstileToken: string; roomNo: number | string; date: string; startBlock: number; endBlock: number }) {
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
      return apiFailure('SSU0000', '인증샷 촬영이 취소되었습니다');
    }

    const turnstileToken = await getTurnstileToken('verify_photo_upload');

    const formData = new FormData();
    formData.append('turnstileToken', turnstileToken);
    formData.append('idx', String(idx));
    const fileBlob = photo.blob ?? dataUriToBlob(photo.uri, photo.type);
    formData.append('file', fileBlob, photo.name);

    return apiClient.postFormData<null>('reserve/verifyPhoto/upload', formData, { authenticated: true });
  }

  async adminTool(params: { type: 'reserveCancel' | 'photoDelete' | 'photoExecpt'; idx: number; text: string | null }) {
    const device = await nativeBridge.getDeviceInfo();
    if (!isNativeApp()) {
      // 앱 설치 모달이 이미 표시됨 (getDeviceInfo 프록시가 showNativeOnlyModal 호출)
      throw new HandledError();
    }
    const signed = await nativeBridge.signWithBiometrics(`${params.type}:${params.idx}`);
    if (!signed) {
      // 사용자가 생체 인증을 취소함 — 추가 토스트 불필요
      throw new HandledError();
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
