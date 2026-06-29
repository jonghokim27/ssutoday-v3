const SSO_BASE_URL = 'https://smartid.ssu.ac.kr/Symtra_sso/smln.asp';

export function getSsoLoginUrl() {
  const callbackUrl = new URL('/sso_callback', window.location.origin).toString();
  const url = new URL(SSO_BASE_URL);
  url.searchParams.set('apiReturnUrl', callbackUrl);
  return url.toString();
}
