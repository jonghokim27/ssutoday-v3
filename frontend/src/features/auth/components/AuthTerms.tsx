import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../../shared/ui/Button';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { SSO_LOGIN_URL } from '../config/sso';
import styles from './AuthTerms.module.css';

const termsSections = [
  {
    title: '제1조 (목적)',
    body: '본 약관은 SSUTODAY가 제공하는 공지사항 확인 및 스터디룸 예약 서비스의 이용 조건과 절차를 안내합니다.',
  },
  {
    title: '개인정보 처리 안내',
    body: '서비스 제공을 위해 학교 통합 계정의 기본 정보와 예약 이용 내역을 처리할 수 있습니다.',
  },
  {
    title: '이용자 의무',
    body: '예약 정책과 서비스 이용 규칙을 준수해야 하며, 부정 이용 시 서비스 이용이 제한될 수 있습니다.',
  },
];

export function AuthTerms() {
  const navigate = useNavigate();
  const [redirecting, setRedirecting] = useState(false);

  function redirectToSso() {
    setRedirecting(true);
    window.setTimeout(() => {
      window.location.assign(SSO_LOGIN_URL);
    }, 120);
  }

  return (
    <div className={styles.screen}>
      <header className={styles.topbar}>
        <IconButton aria-label="뒤로가기" onClick={() => navigate('/landing')} type="button"><Icon name="arrowLeft" /></IconButton>
      </header>
      <section className={styles.headline}>
        <h1>
          SSUTODAY를 이용하려면<br />
          <span>이용약관 및 개인정보취급방침</span>에<br />
          동의해주세요
        </h1>
      </section>
      <section className={styles.webview}>
        <div className={styles.address}><Icon name="lock" width="13" height="13" /> ssu.ac.kr/terms</div>
        <div className={styles.termsBody}>
          <h2>이용약관 및 개인정보취급방침</h2>
          {termsSections.map((section) => (
            <article key={section.title}>
              <h3>{section.title}</h3>
              <p>{section.body}</p>
            </article>
          ))}
        </div>
      </section>
      <div className={styles.cta}>
        <Button disabled={redirecting} onClick={redirectToSso} type="button">
          {redirecting ? (
            <>
              <span className={styles.loader} aria-hidden="true" />
              유세인트로 이동 중
            </>
          ) : (
            <>
              동의하고 계속하기 <Icon name="arrowRight" />
            </>
          )}
        </Button>
      </div>
      {redirecting ? (
        <div className={styles.redirectOverlay} role="status" aria-live="polite">
          <span className={styles.loader} aria-hidden="true" />
          <strong>유세인트로 이동 중</strong>
        </div>
      ) : null}
    </div>
  );
}
