import { apiClient } from '../../../shared/api/apiClient';
import { apiFailure, apiSuccess, type ApiResult } from '../../../shared/api/types';
import { type ArticleProvider } from '../../../shared/storage/appStorage';
import { notices } from '../data/notices';

export type ArticleListRequest = {
  page: number;
  orderBy: 'DESC' | 'ASC';
  search: string;
  provider: ArticleProvider[];
  starredOnly?: boolean;
};

export type ArticleSummary = {
  idx: number;
  title: string;
  content: string;
  createdAt: string;
  provider: string;
  starred: boolean;
};

export type ArticleListData = {
  articles: ArticleSummary[];
  totalPages: number;
};

export type ArticleDetail = {
  idx: number;
  title: string;
  content: string;
  url: string;
};

export type NoticeRepository = {
  list(request: ArticleListRequest): Promise<ApiResult<ArticleListData>>;
  get(idx: number): Promise<ApiResult<{ article: ArticleDetail }>>;
  star(idx: number): Promise<ApiResult<null>>;
  unstar(idx: number): Promise<ApiResult<null>>;
  starredCount(): Promise<ApiResult<{ count: number }>>;
};

export class ApiNoticeRepository implements NoticeRepository {
  async list(request: ArticleListRequest) {
    return apiClient.post<ArticleListRequest, ArticleListData>('article/list', request, { authenticated: true });
  }

  async get(idx: number) {
    return apiClient.post<{ idx: number }, { article: ArticleDetail }>('article/get', { idx }, { authenticated: true });
  }

  async star(idx: number) {
    return apiClient.post<{ idx: number }, null>('article/star', { idx }, { authenticated: true });
  }

  async unstar(idx: number) {
    return apiClient.post<{ idx: number }, null>('article/unstar', { idx }, { authenticated: true });
  }

  async starredCount() {
    return apiClient.post<Record<string, never>, { count: number }>('article/starred-count', {}, { authenticated: true });
  }
}

export class MockNoticeRepository implements NoticeRepository {
  async list(request: ArticleListRequest) {
    const filtered = notices
      .filter((notice) => !request.search || `${notice.title} ${notice.body}`.includes(request.search))
      .map((notice) => ({
        idx: notice.id,
        title: notice.title,
        content: notice.body,
        createdAt: notice.date,
        provider: notice.source,
        starred: false,
      }));

    return apiSuccess('SSU2060', { articles: filtered, totalPages: 1 });
  }

  async get(idx: number) {
    const notice = notices.find((item) => item.id === idx);
    if (!notice) {
      return apiFailure('SSU4080', '게시글이 없습니다.');
    }

    return apiSuccess('SSU2080', {
      article: {
        idx: notice.id,
        title: notice.title,
        content: notice.body,
        url: `https://ssu.today/articles/${notice.id}`,
      },
    });
  }

  async star(_idx: number) {
    return apiSuccess('SSU2000', null);
  }

  async unstar(_idx: number) {
    return apiSuccess('SSU2000', null);
  }

  async starredCount() {
    return apiSuccess('SSU2240', { count: 0 });
  }
}

export const noticeRepository: NoticeRepository = new ApiNoticeRepository();
