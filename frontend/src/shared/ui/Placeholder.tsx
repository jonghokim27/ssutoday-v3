import { Card } from './Card';
import styles from './Placeholder.module.css';

type PlaceholderProps = {
  description: string;
};

export function Placeholder({ description }: PlaceholderProps) {
  return (
    <Card>
      <p className={styles.text}>{description}</p>
    </Card>
  );
}
