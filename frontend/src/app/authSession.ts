import { authRepository } from '../features/auth/api/authRepository';
import { deviceRepository } from '../features/my/api/deviceRepository';
import { WEB_APP_VERSION } from '../shared/config/env';
import { nativeBridge } from '../shared/native/nativeBridge';
import { appStorage } from '../shared/storage/appStorage';

export type AuthSessionResult = 'authenticated' | 'anonymous';

export async function validateAuthSession(): Promise<AuthSessionResult> {
  const accessToken = await appStorage.getItem('accessToken');
  const refreshToken = await appStorage.getItem('refreshToken');
  if (!accessToken || !refreshToken) {
    return 'anonymous';
  }

  const profile = await authRepository.getProfile();
  if (!profile.ok) {
    if (profile.statusCode === 'SSU4001') {
      await appStorage.clearAuth();
    }

    return 'anonymous';
  }

  const notificationEnabled = await appStorage.getItem('notificationEnabled');
  if (notificationEnabled === null || notificationEnabled === 'true') {
    await deviceRepository.register();
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
