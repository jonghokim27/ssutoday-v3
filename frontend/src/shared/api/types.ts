export type ApiResponse<T> = {
  statusCode: string;
  data: T;
  message?: string;
};

export type ApiFailure = {
  statusCode: string;
  message: string;
};

export type ApiResult<T> =
  | { ok: true; statusCode: string; data: T; message?: string }
  | { ok: false; statusCode: string; message: string };

export type AuthHeaders = {
  Authorization: `Bearer ${string}`;
  'Refresh-Token': string;
};

export type ApiClientOptions = {
  authenticated?: boolean;
  headers?: Record<string, string>;
};

export function apiSuccess<T>(statusCode: string, data: T, message?: string): ApiResult<T> {
  return { ok: true, statusCode, data, message };
}

export function apiFailure<T = never>(statusCode: string, message: string): ApiResult<T> {
  return { ok: false, statusCode, message };
}
