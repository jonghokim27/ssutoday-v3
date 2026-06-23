import styles from './Toast.module.css';

type ToastProps = {
  message: string;
};

export function Toast({ message }: ToastProps) {
  if (!message) {
    return null;
  }

  return <div className={styles.toast}>{message}</div>;
}
