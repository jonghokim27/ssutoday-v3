import {
  BRIDGE_REQUEST_TIMEOUT_MS,
  type BridgeErrorCode,
  type BridgeEvent,
  type BridgeEventPayloadMap,
  type BridgeMethod,
  type NativePlatform,
  parseBridgeEnvelope,
} from './bridgeProtocol';

declare global {
  interface Window {
    ReactNativeWebView?: { postMessage(message: string): void };
  }
}

export class BridgeError extends Error {
  code: BridgeErrorCode;

  constructor(code: BridgeErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

type PendingEntry = {
  resolve: (result: unknown) => void;
  reject: (error: BridgeError) => void;
  timeoutId: ReturnType<typeof setTimeout>;
};

type HandshakeInfo = {
  capabilities: Set<BridgeMethod>;
  platform: NativePlatform;
  appVersion: string;
};

const pending = new Map<string, PendingEntry>();

let handshakeInfo: HandshakeInfo | null = null;
let handshakeResolvers: Array<(info: HandshakeInfo) => void> = [];

const eventListeners = new Map<BridgeEvent, Set<(payload: unknown) => void>>();

function generateId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `bridge-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function postToNative(envelope: unknown) {
  window.ReactNativeWebView?.postMessage(JSON.stringify(envelope));
}

function handleMessageEvent(event: MessageEvent) {
  if (typeof event.data !== 'string') {
    return;
  }

  const envelope = parseBridgeEnvelope(event.data);
  if (!envelope) {
    return;
  }

  if (envelope.kind === 'handshake') {
    handshakeInfo = {
      capabilities: new Set(envelope.capabilities),
      platform: envelope.platform,
      appVersion: envelope.appVersion,
    };
    const resolvers = handshakeResolvers;
    handshakeResolvers = [];
    resolvers.forEach((resolve) => resolve(handshakeInfo!));
    return;
  }

  if (envelope.kind === 'response') {
    const entry = pending.get(envelope.id);
    if (!entry) {
      return;
    }

    pending.delete(envelope.id);
    clearTimeout(entry.timeoutId);

    if (envelope.ok) {
      entry.resolve(envelope.result);
    } else {
      entry.reject(new BridgeError(envelope.error.code, envelope.error.message));
    }
    return;
  }

  if (envelope.kind === 'event') {
    eventListeners.get(envelope.event)?.forEach((listener) => listener(envelope.payload));
  }
}

let listening = false;

function ensureListening() {
  if (listening || typeof window === 'undefined') {
    return;
  }

  listening = true;
  window.addEventListener('message', handleMessageEvent);
  document.addEventListener('message', handleMessageEvent as EventListener);
}

ensureListening();

export function waitForHandshake(): Promise<HandshakeInfo> {
  if (handshakeInfo) {
    return Promise.resolve(handshakeInfo);
  }

  return new Promise((resolve) => {
    handshakeResolvers.push(resolve);
  });
}

export function hasCapability(method: BridgeMethod): boolean {
  return handshakeInfo?.capabilities.has(method) ?? false;
}

export function request<T = unknown>(method: BridgeMethod, params?: unknown, timeoutMs?: number): Promise<T> {
  if (handshakeInfo && !hasCapability(method)) {
    return Promise.reject(new BridgeError('UNSUPPORTED_METHOD', `앱이 ${method}을 지원하지 않습니다`));
  }

  const id = generateId();
  const timeout = timeoutMs ?? BRIDGE_REQUEST_TIMEOUT_MS;

  return new Promise<T>((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      pending.delete(id);
      reject(new BridgeError('TIMEOUT', `${method} 응답이 ${timeout}ms 내에 도착하지 않았습니다`));
    }, timeout);

    pending.set(id, { resolve: resolve as (result: unknown) => void, reject, timeoutId });
    postToNative({ v: 1, kind: 'request', id, method, params });
  });
}

export function on<E extends BridgeEvent>(event: E, listener: (payload: BridgeEventPayloadMap[E] | undefined) => void): () => void {
  if (!eventListeners.has(event)) {
    eventListeners.set(event, new Set());
  }

  const listeners = eventListeners.get(event)!;
  listeners.add(listener as (payload: unknown) => void);

  return () => listeners.delete(listener as (payload: unknown) => void);
}
