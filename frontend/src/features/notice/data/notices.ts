export type Notice = {
  id: number;
  source: 'SSU:Catch' | '총학생회' | '컴퓨터학부';
  category: string;
  title: string;
  body: string;
  date: string;
  pinned?: boolean;
  hot?: boolean;
};

export const notices: Notice[] = [
  {
    id: 1,
    source: '컴퓨터학부',
    category: '모집',
    title: '신입생을 위한 중앙도서관 오리엔테이션 안내',
    body: '2026-1학기 신입생 여러분을 환영합니다. 중앙도서관 오리엔테이션 일정과 신청 방법을 확인해 주세요.',
    date: '2026년 3월 16일 09:57',
    pinned: true,
  },
  {
    id: 2,
    source: '컴퓨터학부',
    category: '장학',
    title: '2026학년도 1학기 국가장학금 가구원 동의 안내',
    body: '국가장학금 신청과 관련하여 가구원 정보제공 동의 절차와 마감일을 안내합니다.',
    date: '2026년 3월 13일 14:11',
    hot: true,
  },
  {
    id: 3,
    source: 'SSU:Catch',
    category: '공모전',
    title: '[스파르탄SW교육원] 2026 AI·SW 중심대학 공모전 안내',
    body: 'SW 중심대학 참여 학생을 대상으로 프로젝트 공모전을 진행합니다. 접수 기간과 제출 서류를 확인해 주세요.',
    date: '2026년 3월 6일 09:17',
  },
  {
    id: 4,
    source: '총학생회',
    category: '행사',
    title: '2026 봄 축제 운영 및 안전 수칙 안내',
    body: '봄 축제 운영 일정과 학생 안전 수칙을 안내합니다. 모든 참여자는 사전 등록 후 입장 가능합니다.',
    date: '2026년 2월 28일 11:30',
  },
];

export const noticeSources = ['SSU:Catch', '총학생회', '컴퓨터학부'] as const;
