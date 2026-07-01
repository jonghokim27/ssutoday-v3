import { authRepository } from '../features/auth/api/authRepository';
import { appStorage } from '../shared/storage/appStorage';

export type AuthSessionResult = 'authenticated' | 'anonymous';

export async function validateAuthSession(): Promise<AuthSessionResult> {
  const profile = await authRepository.getProfile();
  if (!profile.ok) {
    if (profile.statusCode === 'SSU4001') {
      await appStorage.clearAuth();
    }

    return 'anonymous';
  }

  return 'authenticated';
}
