import { useEffect, useState } from 'react';
import styles from './Toast.module.css';

type ToastProps = {
  message: string;
  bottomOffset?: number;
};

export function Toast({ message, bottomOffset = 104 }: ToastProps) {
  const [visible, setVisible] = useState(false);
  const [displayMessage, setDisplayMessage] = useState('');

  useEffect(() => {
    if (message) {
      setDisplayMessage(message);
      setVisible(true);
    } else {
      setVisible(false);
      const timer = setTimeout(() => setDisplayMessage(''), 300);
      return () => clearTimeout(timer);
    }
  }, [message]);

  if (!displayMessage) return null;
  return (
    <div
      className={`${styles.toast}${visible ? '' : ` ${styles.hiding}`}`}
      style={{ bottom: bottomOffset }}
    >
      {displayMessage}
    </div>
  );
}
