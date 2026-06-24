import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { BrandHeader } from '../../../shared/layout/BrandHeader';
import { Badge } from '../../../shared/ui/Badge';
import { Icon } from '../../../shared/ui/Icon';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { Toast } from '../../../shared/ui/Toast';
import { openLink } from '../../../shared/native/nativeBridge';
import { appStorage, defaultProviders, type ArticleProvider } from '../../../shared/storage/appStorage';
import { formatKoreanDateTime } from '../../../shared/utils/date';
import { departmentCodeToName } from '../../../shared/utils/department';
import { noticeRepository, type ArticleSummary } from '../api/noticeRepository';
import styles from './NoticePageContent.module.css';

const NOTICE_PAGE_SIZE = 20;

export function NoticePageContent() {
  const screenRef = useRef<HTMLDivElement>(null);
  const noticesRef = useRef<ArticleSummary[]>([]);
  const loadingMoreRef = useRef(false);
  const noticesLengthRef = useRef(0);
  const totalPagesRef = useRef(0);
  const hasMoreRef = useRef(true);
  const pageRef = useRef(0);
  const requestedPageRef = useRef<number | null>(null);
  const requestSeqRef = useRef(0);
  const [query, setQuery] = useState('');
  const [selectedProviders, setSelectedProviders] = useState<ArticleProvider[]>([]);
  const [providersReady, setProvidersReady] = useState(false);
  const [majorLabel, setMajorLabel] = useState('학부 공지');
  const [starredOnly, setStarredOnly] = useState(false);
  const [latest, setLatest] = useState(true);
  const [toast, setToast] = useState('');
  const [notices, setNotices] = useState<ArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [opening, setOpening] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [showTopButton, setShowTopButton] = useState(false);

  const starredCount = useMemo(() => notices.filter((notice) => notice.starred).length, [notices]);

  const loadNotices = useCallback(
    async (nextPage: number, append = false) => {
      if (!providersReady) {
        return;
      }

      if (append) {
        if (
          loadingMoreRef.current ||
          !hasMoreRef.current ||
          requestedPageRef.current === nextPage ||
          nextPage <= pageRef.current ||
          (totalPagesRef.current > 0 && nextPage >= totalPagesRef.current) ||
          noticesLengthRef.current < NOTICE_PAGE_SIZE
        ) {
          return;
        }

        loadingMoreRef.current = true;
        requestedPageRef.current = nextPage;
        setLoadingMore(true);
      } else {
        setLoading(true);
        setNotices([]);
        noticesRef.current = [];
        requestSeqRef.current += 1;
        pageRef.current = 0;
        requestedPageRef.current = 0;
        noticesLengthRef.current = 0;
        totalPagesRef.current = 0;
        hasMoreRef.current = true;
        setHasMore(true);
        setLoadingMore(false);
        loadingMoreRef.current = false;
        screenRef.current?.scrollTo({ top: 0, behavior: 'auto' });
      }

      const requestSeq = requestSeqRef.current;
      const provider = selectedProviders;

      if (provider.length === 0) {
        totalPagesRef.current = 0;
        hasMoreRef.current = false;
        setHasMore(false);
        setLoading(false);
        setLoadingMore(false);
        loadingMoreRef.current = false;
        requestedPageRef.current = null;
        return;
      }

      try {
        const result = await noticeRepository.list({
          page: nextPage,
          orderBy: latest ? 'DESC' : 'ASC',
          search: query,
          provider,
          starredOnly,
        });

        if (requestSeq !== requestSeqRef.current) {
          return;
        }

        if (result.ok) {
          const totalPages = result.data.totalPages;
          const { articles, added } = mergeArticles(noticesRef.current, result.data.articles, append);

          const hasNextPage = totalPages > 0 && nextPage + 1 < totalPages;
          const hasUsefulNextPage = append ? added > 0 : result.data.articles.length >= NOTICE_PAGE_SIZE;
          noticesRef.current = articles;
          setNotices(articles);
          totalPagesRef.current = totalPages;
          hasMoreRef.current = hasNextPage && hasUsefulNextPage;
          setHasMore(hasMoreRef.current);
          pageRef.current = nextPage;
          noticesLengthRef.current = articles.length;
        } else {
          flash(result.message);
        }
      } finally {
        if (requestSeq === requestSeqRef.current && (!append || requestedPageRef.current === nextPage)) {
          setLoading(false);
          setLoadingMore(false);
          loadingMoreRef.current = false;
          requestedPageRef.current = null;
        }
      }
    },
    [latest, providersReady, query, selectedProviders, starredOnly],
  );

  useEffect(() => {
    if (!providersReady) {
      return;
    }

    void loadNotices(0);
  }, [loadNotices, providersReady]);

  async function toggleStar(notice: ArticleSummary) {
    const result = notice.starred ? await noticeRepository.unstar(notice.idx) : await noticeRepository.star(notice.idx);
    if (!result.ok) {
      flash(result.message);
      return;
    }

    const nextNotices = noticesRef.current
      .map((item) => (item.idx === notice.idx ? { ...item, starred: !notice.starred } : item))
      .filter((item) => !starredOnly || item.starred);

    noticesRef.current = nextNotices;
    setNotices(nextNotices);
    noticesLengthRef.current = nextNotices.length;
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
      setProvidersReady(true);
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

  async function selectAllProviders() {
    const next = isAllProvidersSelected(selectedProviders) ? [] : defaultProviders;
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

    await openLink(decodeURIComponent(result.data.article.url));
    setOpening(false);
  }

  function handleScroll() {
    const screen = screenRef.current;
    if (!screen) {
      return;
    }

    setShowTopButton(screen.scrollTop >= 120);

    const distanceToBottom = screen.scrollHeight - screen.scrollTop - screen.clientHeight;
    if (distanceToBottom <= 50 && hasMoreRef.current && !loadingMoreRef.current) {
      const nextPage = pageRef.current + 1;
      if (totalPagesRef.current > 0 && nextPage >= totalPagesRef.current) {
        hasMoreRef.current = false;
        setHasMore(false);
        return;
      }

      void loadNotices(nextPage, true);
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
          <input onChange={(event) => setQuery(event.target.value)} placeholder="제목 및 내용 검색" value={query} />
          {query ? (
            <button
              aria-label="검색어 지우기"
              className={styles.clearSearchButton}
              onClick={(event) => {
                event.preventDefault();
                setQuery('');
              }}
              type="button"
            >
              <Icon name="x" />
            </button>
          ) : null}
        </label>
        <div className={styles.filters}>
          <button className={starredOnly ? styles.starOn : styles.star} onClick={() => setStarredOnly((value) => !value)}>
            <Icon name="star" width="13" height="13" /> 별표 {starredCount}
          </button>
          <span className={styles.divider} />
          <button className={isAllProvidersSelected(selectedProviders) ? styles.filterOn : styles.filter} onClick={() => void selectAllProviders()}>
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
        {notices.map((notice, index) => (
          <NoticeItem
            isStarred={notice.starred}
            isLast={!loadingMore && index === notices.length - 1}
            key={notice.idx}
            notice={notice}
            onOpen={() => void openNotice(notice.idx)}
            onToggleStar={() => void toggleStar(notice)}
          />
        ))}
        {!loading && notices.length === 0 ? (
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
  isLast: boolean;
  onOpen: () => void;
  onToggleStar: () => void;
};

function NoticeItem({ notice, isStarred, isLast, onOpen, onToggleStar }: NoticeItemProps) {
  const itemRef = useRef<HTMLButtonElement>(null);
  const dragRef = useRef({ active: false, startX: 0, startY: 0, dx: 0, moved: false, swiping: false });

  function handlePointerDown(event: PointerEvent<HTMLButtonElement>) {
    dragRef.current = { active: true, startX: event.clientX, startY: event.clientY, dx: 0, moved: false, swiping: false };
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

    const rawDx = event.clientX - drag.startX;
    const rawDy = event.clientY - drag.startY;
    const absDx = Math.abs(rawDx);
    const absDy = Math.abs(rawDy);

    if (absDy > 8 || absDx > 8) {
      drag.moved = true;
    }

    if (!drag.swiping && absDy > absDx) {
      return;
    }

    drag.swiping = rawDx < -6 && absDx > absDy;
    if (!drag.swiping) {
      return;
    }

    const dx = Math.max(-150, Math.min(0, rawDx));
    drag.dx = dx;

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
    <article className={[styles.itemWrap, isLast ? styles.lastItem : ''].join(' ')}>
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

function isAllProvidersSelected(providers: ArticleProvider[]) {
  return defaultProviders.every((provider) => providers.includes(provider));
}

function mergeArticles(current: ArticleSummary[], incoming: ArticleSummary[], append: boolean) {
  const base = append ? current : [];
  const ids = new Set(base.map((article) => article.idx));
  const articles = [...base];
  let added = 0;

  for (const article of incoming) {
    if (ids.has(article.idx)) {
      continue;
    }

    ids.add(article.idx);
    articles.push(article);
    added += 1;
  }

  return { articles, added };
}

function providerLabel(provider: string) {
  if (provider === 'ssucatch') return 'SSU:Catch';
  if (provider === 'ssuCatch') return 'SSU:Catch';
  if (provider === 'stu') return '총학생회';
  if (provider === 'major') return '사용자 학부 공지';
  return departmentCodeToName(provider) || provider;
}
