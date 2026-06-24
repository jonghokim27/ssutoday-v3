import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authRepository } from '../../features/auth/api/authRepository';
import { deviceRepository } from '../../features/my/api/deviceRepository';
import { nativeBridge } from '../../shared/native/nativeBridge';
import { Button } from '../../shared/ui/Button';
import styles from './SsoCallbackPage.module.css';

export function SsoCallbackPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const sToken = params.get('sToken');
    const sIdno = params.get('sIdno');
    if (!sToken || !sIdno) {
      setError('SSO 인증 정보가 없습니다.');
      return;
    }

    authRepository.login({ sToken, sIdno }).then(async (result) => {
      if (result.ok) {
        await deviceRepository.register();
        await nativeBridge.subscribePushTopic('all');
        if (result.data.major) {
          await nativeBridge.subscribePushTopic(result.data.major);
        }
        navigate('/notices', { replace: true });
        return;
      }

      setError(result.message);
    });
  }, [navigate, params]);

  if (error) {
    return (
      <div className={styles.errorScreen}>
        <div className={styles.errorIcon} aria-hidden="true">!</div>
        <p className={styles.eyebrow}>SIGN IN FAILED</p>
        <h1>유세인트 인증에 실패했어요</h1>
        <p className={styles.message}>{error}</p>
        <div className={styles.actions}>
          <Button onClick={() => navigate('/terms')} type="button">다시 시도</Button>
          <Button onClick={() => navigate('/landing')} type="button" variant="secondary">처음으로</Button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.loadingScreen} role="status" aria-live="polite">
      <span className={styles.loader} aria-hidden="true" />
      <strong>유세인트로 이동 중</strong>
      <p>로그인 정보를 확인하고 있어요.</p>
    </div>
  );
}
