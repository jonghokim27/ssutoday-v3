import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '../shared/layout/AppLayout';
import { LandingPage } from '../pages/auth/LandingPage';
import { SsoCallbackPage } from '../pages/auth/SsoCallbackPage';
import { TermsPage } from '../pages/auth/TermsPage';
import { ComingSoonPage } from '../pages/coming-soon/ComingSoonPage';
import { MyPage } from '../pages/my/MyPage';
import { NoticesPage } from '../pages/notices/NoticesPage';
import { ReservationDetailPage } from '../pages/reservations/ReservationDetailPage';
import { ReservationHistoryPage } from '../pages/reservations/ReservationHistoryPage';
import { ReservationHomePage } from '../pages/reservations/ReservationHomePage';
import { ReservationSuccessPage } from '../pages/reservations/ReservationSuccessPage';
import { ProtectedRoute, PublicOnlyRoute } from './routeGuards';

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route element={<PublicOnlyRoute />}>
          <Route path="landing" element={<LandingPage />} />
          <Route path="terms" element={<TermsPage />} />
        </Route>
        <Route path="sso_callback" element={<SsoCallbackPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<Navigate to="/reservations" replace />} />
          <Route path="notices" element={<NoticesPage />} />
          <Route path="reservations" element={<ReservationHomePage />} />
          <Route path="reservations/:roomId" element={<ReservationDetailPage />} />
          <Route path="reservations/success" element={<ReservationSuccessPage />} />
          <Route path="reservations/history" element={<ReservationHistoryPage />} />
          <Route path="my" element={<MyPage />} />
          <Route path="coming-soon" element={<ComingSoonPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/reservations" replace />} />
      </Route>
    </Routes>
  );
}
