import { useEffect } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { LoadingState } from '../shared/ui/LoadingState';
import { useAuthSession } from './authSessionContext';
import styles from './routeGuards.module.css';

export function ProtectedRoute() {
  const location = useLocation();
  const { session, setSession } = useAuthSession();
  const hasTokens = hasAuthTokens();

  useEffect(() => {
    if (!hasTokens && session !== 'anonymous') {
      setSession('anonymous');
    }
  }, [hasTokens, session, setSession]);

  if (!hasTokens) {
    return <Navigate to="/landing" replace state={{ from: location.pathname }} />;
  }

  if (session === 'checking') {
    return <RouteLoading />;
  }

  if (session === 'anonymous') {
    return <Navigate to="/landing" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const { session } = useAuthSession();
  const hasTokens = hasAuthTokens();

  if (session === 'checking' && hasTokens) {
    return <RouteLoading />;
  }

  if (session === 'authenticated') {
    return <Navigate to="/reservations" replace />;
  }

  return <Outlet />;
}

function RouteLoading() {
  return <LoadingState className={styles.loading} label="로그인 상태를 확인하는 중" />;
}

function hasAuthTokens() {
  return Boolean(window.localStorage.getItem('accessToken') && window.localStorage.getItem('refreshToken'));
}
