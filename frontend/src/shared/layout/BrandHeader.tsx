import { type ReactNode } from 'react';
import styles from './BrandHeader.module.css';

type BrandHeaderProps = {
  title: string;
  action?: ReactNode;
  sticky?: boolean;
};

export function BrandHeader({ title, action, sticky = false }: BrandHeaderProps) {
  return (
    <header className={[styles.header, sticky ? styles.sticky : ''].filter(Boolean).join(' ')}>
      <div className={styles.brand}>
        <div className={styles.logo}>SSU</div>
        <div className={styles.copy}>
          <span className={styles.eyebrow}>
            <span>SSU</span>
            <strong>TODAY</strong>
          </span>
          <span className={styles.title}>{title}</span>
        </div>
      </div>
      {action}
    </header>
  );
}
