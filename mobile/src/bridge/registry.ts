import { Platform } from 'react-native';
import Constants from 'expo-constants';

import { BRIDGE_PROTOCOL_VERSION, type BridgeErrorCode, type BridgeMethod, type NativePlatform } from './protocol';

export class BridgeHandlerError extends Error {
  code: BridgeErrorCode;

  constructor(code: BridgeErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

export type BridgeHandler = (params: unknown) => Promise<unknown>;

const handlers = new Map<BridgeMethod, BridgeHandler>();

export function registerHandler(method: BridgeMethod, handler: BridgeHandler) {
  handlers.set(method, handler);
}

export function getCapabilities(): BridgeMethod[] {
  return Array.from(handlers.keys());
}

export async function dispatch(method: BridgeMethod, params: unknown): Promise<unknown> {
  const handler = handlers.get(method);

  if (!handler) {
    throw new BridgeHandlerError('UNSUPPORTED_METHOD', `등록되지 않은 method: ${method}`);
  }

  return handler(params);
}

export function getHandshakeInfo() {
  return {
    platform: Platform.OS as NativePlatform,
    appVersion: Constants.expoConfig?.version ?? '0.0.0',
    protocolVersion: BRIDGE_PROTOCOL_VERSION,
    capabilities: getCapabilities(),
  };
}
