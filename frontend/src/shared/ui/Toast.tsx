import { useEffect, useState } from 'react';
import styles from './Toast.module.css';

type ToastProps = {
  message: string;
  bottomOffset?: number;
  onTap?: () => void;
};

export function Toast({ message, bottomOffset = 104, onTap }: ToastProps) {
  const [visible, setVisible] = useState(false);
  const [displayMessage, setDisplayMessage] = useState('');
  const [displayOnTap, setDisplayOnTap] = useState<(() => void) | undefined>(undefined);

  useEffect(() => {
    if (message) {
      setDisplayMessage(message);
      setDisplayOnTap(() => onTap);
      setVisible(true);
    } else {
      setVisible(false);
      const timer = setTimeout(() => {
        setDisplayMessage('');
        setDisplayOnTap(undefined);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [message, onTap]);

  if (!displayMessage) return null;
  return (
    <div
      className={`${styles.toast}${visible ? '' : ` ${styles.hiding}`}${displayOnTap ? ` ${styles.tappable}` : ''}`}
      style={{ bottom: bottomOffset }}
      onClick={displayOnTap}
      role={displayOnTap ? 'button' : undefined}
    >
      {displayMessage}
    </div>
  );
}
