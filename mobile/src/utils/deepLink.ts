export type DeepLinkPayload =
  | { type: 'notice'; url: string }
  | { type: 'reservation' }
  | { type: 'path'; path: string };

/** 푸시가 지정한 웹 화면 경로인지. 웹뷰 안의 경로만 허용하고 외부 주소나 //로 시작하는 주소는 거부한다. */
export function isWebPath(value: string | undefined): value is string {
  return typeof value === 'string' && /^\/(?!\/)[A-Za-z0-9\-._~/?=&%]*$/.test(value);
}

type Listener = (payload: DeepLinkPayload) => void;

let pending: DeepLinkPayload | null = null;
const listeners = new Set<Listener>();

export const deepLink = {
  emit(payload: DeepLinkPayload): void {
    if (listeners.size > 0) {
      listeners.forEach((fn) => fn(payload));
    } else {
      pending = payload;
    }
  },
  subscribe(fn: Listener): () => void {
    listeners.add(fn);
    if (pending !== null) {
      fn(pending);
      pending = null;
    }
    return () => {
      listeners.delete(fn);
    };
  },
};
