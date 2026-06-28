import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import WebView, { type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BridgeHandlerError, dispatch, getHandshakeInfo, registerHandler } from '../bridge/registry';
import { parseBridgeEnvelope, type BridgeResponseEnvelope } from '../bridge/protocol';
import OfflineScreen from './OfflineScreen';
import TurnstileModal from './TurnstileModal';

const TARGET_URL = 'https://v3.ssu.today';
const ALLOWED_HOSTS = new Set(['v3.ssu.today', 'smartid.ssu.ac.kr', 'challenges.cloudflare.com']);

function generateId(): string {
  return `native-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isAllowedNavigation(url: string): boolean {
  if (url.startsWith('about:')) return true;
  try {
    return ALLOWED_HOSTS.has(new URL(url).host);
  } catch {
    return false;
  }
}

export default function WebViewScreen() {
  const insets = useSafeAreaInsets();
  const webviewRef = useRef<WebView>(null);
  const [isOnline, setIsOnline] = useState(true);
  const [turnstileRequest, setTurnstileRequest] = useState<{ siteKey: string; action: string } | null>(null);
  const turnstileCallbackRef = useRef<{ resolve: (token: string) => void; reject: () => void } | null>(null);

  useEffect(() => {
    NetInfo.fetch().then((state) => {
      const connected = state.isConnected === true && state.isInternetReachable !== false;
      setIsOnline(connected);
    });
  }, []);

  useEffect(() => {
    registerHandler('network.checkConnectivity', async () => {
      const state = await NetInfo.fetch();
      const connected = state.isConnected === true && state.isInternetReachable !== false;
      if (!connected) setIsOnline(false);
      return { online: connected };
    });

    registerHandler('security.getTurnstileToken', (params) => {
      const { siteKey, action } = params as { siteKey: string; action: string };
      return new Promise<string>((resolve, reject) => {
        turnstileCallbackRef.current = { resolve, reject };
        setTurnstileRequest({ siteKey, action });
      });
    });
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
      const connected = state.isConnected === true && state.isInternetReachable !== false;
      setIsOnline(connected);
      if (connected) {
        webviewRef.current?.reload();
      }
    });
  }, []);

  if (!isOnline) {
    return <OfflineScreen onRetry={handleRetry} />;
  }

  return (
    <>
      <WebView
        ref={webviewRef}
        style={styles.webview}
        source={{ uri: targetUri }}
        onLoad={sendHandshake}
        onMessage={handleMessage}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        userAgent="Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 SSUTODAY"
        originWhitelist={['https://*', 'about:*']}
        allowsBackForwardNavigationGestures
      />
      {turnstileRequest ? (
        <TurnstileModal
          siteKey={turnstileRequest.siteKey}
          action={turnstileRequest.action}
          onToken={(token) => {
            turnstileCallbackRef.current?.resolve(token);
            turnstileCallbackRef.current = null;
            setTurnstileRequest(null);
          }}
          onError={() => {
            turnstileCallbackRef.current?.reject();
            turnstileCallbackRef.current = null;
            setTurnstileRequest(null);
          }}
        />
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  webview: {
    flex: 1,
  },
});
