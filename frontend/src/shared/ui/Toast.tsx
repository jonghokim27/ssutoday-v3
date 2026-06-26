import styles from './Toast.module.css';

type ToastProps = {
  message: string;
  bottomOffset?: number;
};

export function Toast({ message, bottomOffset = 104 }: ToastProps) {
  if (!message) {
    return null;
  }

  return <div className={styles.toast} style={{ bottom: bottomOffset }}>{message}</div>;
}
