import { Link } from 'react-router-dom';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Badge } from '../../../shared/ui/Badge';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { Toast } from '../../../shared/ui/Toast';
import { HandledError, isNativeApp, openLink, requireNativeApp, triggerHaptic } from '../../../shared/native/nativeBridge';
import { reserveToHistoryView } from '../api/reservationMappers';
import { reservationRepository } from '../api/reservationRepository';
import styles from './ReservationHistory.module.css';

type HistoryViewItem = ReturnType<typeof reserveToHistoryView>;
type HistoryTab = 'active' | 'done';

const RESERVE_PAGE_SIZE = 10;

export function ReservationHistory() {
  const safePath = useSafeAreaPath();
  const screenRef = useRef<HTMLDivElement>(null);
  const itemsRef = useRef<HistoryViewItem[]>([]);
  const loadingMoreRef = useRef(false);
  const itemsLengthRef = useRef(0);
  const totalPagesRef = useRef(0);
  const hasMoreRef = useRef(true);
  const pageRef = useRef(0);
  const requestedPageRef = useRef<number | null>(null);
  const requestSeqRef = useRef(0);
  const tabRef = useRef<HistoryTab>('active');
  const [tab, setTab] = useState<HistoryTab>('active');
  const [items, setItems] = useState<HistoryViewItem[]>([]);
  const [cancelTarget, setCancelTarget] = useState<HistoryViewItem | null>(null);
  const [doneTarget, setDoneTarget] = useState<HistoryViewItem | null>(null);
  const [toast, setToast] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [showTopButton, setShowTopButton] = useState(false);

  const loadItems = useCallback(
    async (nextPage: number, append = false) => {
      if (append) {
        if (
          loadingMoreRef.current ||
          !hasMoreRef.current ||
          requestedPageRef.current === nextPage ||
          nextPage <= pageRef.current ||
          (totalPagesRef.current > 0 && nextPage >= totalPagesRef.current) ||
          itemsLengthRef.current < RESERVE_PAGE_SIZE
        ) {
          return;
        }

        loadingMoreRef.current = true;
        requestedPageRef.current = nextPage;
        setLoadingMore(true);
      } else {
        setLoading(true);
        setItems([]);
        itemsRef.current = [];
        requestSeqRef.current += 1;
        pageRef.current = 0;
        requestedPageRef.current = 0;
        itemsLengthRef.current = 0;
        totalPagesRef.current = 0;
        hasMoreRef.current = true;
        setHasMore(true);
        setLoadingMore(false);
        loadingMoreRef.current = false;
        screenRef.current?.scrollTo({ top: 0, behavior: 'auto' });
      }

      const requestSeq = requestSeqRef.current;
      const type = tabRef.current === 'active' ? 1 : 0;

      try {
        const result = await reservationRepository.listReserves(type, nextPage);

        if (requestSeq !== requestSeqRef.current) {
          return;
        }

        if (result.ok) {
          const totalPages = result.data.totalPages;
          const { reserves, added } = mergeReserves(itemsRef.current, result.data.reserves.map(reserveToHistoryView), append);

          const hasNextPage = totalPages > 0 && nextPage + 1 < totalPages;
          const hasUsefulNextPage = append ? added > 0 : result.data.reserves.length >= RESERVE_PAGE_SIZE;
          itemsRef.current = reserves;
          setItems(reserves);
          totalPagesRef.current = totalPages;
          hasMoreRef.current = hasNextPage && hasUsefulNextPage;
          setHasMore(hasMoreRef.current);
          pageRef.current = nextPage;
          itemsLengthRef.current = reserves.length;
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
    [],
  );

  const silentRefreshActive = useCallback(async () => {
    if (tabRef.current !== 'active') return;

    const currentPage = pageRef.current;
    const requestSeq = requestSeqRef.current;

    const results = await Promise.all(
      Array.from({ length: currentPage + 1 }, (_, p) =>
        reservationRepository.listReserves(1, p)
      )
    );

    if (requestSeq !== requestSeqRef.current) return;

    const allItems: HistoryViewItem[] = [];
    let latestTotalPages = totalPagesRef.current;

    for (const result of results) {
      if (!result.ok) return;
      latestTotalPages = result.data.totalPages;
      for (const r of result.data.reserves) {
        allItems.push(reserveToHistoryView(r));
      }
    }

    const seen = new Set<number>();
    const deduped = allItems.filter((item) => {
      if (seen.has(item.id)) return false;
      seen.add(item.id);
      return true;
    });

    itemsRef.current = deduped;
    setItems(deduped);
    totalPagesRef.current = latestTotalPages;
    itemsLengthRef.current = deduped.length;
    hasMoreRef.current = latestTotalPages > 0 && currentPage + 1 < latestTotalPages;
    setHasMore(hasMoreRef.current);
  }, []);

  useEffect(() => {
    tabRef.current = tab;
    void loadItems(0);
  }, [loadItems, tab]);

  useEffect(() => {
    if (tab !== 'active') return;
    const intervalId = window.setInterval(() => { void silentRefreshActive(); }, 1000);
    return () => window.clearInterval(intervalId);
  }, [tab, silentRefreshActive]);

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 1700);
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

      void loadItems(nextPage, true);
    }
  }

  async function cancelReserve(item: HistoryViewItem) {
    setCancelTarget(null);
    setActionLoading(true);
    const result = await reservationRepository.cancelReserve(item.id);
    if (!result.ok) {
      setActionLoading(false);
      flash(cancelFailureMessage(result.statusCode, result.message));
      return;
    }

    await loadItems(0);
    setActionLoading(false);
    flash('예약이 취소됐어요');
  }

  async function doneReserve(item: HistoryViewItem) {
    setDoneTarget(null);
    setActionLoading(true);
    const result = await reservationRepository.doneReserve(item.id);
    if (!result.ok) {
      setActionLoading(false);
      flash(doneFailureMessage(result.statusCode, result.message));
      return;
    }

    await loadItems(0);
    setActionLoading(false);
    flash('이용을 종료했어요');
  }

  async function viewPhoto(item: HistoryViewItem) {
    await openLink(item.verifyPhotoUrl ?? '', 'internal');
  }

  async function shootPhoto(item: HistoryViewItem) {
    if (!isNativeApp()) {
      requireNativeApp();
      return;
    }
    setActionLoading(true);
    try {
      const result = await reservationRepository.uploadVerifyPhoto(item.id);
      flash(result.ok ? '인증샷을 업로드했어요' : result.message);
      if (result.ok) await loadItems(0);
    } catch (error) {
      if (!(error instanceof HandledError)) {
        flash('오류가 발생했습니다. 다시 시도해주세요');
      }
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <div className={styles.screen} onScroll={handleScroll} ref={screenRef}>
      <header className={styles.header}>
        <Link to={safePath('/reservations')}><IconButton><Icon name="arrowLeft" /></IconButton></Link>
        <h1>예약 내역</h1>
      </header>
      <div className={styles.tabs}>
        <button className={tab === 'active' ? styles.active : ''} onClick={() => { triggerHaptic('medium'); setTab('active'); }} type="button">
          이용 중 · 대기
        </button>
        <button className={tab === 'done' ? styles.active : ''} onClick={() => { triggerHaptic('medium'); setTab('done'); }} type="button">
          이용 완료
        </button>
      </div>
      <section className={styles.list}>
        {loading ? <LoadingState label="예약 내역을 불러오는 중" /> : null}
        {items.map((item) => (
          <article className={styles.historyCard} key={item.id}>
            <div className={styles.cardBody}>
              <div className={styles.cardHead}>
                <span className={styles.calendar}><Icon name="calendar" width="16" height="16" /></span>
                <strong>{item.date}</strong>
              </div>
              <dl className={styles.details}>
                <div><dt>시설명</dt><dd>{item.room}</dd></div>
                <div><dt>이용 시간</dt><dd>{item.time} <span>({item.duration})</span></dd></div>
                {item.deletedReason ? <div><dt>취소 구분</dt><dd>{item.deletedReason.replace('(', '\n(')}</dd></div> : null}
              </dl>
            </div>
            <div className={styles.actions}>
              {item.canDone && (item.canShootPhoto || item.photoExempted || item.canViewPhoto) ? (
                <>
                  <div className={styles.leftDualCell}>
                    <div className={styles.statusCell}>
                      <Badge strong tone={statusTone(item.state)}>{item.status}</Badge>
                    </div>
                    <button className={styles.doneSubAction} onClick={() => { triggerHaptic('medium'); setDoneTarget(item); }} type="button">
                      이용을 종료하려면 선택
                    </button>
                  </div>
                  {item.canShootPhoto ? (
                    <button className={styles.shotButton} onClick={() => { triggerHaptic('medium'); void shootPhoto(item); }} type="button">
                      <span className={styles.blinkAction}>인증샷 촬영<i /></span>
                      <span>촬영 기한: {item.shotDeadline} 까지</span>
                    </button>
                  ) : item.canViewPhoto ? (
                    <button className={styles.shotButton} onClick={() => { triggerHaptic('medium'); void viewPhoto(item); }} type="button">
                      인증샷 보기<span>{item.verifyPhotoCreatedAt}</span>
                    </button>
                  ) : (
                    <div className={styles.actionInfo}>
                      인증샷 촬영<span>촬영이 면제됨</span>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div className={styles.statusCell}>
                    <Badge strong={item.state !== 'waiting'} tone={statusTone(item.state)}>{item.status}</Badge>
                    {item.deletedAtLabel ? <span>{item.deletedAtLabel}</span> : null}
                  </div>
                  {item.canDone ? (
                    <button className={styles.primaryAction} onClick={() => { triggerHaptic('medium'); setDoneTarget(item); }} type="button">
                      이용 종료<span>종료하려면 선택</span>
                    </button>
                  ) : null}
                  {item.canViewPhoto ? (
                    <button className={styles.shotButton} onClick={() => { triggerHaptic('medium'); void viewPhoto(item); }} type="button">
                      인증샷 보기<span>{item.verifyPhotoCreatedAt}</span>
                    </button>
                  ) : null}
                  {item.photoMissing ? (
                    <div className={styles.actionInfo}>
                      인증샷 보기<span>촬영되지 않음</span>
                    </div>
                  ) : null}
                </>
              )}
              {item.cancelable ? <button className={styles.danger} onClick={() => { triggerHaptic('medium'); setCancelTarget(item); }} type="button">예약 취소</button> : null}
              {item.canRebook ? <Link className={styles.rebook} to={safePath(`/reservations/${item.roomNo}?date=${item.rawDate}`)}>다시 예약하기</Link> : null}
            </div>
          </article>
        ))}
        {!loading && items.length === 0 ? (
          <ReservationEmptyState
            message={tab === 'active' ? '이용중이거나 대기 중인 예약이 없어요' : '이용 완료한 예약이 없어요'}
            subMessage={tab === 'active' ? '스터디룸을 예약하면 여기에 표시돼요' : '이용을 마친 예약 내역이 모이는 곳이에요'}
          />
        ) : null}
        {loadingMore ? <LoadingState compact label="예약 내역을 더 불러오는 중" /> : null}
      </section>
      {showTopButton ? (
        <button
          className={styles.topButton}
          onClick={() => { triggerHaptic('medium'); screenRef.current?.scrollTo({ top: 0, behavior: 'smooth' }); }}
          type="button"
          aria-label="맨 위로 이동"
        >
          <Icon className={styles.topIcon} name="arrowDown" />
        </button>
      ) : null}
      {actionLoading ? (
        <div className={styles.loadingOverlay}>
          <LoadingState compact label="요청을 처리하는 중" />
        </div>
      ) : null}
      {cancelTarget ? (
        <ConfirmDialog
          confirmLabel="예약 취소"
          details={[
            { label: '스터디룸', value: cancelTarget.room },
            { label: '날짜', value: cancelTarget.date },
            { label: '시간', value: cancelTarget.time },
          ]}
          icon="x"
          message="취소한 예약은 되돌릴 수 없어요"
          onCancel={() => setCancelTarget(null)}
          onConfirm={() => {
            void cancelReserve(cancelTarget);
          }}
          title="예약을 취소할까요?"
          tone="danger"
        />
      ) : null}
      {doneTarget ? (
        <ConfirmDialog
          confirmLabel="이용 종료"
          details={[
            { label: '스터디룸', value: doneTarget.room },
            { label: '날짜', value: doneTarget.date },
            { label: '시간', value: doneTarget.time },
          ]}
          icon="check"
          message={'종료 후에는 취소할 수 없어요.\n최소 30분 이상 이용한 경우에만 종료할 수 있어요.'}
          onCancel={() => setDoneTarget(null)}
          onConfirm={() => {
            void doneReserve(doneTarget);
          }}
          title="이용을 종료할까요?"
        />
      ) : null}
      <Toast message={toast} bottomOffset={40} />
    </div>
  );
}

type ReservationEmptyStateProps = {
  message: string;
  subMessage: string;
};

function ReservationEmptyState({ message, subMessage }: ReservationEmptyStateProps) {
  return (
    <div className={styles.empty}>
      <div className={styles.emptyIcon} aria-hidden="true">
        <Icon name="calendar" />
      </div>
      <strong>{message}</strong>
      <p>{subMessage}</p>
    </div>
  );
}

function mergeReserves(current: HistoryViewItem[], incoming: HistoryViewItem[], append: boolean) {
  const base = append ? current : [];
  const ids = new Set(base.map((item) => item.id));
  const reserves = [...base];
  let added = 0;

  for (const item of incoming) {
    if (ids.has(item.id)) {
      continue;
    }

    ids.add(item.id);
    reserves.push(item);
    added += 1;
  }

  return { reserves, added };
}

function statusTone(state: HistoryViewItem['state']) {
  if (state === 'cancelled') return 'red';
  if (state === 'waiting') return 'gray';
  if (state === 'using') return 'blue';
  return 'green';
}

function cancelFailureMessage(statusCode: string, fallback: string) {
  if (statusCode === 'SSU4141' || statusCode === 'SSU4142') return '이미 이용이 완료된 예약이에요';
  if (statusCode === 'SSU4143') return '현재 이용중인 예약이에요';
  return fallback;
}

function doneFailureMessage(statusCode: string, fallback: string) {
  if (statusCode === 'SSU4231' || statusCode === 'SSU4232') return '이미 이용이 완료된 예약이에요';
  if (statusCode === 'SSU4233') return '아직 이용을 시작하지 않은 예약이에요';
  if (statusCode === 'SSU4234') return '이용 종료는 최소 30분 이상 이용하신 경우에만 가능해요';
  if (statusCode === 'SSU4235') return '인증샷을 먼저 촬영해주세요';
  if (statusCode === 'SSU4236') return '이용 종료 시간에 근접하여 이용을 종료할 수 없어요';
  return fallback;
}
