import { Outlet } from 'react-router-dom';
import { useLocation } from 'react-router-dom';
import { BottomNavigation } from './BottomNavigation';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const location = useLocation();
  const isReservationDetail = Boolean(location.pathname.match(/^\/reservations\/[^/]+$/));
  const isAuthFlow = ['/landing', '/terms', '/sso_callback'].includes(location.pathname);
  const showBottomNavigation = !isReservationDetail && !isAuthFlow;

  return (
    <div className={styles.viewport}>
      <div className={styles.device}>
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
