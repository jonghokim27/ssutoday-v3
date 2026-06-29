import { type ButtonHTMLAttributes, type ReactNode } from 'react';
import { triggerHaptic } from '../native/nativeBridge';
import styles from './IconButton.module.css';

type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
};

export function IconButton({ children, className, onClick, ...props }: IconButtonProps) {
  return (
    <button
      className={[styles.button, className].filter(Boolean).join(' ')}
      onClick={(e) => { triggerHaptic('heavy'); onClick?.(e); }}
      {...props}
    >
      {children}
    </button>
  );
}
