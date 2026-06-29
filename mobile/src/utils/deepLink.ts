export type DeepLinkPayload =
  | { type: 'notice'; url: string }
  | { type: 'reservation' };

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
