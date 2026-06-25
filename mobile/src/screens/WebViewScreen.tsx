import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import WebView, { type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BridgeHandlerError, dispatch, getHandshakeInfo } from '../bridge/registry';
import { parseBridgeEnvelope, type BridgeResponseEnvelope } from '../bridge/protocol';
import OfflineScreen from './OfflineScreen';

const TARGET_URL = 'https://v3.ssu.today';
const ALLOWED_HOST = 'v3.ssu.today';

function generateId(): string {
  return `native-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isAllowedNavigation(url: string): boolean {
  try {
    return new URL(url).host === ALLOWED_HOST;
  } catch {
    return false;
  }
}

export default function WebViewScreen() {
  const insets = useSafeAreaInsets();
  const webviewRef = useRef<WebView>(null);
  const [isOnline, setIsOnline] = useState(true);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      setIsOnline(state.isConnected !== false);
    });
    return unsubscribe;
  }, []);

  const targetUri = `${TARGET_URL}?safeAreaTop=${Math.round(insets.top)}`;

  const sendHandshake = useCallback(() => {
    const handshake = { v: 1 as const, kind: 'handshake' as const, id: generateId(), ...getHandshakeInfo() };
    webviewRef.current?.postMessage(JSON.stringify(handshake));
  }, []);

  const handleMessage = useCallback((event: WebViewMessageEvent) => {
    const envelope = parseBridgeEnvelope(event.nativeEvent.data);
    if (!envelope || envelope.kind !== 'request') {
      return;
    }

    const { id, method, params } = envelope;

    dispatch(method, params)
      .then((result) => {
        const response: BridgeResponseEnvelope = { v: 1, kind: 'response', id, ok: true, result };
        webviewRef.current?.postMessage(JSON.stringify(response));
      })
      .catch((error: unknown) => {
        const code = error instanceof BridgeHandlerError ? error.code : 'NATIVE_ERROR';
        const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.';
        const response: BridgeResponseEnvelope = { v: 1, kind: 'response', id, ok: false, error: { code, message } };
        webviewRef.current?.postMessage(JSON.stringify(response));
      });
  }, []);

  const handleShouldStartLoadWithRequest = useCallback((request: WebViewNavigation) => {
    return isAllowedNavigation(request.url);
  }, []);

  const handleRetry = useCallback(() => {
    NetInfo.fetch().then((state) => {
      setIsOnline(state.isConnected !== false);
      if (state.isConnected !== false) {
        webviewRef.current?.reload();
      }
    });
  }, []);

  if (!isOnline) {
    return <OfflineScreen onRetry={handleRetry} />;
  }

  return (
    <WebView
      ref={webviewRef}
      style={styles.webview}
      source={{ uri: targetUri }}
      onLoad={sendHandshake}
      onMessage={handleMessage}
      onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
      originWhitelist={['https://*']}
      allowsBackForwardNavigationGestures
    />
  );
}

const styles = StyleSheet.create({
  webview: {
    flex: 1,
  },
});
