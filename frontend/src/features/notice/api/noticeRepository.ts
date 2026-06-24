import { apiClient } from '../../../shared/api/apiClient';
import { type ApiResult } from '../../../shared/api/types';
import { type ArticleProvider } from '../../../shared/storage/appStorage';

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

export const noticeRepository: NoticeRepository = new ApiNoticeRepository();
