import styles from './LoadingState.module.css';

type LoadingStateProps = {
  label?: string;
  compact?: boolean;
  className?: string;
};

export function LoadingState({ label = '불러오는 중', compact = false, className }: LoadingStateProps) {
  return (
    <div className={[styles.root, compact ? styles.compact : '', className].filter(Boolean).join(' ')} role="status" aria-live="polite">
      <span className={styles.spinner} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
