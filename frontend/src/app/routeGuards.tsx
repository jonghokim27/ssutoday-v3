import { Outlet, useLocation } from 'react-router-dom';
import { SafeAreaNavigate } from '../shared/routing/SafeAreaNavigate';
import { LoadingState } from '../shared/ui/LoadingState';
import { useAuthSession } from './authSessionContext';
import styles from './routeGuards.module.css';

export function ProtectedRoute() {
  const location = useLocation();
  const { session } = useAuthSession();

  if (session === 'checking') {
    return <RouteLoading />;
  }

  if (session === 'anonymous') {
    return <SafeAreaNavigate to="/landing" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const { session } = useAuthSession();

  if (session === 'authenticated') {
    return <SafeAreaNavigate to="/reservations" replace />;
  }

  return <Outlet />;
}

function RouteLoading() {
  return <LoadingState className={styles.loading} label="로그인 상태를 확인하는 중" />;
}
