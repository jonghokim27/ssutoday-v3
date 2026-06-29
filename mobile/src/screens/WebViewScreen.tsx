import { useCallback, useEffect, useRef, useState } from 'react';
import { Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import * as Application from 'expo-application';
import * as ImagePicker from 'expo-image-picker';
import * as LocalAuthentication from 'expo-local-authentication';
import * as Notifications from 'expo-notifications';
import NetInfo from '@react-native-community/netinfo';
import WebView, { type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BridgeHandlerError, dispatch, getHandshakeInfo, registerHandler } from '../bridge/registry';
import { parseBridgeEnvelope, type BridgeResponseEnvelope } from '../bridge/protocol';
import OfflineScreen from './OfflineScreen';
import TurnstileModal from './TurnstileModal';

const TARGET_URL = 'https://v3.ssu.today';

const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const OS_INFO = `${Platform.OS} ${Platform.Version}`;
const DEVICE_NAME = Constants.deviceName ?? 'unknown';
const USER_AGENT = `SSUTODAY/${APP_VERSION}/${OS_INFO}/${DEVICE_NAME}`;
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

function isSmartIdUrl(url: string): boolean {
  try {
    return new URL(url).host === 'smartid.ssu.ac.kr';
  } catch {
    return false;
  }
}

export default function WebViewScreen() {
  const insets = useSafeAreaInsets();
  const webviewRef = useRef<WebView>(null);
  const [isOnline, setIsOnline] = useState(true);
  const [currentUrl, setCurrentUrl] = useState(TARGET_URL);
  const [turnstileRequest, setTurnstileRequest] = useState<{ siteKey: string; action: string } | null>(null);
  const turnstileCallbackRef = useRef<{ resolve: (token: string) => void; reject: () => void } | null>(null);

  useEffect(() => {
    NetInfo.fetch().then((state) => {
      const connected = state.isConnected === true && state.isInternetReachable !== false;
      setIsOnline(connected);
    });
  }, []);

  useEffect(() => {
    registerHandler('device.getInfo', async () => {
      const uuid = Platform.OS === 'ios'
        ? ((await Application.getIosIdForVendorAsync()) ?? 'unknown')
        : (Application.getAndroidId() ?? 'unknown');
      return {
        osType: Platform.OS as 'ios' | 'android',
        uuid,
        appVersion: Constants.expoConfig?.version ?? '0.0.0',
      };
    });

    registerHandler('push.requestPermission', async () => {
      const result = await Notifications.requestPermissionsAsync();
      return (result as unknown as { granted: boolean }).granted;
    });

    registerHandler('push.getToken', async () => {
      const result = await Notifications.getPermissionsAsync();
      if (!(result as unknown as { granted: boolean }).granted) return null;
      try {
        const token = await Notifications.getExpoPushTokenAsync({
          projectId: Constants.expoConfig?.extra?.eas?.projectId as string,
        });
        return token.data;
      } catch {
        return null;
      }
    });

    registerHandler('push.subscribeTopic', async () => {
      // FCM topic 구독은 Firebase SDK 필요 - 서버에서 관리
    });

    registerHandler('push.unsubscribeTopic', async () => {
      // FCM topic 구독 해제는 Firebase SDK 필요 - 서버에서 관리
    });

    registerHandler('browser.openExternalUrl', async (params) => {
      const { url, mode } = params as { url: string; mode?: string };
      if (mode === 'internal') {
        router.push({ pathname: '/browser', params: { url } });
      } else {
        await Linking.openURL(url);
      }
    });

    registerHandler('system.openAppSettings', async () => {
      await Linking.openSettings();
    });

    registerHandler('auth.signWithBiometrics', async (params) => {
      const { payload } = params as { payload: string };

      const compatible = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      if (!compatible || !enrolled) {
        return null;
      }

      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: '본인 확인을 위해 생체 인증을 진행해주세요',
        cancelLabel: '취소',
        disableDeviceFallback: false,
      });

      if (!result.success) {
        return null;
      }

      return { signature: payload };
    });

    registerHandler('camera.requestPermission', async () => {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      return status === 'granted';
    });

    registerHandler('camera.captureVerifyPhoto', async () => {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      if (status !== 'granted') {
        throw new BridgeHandlerError('PERMISSION_DENIED', '카메라 권한이 없습니다');
      }

      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: 'images',
        allowsEditing: false,
        quality: 0.8,
        base64: true,
        exif: false,
      });

      if (result.canceled) {
        return null;
      }

      const asset = result.assets[0];
      if (!asset.base64) {
        throw new BridgeHandlerError('NATIVE_ERROR', '이미지 데이터를 가져오지 못했습니다');
      }
      return {
        name: `verify-photo-${Date.now()}.jpg`,
        type: 'image/jpeg',
        uri: `data:image/jpeg;base64,${asset.base64}`,
      };
    });

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
        const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다';
        const response: BridgeResponseEnvelope = { v: 1, kind: 'response', id, ok: false, error: { code, message } };
        webviewRef.current?.postMessage(JSON.stringify(response));
      });
  }, []);

  const handleShouldStartLoadWithRequest = useCallback((request: WebViewNavigation) => {
    return isAllowedNavigation(request.url);
  }, []);

  const handleNavigationStateChange = useCallback((state: WebViewNavigation) => {
    setCurrentUrl(state.url);
  }, []);

  const handleBack = useCallback(() => {
    webviewRef.current?.goBack();
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

  const smartId = isSmartIdUrl(currentUrl);

  return (
    <View style={styles.container}>
      {smartId && (
        <View style={styles.smartIdHeader}>
          <View style={[styles.smartIdHeaderInner, { paddingTop: insets.top > 0 ? insets.top : 18 }]}>
            <Pressable
              style={({ pressed }) => [styles.backButton, pressed && styles.backButtonPressed]}
              onPress={handleBack}
              hitSlop={8}
            >
              <View style={styles.chevron} />
            </Pressable>
          </View>
        </View>
      )}
      <WebView
        ref={webviewRef}
        style={styles.webview}
        source={{ uri: targetUri }}
        onLoad={sendHandshake}
        onMessage={handleMessage}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        onNavigationStateChange={handleNavigationStateChange}
        userAgent={USER_AGENT}
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
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  webview: {
    flex: 1,
  },
  smartIdHeader: {
    backgroundColor: '#ffffff',
  },
  smartIdHeaderInner: {
    paddingHorizontal: 22,
    paddingBottom: 12,
  },
  backButton: {
    width: 42,
    height: 42,
    borderRadius: 14,
    backgroundColor: '#f2f3f8',
    alignItems: 'center',
    justifyContent: 'center',
  },
  backButtonPressed: {
    transform: [{ scale: 0.94 }],
    backgroundColor: '#e9ebf3',
  },
  chevron: {
    width: 9,
    height: 9,
    borderLeftWidth: 2,
    borderBottomWidth: 2,
    borderColor: '#4f5566',
    transform: [{ rotate: '45deg' }],
    marginLeft: 3,
  },
});
