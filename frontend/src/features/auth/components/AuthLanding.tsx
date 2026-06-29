import { useNavigate } from 'react-router-dom';
import ssutodayIcon from '../../../shared/assets/ssutoday-icon.png';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import styles from './AuthLanding.module.css';

export function AuthLanding() {
  const navigate = useNavigate();
  const safePath = useSafeAreaPath();

  return (
    <div className={styles.screen}>
      <section className={styles.brand}>
        {/* <img alt="SSUTODAY" src={ssutodayIcon} /> */}
        <h1><span>SSU</span>TODAY</h1>
        <p>스터디룸 예약과 공지사항 확인을 한번에</p>
      </section>
      <section className={styles.cta}>
        <Button onClick={() => navigate(safePath('/terms'), { replace: true })} type="button">
          <Icon name="lock" /> 유세인트로 로그인
        </Button>
        <p>숭실대학교 통합 계정(SSO)으로 로그인해요</p>
      </section>
    </div>
  );
}
