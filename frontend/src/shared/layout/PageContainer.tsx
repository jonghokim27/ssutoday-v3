import { type ReactNode } from 'react';
import styles from './PageContainer.module.css';

type PageContainerProps = {
  children: ReactNode;
  title?: string;
  className?: string;
};

export function PageContainer({ children, title, className }: PageContainerProps) {
  return (
    <section className={[styles.container, className].filter(Boolean).join(' ')}>
      {title ? <h1 className={styles.title}>{title}</h1> : null}
      {children}
    </section>
  );
}
