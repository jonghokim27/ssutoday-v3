import { type ButtonHTMLAttributes, type ReactNode } from 'react';
import styles from './IconButton.module.css';

type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
};

export function IconButton({ children, className, ...props }: IconButtonProps) {
  return (
    <button className={[styles.button, className].filter(Boolean).join(' ')} {...props}>
      {children}
    </button>
  );
}
