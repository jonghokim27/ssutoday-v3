import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import WebView, { type WebViewNavigation } from 'react-native-webview';

export default function InAppBrowserScreen() {
  const { url } = useLocalSearchParams<{ url: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [title, setTitle] = useState('');

  const handleBack = useCallback(() => {
    router.back();
  }, [router]);

  const handleNavigationStateChange = useCallback((state: WebViewNavigation) => {
    if (state.title) setTitle(state.title);
  }, []);

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Pressable
          style={({ pressed }) => [styles.backButton, pressed && styles.backButtonPressed]}
          onPress={handleBack}
          hitSlop={8}
        >
          <View style={styles.chevron} />
        </Pressable>
        {title ? (
          <Text style={styles.title} numberOfLines={1}>{title}</Text>
        ) : null}
      </View>
      <WebView
        source={{ uri: url ?? '' }}
        style={styles.webview}
        onNavigationStateChange={handleNavigationStateChange}
      />
    </View>
  );
}

const COLORS = {
  background: '#ffffff',
  surface: '#f2f3f8',
  textPrimary: '#0f1222',
  textSecondary: '#4f5566',
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 18,
    paddingTop: 8,
    paddingBottom: 8,
  },
  backButton: {
    width: 42,
    height: 42,
    borderRadius: 14,
    backgroundColor: COLORS.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backButtonPressed: {
    transform: [{ scale: 0.94 }],
    backgroundColor: '#e9ebf3',
  },
  chevron: {
    width: 9,
    height: 9,
    borderLeftWidth: 2,
    borderBottomWidth: 2,
    borderColor: COLORS.textSecondary,
    transform: [{ rotate: '45deg' }],
    marginLeft: 3,
  },
  title: {
    flex: 1,
    fontSize: 24,
    fontWeight: '800',
    color: COLORS.textPrimary,
    letterSpacing: -0.6,
  },
  webview: {
    flex: 1,
  },
});
