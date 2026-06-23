import { useMemo, useState } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { Badge } from '../../../shared/ui/Badge';
import { Icon } from '../../../shared/ui/Icon';
import { Toast } from '../../../shared/ui/Toast';
import { notices, noticeSources, type Notice } from '../data/notices';
import styles from './NoticePageContent.module.css';

export function NoticePageContent() {
  const [query, setQuery] = useState('');
  const [selectedSource, setSelectedSource] = useState<string>('all');
  const [starredOnly, setStarredOnly] = useState(false);
  const [starred, setStarred] = useState<number[]>([1]);
  const [latest, setLatest] = useState(true);
  const [toast, setToast] = useState('');

  const feed = useMemo(() => {
    return notices
      .filter((notice) => selectedSource === 'all' || notice.source === selectedSource)
      .filter((notice) => !query || `${notice.title} ${notice.body}`.includes(query))
      .filter((notice) => !starredOnly || starred.includes(notice.id))
      .sort((a, b) => latest ? b.id - a.id : a.id - b.id);
  }, [latest, query, selectedSource, starred, starredOnly]);

  function toggleStar(id: number) {
    setStarred((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  }

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  return (
    <div className={styles.screen}>
      <div className={styles.header}>
        <BrandHeader
          action={
            <button className={styles.sortButton} onClick={() => setLatest((value) => !value)} type="button">
              <Icon className={latest ? '' : styles.sortReverse} name="arrowDown" />
              {latest ? '최신순' : '오래된순'}
            </button>
          }
          title="공지사항"
        />
        <label className={styles.search}>
          <Icon name="search" />
          <input onChange={(event) => setQuery(event.target.value)} placeholder="공지 검색" value={query} />
        </label>
        <div className={styles.filters}>
          <button className={starredOnly ? styles.starOn : styles.star} onClick={() => setStarredOnly((value) => !value)}>
            <Icon name="star" width="13" height="13" /> 별표 {starred.length}
          </button>
          <span className={styles.divider} />
          <button className={selectedSource === 'all' ? styles.filterOn : styles.filter} onClick={() => setSelectedSource('all')}>전체</button>
          {noticeSources.map((source) => (
            <button
              className={selectedSource === source ? styles.filterOn : styles.filter}
              key={source}
              onClick={() => setSelectedSource(source)}
            >
              {source}
            </button>
          ))}
        </div>
      </div>

      <section className={styles.feed}>
        {feed.map((notice) => (
          <NoticeItem
            isStarred={starred.includes(notice.id)}
            key={notice.id}
            notice={notice}
            onOpen={() => flash('공지 상세 화면은 준비 중이에요')}
            onToggleStar={() => toggleStar(notice.id)}
          />
        ))}
        {feed.length === 0 ? (
          <NoticeEmptyState
            message={starredOnly ? '별표한 공지가 없어요' : '조건에 맞는 공지가 없어요'}
            starredOnly={starredOnly}
            subMessage={starredOnly ? '공지의 별표를 눌러 모아볼 수 있어요' : '검색어와 필터를 다시 확인해 주세요'}
          />
        ) : null}
      </section>
      <Toast message={toast} />
    </div>
  );
}

type NoticeEmptyStateProps = {
  message: string;
  subMessage: string;
  starredOnly: boolean;
};

function NoticeEmptyState({ message, subMessage, starredOnly }: NoticeEmptyStateProps) {
  return (
    <div className={styles.empty}>
      <div className={styles.emptyIcon} aria-hidden="true">
        <Icon name={starredOnly ? 'star' : 'search'} />
      </div>
      <strong>{message}</strong>
      <p>{subMessage}</p>
    </div>
  );
}

type NoticeItemProps = {
  notice: Notice;
  isStarred: boolean;
  onOpen: () => void;
  onToggleStar: () => void;
};

function NoticeItem({ notice, isStarred, onOpen, onToggleStar }: NoticeItemProps) {
  return (
    <article className={styles.item} onClick={onOpen}>
      <button
        className={[styles.starButton, isStarred ? styles.starred : ''].join(' ')}
        onClick={(event) => {
          event.stopPropagation();
          onToggleStar();
        }}
        type="button"
      >
        <Icon name="star" />
      </button>
      <div className={styles.itemTop}>
        <Badge tone={toneByCategory(notice.category)}>{notice.category}</Badge>
        {notice.hot ? <Badge tone="red">HOT</Badge> : null}
        {notice.pinned ? <Badge tone="purple">고정</Badge> : null}
      </div>
      <h2>{notice.title}</h2>
      <p>{notice.body}</p>
      <footer>
        <span>{notice.date}</span>
        <strong>{notice.source}</strong>
      </footer>
    </article>
  );
}

function toneByCategory(category: string) {
  if (category === '장학') return 'purple';
  if (category === '공모전') return 'green';
  if (category === '행사') return 'red';
  return 'blue';
}
