import { type ReactNode } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AuthSessionProvider } from './authSessionContext';

type AppProvidersProps = {
  children: ReactNode;
};

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <AuthSessionProvider>
      <BrowserRouter>{children}</BrowserRouter>
    </AuthSessionProvider>
  );
}
