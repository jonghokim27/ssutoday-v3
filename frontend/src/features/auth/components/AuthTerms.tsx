import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../../shared/ui/Button';
import { useSafeAreaPath } from '../../../shared/routing/safeAreaParams';
import { Icon } from '../../../shared/ui/Icon';
import { IconButton } from '../../../shared/ui/IconButton';
import { isNativeApp } from '../../../shared/native/nativeBridge';
import { getSsoLoginUrl } from '../config/sso';
import styles from './AuthTerms.module.css';

const termsGroups = [
  {
    title: '이용약관',
    meta: ['공개일: 2023년 08월 28일', '수정일: 2023년 08월 28일'],
    sections: [
      {
        title: '제1조 (목적)',
        body: [
          '본 약관은 “슈투데이”(이하 “회사”)가 제공하는 “공지사항 확인 및 스터디룸 예약 서비스”(이하 “서비스”)의 이용 조건 및 절차에 관한 기본적인 사항을 규정합니다.',
        ],
      },
      {
        title: '제2조 (이용자의 의무)',
        body: [
          '이용자는 본 약관에 따라 회사의 서비스를 이용함에 있어서 관련 법령 및 약관을 준수해야 합니다.',
          '이용자는 본 서비스를 통해 게시되는 공지사항 및 안내사항을 주기적으로 확인하여야 합니다.',
        ],
      },
      {
        title: '제3조 (서비스의 제공 및 변경)',
        body: [
          '회사는 이용자에게 공지사항 확인 및 스터디룸 예약 서비스를 제공합니다.',
          '회사는 필요한 경우 사전 공지 후 서비스의 일부 혹은 전체 내용을 변경할 수 있습니다.',
        ],
      },
      {
        title: '제4조 (이용계약의 성립)',
        body: [
          '서비스 이용을 희망하는 자는 회사의 신청서 양식에 따라 정보를 제공하여야 합니다.',
          '회사는 이용 신청 정보의 정확성 및 확인을 위해 필요한 조치를 취할 수 있습니다.',
        ],
      },
      {
        title: '제5조 (서비스의 제한 및 중단)',
        body: [
          '이용자가 약관 또는 관련 법령을 위반하거나, 서비스 이용에 지장을 초래하는 경우, 회사는 해당 이용자의 서비스 이용을 제한하거나 중단할 수 있습니다.',
          '회사는 서비스 제공 중단 및 중단 예정에 대한 사항을 공지사항을 통해 사전에 알립니다.',
        ],
      },
      {
        title: '제6조 (면책 조항)',
        body: [
          '회사는 천재지변, 정전, 시스템 오류 등 부득이한 사유로 인한 서비스 중단에 대해서는 책임을 지지 않습니다.',
          '이용자의 귀책사유로 인한 서비스 이용의 어려움이나 손해에 대해서도 회사는 책임을 지지 않습니다.',
        ],
      },
      {
        title: '제7조 (분쟁해결)',
        body: ['본 약관에서 정하지 않은 사항 및 본 약관의 해석에 관하여는 대한민국의 관련 법령 및 상관례에 따릅니다.'],
      },
      {
        title: '제8조 (약관의 변경)',
        body: ['회사는 필요한 경우 약관을 변경할 수 있으며, 변경된 약관은 공지사항을 통해 이용자에게 공지됩니다.'],
      },
      {
        title: '제 9조 (기타)',
        body: [
          '본 약관에 명시되지 않은 사항은 관련 법령 및 회사의 정책에 따릅니다.',
          '본 약관은 2023년 08월 28일부터 적용됩니다.',
        ],
      },
    ],
  },
  {
    title: '개인정보취급방침',
    meta: ['공개일: 2022년 08월 23일', '수정일: 2023년 08월 28일'],
    sections: [
      {
        title: '제1조 (개인정보의 처리 목적)',
        body: [
          '“슈투데이”(이하 “회사")는 다음의 목적을 위하여 개인정보를 처리하고 있으며, 다음의 목적 이외의 용도로는 이용하지 않습니다.',
          '이용자 가입의사 확인, 이용자 식별',
          '푸시 알림 발송',
        ],
      },
      {
        title: '제2조 (개인정보의 처리 및 보유 기간)',
        body: [
          '회사는 정보주체로부터 개인정보를 수집할 때 수집을 동의받은 개인정보만 보유합니다.',
          '수집된 개인정보는 최대 5년간 보관될 수 있습니다.',
        ],
      },
      {
        title: '제3조 (정보주체의 권리 및 행사 방법)',
        body: [
          '이용자는 개인정보주체로서 다음과 같은 권리를 행사할 수 있습니다.',
          '개인정보 열람 요구',
          '오류 등이 있을 경우 정정 요구',
          '삭제 요구',
          '처리정지 요구',
        ],
      },
      {
        title: '제4조 (처리하는 개인정보의 항목 작성)',
        body: [
          '회사는 다음의 개인정보 항목을 처리하고 있습니다.',
          '필수 항목 : 학번, 성명, 소속',
          '선택 항목 : 푸시 토큰',
        ],
      },
      {
        title: '제5조 (개인정보의 제공 및 위탁)',
        body: ['회사는 이용자의 사전 동의 없이 개인정보를 제3자에게 제공하지 않습니다.'],
      },
      {
        title: '제6조 (개인정보의 파기)',
        body: [
          '회사는 원칙적으로 개인정보 처리목적이 달성된 경우에는 지체없이 개인정보를 파기합니다. 파기의 절차 및 방법은 다음과 같습니다.',
          '파기 절차 : 데이터베이스에 수집된 개인정보 삭제',
          '파기 기한 : 즉시',
        ],
      },
      {
        title: '제7조 (개인정보 보호 책임자 작성)',
        body: [
          '회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한 정보주체의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호 책임자를 지정하고 있습니다.',
          '개인정보 보호 책임자',
          '성명: 김종호',
          '직책: 제27대 숭실대학교 컴퓨터학부 부학생회장',
          '연락처: joey0307@naver.com',
          '슈투데이를 이용하시면서 발생한 모든 개인정보 보호 관련 문의, 불만 처리, 피해구제 등에 관한 사항을 개인정보 보호 책임자에게 문의하실 수 있습니다.',
          '또한, 정보주체의 문의에 대해 지체 없이 답변 및 처리해드리겠습니다.',
        ],
      },
    ],
  },
];

export function AuthTerms() {
  const navigate = useNavigate();
  const safePath = useSafeAreaPath();
  const [redirecting, setRedirecting] = useState(false);
  const [showPersistDialog, setShowPersistDialog] = useState(false);

  function redirectToSso() {
    setRedirecting(true);
    window.setTimeout(() => {
      window.location.assign(getSsoLoginUrl());
    }, 120);
  }

  function handleContinueClick() {
    if (isNativeApp()) {
      redirectToSso();
    } else {
      setShowPersistDialog(true);
    }
  }

  function handlePersistChoice(persist: boolean) {
    if (persist) {
      sessionStorage.setItem('ssu_persist_login', '1');
    } else {
      sessionStorage.removeItem('ssu_persist_login');
    }
    setShowPersistDialog(false);
    redirectToSso();
  }

  return (
    <div className={styles.screen}>
      <header className={styles.topbar}>
        <IconButton aria-label="뒤로가기" onClick={() => navigate(safePath('/landing'))} type="button"><Icon name="arrowLeft" /></IconButton>
      </header>
      <section className={styles.headline}>
        <h1>
          슈투데이를 이용하려면<br />
          <span>이용약관 및 개인정보취급방침</span>에<br />
          동의해주세요
        </h1>
      </section>
      <section className={styles.webview}>
        <div className={styles.address}><Icon name="lock" width="13" height="13" />이용약관 및 개인정보취급방침 전문</div>
        <div className={styles.termsBody}>
          {termsGroups.map((group) => (
            <article className={styles.termsGroup} key={group.title}>
              <h3>{group.title}</h3>
              <div className={styles.meta}>
                {group.meta.map((item) => (
                  <p key={item}>{item}</p>
                ))}
              </div>
              {group.sections.map((section) => (
                <section key={section.title}>
                  <h4>{section.title}</h4>
                  {section.body.map((paragraph) => (
                    <p key={paragraph}>{paragraph}</p>
                  ))}
                </section>
              ))}
            </article>
          ))}
        </div>
      </section>
      <div className={styles.cta}>
        <Button disabled={redirecting} onClick={handleContinueClick} type="button">
          {redirecting ? (
            <>
              <span className={styles.loader} aria-hidden="true" />
              유세인트로 이동 중
            </>
          ) : (
            <>
              동의하고 계속하기 <Icon name="arrowRight" />
            </>
          )}
        </Button>
      </div>
      {showPersistDialog ? (
        <div className={styles.persistOverlay} onClick={(e) => { if (e.target === e.currentTarget) { handlePersistChoice(false); } }}>
          <div className={styles.persistDialog}>
            <h3>이 기기에서 로그인을 유지할까요?</h3>
            <p>유지하면 브라우저를 닫아도 로그인이 유지돼요</p>
            <div className={styles.persistActions}>
              <Button onClick={() => handlePersistChoice(true)} type="button">유지하기</Button>
              <Button onClick={() => handlePersistChoice(false)} type="button" variant="secondary">유지 안 함</Button>
            </div>
          </div>
        </div>
      ) : null}
      {redirecting ? (
        <div className={styles.redirectOverlay} role="status" aria-live="polite">
          <span className={styles.loader} aria-hidden="true" />
          <strong>유세인트로 이동 중</strong>
        </div>
      ) : null}
    </div>
  );
}
