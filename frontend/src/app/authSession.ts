import { authRepository } from '../features/auth/api/authRepository';
import { WEB_APP_VERSION } from '../shared/config/env';
import { nativeBridge } from '../shared/native/nativeBridge';
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

export async function checkWebVersion() {
  const device = await nativeBridge.getDeviceInfo();
  return {
    osType: device.osType,
    version: WEB_APP_VERSION,
  };
}
