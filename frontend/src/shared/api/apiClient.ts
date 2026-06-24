import { API_BASE_URL } from '../config/env';
import { appStorage } from '../storage/appStorage';
import { type ApiClientOptions, type ApiResponse, type ApiResult } from './types';

export class ApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly storage = appStorage,
  ) {}

  async post<TRequest extends object, TData>(
    path: string,
    body: TRequest,
    options: ApiClientOptions = {},
  ): Promise<ApiResult<TData>> {
    try {
      const headers = await this.createHeaders(options);
      const response = await fetch(new URL(path, this.baseUrl), {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      });

      await this.persistTokensFromHeaders(response.headers);

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
      const headers = await this.createHeaders({ ...options, headers: {} });
      delete headers['Content-Type'];
      const response = await fetch(new URL(path, this.baseUrl), {
        method: 'POST',
        headers,
        body,
      });

      await this.persistTokensFromHeaders(response.headers);

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

  private async createHeaders(options: ApiClientOptions) {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (options.authenticated) {
      const accessToken = await this.storage.getItem('accessToken');
      const refreshToken = await this.storage.getItem('refreshToken');
      if (accessToken && refreshToken) {
        headers.Authorization = `Bearer ${accessToken}`;
        headers['Refresh-Token'] = refreshToken;
      }
    }

    return headers;
  }

  private async persistTokensFromHeaders(headers: Headers) {
    const accessToken = headers.get('access-token');
    const refreshToken = headers.get('refresh-token');

    if (accessToken) {
      await this.storage.setItem('accessToken', accessToken);
    }

    if (refreshToken) {
      await this.storage.setItem('refreshToken', refreshToken);
    }
  }
}

export const apiClient = new ApiClient(API_BASE_URL);
