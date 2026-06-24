import { type ComponentProps } from 'react';
import { Navigate } from 'react-router-dom';
import { useSafeAreaPath } from './safeAreaParams';

type SafeAreaNavigateProps = Omit<ComponentProps<typeof Navigate>, 'to'> & {
  to: string;
};

export function SafeAreaNavigate({ to, ...props }: SafeAreaNavigateProps) {
  const safePath = useSafeAreaPath();
  return <Navigate {...props} to={safePath(to)} />;
}
