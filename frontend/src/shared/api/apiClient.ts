import { API_BASE_URL } from '../config/env';
import { type ApiClientOptions, type ApiResponse, type ApiResult } from './types';

export class ApiClient {
  constructor(private readonly baseUrl: string) {}

  async post<TRequest extends object, TData>(
    path: string,
    body: TRequest,
    options: ApiClientOptions = {},
  ): Promise<ApiResult<TData>> {
    try {
      const response = await fetch(new URL(path, this.baseUrl), {
        method: 'POST',
        credentials: 'include',
        headers: this.createHeaders(options),
        body: JSON.stringify(body),
      });

      if (response.status === 521) {
        return { ok: false, statusCode: 'SSU0000', message: '서버 연결에 실패했습니다.' };
      }

      const payload = (await response.json()) as ApiResponse<TData>;
      if (payload.statusCode.startsWith('SSU2')) {
        return { ok: true, statusCode: payload.statusCode, data: payload.data, message: payload.message };
      }

      return {
        ok: false,
        statusCode: payload.statusCode,
        message: payload.message ?? '요청을 처리하지 못했습니다.',
      };
    } catch {
      return { ok: false, statusCode: 'SSU0000', message: '네트워크 연결에 실패했습니다.' };
    }
  }

  async postFormData<TData>(
    path: string,
    body: FormData,
    options: Omit<ApiClientOptions, 'headers'> = {},
  ): Promise<ApiResult<TData>> {
    try {
      const response = await fetch(new URL(path, this.baseUrl), {
        method: 'POST',
        credentials: 'include',
        body,
      });

      if (response.status === 521) {
        return { ok: false, statusCode: 'SSU0000', message: '서버 연결에 실패했습니다.' };
      }

      const payload = (await response.json()) as ApiResponse<TData>;
      if (payload.statusCode.startsWith('SSU2')) {
        return { ok: true, statusCode: payload.statusCode, data: payload.data, message: payload.message };
      }

      return {
        ok: false,
        statusCode: payload.statusCode,
        message: payload.message ?? '요청을 처리하지 못했습니다.',
      };
    } catch {
      return { ok: false, statusCode: 'SSU0000', message: '네트워크 연결에 실패했습니다.' };
    }
  }

  private createHeaders(options: ApiClientOptions) {
    return {
      'Content-Type': 'application/json',
      ...options.headers,
    };
  }
}

export const apiClient = new ApiClient(API_BASE_URL);
