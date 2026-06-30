import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import * as Application from 'expo-application';
import * as SplashScreen from 'expo-splash-screen';
import * as Haptics from 'expo-haptics';
import * as ImagePicker from 'expo-image-picker';
import * as LocalAuthentication from 'expo-local-authentication';
import * as Notifications from 'expo-notifications';
import NetInfo from '@react-native-community/netinfo';
import WebView, { type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import messaging from '@react-native-firebase/messaging';
import { BridgeHandlerError, dispatch, getHandshakeInfo, registerHandler } from '../bridge/registry';
import { deepLink } from '../utils/deepLink';
import { parseBridgeEnvelope, type BridgeResponseEnvelope } from '../bridge/protocol';
import OfflineScreen from './OfflineScreen';
import UpdateRequiredScreen from './UpdateRequiredScreen';
import TurnstileModal from './TurnstileModal';

const TARGET_URL = 'https://v3.ssu.today';
const VERSION_CHECK_URL = 'https://api.ssu.today/device/checkVersion';

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

type VersionStatus = 'checking' | 'ok' | 'update-required' | 'offline';

export default function WebViewScreen() {
  const insets = useSafeAreaInsets();
  const webviewRef = useRef<WebView>(null);
  const [versionStatus, setVersionStatus] = useState<VersionStatus>('checking');
  const [isOnline, setIsOnline] = useState(true);
  const [currentUrl, setCurrentUrl] = useState(TARGET_URL);
  const [webviewCanGoBack, setWebviewCanGoBack] = useState(false);
  const [turnstileRequest, setTurnstileRequest] = useState<{ siteKey: string; action: string } | null>(null);
  const turnstileCallbackRef = useRef<{ resolve: (token: string) => void; reject: () => void } | null>(null);
  const webviewReady = useRef(false);
  const pendingReservationNav = useRef(false);
  const smartIdHeaderHeightRef = useRef(0);

  const checkVersion = useCallback(async () => {
    setVersionStatus('checking');
    try {
      const res = await fetch(VERSION_CHECK_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ osType: Platform.OS as 'ios' | 'android', version: APP_VERSION }),
      });
      const json = await res.json() as { statusCode: string };
      setVersionStatus(json.statusCode === 'SSU2071' ? 'update-required' : 'ok');
    } catch {
      setVersionStatus('offline');
    } finally {
      void SplashScreen.hideAsync();
    }
  }, []);

  useEffect(() => {
    void checkVersion();
  }, [checkVersion]);

  const injectReservationNavigation = useCallback(() => {
    webviewRef.current?.injectJavaScript(`
      (function() {
        var attempt = 0;
        var iv = setInterval(function() {
          attempt++;
          if (typeof window.__spaNavigate === 'function') {
            clearInterval(iv);
            window.__spaNavigate('/reservations/history');
          } else if (attempt >= 100) {
            clearInterval(iv);
          }
        }, 50);
      })();
      true;
    `);
  }, []);

  useEffect(() => {
    return deepLink.subscribe((payload) => {
      if (payload.type === 'reservation') {
        if (webviewReady.current) {
          injectReservationNavigation();
        } else {
          pendingReservationNav.current = true;
        }
      }
    });
  }, [injectReservationNavigation]);

  useEffect(() => {
    registerHandler('haptic.impact', async (params) => {
      const { style } = (params ?? {}) as { style?: string };
      switch (style) {
        case 'medium':
          await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
          break;
        case 'heavy':
          await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
          break;
        case 'selection':
          await Haptics.selectionAsync();
          break;
        default:
          await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      }
    });

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
      const { granted } = result as unknown as { granted: boolean };
      if (!granted) {
        throw new BridgeHandlerError('PERMISSION_DENIED', '설정에서 알림 권한을 허용해 주세요');
      }
      return granted;
    });

    registerHandler('push.getToken', async () => {
      const result = await Notifications.getPermissionsAsync();
      if (!(result as unknown as { granted: boolean }).granted) return null;
      try {
        return await messaging().getToken();
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
        throw new BridgeHandlerError('PERMISSION_DENIED', '설정에서 카메라 권한을 허용해 주세요');
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

  const targetUri = `${TARGET_URL}?safeAreaTop=${Math.round(insets.top)}&safeAreaBottom=${Math.round(insets.bottom)}`;

  const sendHandshake = useCallback(() => {
    const handshake = { v: 1 as const, kind: 'handshake' as const, id: generateId(), ...getHandshakeInfo() };
    webviewRef.current?.postMessage(JSON.stringify(handshake));
    webviewReady.current = true;
    if (pendingReservationNav.current) {
      pendingReservationNav.current = false;
      injectReservationNavigation();
    }
  }, [injectReservationNavigation]);

  const handleLoad = useCallback((e: { nativeEvent: WebViewNavigation }) => {
    sendHandshake();
    if (isSmartIdUrl(e.nativeEvent.url) && smartIdHeaderHeightRef.current > 0) {
      webviewRef.current?.injectJavaScript(
        `document.body.style.marginTop='${smartIdHeaderHeightRef.current + 20}px';true;`
      );
    }
  }, [sendHandshake]);

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
    // iOS 스와이프 뒤로가기/앞으로가기 인터셉트
    if (request.navigationType === 'backforward') {
      try {
        const dest = new URL(request.url);
        if (dest.host !== 'v3.ssu.today') return false; // SSO 등 외부 페이지로 복귀 차단
        const curr = new URL(currentUrl);
        const onAuthenticatedPage =
          curr.host === 'v3.ssu.today' &&
          !curr.pathname.startsWith('/landing') &&
          !curr.pathname.startsWith('/terms');
        if (onAuthenticatedPage) return true; // 인증 앱 내부: 자유롭게 뒤로가기 허용
        return dest.pathname.startsWith('/landing'); // 인증 플로우 중: /landing으로만 허용
      } catch {
        return false;
      }
    }
    // SSO 등 외부 도메인에 있을 때는 HTTPS 네비게이션을 모두 허용 (SSO 리다이렉트 체인 통과)
    if (!currentUrl.startsWith(TARGET_URL)) {
      return request.url.startsWith('https://') || request.url.startsWith('about:');
    }
    return isAllowedNavigation(request.url);
  }, [currentUrl]);

  const handleNavigationStateChange = useCallback((state: WebViewNavigation) => {
    setCurrentUrl(state.url);
    setWebviewCanGoBack(state.canGoBack);
  }, []);

  const backPressedOnce = useRef(false);
  const backPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (Platform.OS !== 'android') return;

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (versionStatus !== 'ok' || !isOnline) return false;

      if (webviewCanGoBack && !currentUrl.includes('/landing')) {
        webviewRef.current?.goBack();
        return true;
      }

      if (backPressedOnce.current) {
        BackHandler.exitApp();
        return true;
      }

      backPressedOnce.current = true;
      webviewRef.current?.postMessage(JSON.stringify({ v: 1, kind: 'event', event: 'app.backPressed' }));

      backPressTimer.current = setTimeout(() => {
        backPressedOnce.current = false;
      }, 3000);

      return true;
    });

    return () => {
      subscription.remove();
      if (backPressTimer.current) clearTimeout(backPressTimer.current);
      backPressedOnce.current = false;
    };
  }, [versionStatus, isOnline, webviewCanGoBack, currentUrl]);

  const handleBack = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
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

  if (versionStatus === 'checking') return null;
  if (versionStatus === 'update-required') return <UpdateRequiredScreen />;
  if (versionStatus === 'offline') return <OfflineScreen onRetry={checkVersion} />;

  if (!isOnline) {
    return <OfflineScreen onRetry={handleRetry} />;
  }

  const smartId = isSmartIdUrl(currentUrl);

  return (
    <View style={styles.container}>
      <WebView
        ref={webviewRef}
        style={styles.webview}
        source={{ uri: targetUri }}
        onLoad={handleLoad}
        onMessage={handleMessage}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        onNavigationStateChange={handleNavigationStateChange}
        userAgent={USER_AGENT}
        originWhitelist={['https://*', 'about:*']}
        allowsBackForwardNavigationGestures={!currentUrl.includes('/landing')}
      />
      {smartId && (
        <View
          style={[styles.smartIdHeader, { paddingTop: insets.top > 0 ? insets.top : 18 }]}
          onLayout={(e) => {
            const h = e.nativeEvent.layout.height;
            smartIdHeaderHeightRef.current = h;
            webviewRef.current?.injectJavaScript(
              `document.body.style.marginTop='${h + 20}px';true;`
            );
          }}
        >
          <Pressable
            style={({ pressed }) => [styles.backButton, pressed && styles.backButtonPressed]}
            onPress={handleBack}
            hitSlop={8}
          >
            <View style={styles.chevron} />
          </Pressable>
        </View>
      )}
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
    backgroundColor: '#ffffff',
  },
  webview: {
    flex: 1,
  },
  smartIdHeader: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 22,
    paddingBottom: 12,
    backgroundColor: '#ffffff',
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
