import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState, type CSSProperties } from 'react';
import {
  extractAndStoreSafeAreaTop,
  extractAndStoreSafeAreaBottom,
  getSafeAreaTopExtra,
  getSafeAreaBottomExtra,
  SAFE_AREA_TOP_PARAMS,
  SAFE_AREA_BOTTOM_PARAMS,
} from '../routing/safeAreaParams';
import { BottomNavigation } from './BottomNavigation';
import { Toast } from '../ui/Toast';
import type { GlobalToastDetail } from '../ui/globalToast';
import { showGlobalToast } from '../ui/globalToast';
import { on } from '../native/bridgeTransport';
import { getNativePlatform } from '../native/nativeBridge';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isReservationDetail = Boolean(location.pathname.match(/^\/reservations\/[^/]+$/));
  const isAuthFlow = ['/landing', '/terms', '/sso_callback'].includes(location.pathname);
  const showBottomNavigation = !isReservationDetail && !isAuthFlow;

  const [globalToastMessage, setGlobalToastMessage] = useState('');
  const globalToastOnTap = useRef<(() => void) | undefined>(undefined);
  const globalToastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return on('app.backPressed', () => {
      showGlobalToast('한 번 더 누르면 앱이 종료됩니다');
    });
  }, []);

  useEffect(() => {
    function handleGlobalToast(e: Event) {
      const { message, onTap } = (e as CustomEvent<GlobalToastDetail>).detail;
      if (globalToastTimer.current) clearTimeout(globalToastTimer.current);
      globalToastOnTap.current = onTap;
      setGlobalToastMessage(message);
      globalToastTimer.current = setTimeout(() => setGlobalToastMessage(''), 3000);
    }

    document.addEventListener('global-toast', handleGlobalToast);
    return () => document.removeEventListener('global-toast', handleGlobalToast);
  }, []);

  // Store synchronously so CSS variables are correct on first paint.
  extractAndStoreSafeAreaTop(location.search);
  extractAndStoreSafeAreaBottom(location.search);
  const safeAreaTopExtra = getSafeAreaTopExtra();
  const safeAreaBottomExtra = getSafeAreaBottomExtra();
  const isAndroid = getNativePlatform() === 'android';
  // Android: status bar is shorter than iOS notch, add 8px breathing room
  const effectiveSafeAreaTop = isAndroid ? safeAreaTopExtra + 8 : safeAreaTopExtra;
  // Android: navigation bar is opaque — use full inset; iOS: home indicator is transparent — half suffices
  const safeAreaBottomSpacing = isAndroid ? safeAreaBottomExtra : safeAreaBottomExtra / 2;

  const deviceStyle = {
    '--space-safe-area-top-extra': `${effectiveSafeAreaTop}px`,
    '--space-safe-area-bottom-extra': `${safeAreaBottomSpacing}px`,
    '--space-sys-nav-bottom': isAndroid ? `${safeAreaBottomExtra}px` : '0px',
  } as CSSProperties;

  useEffect(() => {
    document.documentElement.style.setProperty('--space-safe-area-top-extra', `${effectiveSafeAreaTop}px`);
    document.documentElement.style.setProperty('--space-safe-area-bottom-extra', `${safeAreaBottomSpacing}px`);
    document.documentElement.style.setProperty('--space-sys-nav-bottom', isAndroid ? `${safeAreaBottomExtra}px` : '0px');
  }, [effectiveSafeAreaTop, safeAreaBottomSpacing]);

  // Remove safe area params from URL after storing them.
  useEffect(() => {
    const hasTop = extractAndStoreSafeAreaTop(location.search);
    const hasBottom = extractAndStoreSafeAreaBottom(location.search);
    if (!hasTop && !hasBottom) return;
    const params = new URLSearchParams(location.search);
    [...SAFE_AREA_TOP_PARAMS, ...SAFE_AREA_BOTTOM_PARAMS].forEach((p) => params.delete(p));
    const newSearch = params.toString();
    navigate(
      { pathname: location.pathname, search: newSearch ? `?${newSearch}` : '', hash: location.hash },
      { replace: true },
    );
  }, [location.search, location.pathname, location.hash, navigate]);

  return (
    <div className={styles.viewport}>
      <div className={styles.device} style={deviceStyle}>
        <main className={styles.main}>
          <div className={styles.routeFrame} key={location.pathname}>
            <Outlet />
          </div>
        </main>
        {showBottomNavigation ? <BottomNavigation /> : null}
        <Toast
          message={globalToastMessage}
          onTap={globalToastOnTap.current}
          bottomOffset={showBottomNavigation ? 96 : 32}
        />
      </div>
    </div>
  );
}
