import { useParams } from 'react-router-dom';
import { ReservationDetail } from '../../features/reservation/components/ReservationDetail';

export function ReservationDetailPage() {
  const { roomId } = useParams();

  return <ReservationDetail roomId={roomId} />;
}
