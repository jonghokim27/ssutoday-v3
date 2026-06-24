import { apiClient } from '../../../shared/api/apiClient';
import { apiFailure, apiSuccess, type ApiResult } from '../../../shared/api/types';
import { appStorage, type StoredProfile } from '../../../shared/storage/appStorage';

export type StudentLoginRequest = {
  sToken: string;
  sIdno: string;
};

export type StudentLoginData = StoredProfile & {
  accessToken: string;
  refreshToken: string;
};

export type AuthRepository = {
  login(request: StudentLoginRequest): Promise<ApiResult<StudentLoginData>>;
  getProfile(): Promise<ApiResult<StoredProfile>>;
  logout(): Promise<ApiResult<null>>;
};

export class ApiAuthRepository implements AuthRepository {
  async login(request: StudentLoginRequest) {
    const result = await apiClient.post<StudentLoginRequest, StudentLoginData>('student/login', request);
    if (result.ok) {
      await appStorage.setItem('accessToken', result.data.accessToken);
      await appStorage.setItem('refreshToken', result.data.refreshToken);
      await appStorage.setProfile(result.data);
    }
    return result;
  }

  async getProfile() {
    const result = await apiClient.post<Record<string, never>, StoredProfile>('student/profile', {}, { authenticated: true });
    if (result.ok) {
      await appStorage.setProfile(result.data);
    }
    return result;
  }

  async logout() {
    const result = await apiClient.post<Record<string, never>, null>('student/logout', {}, { authenticated: true });
    await appStorage.clearAuth();
    return result;
  }
}

export class MockAuthRepository implements AuthRepository {
  async login() {
    const data: StudentLoginData = {
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
      studentId: '20221488',
      name: '김종호',
      major: 'cse',
      isAdmin: false,
    };
    await appStorage.setItem('accessToken', data.accessToken);
    await appStorage.setItem('refreshToken', data.refreshToken);
    await appStorage.setProfile(data);
    return apiSuccess('SSU2010', data);
  }

  async getProfile() {
    const profile = await appStorage.getProfile();
    if (!profile) {
      return apiFailure('SSU4001', '로그인이 필요합니다.');
    }
    return apiSuccess('SSU2020', profile);
  }

  async logout() {
    await appStorage.clearAuth();
    return apiSuccess('SSU2030', null);
  }
}

export const authRepository: AuthRepository = new ApiAuthRepository();
