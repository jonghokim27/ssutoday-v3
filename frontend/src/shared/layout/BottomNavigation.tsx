import { NavLink } from 'react-router-dom';
import { Icon, type IconName } from '../ui/Icon';
import styles from './BottomNavigation.module.css';

const navItems = [
  { to: '/notices', label: '공지', icon: 'home' },
  { to: '/reservations', label: '예약', icon: 'calendar', primary: true },
  { to: '/my', label: '마이', icon: 'user' },
] satisfies Array<{ to: string; label: string; icon: IconName; primary?: boolean }>;

export function BottomNavigation() {
  return (
    <nav className={styles.nav} aria-label="주요 메뉴">
      {navItems.map((item) => (
        <NavLink
          className={({ isActive }) =>
            [styles.item, item.primary ? styles.primary : '', isActive ? styles.active : '']
              .filter(Boolean)
              .join(' ')
          }
          key={item.to}
          to={item.to}
        >
          <Icon name={item.icon} />
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
