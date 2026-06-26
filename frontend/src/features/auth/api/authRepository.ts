import { apiClient } from '../../../shared/api/apiClient';
import { type ApiResult } from '../../../shared/api/types';
import { appStorage, type StoredProfile } from '../../../shared/storage/appStorage';

export type StudentLoginRequest = {
  sToken: string;
  sIdno: string;
  persistLogin?: boolean;
};

export type AuthRepository = {
  login(request: StudentLoginRequest): Promise<ApiResult<StoredProfile>>;
  getProfile(): Promise<ApiResult<StoredProfile>>;
  logout(): Promise<ApiResult<null>>;
};

export class ApiAuthRepository implements AuthRepository {
  async login(request: StudentLoginRequest) {
    const result = await apiClient.post<StudentLoginRequest, StoredProfile>('student/login', request);
    if (result.ok) {
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

export const authRepository: AuthRepository = new ApiAuthRepository();
