import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';
import { Icon, type IconName } from './Icon';
import styles from './PromptDialog.module.css';

type PromptDialogTone = 'primary' | 'danger';

type PromptDialogProps = {
  title: string;
  message?: string;
  placeholder?: string;
  defaultValue?: string;
  confirmLabel: string;
  icon?: IconName;
  tone?: PromptDialogTone;
  onCancel: () => void;
  onConfirm: (value: string) => void;
};

export function PromptDialog({
  title,
  message,
  placeholder,
  defaultValue = '',
  confirmLabel,
  icon = 'check',
  tone = 'primary',
  onCancel,
  onConfirm,
}: PromptDialogProps) {
  const [value, setValue] = useState(defaultValue);
  const trimmed = value.trim();

  const dialog = (
    <div className={styles.backdrop} onClick={onCancel}>
      <div className={styles.dialog} onClick={(event) => event.stopPropagation()}>
        <div className={[styles.icon, styles[tone]].join(' ')}>
          <Icon name={icon} />
        </div>
        <h2>{title}</h2>
        {message ? <p>{message}</p> : null}
        <textarea
          autoFocus
          className={styles.textarea}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          rows={3}
          value={value}
        />
        <div className={styles.actions}>
          <Button onClick={onCancel} type="button" variant="secondary">취소</Button>
          <Button
            className={tone === 'danger' ? styles.dangerButton : ''}
            disabled={!trimmed}
            onClick={() => onConfirm(trimmed)}
            type="button"
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );

  return createPortal(dialog, document.body);
}
