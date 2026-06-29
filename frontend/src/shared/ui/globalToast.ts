export type GlobalToastDetail = {
  message: string;
  onTap?: () => void;
};

export function showGlobalToast(message: string, onTap?: () => void): void {
  document.dispatchEvent(
    new CustomEvent<GlobalToastDetail>('global-toast', { detail: { message, onTap } }),
  );
}
