import { useEffect, useRef } from 'react';
import { Modal, StyleSheet, Text, View } from 'react-native';
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
        <View style={styles.card}>
          <View style={styles.header}>
            <View style={styles.iconBadge}>
              <Text style={styles.iconText}>🔒</Text>
            </View>
            <Text style={styles.title}>보안 검사</Text>
            <Text style={styles.subtitle}>안전한 예약을 위해 잠깐 확인할게요</Text>
          </View>
          <View style={styles.divider} />
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
    backgroundColor: 'rgba(15, 18, 34, 0.45)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    width: 300,
    backgroundColor: '#fff',
    borderRadius: 24,
    overflow: 'hidden',
  },
  header: {
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 24,
    paddingTop: 28,
    paddingBottom: 20,
  },
  iconBadge: {
    width: 52,
    height: 52,
    borderRadius: 16,
    backgroundColor: 'rgba(79, 124, 255, 0.1)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  iconText: {
    fontSize: 24,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0f1222',
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 13,
    color: '#8a8f9c',
    textAlign: 'center',
    lineHeight: 18,
  },
  divider: {
    height: 1,
    backgroundColor: '#f0f1f5',
  },
  webview: {
    width: 300,
    height: 65,
    backgroundColor: '#fff',
  },
});
