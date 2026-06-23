import { createPortal } from 'react-dom';
import { Button } from './Button';
import { Icon, type IconName } from './Icon';
import styles from './ConfirmDialog.module.css';

type ConfirmDialogTone = 'primary' | 'danger';

type ConfirmDialogProps = {
  title: string;
  message?: string;
  details?: Array<{ label: string; value: string }>;
  confirmLabel: string;
  icon?: IconName;
  tone?: ConfirmDialogTone;
  onCancel: () => void;
  onConfirm: () => void;
};

export function ConfirmDialog({
  title,
  message,
  details,
  confirmLabel,
  icon = 'check',
  tone = 'primary',
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  const dialog = (
    <div className={styles.backdrop} onClick={onCancel}>
      <div className={styles.dialog} onClick={(event) => event.stopPropagation()}>
        <div className={[styles.icon, styles[tone]].join(' ')}>
          <Icon name={icon} />
        </div>
        <h2>{title}</h2>
        {details?.length ? (
          <dl className={styles.details}>
            {details.map((detail) => (
              <div key={detail.label}>
                <dt>{detail.label}</dt>
                <dd>{detail.value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {message ? <p>{message}</p> : null}
        <div className={styles.actions}>
          <Button onClick={onCancel} type="button" variant="secondary">취소</Button>
          <Button className={tone === 'danger' ? styles.dangerButton : ''} onClick={onConfirm} type="button">
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );

  return createPortal(dialog, document.body);
}
