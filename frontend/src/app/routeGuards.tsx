import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { validateAuthSession, type AuthSessionResult } from './authSession';

type GuardState = AuthSessionResult | 'checking';

export function ProtectedRoute() {
  const location = useLocation();
  const session = useAuthSession();

  if (session === 'checking') {
    return <div style={{ padding: 24 }}>SSUTODAY를 준비하고 있어요.</div>;
  }

  if (session === 'anonymous') {
    return <Navigate to="/landing" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const session = useAuthSession();

  if (session === 'checking') {
    return <div style={{ padding: 24 }}>SSUTODAY를 준비하고 있어요.</div>;
  }

  if (session === 'authenticated') {
    return <Navigate to="/reservations" replace />;
  }

  return <Outlet />;
}

function useAuthSession() {
  const [session, setSession] = useState<GuardState>('checking');

  useEffect(() => {
    let mounted = true;
    void validateAuthSession().then((result) => {
      if (mounted) {
        setSession(result);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  return session;
}
