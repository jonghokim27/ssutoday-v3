import { Outlet } from 'react-router-dom';
import { useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import { type CSSProperties } from 'react';
import { getSafeAreaTopExtra } from '../routing/safeAreaParams';
import { BottomNavigation } from './BottomNavigation';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const location = useLocation();
  const isReservationDetail = Boolean(location.pathname.match(/^\/reservations\/[^/]+$/));
  const isAuthFlow = ['/landing', '/terms', '/sso_callback'].includes(location.pathname);
  const showBottomNavigation = !isReservationDetail && !isAuthFlow;
  const safeAreaTopExtra = getSafeAreaTopExtra(location.search);
  const deviceStyle = {
    '--space-safe-area-top-extra': `${safeAreaTopExtra}px`,
  } as CSSProperties;

  useEffect(() => {
    document.documentElement.style.setProperty('--space-safe-area-top-extra', `${safeAreaTopExtra}px`);
  }, [safeAreaTopExtra]);

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
