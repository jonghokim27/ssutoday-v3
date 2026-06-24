import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { Badge } from '../../../shared/ui/Badge';
import { Icon } from '../../../shared/ui/Icon';
import { Toast } from '../../../shared/ui/Toast';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { appStorage, defaultProviders, type ArticleProvider } from '../../../shared/storage/appStorage';
import { noticeRepository, type ArticleSummary } from '../api/noticeRepository';
import { noticeSources } from '../data/notices';
import styles from './NoticePageContent.module.css';

export function NoticePageContent() {
  const [query, setQuery] = useState('');
  const [selectedSource, setSelectedSource] = useState<string>('all');
  const [starredOnly, setStarredOnly] = useState(false);
  const [starred, setStarred] = useState<number[]>([1]);
  const [latest, setLatest] = useState(true);
  const [toast, setToast] = useState('');
  const [notices, setNotices] = useState<ArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const feed = useMemo(() => {
    return notices
      .filter((notice) => !starredOnly || starred.includes(notice.idx))
      .sort((a, b) => (latest ? b.idx - a.idx : a.idx - b.idx));
  }, [latest, notices, starred, starredOnly]);

  useEffect(() => {
    let mounted = true;

    async function load() {
      setLoading(true);
      const storedProviders = await appStorage.getProviders();
      const provider =
        selectedSource === 'all'
          ? storedProviders ?? defaultProviders
          : [providerFromLabel(selectedSource)];
      await appStorage.setProviders(provider);
      const result = await noticeRepository.list({
        page: 1,
        orderBy: latest ? 'DESC' : 'ASC',
        search: query,
        provider,
      });
      if (!mounted) {
        return;
      }
      if (result.ok) {
        setNotices(result.data.articles);
      } else {
        flash(result.message);
      }
      setLoading(false);
    }

    void load();

    return () => {
      mounted = false;
    };
  }, [latest, query, selectedSource]);

  function toggleStar(id: number) {
    setStarred((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  }

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  async function openNotice(idx: number) {
    const result = await noticeRepository.get(idx);
    if (!result.ok) {
      flash(result.message);
      return;
    }

    await nativeBridge.openExternalUrl(decodeURIComponent(result.data.article.url));
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
            isStarred={starred.includes(notice.idx)}
            key={notice.idx}
            notice={notice}
            onOpen={() => void openNotice(notice.idx)}
            onToggleStar={() => toggleStar(notice.idx)}
          />
        ))}
        {!loading && feed.length === 0 ? (
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
  notice: ArticleSummary;
  isStarred: boolean;
  onOpen: () => void;
  onToggleStar: () => void;
};

function NoticeItem({ notice, isStarred, onOpen, onToggleStar }: NoticeItemProps) {
  const itemRef = useRef<HTMLButtonElement>(null);
  const dragRef = useRef({ active: false, startX: 0, dx: 0, moved: false });

  function handlePointerDown(event: PointerEvent<HTMLButtonElement>) {
    dragRef.current = { active: true, startX: event.clientX, dx: 0, moved: false };
    itemRef.current?.setPointerCapture(event.pointerId);
    if (itemRef.current) {
      itemRef.current.style.transition = 'none';
    }
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag.active) {
      return;
    }

    const dx = Math.max(-150, Math.min(0, event.clientX - drag.startX));
    drag.dx = dx;
    drag.moved = Math.abs(dx) > 5;

    if (itemRef.current) {
      itemRef.current.style.transform = `translateX(${dx}px)`;
    }
  }

  function handlePointerUp(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag.active) {
      return;
    }

    drag.active = false;
    itemRef.current?.releasePointerCapture(event.pointerId);

    if (itemRef.current) {
      itemRef.current.style.transition = 'transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1.4)';
      itemRef.current.style.transform = 'translateX(0)';
    }

    if (drag.dx <= -72) {
      onToggleStar();
      return;
    }

    if (!drag.moved) {
      onOpen();
    }
  }

  return (
    <article className={styles.itemWrap}>
      <div className={styles.swipeAction} aria-hidden="true">
        <Icon name="star" />
        <span>별표</span>
      </div>
      <button
        className={styles.item}
        onPointerCancel={handlePointerUp}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        ref={itemRef}
        type="button"
      >
        <span
          className={[styles.starButton, isStarred ? styles.starred : ''].join(' ')}
          onClick={(event) => {
            event.stopPropagation();
            onToggleStar();
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              event.stopPropagation();
              onToggleStar();
            }
          }}
          onPointerDown={(event) => event.stopPropagation()}
          onPointerUp={(event) => event.stopPropagation()}
          role="button"
          tabIndex={0}
        >
          <Icon name="star" />
        </span>
        <span className={styles.itemTop}>
          <Badge tone={toneByCategory(notice.provider)}>{providerLabel(notice.provider)}</Badge>
        </span>
        <span className={styles.title}>{notice.title}</span>
        <span className={styles.body}>{notice.content}</span>
        <span className={styles.footer}>
          <span>{notice.createdAt}</span>
          <strong>{providerLabel(notice.provider)}</strong>
        </span>
      </button>
    </article>
  );
}

function toneByCategory(category: string) {
  if (category === 'stu' || category === '총학생회') return 'purple';
  if (category === 'major' || category === '사용자 학부 공지') return 'green';
  return 'blue';
}

function providerFromLabel(label: string): ArticleProvider {
  if (label === 'SSU:Catch') return 'ssuCatch';
  if (label === '총학생회') return 'stu';
  if (label === '사용자 학부 공지') return 'major';
  return label;
}

function providerLabel(provider: string) {
  if (provider === 'ssuCatch') return 'SSU:Catch';
  if (provider === 'stu') return '총학생회';
  if (provider === 'major') return '사용자 학부 공지';
  return provider;
}
