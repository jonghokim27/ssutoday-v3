import { withSafeAreaParams } from '../../../shared/routing/safeAreaParams';

const SSO_BASE_URL = 'https://smartid.ssu.ac.kr/Symtra_sso/smln.asp';

export function getSsoLoginUrl(currentSearch?: string) {
  const callbackPath = currentSearch
    ? withSafeAreaParams('/sso_callback', currentSearch)
    : '/sso_callback';
  const callbackUrl = new URL(callbackPath, window.location.origin).toString();
  const url = new URL(SSO_BASE_URL);
  url.searchParams.set('apiReturnUrl', callbackUrl);
  return url.toString();
}
