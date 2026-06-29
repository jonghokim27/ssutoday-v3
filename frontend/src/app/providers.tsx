import { type ReactNode, useEffect } from 'react';
import { BrowserRouter, useNavigate } from 'react-router-dom';
import { AuthSessionProvider } from './authSessionContext';

type AppProvidersProps = {
  children: ReactNode;
};

function NavigateExposer() {
  const navigate = useNavigate();
  useEffect(() => {
    (window as unknown as Record<string, unknown>).__spaNavigate = navigate;
    return () => {
      delete (window as unknown as Record<string, unknown>).__spaNavigate;
    };
  }, [navigate]);
  return null;
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <AuthSessionProvider>
      <BrowserRouter>
        <NavigateExposer />
        {children}
      </BrowserRouter>
    </AuthSessionProvider>
  );
}
