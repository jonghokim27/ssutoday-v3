import { Link } from 'react-router-dom';
import { PageContainer } from '../../shared/layout/PageContainer';
import { Button } from '../../shared/ui/Button';

export function ComingSoonPage() {
  return (
    <PageContainer title="준비중">
      <p>곧 만나요. 지금은 예약 화면을 이용해 주세요.</p>
      <Link to="/reservations">
        <Button>예약하러 가기</Button>
      </Link>
    </PageContainer>
  );
}
