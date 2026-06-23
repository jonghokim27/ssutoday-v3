import { type ReactNode } from 'react';
import styles from './Badge.module.css';

type BadgeTone = 'blue' | 'purple' | 'gray' | 'green' | 'orange' | 'red' | 'yellow';

type BadgeProps = {
  children: ReactNode;
  tone?: BadgeTone;
};

export function Badge({ children, tone = 'gray' }: BadgeProps) {
  return <span className={[styles.badge, styles[tone]].join(' ')}>{children}</span>;
}
