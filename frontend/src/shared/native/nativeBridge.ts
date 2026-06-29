import { BridgeError, hasCapability as hasBridgeCapability, request } from './bridgeTransport';
import { showGlobalToast } from '../ui/globalToast';
import type { BridgeMethod } from './bridgeProtocol';

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
  openExternalUrl(url: string, mode?: 'external' | 'internal'): Promise<void>;
  openAppSettings(): Promise<void>;
  requestCameraPermission(): Promise<boolean>;
  captureVerifyPhoto(): Promise<CapturedPhoto | null>;
  signWithBiometrics(payload: string): Promise<{ signature: string } | null>;
  checkConnectivity(): Promise<{ online: boolean }>;
  getTurnstileToken(siteKey: string, action: string): Promise<string>;
};

const METHOD_FOR: Record<keyof NativeBridge, BridgeMethod> = {
  getDeviceInfo: 'device.getInfo',
  requestPushPermission: 'push.requestPermission',
  getPushToken: 'push.getToken',
  subscribePushTopic: 'push.subscribeTopic',
  unsubscribePushTopic: 'push.unsubscribeTopic',
  openExternalUrl: 'browser.openExternalUrl',
  openAppSettings: 'system.openAppSettings',
  requestCameraPermission: 'camera.requestPermission',
  captureVerifyPhoto: 'camera.captureVerifyPhoto',
  signWithBiometrics: 'auth.signWithBiometrics',
  checkConnectivity: 'network.checkConnectivity',
  getTurnstileToken: 'security.getTurnstileToken',
};

export function hasCapability(method: keyof NativeBridge): boolean {
  return hasBridgeCapability(METHOD_FOR[method]);
}

class WebViewNativeBridge implements NativeBridge {
  getDeviceInfo() {
    return request<NativeDeviceInfo>(METHOD_FOR.getDeviceInfo);
  }

  requestPushPermission() {
    return request<boolean>(METHOD_FOR.requestPushPermission);
  }

  getPushToken() {
    return request<string | null>(METHOD_FOR.getPushToken);
  }

  subscribePushTopic(topic: string) {
    return request<void>(METHOD_FOR.subscribePushTopic, { topic });
  }

  unsubscribePushTopic(topic: string) {
    return request<void>(METHOD_FOR.unsubscribePushTopic, { topic });
  }

  openExternalUrl(url: string, mode: 'external' | 'internal' = 'external') {
    return request<void>(METHOD_FOR.openExternalUrl, { url, mode });
  }

  openAppSettings() {
    return request<void>(METHOD_FOR.openAppSettings);
  }

  requestCameraPermission() {
    return request<boolean>(METHOD_FOR.requestCameraPermission);
  }

  captureVerifyPhoto() {
    return request<CapturedPhoto | null>(METHOD_FOR.captureVerifyPhoto, undefined, 120_000);
  }

  signWithBiometrics(payload: string) {
    return request<{ signature: string } | null>(METHOD_FOR.signWithBiometrics, { payload });
  }

  checkConnectivity() {
    return request<{ online: boolean }>(METHOD_FOR.checkConnectivity);
  }

  getTurnstileToken(siteKey: string, action: string) {
    return request<string>(METHOD_FOR.getTurnstileToken, { siteKey, action });
  }
}

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

  async openExternalUrl(url: string, _mode?: 'external' | 'internal') {
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

  async checkConnectivity() {
    return { online: true };
  }

  async getTurnstileToken() {
    return 'mock-turnstile-token';
  }
}

export function isNativeApp() {
  return navigator.userAgent.includes('SSUTODAY');
}

const APP_DOWNLOAD_URL = 'https://r2.ssu.today/install.html';

let nativeOnlyOverlay: HTMLDivElement | null = null;

const NATIVE_ONLY_STYLE_ID = 'ssu-native-only-modal-style';

function ensureNativeOnlyStyles() {
  if (document.getElementById(NATIVE_ONLY_STYLE_ID)) {
    return;
  }

  const style = document.createElement('style');
  style.id = NATIVE_ONLY_STYLE_ID;
  style.textContent = `
    .ssu-native-overlay {
      position: fixed;
      inset: 0;
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 30px;
      background: rgba(15, 18, 34, 0.42);
      backdrop-filter: blur(3px);
      font-family: var(--font-family-base, Pretendard, -apple-system, sans-serif);
      animation: ssuNativeFadeIn 0.2s ease both;
    }
    .ssu-native-dialog {
      width: 100%;
      max-width: 300px;
      background: var(--color-surface-base, #fff);
      border-radius: var(--radius-dialog, 26px);
      box-shadow: var(--shadow-dialog, 0 24px 60px -20px rgba(20, 20, 40, 0.4));
      padding: 26px 22px 20px;
      text-align: center;
      animation: ssuNativeScaleIn 0.3s cubic-bezier(0.2, 0.8, 0.2, 1.3) both;
    }
    .ssu-native-icon {
      width: 60px;
      height: 60px;
      margin: 0 auto 18px;
      border-radius: var(--radius-card, 20px);
      background: rgba(79, 124, 255, 0.1);
      color: var(--color-brand-blue, #4f7cff);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .ssu-native-message {
      margin: 0;
      color: var(--color-text-primary, #0f1222);
      font-size: 15.5px;
      font-weight: var(--font-weight-extra-bold, 800);
      letter-spacing: -0.3px;
      line-height: 1.5;
    }
    .ssu-native-sub {
      margin: 7px 4px 0;
      color: var(--color-text-muted, #8a8f9c);
      font-size: 13px;
      font-weight: var(--font-weight-regular, 500);
      line-height: 1.5;
    }
    .ssu-native-actions {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-top: 20px;
    }
    .ssu-native-actions button {
      border: 0;
      border-radius: var(--radius-button, 16px);
      padding: 14px;
      font-size: var(--font-size-button, 15px);
      font-weight: var(--font-weight-bold, 700);
      cursor: pointer;
      transition: transform 0.15s ease;
    }
    .ssu-native-actions button:active {
      transform: scale(0.97);
    }
    .ssu-native-download {
      background: var(--color-brand-gradient, linear-gradient(135deg, #4f7cff, #9b5cff));
      color: #fff;
      box-shadow: var(--shadow-primary-small, 0 8px 16px -6px rgba(106, 76, 255, 0.55));
    }
    .ssu-native-close {
      background: var(--color-surface-control-strong, #f2f3f8);
      color: var(--color-text-secondary, #4f5566);
    }
    @keyframes ssuNativeFadeIn {
      from { opacity: 0; }
    }
    @keyframes ssuNativeScaleIn {
      from { opacity: 0; transform: scale(0.94); }
    }
  `;
  document.head.append(style);
}

function showNativeOnlyModal() {
  if (nativeOnlyOverlay) {
    return;
  }

  ensureNativeOnlyStyles();

  const overlay = document.createElement('div');
  overlay.className = 'ssu-native-overlay';

  const dialog = document.createElement('div');
  dialog.className = 'ssu-native-dialog';

  const icon = document.createElement('div');
  icon.className = 'ssu-native-icon';
  icon.innerHTML =
    '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="2" width="12" height="20" rx="3"/><path d="M11 18h2"/></svg>';

  const message = document.createElement('p');
  message.className = 'ssu-native-message';
  message.innerHTML = '해당 기능은 슈투데이 앱에서만<br>이용하실 수 있어요';

  const sub = document.createElement('p');
  sub.className = 'ssu-native-sub';
  sub.textContent = '앱을 설치하면 이 기능을 바로 이용할 수 있어요';

  function closeModal() {
    overlay.remove();
    nativeOnlyOverlay = null;
  }

  const actions = document.createElement('div');
  actions.className = 'ssu-native-actions';

  const downloadButton = document.createElement('button');
  downloadButton.type = 'button';
  downloadButton.className = 'ssu-native-download';
  downloadButton.textContent = '앱 다운로드';
  downloadButton.onclick = () => {
    window.open(APP_DOWNLOAD_URL, '_blank', 'noopener,noreferrer');
    closeModal();
  };

  const closeButton = document.createElement('button');
  closeButton.type = 'button';
  closeButton.className = 'ssu-native-close';
  closeButton.textContent = '닫기';
  closeButton.onclick = closeModal;

  overlay.onclick = (event) => {
    if (event.target === overlay) {
      closeModal();
    }
  };

  actions.append(downloadButton, closeButton);
  dialog.append(icon, message, sub, actions);
  overlay.append(dialog);
  document.body.append(overlay);
  nativeOnlyOverlay = overlay;
}

export function requireNativeApp() {
  if (isNativeApp()) {
    return true;
  }

  showNativeOnlyModal();
  return false;
}

function fallbackResultFor(method: keyof NativeBridge): Promise<unknown> {
  switch (method) {
    case 'getDeviceInfo':
      return Promise.resolve<NativeDeviceInfo>({ osType: 'android', uuid: '', appVersion: '' });
    case 'requestPushPermission':
    case 'requestCameraPermission':
      return Promise.resolve(false);
    case 'getPushToken':
    case 'captureVerifyPhoto':
    case 'signWithBiometrics':
      return Promise.resolve(null);
    case 'checkConnectivity':
      return Promise.resolve({ online: true });
    case 'getTurnstileToken':
      return Promise.resolve('mock-turnstile-token');
    default:
      return Promise.resolve(undefined);
  }
}

function createGatedNativeBridge(real: NativeBridge, mock: NativeBridge): NativeBridge {
  return new Proxy(mock, {
    get(target, prop, receiver) {
      const value = Reflect.get(target, prop, receiver);
      if (typeof value !== 'function') {
        return value;
      }

      return (...args: unknown[]) => {
        if (isNativeApp()) {
          const realValue = Reflect.get(real, prop, real);
          const result = (realValue as (...fnArgs: unknown[]) => unknown).apply(real, args);
          if (result instanceof Promise) {
            return result.catch((error: unknown) => {
              if (error instanceof BridgeError) {
                if (error.code === 'PERMISSION_DENIED') {
                  showGlobalToast('설정에서 권한을 허용해 주세요', () => {
                    request('system.openAppSettings').catch(() => {});
                  });
                } else if (error.code === 'NATIVE_ERROR') {
                  showGlobalToast('오류가 발생했어요. 잠시 후 다시 시도해 주세요');
                }
              }
              return Promise.reject(error);
            });
          }
          return result;
        }

        showNativeOnlyModal();
        return fallbackResultFor(prop as keyof NativeBridge);
      };
    },
  });
}

export const nativeBridge: NativeBridge = createGatedNativeBridge(new WebViewNativeBridge(), new MockNativeBridge());

export function notifyNetworkFailure() {
  if (!isNativeApp()) return;
  new WebViewNativeBridge().checkConnectivity().catch(() => {});
}

export async function openLink(url: string) {
  if (isNativeApp()) {
    await nativeBridge.openExternalUrl(url);
    return;
  }

  window.open(url, '_blank', 'noopener,noreferrer');
}

export type HapticStyle = 'light' | 'medium' | 'heavy' | 'selection';

export function triggerHaptic(style: HapticStyle = 'light'): void {
  if (!isNativeApp()) return;
  request<void>('haptic.impact', { style }).catch(() => {});
}
