import { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, BackHandler, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';
import WebView, { type WebViewNavigation } from 'react-native-webview';

export default function InAppBrowserScreen() {
  const { url } = useLocalSearchParams<{ url: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(true);

  const progress = useRef(new Animated.Value(0)).current;
  const opacity = useRef(new Animated.Value(1)).current;

  const animateProgress = useCallback((toValue: number) => {
    Animated.timing(progress, {
      toValue,
      duration: 200,
      useNativeDriver: false,
    }).start();
  }, [progress]);

  const handleLoadStart = useCallback(() => {
    opacity.setValue(1);
    progress.setValue(0);
    setLoading(true);
    animateProgress(0.2);
  }, [opacity, progress, animateProgress]);

  const handleLoadProgress = useCallback(({ nativeEvent }: { nativeEvent: { progress: number } }) => {
    animateProgress(nativeEvent.progress);
  }, [animateProgress]);

  const handleLoadEnd = useCallback(() => {
    animateProgress(1);
    setLoading(false);
    Animated.sequence([
      Animated.delay(200),
      Animated.timing(opacity, { toValue: 0, duration: 250, useNativeDriver: false }),
    ]).start();
  }, [animateProgress, opacity]);

  const handleNavigationStateChange = useCallback((state: WebViewNavigation) => {
    if (state.title) setTitle(state.title);
  }, []);

  useEffect(() => {
    if (Platform.OS !== 'android') return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      router.back();
      return true;
    });
    return () => subscription.remove();
  }, [router]);

  const handleBack = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    router.back();
  }, [router]);

  const barWidth = progress.interpolate({
    inputRange: [0, 1],
    outputRange: ['0%', '100%'],
  });

  const headerPaddingTop = insets.top > 0 ? insets.top : 18;

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: headerPaddingTop }]}>
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
      <View style={styles.progressTrack}>
        <Animated.View style={[styles.progressBar, { width: barWidth, opacity }]} />
      </View>
      <WebView
        source={{ uri: url ?? '' }}
        style={styles.webview}
        onLoadStart={handleLoadStart}
        onLoadProgress={handleLoadProgress}
        onLoadEnd={handleLoadEnd}
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
  brandBlue: '#4f7cff',
  brandPurple: '#9b5cff',
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
    paddingHorizontal: 22,
    paddingBottom: 12,
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
  progressTrack: {
    height: 2,
    backgroundColor: 'transparent',
  },
  progressBar: {
    height: 2,
    backgroundColor: COLORS.brandBlue,
    shadowColor: COLORS.brandPurple,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 4,
  },
  webview: {
    flex: 1,
  },
});
