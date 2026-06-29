import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthSession } from '../../../app/authSessionContext';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { Toast } from '../../../shared/ui/Toast';
import { authRepository } from '../../auth/api/authRepository';
import { isNativeApp, nativeBridge, openLink, requireNativeApp, triggerHaptic } from '../../../shared/native/nativeBridge';
import { appStorage, type StoredProfile } from '../../../shared/storage/appStorage';
import { departmentCodeToName } from '../../../shared/utils/department';
import { appInfo, notificationRows, profile as fallbackProfile } from '../data/myPageData';
import { deviceRepository, type NotificationOptions } from '../api/deviceRepository';
import styles from './MyPageContent.module.css';

const SUPPORT_URL = 'https://open.kakao.com/o/sjCaLNTf';
const GITHUB_URL = 'https://github.com/jonghokim27/ssutoday';

export function MyPageContent() {
  const navigate = useNavigate();
  const safePath = useSafeAreaPath();
  const { setSession } = useAuthSession();
  const [devOpen, setDevOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [profile, setProfile] = useState<StoredProfile | null>(null);
  const [notificationEnabled, setNotificationEnabled] = useState(false);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [notifications, setNotifications] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(notificationRows.map((row) => [row.key, false])),
  );

  useEffect(() => {
    let mounted = true;

    async function load() {
      const storedProfile = await appStorage.getProfile();
      if (mounted) {
        setProfile(storedProfile);
      }

      if (!isNativeApp()) {
        if (mounted) {
          setNotificationEnabled(false);
          setNotifications(allNotificationRows(false));
          setLoadingOptions(false);
        }
        return;
      }

      const enabled = await appStorage.getItem('notificationEnabled');
      if (enabled === 'false') {
        if (mounted) {
          setNotificationEnabled(false);
          setNotifications(allNotificationRows(false));
          setLoadingOptions(false);
        }
        return;
      }

      if (mounted) {
        setNotificationEnabled(true);
      }
      const result = await deviceRepository.getOptions();
      if (mounted && result.ok) {
        setNotifications(optionsToRows(result.data));
      }
      if (mounted) {
        setLoadingOptions(false);
      }
    }

    void load();

    return () => {
      mounted = false;
    };
  }, []);

  async function toggle(key: string) {
    if (!requireNativeApp() || !notificationEnabled) {
      return;
    }

    const next = !notifications[key];
    setActionLoading(true);
    setNotifications((prev) => ({ ...prev, [key]: next }));
    const option = optionFromRowKey(key);
    const result = await deviceRepository.updateOption(option, next);
    if (!result.ok) {
      setActionLoading(false);
      flash(result.message);
      return;
    }

    if (option === 'notice') {
      const topicAction = next ? nativeBridge.subscribePushTopic.bind(nativeBridge) : nativeBridge.unsubscribePushTopic.bind(nativeBridge);
      await topicAction('all');
      if (profile?.major) {
        await topicAction(profile.major);
      }
    }
    setActionLoading(false);
  }

  async function toggleAll() {
    if (!requireNativeApp()) {
      return;
    }

    const next = !notificationEnabled;
    setActionLoading(true);
    setNotificationEnabled(next);
    setNotifications(allNotificationRows(next));
    await appStorage.setItem('notificationEnabled', next ? 'true' : 'false');
    if (next) {
      await deviceRepository.register();
    } else {
      await deviceRepository.unregister();
    }
    setActionLoading(false);
  }

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  async function logout() {
    setLogoutOpen(false);
    setActionLoading(true);
    if (isNativeApp()) {
      const enabled = await appStorage.getItem('notificationEnabled');
      if (enabled !== 'false') {
        await deviceRepository.unregister();
      }
      if (profile?.major) {
        await nativeBridge.unsubscribePushTopic(profile.major);
      }
      await nativeBridge.unsubscribePushTopic('all');
    }
    await authRepository.logout();
    setSession('anonymous');
    navigate(safePath('/landing'), { replace: true });
  }

  const displayProfile = {
    department: departmentCodeToName(profile?.major) || fallbackProfile.department,
    name: profile?.name ?? fallbackProfile.name,
    studentId: profile?.studentId ?? fallbackProfile.studentId,
  };

  return (
    <div className={styles.screen}>
      <BrandHeader sticky title="마이페이지" />
      <section className={styles.profile}>
        <div className={styles.avatar}><Icon name="user" width="44" height="44" /></div>
        <div>
          <Badge tone="purple">{displayProfile.department}</Badge>
          <h1>{displayProfile.name}<span>님</span></h1>
          <p>학번 <strong>{displayProfile.studentId}</strong></p>
        </div>
      </section>

      <section className={styles.card}>
        <div className={styles.cardTitle}>
          <strong>알림 설정</strong>
          <Switch checked={isNativeApp() && notificationEnabled} onClick={() => void toggleAll()} />
        </div>
        {loadingOptions ? <LoadingState compact label="알림 설정을 불러오는 중" /> : null}
        {!loadingOptions && isNativeApp() && notificationEnabled && notificationRows.map((row) => (
          <div className={styles.row} key={row.key}>
            <span>{row.label}</span>
            <Switch checked={notifications[row.key]} onClick={() => void toggle(row.key)} />
          </div>
        ))}
      </section>

      <section className={styles.menu}>
        <button onClick={() => { triggerHaptic('heavy'); void openLink(SUPPORT_URL); }} type="button">
          <span>지원</span>
          <Icon name="chevronRight" />
        </button>
        <button onClick={() => { triggerHaptic('heavy'); setDevOpen((value) => !value); }} type="button">
          <span>개발자 정보</span>
          <Icon className={devOpen ? styles.open : ''} name="chevronDown" />
        </button>
        {devOpen ? (
          <div className={styles.dev}>
            <p><span>개발/디자인</span><strong>제27대 컴퓨터학부 부학생회장 김종호</strong></p>
            <p><span>기여하기</span><strong onClick={() => void openLink(GITHUB_URL)}>{GITHUB_URL}</strong></p>
          </div>
        ) : null}
        <button className={styles.logout} onClick={() => { triggerHaptic('heavy'); setLogoutOpen(true); }} type="button">
          <span>로그아웃</span>
          <Icon name="logout" />
        </button>
      </section>

      <footer className={styles.footer}>
        <p>{appInfo.version}</p>
        <p>{appInfo.copyright}</p>
      </footer>
      {logoutOpen ? (
        <ConfirmDialog
          confirmLabel="로그아웃"
          icon="logout"
          message="로그아웃하면 다시 로그인해야 예약 내역을 확인할 수 있어요"
          onCancel={() => setLogoutOpen(false)}
          onConfirm={() => {
            void logout();
          }}
          title="로그아웃 할까요?"
          tone="danger"
        />
      ) : null}
      {actionLoading ? (
        <div className={styles.loadingOverlay}>
          <LoadingState compact label="요청을 처리하는 중" />
        </div>
      ) : null}
      <Toast message={toast} />
    </div>
  );
}

function optionsToRows(options: NotificationOptions) {
  return {
    notice: options.notice,
    reservation: options.reserve,
    lms: options.lms,
  };
}

function allNotificationRows(value: boolean) {
  return Object.fromEntries(notificationRows.map((row) => [row.key, value]));
}

function optionFromRowKey(key: string): keyof NotificationOptions {
  if (key === 'reservation') return 'reserve';
  if (key === 'lms') return 'lms';
  return 'notice';
}

type SwitchProps = {
  checked: boolean;
  disabled?: boolean;
  onClick: () => void;
};

function Switch({ checked, disabled = false, onClick }: SwitchProps) {
  return (
    <button className={[styles.switch, checked ? styles.switchOn : ''].join(' ')} disabled={disabled} onClick={() => { triggerHaptic('heavy'); onClick(); }} type="button">
      <span />
    </button>
  );
}
