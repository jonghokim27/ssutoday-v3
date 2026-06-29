import { useEffect, useRef } from 'react';
import { Modal, StyleSheet, View } from 'react-native';
import WebView, { type WebViewMessageEvent } from 'react-native-webview';

const TURNSTILE_PAGE = 'https://v3.ssu.today/turnstile.html';
const TIMEOUT_MS = 28_000;

type Props = {
  siteKey: string;
  action: string;
  onToken: (token: string) => void;
  onError: () => void;
};

export default function TurnstileModal({ siteKey, action, onToken, onError }: Props) {
  const doneRef = useRef(false);

  const uri = `${TURNSTILE_PAGE}?siteKey=${encodeURIComponent(siteKey)}&action=${encodeURIComponent(action)}`;

  useEffect(() => {
    const timer = setTimeout(() => {
      if (!doneRef.current) {
        doneRef.current = true;
        onError();
      }
    }, TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [onError]);

  const handleMessage = (event: WebViewMessageEvent) => {
    if (doneRef.current) return;
    try {
      const data = JSON.parse(event.nativeEvent.data) as { ok: boolean; token?: string };
      if (data.ok && data.token) {
        doneRef.current = true;
        onToken(data.token);
      } else {
        doneRef.current = true;
        onError();
      }
    } catch {
      doneRef.current = true;
      onError();
    }
  };

  return (
    <Modal transparent animationType="none">
      <View style={styles.overlay}>
        <WebView
          style={styles.webview}
          source={{ uri }}
          onMessage={handleMessage}
          javaScriptEnabled
          domStorageEnabled
          originWhitelist={['https://*', 'about:*']}
          scrollEnabled={false}
        />
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.85)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  webview: {
    width: 300,
    height: 65,
    backgroundColor: 'transparent',
  },
});
