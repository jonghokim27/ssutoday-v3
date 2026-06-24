import { useCallback } from 'react';
import { useLocation } from 'react-router-dom';

export const SAFE_AREA_TOP_PARAMS = [
  'safeAreaTop',
  'safeareaTop',
  'safearea',
  'safeArea',
  'topInset',
  'insetTop',
  'statusBarHeight',
  'top',
];
export const MAX_SAFE_AREA_TOP = 120;

export function getSafeAreaTopExtra(search: string) {
  const parsedValue = getSafeAreaTopParamValue(search);

  if (parsedValue == null) {
    return 0;
  }

  return Math.min(Math.max(Math.round(parsedValue), 0), MAX_SAFE_AREA_TOP);
}

export function getSafeAreaTopParamValue(search: string) {
  const params = new URLSearchParams(search);
  const namedValue = SAFE_AREA_TOP_PARAMS.map((param) => params.get(param)).find((value) => value != null);
  return parseCssPixelNumber(namedValue);
}

export function withSafeAreaParams(to: string, currentSearch: string) {
  const currentParams = new URLSearchParams(currentSearch);
  const safeAreaParams = SAFE_AREA_TOP_PARAMS.flatMap((param) => {
    const value = currentParams.get(param);
    return value == null ? [] : [[param, value] as const];
  });

  if (safeAreaParams.length === 0) {
    return to;
  }

  const hashIndex = to.indexOf('#');
  const pathAndSearch = hashIndex === -1 ? to : to.slice(0, hashIndex);
  const hash = hashIndex === -1 ? '' : to.slice(hashIndex);
  const searchIndex = pathAndSearch.indexOf('?');
  const path = searchIndex === -1 ? pathAndSearch : pathAndSearch.slice(0, searchIndex);
  const targetSearch = searchIndex === -1 ? '' : pathAndSearch.slice(searchIndex + 1);
  const targetParams = new URLSearchParams(targetSearch);

  safeAreaParams.forEach(([param, value]) => {
    if (!targetParams.has(param)) {
      targetParams.set(param, value);
    }
  });

  const nextSearch = targetParams.toString();
  return `${path}${nextSearch ? `?${nextSearch}` : ''}${hash}`;
}

export function useSafeAreaPath() {
  const location = useLocation();
  return useCallback((to: string) => withSafeAreaParams(to, location.search), [location.search]);
}

function parseCssPixelNumber(value?: string | null) {
  if (value == null || value.trim() === '') {
    return null;
  }

  const parsedValue = Number.parseFloat(value);
  return Number.isFinite(parsedValue) ? parsedValue : null;
}
