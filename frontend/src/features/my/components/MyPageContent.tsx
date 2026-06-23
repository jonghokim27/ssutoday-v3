import { useState } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { Toast } from '../../../shared/ui/Toast';
import { appInfo, notificationRows, profile } from '../data/myPageData';
import styles from './MyPageContent.module.css';

export function MyPageContent() {
  const [devOpen, setDevOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [notifications, setNotifications] = useState(() =>
    Object.fromEntries(notificationRows.map((row) => [row.key, row.enabled])),
  );
  const allOn = Object.values(notifications).every(Boolean);

  function toggle(key: string) {
    setNotifications((prev) => ({ ...prev, [key]: !prev[key] }));
  }

  function toggleAll() {
    const next = !allOn;
    setNotifications(Object.fromEntries(notificationRows.map((row) => [row.key, next])));
  }

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  return (
    <div className={styles.screen}>
      <BrandHeader title="마이페이지" />
      <section className={styles.profile}>
        <div className={styles.avatar}><Icon name="user" width="44" height="44" /></div>
        <div>
          <Badge tone="purple">{profile.department}</Badge>
          <h1>{profile.name}<span>님</span></h1>
          <p>학번 <strong>{profile.studentId}</strong></p>
        </div>
      </section>

      <section className={styles.card}>
        <div className={styles.cardTitle}>
          <strong>알림 설정</strong>
          <Switch checked={allOn} onClick={toggleAll} />
        </div>
        {notificationRows.map((row) => (
          <div className={styles.row} key={row.key}>
            <span>{row.label}</span>
            <Switch checked={notifications[row.key]} onClick={() => toggle(row.key)} />
          </div>
        ))}
      </section>

      <section className={styles.menu}>
        <button onClick={() => flash('지원 센터로 연결할게요')} type="button">
          <span>지원</span>
          <Icon name="chevronRight" />
        </button>
        <button onClick={() => setDevOpen((value) => !value)} type="button">
          <span>개발자 정보</span>
          <Icon className={devOpen ? styles.open : ''} name="chevronDown" />
        </button>
        {devOpen ? (
          <div className={styles.dev}>
            <p><span>개발</span><strong>SSUTODAY 팀</strong></p>
            <p><span>문의</span><strong>help@ssutoday.com</strong></p>
            <p><span>오픈소스 라이선스</span><strong>보기</strong></p>
          </div>
        ) : null}
        <button className={styles.logout} onClick={() => setLogoutOpen(true)} type="button">
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
          message="로그아웃하면 다시 로그인해야 예약 내역을 확인할 수 있어요."
          onCancel={() => setLogoutOpen(false)}
          onConfirm={() => {
            setLogoutOpen(false);
            flash('로그아웃되었습니다');
          }}
          title="로그아웃 할까요?"
          tone="danger"
        />
      ) : null}
      <Toast message={toast} />
    </div>
  );
}

type SwitchProps = {
  checked: boolean;
  onClick: () => void;
};

function Switch({ checked, onClick }: SwitchProps) {
  return (
    <button className={[styles.switch, checked ? styles.switchOn : ''].join(' ')} onClick={onClick} type="button">
      <span />
    </button>
  );
}
