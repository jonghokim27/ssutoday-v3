import { useEffect, useRef } from 'react';
import { ActivityIndicator, Modal, StyleSheet, Text, View } from 'react-native';
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
    <Modal transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.content}>
          <View style={styles.loader}>
            <ActivityIndicator size={18} color="#6a4cff" />
            <Text style={styles.label}>보안 검사 중</Text>
          </View>
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
  content: {
    alignItems: 'center',
    gap: 16,
  },
  loader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  label: {
    fontSize: 13,
    fontWeight: '700',
    color: '#8a8f9c',
  },
  webview: {
    width: 300,
    height: 65,
    backgroundColor: 'transparent',
  },
});
