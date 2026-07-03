import { authRepository } from '../features/auth/api/authRepository';
import { appStorage } from '../shared/storage/appStorage';

export type AuthSessionResult = 'authenticated' | 'anonymous';

export async function validateAuthSession(): Promise<AuthSessionResult> {
  const profile = await authRepository.getProfile();
  if (profile.ok) return 'authenticated';

  if (profile.statusCode === 'SSU4001') {
    // On cold start the WebView cookie store may not be flushed yet, causing a
    // transient 401. Retry once if we have a locally cached profile.
    const storedProfile = await appStorage.getProfile();
    if (storedProfile) {
      const retry = await authRepository.getProfile();
      if (retry.ok) return 'authenticated';
      if (retry.statusCode === 'SSU4001') {
        await appStorage.clearAuth();
      }
      return 'anonymous';
    }
    await appStorage.clearAuth();
    return 'anonymous';
  }

  return 'anonymous';
}
