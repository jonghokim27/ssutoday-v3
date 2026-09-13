import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, BackHandler, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import Constants from 'expo-constants';
import { router, useFocusEffect } from 'expo-router';
import * as Application from 'expo-application';
import * as SplashScreen from 'expo-splash-screen';
import * as Haptics from 'expo-haptics';
import { Camera } from 'expo-camera';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as LocalAuthentication from 'expo-local-authentication';
import * as Notifications from 'expo-notifications';
import NetInfo from '@react-native-community/netinfo';
import WebView, { type WebViewMessageEvent, type WebViewNavigation } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import messaging from '@react-native-firebase/messaging';
import { BridgeHandlerError, clearHandlers, dispatch, getHandshakeInfo, registerHandler } from '../bridge/registry';
import { canDispatchBridge, isAppAttestKey, isAppAttestStudent, isAttestParams, isCaptureScope, isTrustedBridgeUrl, secureBridgeScript } from '../bridge/bridgeSecurity';
import AttestationModule from '../../modules/ssutoday-attestation';
import { deepLink } from '../utils/deepLink';
import { parseBridgeEnvelope, type BridgeResponseEnvelope } from '../bridge/protocol';
import OfflineScreen from './OfflineScreen';
import UpdateRequiredScreen from './UpdateRequiredScreen';
import TurnstileModal from './TurnstileModal';
import VerifyPhotoCameraModal from './VerifyPhotoCameraModal';

const TARGET_URL = 'https://v3.ssu.today';

const DISABLE_CONTEXT_MENU_JS = `
(function(){
  document.addEventListener('contextmenu',function(e){e.preventDefault();},true);
  function addStyle(){
    var s=document.createElement('style');
    s.textContent='*{-webkit-touch-callout:none!important;}';
    document.head.appendChild(s);
  }
  if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',addStyle);}
  else{addStyle();}
})();
true;
`;
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
  const documentUrl = useRef(TARGET_URL);
  const documentEpoch = useRef(0);
  const captureGeneration = useRef(0);
  const captureBusy = useRef(false);
  const [cameraVisible, setCameraVisible] = useState(false);
  const cameraCallback = useRef<{ resolve: (uri: string | null) => void; reject: () => void } | null>(null);
  const bridgeToken = useRef<string | null>(null);
  if (AttestationModule && !bridgeToken.current) {
    bridgeToken.current = AttestationModule.createBridgeToken();
  }

  const clearCaptures = useCallback(() => {
    captureGeneration.current++;
    AttestationModule?.clearCaptures();
    cameraCallback.current?.resolve(null);
    cameraCallback.current = null;
    setCameraVisible(false);
  }, []);

  useFocusEffect(useCallback(() => () => clearCaptures(), [clearCaptures]));

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'background') clearCaptures();
    });
    return () => subscription.remove();
  }, [clearCaptures]);

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
        throw new BridgeHandlerError('PERMISSION_DENIED', '기기에서 생체 인증을 설정해 주세요');
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
      const { status } = await Camera.requestCameraPermissionsAsync();
      return status === 'granted';
    });

    registerHandler('camera.captureVerifyPhoto', async (params) => {
      if (captureBusy.current) throw new BridgeHandlerError('INVALID_PARAMS', '촬영이 이미 진행 중입니다');
      if (params !== undefined && !isCaptureScope(params)) throw new BridgeHandlerError('INVALID_PARAMS', '촬영 요청이 올바르지 않습니다');
      clearCaptures();
      const generation = captureGeneration.current;
      captureBusy.current = true;
      try {
        const permissionApi = Camera;
        const current = await permissionApi.getCameraPermissionsAsync();
        if (current.status !== 'granted') {
          if (!current.canAskAgain) {
            throw new BridgeHandlerError('PERMISSION_DENIED', '설정에서 카메라 권한을 허용해 주세요');
          }
          const { status } = await permissionApi.requestCameraPermissionsAsync();
          if (status !== 'granted') {
            throw new BridgeHandlerError('PERMISSION_DENIED', '설정에서 카메라 권한을 허용해 주세요');
          }
        }

        if (generation !== captureGeneration.current) return null;
        const photoUri = await new Promise<string | null>((resolve, reject) => {
          cameraCallback.current = { resolve, reject: () => reject(new BridgeHandlerError('NATIVE_ERROR', '촬영하지 못했습니다')) };
          setCameraVisible(true);
        });
        if (!photoUri) return null;
        if (generation !== captureGeneration.current) return null;
        const context = ImageManipulator.manipulate(photoUri);
        context.resize({ width: 1280 });
        const imageRef = await context.renderAsync();
        const manipulated = await imageRef.saveAsync({
          compress: 0.8,
          format: SaveFormat.JPEG,
          base64: !(AttestationModule && isCaptureScope(params)),
        });

        if (generation !== captureGeneration.current) return null;
        if (AttestationModule && isCaptureScope(params)) {
          const registered = await AttestationModule.storeCapture(manipulated.uri, params.studentId, params.reservationId);
          if (generation !== captureGeneration.current) {
            AttestationModule.releaseCapture(registered.captureId);
            return null;
          }
          return { ...registered, name: `verify-photo-${Date.now()}.jpg`, type: 'image/jpeg' };
        }
        if (!manipulated.base64) {
          throw new BridgeHandlerError('NATIVE_ERROR', '이미지 데이터를 가져오지 못했습니다');
        }
        return {
          name: `verify-photo-${Date.now()}.jpg`,
          type: 'image/jpeg',
          uri: `data:image/jpeg;base64,${manipulated.base64}`,
        };
      } finally {
        captureBusy.current = false;
      }
    });

    if (AttestationModule) {
      const module = AttestationModule;
      if (Platform.OS === 'ios') {
        const callIos = async <T,>(operation: () => Promise<T>): Promise<T> => {
          try { return await operation(); }
          catch (error) {
            const code = (error as { code?: string })?.code;
            if (code === 'ERR_INTEGRITY_UNAVAILABLE') throw new BridgeHandlerError('ATTESTATION_UNAVAILABLE', '증명을 준비하지 못했습니다');
            if (code === 'ERR_APP_ATTEST_KEY_INVALID') throw new BridgeHandlerError('APP_ATTEST_KEY_INVALID', '앱 인증 키를 다시 준비해 주세요');
            throw new BridgeHandlerError('ATTESTATION_REJECTED', '증명 요청이 올바르지 않습니다');
          }
        };
        registerHandler('security.prepareAppAttest', async params => {
          if (!isAppAttestStudent(params)) throw new BridgeHandlerError('INVALID_PARAMS', '학생 정보가 올바르지 않습니다');
          return callIos(() => module.prepareAppAttest(params.studentId));
        });
        registerHandler('security.attestRegister', async params => {
          const input = params as { studentId: number; keyId: string; challenge?: unknown };
          if (!isAppAttestKey(input) || typeof input.challenge !== 'string' || !/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(input.challenge)) {
            throw new BridgeHandlerError('INVALID_PARAMS', '등록 요청이 올바르지 않습니다');
          }
          const challenge = input.challenge;
          return callIos(() => module.attestRegister(input.studentId, input.keyId, challenge));
        });
        registerHandler('security.confirmAppAttest', async params => {
          if (!isAppAttestKey(params)) throw new BridgeHandlerError('INVALID_PARAMS', '키 정보가 올바르지 않습니다');
          return callIos(() => module.confirmAppAttest(params.studentId, params.keyId));
        });
        registerHandler('security.resetAppAttest', async params => {
          if (!isAppAttestKey(params)) throw new BridgeHandlerError('INVALID_PARAMS', '키 정보가 올바르지 않습니다');
          return callIos(() => module.resetAppAttest(params.studentId, params.keyId));
        });
      }
      if (Platform.OS === 'android') registerHandler('security.prepareAttestation', async () => {
        try { await module.prepare(); } catch { throw new BridgeHandlerError('ATTESTATION_UNAVAILABLE', '증명을 준비하지 못했습니다'); }
      });
      registerHandler('security.attest', async (params) => {
        if (!isAttestParams(params)) throw new BridgeHandlerError('INVALID_PARAMS', '증명 요청이 올바르지 않습니다');
        try {
          return await module.attest(params.captureId, params.studentId, params.reservationId, params.challenge);
        } catch (error) {
          const code = (error as { code?: string })?.code;
          if (code === 'ERR_APP_ATTEST_KEY_INVALID') throw new BridgeHandlerError('APP_ATTEST_KEY_INVALID', '기기 인증 키를 다시 등록해 주세요');
          if (code === 'ERR_INTEGRITY_UNAVAILABLE') throw new BridgeHandlerError('ATTESTATION_UNAVAILABLE', '증명을 생성하지 못했습니다');
          throw new BridgeHandlerError('ATTESTATION_REJECTED', '사진을 다시 촬영해 주세요');
        }
      });
      registerHandler('security.releaseCapture', async (params) => {
        const id = (params as { captureId?: unknown })?.captureId;
        if (typeof id !== 'string') throw new BridgeHandlerError('INVALID_PARAMS', '잘못된 촬영 정보입니다');
        module.releaseCapture(id);
      });
      registerHandler('security.clearCaptures', async () => { clearCaptures(); });
    }

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
    return () => {
      webviewReady.current = false;
      documentEpoch.current++;
      clearCaptures();
      clearHandlers();
    };
  }, [clearCaptures]);

  const [targetUri] = useState(
    () => `${TARGET_URL}?safeAreaTop=${Math.round(insets.top)}&safeAreaBottom=${Math.round(insets.bottom)}`
  );

  const sendHandshake = useCallback(() => {
    const handshake = { v: 1 as const, kind: 'handshake' as const, id: generateId(), ...getHandshakeInfo() };
    webviewRef.current?.injectJavaScript(`${secureBridgeScript(bridgeToken.current)}
      if (window.top === window && location.origin === ${JSON.stringify(TARGET_URL)}) {
        window.dispatchEvent(new MessageEvent('message', { data: ${JSON.stringify(JSON.stringify(handshake))} }));
      }
      true;`);
    webviewReady.current = true;
    if (pendingReservationNav.current) {
      pendingReservationNav.current = false;
      injectReservationNavigation();
    }
  }, [injectReservationNavigation]);

  const handleLoad = useCallback((e: { nativeEvent: WebViewNavigation }) => {
    documentUrl.current = e.nativeEvent.url;
    if (isTrustedBridgeUrl(e.nativeEvent.url)) sendHandshake();
    if (isSmartIdUrl(e.nativeEvent.url) && smartIdHeaderHeightRef.current > 0) {
      webviewRef.current?.injectJavaScript(
        `document.body.style.marginTop='${smartIdHeaderHeightRef.current + 40}px';true;`
      );
    }
  }, [sendHandshake]);

  const handleMessage = useCallback((event: WebViewMessageEvent) => {
    const envelope = parseBridgeEnvelope(event.nativeEvent.data);
    if (!envelope || envelope.kind !== 'request') {
      return;
    }
    if (!canDispatchBridge(event.nativeEvent.url, documentUrl.current, webviewReady.current, envelope.bridgeToken, bridgeToken.current)) return;

    const { id, method, params } = envelope;
    const epoch = documentEpoch.current;

    dispatch(method, params)
      .then((result) => {
        if (epoch !== documentEpoch.current || !webviewReady.current || !isTrustedBridgeUrl(documentUrl.current)) return;
        const response: BridgeResponseEnvelope = { v: 1, kind: 'response', id, ok: true, result };
        webviewRef.current?.postMessage(JSON.stringify(response));
      })
      .catch((error: unknown) => {
        if (epoch !== documentEpoch.current || !webviewReady.current || !isTrustedBridgeUrl(documentUrl.current)) return;
        const code = error instanceof BridgeHandlerError ? error.code : 'NATIVE_ERROR';
        const message = error instanceof BridgeHandlerError ? error.message : '요청을 처리하지 못했습니다';
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
    if (!isTrustedBridgeUrl(currentUrl)) {
      return request.url.startsWith('https://') || request.url.startsWith('about:');
    }
    return isAllowedNavigation(request.url);
  }, [currentUrl]);

  const handleNavigationStateChange = useCallback((state: WebViewNavigation) => {
    if (documentUrl.current !== state.url) clearCaptures();
    documentUrl.current = state.url;
    if (!isTrustedBridgeUrl(state.url) || new URL(state.url).pathname.startsWith('/landing')) clearCaptures();
    setCurrentUrl(state.url);
    setWebviewCanGoBack(state.canGoBack);
  }, [clearCaptures]);

  const backPressedOnce = useRef(false);
  const backPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useFocusEffect(
    useCallback(() => {
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
    }, [versionStatus, isOnline, webviewCanGoBack, currentUrl])
  );

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
  const activeCamera = cameraCallback.current;

  function finishCamera(uri: string | null, failed = false) {
    if (!activeCamera || cameraCallback.current !== activeCamera) return;
    cameraCallback.current = null;
    setCameraVisible(false);
    if (failed) activeCamera.reject();
    else activeCamera.resolve(uri);
  }

  return (
    <View style={styles.container}>
      <WebView
        ref={webviewRef}
        style={styles.webview}
        source={{ uri: targetUri }}
        onLoad={handleLoad}
        onLoadStart={(event) => {
          webviewReady.current = false;
          documentEpoch.current++;
          documentUrl.current = event.nativeEvent.url;
          clearCaptures();
        }}
        onMessage={handleMessage}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        onNavigationStateChange={handleNavigationStateChange}
        userAgent={USER_AGENT}
        originWhitelist={['https://*', 'about:*']}
        allowsBackForwardNavigationGestures={!currentUrl.includes('/landing')}
        allowsLinkPreview={false}
        injectedJavaScriptBeforeContentLoaded={`${secureBridgeScript(bridgeToken.current)}\n${DISABLE_CONTEXT_MENU_JS}`}
        injectedJavaScriptBeforeContentLoadedForMainFrameOnly
        overScrollMode="never"
        sharedCookiesEnabled
      />
      {cameraVisible && <VerifyPhotoCameraModal
        onCapture={(uri) => finishCamera(uri)}
        onCancel={() => finishCamera(null)}
        onError={() => finishCamera(null, true)}
      />}
      {smartId && (
        <View
          style={[styles.smartIdHeader, { paddingTop: (insets.top > 0 ? insets.top : 18) + (Platform.OS === 'android' ? 8 : 0) }]}
          onLayout={(e) => {
            const h = e.nativeEvent.layout.height;
            smartIdHeaderHeightRef.current = h;
            webviewRef.current?.injectJavaScript(
              `document.body.style.marginTop='${h + 40}px';true;`
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
