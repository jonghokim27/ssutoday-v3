import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthSession } from '../../app/authSessionContext';
import { authRepository } from '../../features/auth/api/authRepository';
import { deviceRepository } from '../../features/my/api/deviceRepository';
import { nativeBridge } from '../../shared/native/nativeBridge';
import { useSafeAreaPath } from '../../shared/routing/safeAreaParams';
import { Button } from '../../shared/ui/Button';
import styles from './SsoCallbackPage.module.css';

export function SsoCallbackPage() {
  const navigate = useNavigate();
  const safePath = useSafeAreaPath();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const { setSession } = useAuthSession();

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
        setSession('authenticated');
        navigate(safePath('/notices'), { replace: true });
        return;
      }

      if (result.statusCode === "SSU4010") {
        setError('유세인트 인증에 실패했어요');
      } else if (result.statusCode === "SSU4011") {
        setError('지원하지 않는 학과(부)에요');
      } else if (result.statusCode === "SSU4012") {
        setError('학사과정 재학/휴학/졸업 상태가 아니에요');
      } else {
        setError(result.message);
      }
    });
  }, [navigate, params, safePath, setSession]);

  if (error) {
    return (
      <div className={styles.errorScreen}>
        <div className={styles.errorIcon} aria-hidden="true">!</div>
        <p className={styles.eyebrow}>FAILED</p>
        <h1>유세인트 인증에 실패했어요</h1>
        <p className={styles.message}>{error}</p>
        <div className={styles.actions}>
          <Button onClick={() => navigate(safePath('/terms'))} type="button">다시 시도</Button>
          <Button onClick={() => navigate(safePath('/landing'))} type="button" variant="secondary">처음으로</Button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.loadingScreen} role="status" aria-live="polite">
      <span className={styles.loader} aria-hidden="true" />
      <strong>로그인 정보 확인 중</strong>
      <p>로그인 정보를 확인하고 있어요.</p>
    </div>
  );
}
