import { TURNSTILE_SITE_KEY } from '../config/env';
import { isNativeApp } from '../native/nativeBridge';
import { request } from '../native/bridgeTransport';

declare global {
  interface Window {
    turnstile?: {
      render(container: HTMLElement, options: TurnstileOptions): string;
      remove(widgetId: string): void;
    };
    onloadTurnstileCallback?: () => void;
  }
}

type TurnstileOptions = {
  sitekey: string;
  action?: string;
  callback?: (token: string) => void;
  'error-callback'?: () => void;
  'before-interactive-callback'?: () => void;
  'after-interactive-callback'?: () => void;
  appearance?: 'always' | 'execute' | 'interaction-only';
};

let scriptPromise: Promise<void> | null = null;
let container: HTMLElement | null = null;
let currentWidgetId: string | null = null;

function loadScript(): Promise<void> {
  if (scriptPromise) return scriptPromise;

  scriptPromise = new Promise((resolve, reject) => {
    if (window.turnstile) {
      resolve();
      return;
    }

    window.onloadTurnstileCallback = () => resolve();
    const script = document.createElement('script');
    script.src =
      'https://challenges.cloudflare.com/turnstile/v0/api.js?onload=onloadTurnstileCallback&render=explicit';
    script.async = true;
    script.onerror = () => reject(new Error('Turnstile 스크립트를 불러오지 못했습니다'));
    document.head.appendChild(script);
  });

  return scriptPromise;
}

function getContainer(): HTMLElement {
  if (!container) {
    container = document.createElement('div');
    container.style.position = 'fixed';
    container.style.zIndex = '9999';
    hideContainer();
    document.body.appendChild(container);
  }
  return container;
}

function hideContainer(): void {
  if (!container) return;
  container.style.bottom = '-9999px';
  container.style.right = '-9999px';
}

function showContainer(): void {
  if (!container) return;
  container.style.bottom = '16px';
  container.style.right = '16px';
}

export async function getTurnstileToken(action: string): Promise<string> {
  if (isNativeApp()) {
    return request<string>('security.getTurnstileToken', { siteKey: TURNSTILE_SITE_KEY, action }, 30_000);
  }

  await loadScript();

  if (!window.turnstile) {
    throw new Error('Turnstile을 사용할 수 없습니다');
  }

  const el = getContainer();

  if (currentWidgetId !== null) {
    window.turnstile.remove(currentWidgetId);
    currentWidgetId = null;
  }

  return new Promise((resolve, reject) => {
    currentWidgetId = window.turnstile!.render(el, {
      sitekey: TURNSTILE_SITE_KEY,
      action,
      callback: (token) => { hideContainer(); resolve(token); },
      'error-callback': () => { hideContainer(); reject(new Error('Turnstile 인증에 실패했습니다')); },
      'before-interactive-callback': showContainer,
      'after-interactive-callback': hideContainer,
      appearance: 'interaction-only',
    });
  });
}
