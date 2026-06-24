import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { validateAuthSession, type AuthSessionResult } from './authSession';

type AuthSessionState = AuthSessionResult | 'checking';

type AuthSessionContextValue = {
  session: AuthSessionState;
  setSession: (session: AuthSessionResult) => void;
  refreshSession: () => Promise<AuthSessionResult>;
};

const AuthSessionContext = createContext<AuthSessionContextValue | null>(null);

type AuthSessionProviderProps = {
  children: ReactNode;
};

export function AuthSessionProvider({ children }: AuthSessionProviderProps) {
  const [session, setSessionState] = useState<AuthSessionState>('checking');

  async function refreshSession() {
    const result = await validateAuthSession();
    setSessionState(result);
    return result;
  }

  useEffect(() => {
    let mounted = true;
    void validateAuthSession().then((result) => {
      if (mounted) {
        setSessionState(result);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  const value = useMemo<AuthSessionContextValue>(
    () => ({
      session,
      setSession: setSessionState,
      refreshSession,
    }),
    [session],
  );

  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>;
}

export function useAuthSession() {
  const context = useContext(AuthSessionContext);
  if (!context) {
    throw new Error('useAuthSession must be used within AuthSessionProvider');
  }

  return context;
}
