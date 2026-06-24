import { apiClient } from '../../../shared/api/apiClient';
import { apiFailure, apiSuccess, type ApiResult } from '../../../shared/api/types';
import { type ArticleProvider } from '../../../shared/storage/appStorage';
import { notices } from '../data/notices';

export type ArticleListRequest = {
  page: number;
  orderBy: 'DESC' | 'ASC';
  search: string;
  provider: ArticleProvider[];
};

export type ArticleSummary = {
  idx: number;
  title: string;
  content: string;
  createdAt: string;
  provider: string;
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
};

export class ApiNoticeRepository implements NoticeRepository {
  async list(request: ArticleListRequest) {
    return apiClient.post<ArticleListRequest, ArticleListData>('article/list', request, { authenticated: true });
  }

  async get(idx: number) {
    return apiClient.post<{ idx: number }, { article: ArticleDetail }>('article/get', { idx }, { authenticated: true });
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
}

export const noticeRepository: NoticeRepository = new ApiNoticeRepository();
