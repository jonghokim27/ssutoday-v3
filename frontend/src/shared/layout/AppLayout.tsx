import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, type CSSProperties } from 'react';
import {
  extractAndStoreSafeAreaTop,
  extractAndStoreSafeAreaBottom,
  getSafeAreaTopExtra,
  getSafeAreaBottomExtra,
  SAFE_AREA_TOP_PARAMS,
  SAFE_AREA_BOTTOM_PARAMS,
} from '../routing/safeAreaParams';
import { BottomNavigation } from './BottomNavigation';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isReservationDetail = Boolean(location.pathname.match(/^\/reservations\/[^/]+$/));
  const isAuthFlow = ['/landing', '/terms', '/sso_callback'].includes(location.pathname);
  const showBottomNavigation = !isReservationDetail && !isAuthFlow;

  // Store synchronously so CSS variables are correct on first paint.
  extractAndStoreSafeAreaTop(location.search);
  extractAndStoreSafeAreaBottom(location.search);
  const safeAreaTopExtra = getSafeAreaTopExtra();
  const safeAreaBottomExtra = getSafeAreaBottomExtra();
  const safeAreaBottomSpacing = safeAreaBottomExtra / 2;

  const deviceStyle = {
    '--space-safe-area-top-extra': `${safeAreaTopExtra}px`,
    '--space-safe-area-bottom-extra': `${safeAreaBottomSpacing}px`,
  } as CSSProperties;

  useEffect(() => {
    document.documentElement.style.setProperty('--space-safe-area-top-extra', `${safeAreaTopExtra}px`);
    document.documentElement.style.setProperty('--space-safe-area-bottom-extra', `${safeAreaBottomSpacing}px`);
  }, [safeAreaTopExtra, safeAreaBottomSpacing]);

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
      </div>
    </div>
  );
}
