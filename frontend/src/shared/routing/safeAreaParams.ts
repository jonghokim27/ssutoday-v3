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
export const SAFE_AREA_BOTTOM_PARAMS = ['safeAreaBottom', 'bottomInset', 'insetBottom'];
export const MAX_SAFE_AREA_TOP = 120;
export const MAX_SAFE_AREA_BOTTOM = 60;
const SAFE_AREA_TOP_SESSION_KEY = 'ssu_safe_area_top';
const SAFE_AREA_BOTTOM_SESSION_KEY = 'ssu_safe_area_bottom';

export function getSafeAreaTopParamValue(search: string) {
  const params = new URLSearchParams(search);
  const namedValue = SAFE_AREA_TOP_PARAMS.map((param) => params.get(param)).find((value) => value != null);
  return parseCssPixelNumber(namedValue);
}

export function getSafeAreaTopExtra() {
  const stored = sessionStorage.getItem(SAFE_AREA_TOP_SESSION_KEY);
  const value = parseCssPixelNumber(stored);
  if (value == null) return 0;
  return Math.min(Math.max(Math.round(value), 0), MAX_SAFE_AREA_TOP);
}

export function getSafeAreaBottomExtra() {
  const stored = sessionStorage.getItem(SAFE_AREA_BOTTOM_SESSION_KEY);
  const value = parseCssPixelNumber(stored);
  if (value == null) return 0;
  return Math.min(Math.max(Math.round(value), 0), MAX_SAFE_AREA_BOTTOM);
}

export function extractAndStoreSafeAreaTop(search: string): boolean {
  const value = getSafeAreaTopParamValue(search);
  if (value == null) return false;
  const clamped = Math.min(Math.max(Math.round(value), 0), MAX_SAFE_AREA_TOP);
  sessionStorage.setItem(SAFE_AREA_TOP_SESSION_KEY, String(clamped));
  return true;
}

export function extractAndStoreSafeAreaBottom(search: string): boolean {
  const params = new URLSearchParams(search);
  const namedValue = SAFE_AREA_BOTTOM_PARAMS.map((p) => params.get(p)).find((v) => v != null);
  const value = parseCssPixelNumber(namedValue);
  if (value == null) return false;
  const clamped = Math.min(Math.max(Math.round(value), 0), MAX_SAFE_AREA_BOTTOM);
  sessionStorage.setItem(SAFE_AREA_BOTTOM_SESSION_KEY, String(clamped));
  return true;
}

// No longer propagates params through URL — kept for call-site compatibility.
export function withSafeAreaParams(to: string, _currentSearch: string) {
  return to;
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
