import { useEffect, useRef } from 'react';
import { ActivityIndicator, Modal, StyleSheet, Text, View } from 'react-native';
import WebView, { type WebViewMessageEvent } from 'react-native-webview';

const TIMEOUT_MS = 28_000;

type Props = {
  siteKey: string;
  action: string;
  onToken: (token: string) => void;
  onError: () => void;
};

function buildHtml(siteKey: string, action: string): string {
  return `<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
  <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=__onLoad&render=explicit" defer></script>
  <style>
    html, body { margin: 0; padding: 0; background: transparent; display: flex; justify-content: center; align-items: center; min-height: 65px; }
  </style>
</head>
<body>
  <div id="w"></div>
  <script>
    window.__onLoad = function() {
      turnstile.render('#w', {
        sitekey: '${siteKey}',
        action: '${action}',
        appearance: 'always',
        callback: function(token) {
          window.ReactNativeWebView.postMessage(JSON.stringify({ ok: true, token: token }));
        },
        'error-callback': function() {
          window.ReactNativeWebView.postMessage(JSON.stringify({ ok: false }));
        },
        'expired-callback': function() {
          turnstile.reset();
        },
      });
    };
  </script>
</body>
</html>`;
}

export default function TurnstileModal({ siteKey, action, onToken, onError }: Props) {
  const doneRef = useRef(false);

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
          <ActivityIndicator size="small" color="#4F7CFF" />
          <Text style={styles.label}>보안 검사 중...</Text>
          <WebView
            style={styles.webview}
            source={{ html: buildHtml(siteKey, action), baseUrl: 'https://v3.ssu.today' }}
            onMessage={handleMessage}
            javaScriptEnabled
            domStorageEnabled
            originWhitelist={['*', 'about:*']}
            userAgent="Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
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
    backgroundColor: 'rgba(15, 18, 34, 0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
    gap: 12,
    width: 300,
  },
  label: {
    fontSize: 14,
    color: '#4f5566',
    fontWeight: '600',
  },
  webview: {
    width: 300,
    height: 65,
    backgroundColor: 'transparent',
  },
});
