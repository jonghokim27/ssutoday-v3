import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { Badge } from '../../../shared/ui/Badge';
import { Icon } from '../../../shared/ui/Icon';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { Toast } from '../../../shared/ui/Toast';
import { nativeBridge } from '../../../shared/native/nativeBridge';
import { appStorage, defaultProviders, type ArticleProvider } from '../../../shared/storage/appStorage';
import { formatKoreanDateTime } from '../../../shared/utils/date';
import { departmentCodeToName } from '../../../shared/utils/department';
import { noticeRepository, type ArticleSummary } from '../api/noticeRepository';
import styles from './NoticePageContent.module.css';

export function NoticePageContent() {
  const screenRef = useRef<HTMLDivElement>(null);
  const loadingMoreRef = useRef(false);
  const noticesLengthRef = useRef(0);
  const totalPagesRef = useRef(0);
  const [query, setQuery] = useState('');
  const [selectedProviders, setSelectedProviders] = useState<ArticleProvider[]>(defaultProviders);
  const [majorLabel, setMajorLabel] = useState('학부 공지');
  const [starredOnly, setStarredOnly] = useState(false);
  const [starred, setStarred] = useState<number[]>([1]);
  const [latest, setLatest] = useState(true);
  const [toast, setToast] = useState('');
  const [notices, setNotices] = useState<ArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [opening, setOpening] = useState(false);
  const [page, setPage] = useState(0);
  const [showTopButton, setShowTopButton] = useState(false);

  const feed = useMemo(() => {
    return notices
      .filter((notice) => !starredOnly || starred.includes(notice.idx))
      .sort((a, b) => (latest ? b.idx - a.idx : a.idx - b.idx));
  }, [latest, notices, starred, starredOnly]);

  const loadNotices = useCallback(
    async (nextPage: number, append = false) => {
      if (append) {
        if (loadingMoreRef.current || (totalPagesRef.current > 0 && nextPage >= totalPagesRef.current) || noticesLengthRef.current < 20) {
          return;
        }

        loadingMoreRef.current = true;
        setLoadingMore(true);
      } else {
        setLoading(true);
        setPage(0);
        totalPagesRef.current = 0;
      }

      const storedProviders = await appStorage.getProviders();
      const provider = selectedProviders.length > 0 ? selectedProviders : storedProviders ?? defaultProviders;
      await appStorage.setProviders(provider);
      const result = await noticeRepository.list({
        page: nextPage,
        orderBy: latest ? 'DESC' : 'ASC',
        search: query,
        provider,
      });

      if (result.ok) {
        setNotices((prev) => {
          const next = append ? [...prev, ...result.data.articles] : result.data.articles;
          noticesLengthRef.current = next.length;
          return next;
        });
        totalPagesRef.current = result.data.totalPages;
        setPage(nextPage);
      } else {
        flash(result.message);
      }

      setLoading(false);
      setLoadingMore(false);
      loadingMoreRef.current = false;
    },
    [latest, query, selectedProviders],
  );

  useEffect(() => {
    void loadNotices(0);
  }, [loadNotices]);

  function toggleStar(id: number) {
    setStarred((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  }

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
  }

  useEffect(() => {
    let mounted = true;
    void Promise.all([appStorage.getProviders(), appStorage.getProfile()]).then(([providers, profile]) => {
      if (!mounted) {
        return;
      }

      setSelectedProviders(providers);
      setMajorLabel(departmentCodeToName(profile?.major) || '학부 공지');
    });

    return () => {
      mounted = false;
    };
  }, []);

  async function toggleProvider(provider: ArticleProvider) {
    const next = selectedProviders.includes(provider)
      ? selectedProviders.filter((item) => item !== provider)
      : [...selectedProviders, provider];
    setSelectedProviders(next);
    await appStorage.setProviders(next);
  }

  async function openNotice(idx: number) {
    setOpening(true);
    const result = await noticeRepository.get(idx);
    if (!result.ok) {
      setOpening(false);
      flash(result.message);
      return;
    }

    await nativeBridge.openExternalUrl(decodeURIComponent(result.data.article.url));
    setOpening(false);
  }

  function handleScroll() {
    const screen = screenRef.current;
    if (!screen) {
      return;
    }

    setShowTopButton(screen.scrollTop >= 120);

    const distanceToBottom = screen.scrollHeight - screen.scrollTop - screen.clientHeight;
    if (distanceToBottom <= 50) {
      void loadNotices(page + 1, true);
    }
  }

  return (
    <div className={styles.screen} onScroll={handleScroll} ref={screenRef}>
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
          <button className={selectedProviders.length === defaultProviders.length ? styles.filterOn : styles.filter} onClick={() => setSelectedProviders(defaultProviders)}>
            전체
          </button>
          {defaultProviders.map((provider) => (
            <button className={selectedProviders.includes(provider) ? styles.filterOn : styles.filter} key={provider} onClick={() => void toggleProvider(provider)}>
              {provider === 'major' ? majorLabel : providerLabel(provider)}
            </button>
          ))}
        </div>
      </div>

      <section className={styles.feed}>
        {loading ? <LoadingState label="공지사항을 불러오는 중" /> : null}
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
        {loadingMore ? <LoadingState compact label="공지사항을 더 불러오는 중" /> : null}
      </section>
      {showTopButton ? (
        <button
          className={styles.topButton}
          onClick={() => screenRef.current?.scrollTo({ top: 0, behavior: 'smooth' })}
          type="button"
          aria-label="맨 위로 이동"
        >
          <Icon className={styles.topIcon} name="arrowDown" />
        </button>
      ) : null}
      {opening ? (
        <div className={styles.loadingOverlay}>
          <LoadingState compact label="공지사항을 여는 중" />
        </div>
      ) : null}
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
          <span>{formatKoreanDateTime(notice.createdAt)}</span>
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

function providerLabel(provider: string) {
  if (provider === 'ssucatch') return 'SSU:Catch';
  if (provider === 'ssuCatch') return 'SSU:Catch';
  if (provider === 'stu') return '총학생회';
  if (provider === 'major') return '사용자 학부 공지';
  return departmentCodeToName(provider) || provider;
}
