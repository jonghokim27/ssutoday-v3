import { TURNSTILE_SITE_KEY } from '../config/env';

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
  execution?: 'render' | 'execute';
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
    script.onerror = () => reject(new Error('Turnstile 스크립트를 불러오지 못했습니다.'));
    document.head.appendChild(script);
  });

  return scriptPromise;
}

function getContainer(): HTMLElement {
  if (!container) {
    container = document.createElement('div');
    container.style.display = 'none';
    document.body.appendChild(container);
  }
  return container;
}

export async function getTurnstileToken(action: string): Promise<string> {
  await loadScript();

  if (!window.turnstile) {
    throw new Error('Turnstile을 사용할 수 없습니다.');
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
      callback: resolve,
      'error-callback': () => reject(new Error('Turnstile 인증에 실패했습니다.')),
      execution: 'execute',
      appearance: 'interaction-only',
    });
  });
}
