import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, type CSSProperties } from 'react';
import {
  extractAndStoreSafeAreaTop,
  getSafeAreaTopExtra,
  SAFE_AREA_TOP_PARAMS,
} from '../routing/safeAreaParams';
import { BottomNavigation } from './BottomNavigation';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isReservationDetail = Boolean(location.pathname.match(/^\/reservations\/[^/]+$/));
  const isAuthFlow = ['/landing', '/terms', '/sso_callback'].includes(location.pathname);
  const showBottomNavigation = !isReservationDetail && !isAuthFlow;

  // Store synchronously so CSS variable is correct on first paint.
  extractAndStoreSafeAreaTop(location.search);
  const safeAreaTopExtra = getSafeAreaTopExtra();

  const deviceStyle = {
    '--space-safe-area-top-extra': `${safeAreaTopExtra}px`,
  } as CSSProperties;

  useEffect(() => {
    document.documentElement.style.setProperty('--space-safe-area-top-extra', `${safeAreaTopExtra}px`);
  }, [safeAreaTopExtra]);

  // Remove safe area params from URL after storing them.
  useEffect(() => {
    if (!extractAndStoreSafeAreaTop(location.search)) return;
    const params = new URLSearchParams(location.search);
    SAFE_AREA_TOP_PARAMS.forEach((p) => params.delete(p));
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
