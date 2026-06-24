export type NativePlatform = 'ios' | 'android';

export type NativeDeviceInfo = {
  osType: NativePlatform;
  uuid: string;
  appVersion: string;
};

export type CapturedPhoto = {
  name: string;
  type: 'image/jpeg';
  uri: string;
  blob?: Blob;
};

export type NativeBridge = {
  getDeviceInfo(): Promise<NativeDeviceInfo>;
  requestPushPermission(): Promise<boolean>;
  getPushToken(): Promise<string | null>;
  subscribePushTopic(topic: string): Promise<void>;
  unsubscribePushTopic(topic: string): Promise<void>;
  openExternalUrl(url: string): Promise<void>;
  openAppSettings(): Promise<void>;
  requestCameraPermission(): Promise<boolean>;
  captureVerifyPhoto(): Promise<CapturedPhoto | null>;
  signWithBiometrics(payload: string): Promise<{ signature: string } | null>;
  clearWebViewCookies(): Promise<void>;
  readCookie(url: string, name: string): Promise<string | null>;
  logScreenView(screenName: string): Promise<void>;
};

class MockNativeBridge implements NativeBridge {
  async getDeviceInfo() {
    return {
      osType: 'android' as const,
      uuid: 'web-mock-device',
      appVersion: '0.0.0',
    };
  }

  async requestPushPermission() {
    return true;
  }

  async getPushToken() {
    return 'mock-web-push-token';
  }

  async subscribePushTopic() {}

  async unsubscribePushTopic() {}

  async openExternalUrl(url: string) {
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  async openAppSettings() {}

  async requestCameraPermission() {
    return true;
  }

  async captureVerifyPhoto() {
    return {
      name: 'verify-photo.jpg',
      type: 'image/jpeg' as const,
      uri: 'mock://verify-photo.jpg',
      blob: new Blob(['mock verify photo'], { type: 'image/jpeg' }),
    };
  }

  async signWithBiometrics(payload: string) {
    return { signature: `mock-signature:${payload}` };
  }

  async clearWebViewCookies() {}

  async readCookie() {
    return null;
  }

  async logScreenView() {}
}

export const nativeBridge: NativeBridge = new MockNativeBridge();
