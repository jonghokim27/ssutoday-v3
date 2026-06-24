import { type ReactNode } from 'react';
import styles from './Badge.module.css';

type BadgeTone = 'blue' | 'purple' | 'gray' | 'green' | 'orange' | 'red' | 'yellow';

type BadgeProps = {
  children: ReactNode;
  tone?: BadgeTone;
  strong?: boolean;
};

export function Badge({ children, tone = 'gray', strong = false }: BadgeProps) {
  return <span className={[styles.badge, styles[tone], strong ? styles.strong : ''].join(' ')}>{children}</span>;
}
