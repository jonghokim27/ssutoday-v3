import { type ButtonHTMLAttributes } from 'react';
import { triggerHaptic } from '../native/nativeBridge';
import styles from './Button.module.css';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ className, variant = 'primary', onClick, ...props }: ButtonProps) {
  return (
    <button
      className={[styles.button, styles[variant], className].filter(Boolean).join(' ')}
      onClick={(e) => { triggerHaptic('medium'); onClick?.(e); }}
      {...props}
    />
  );
}
