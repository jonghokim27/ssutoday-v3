import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

type OfflineScreenProps = {
  onRetry: () => void;
};

export default function OfflineScreen({ onRetry }: OfflineScreenProps) {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.icon}>
        <Text style={styles.iconGlyph}>!</Text>
      </View>
      <Text style={styles.message}>인터넷 연결을 확인해주세요</Text>
      <Text style={styles.sub}>네트워크 상태가 좋지 않아 페이지를 불러올 수 없어요</Text>
      <Pressable style={styles.retryButton} onPress={onRetry}>
        <Text style={styles.retryText}>다시 시도</Text>
      </Pressable>
    </SafeAreaView>
  );
}

const COLORS = {
  background: '#ffffff',
  brandBlue: '#4f7cff',
  textPrimary: '#0f1222',
  textMuted: '#8a8f9c',
  iconBackground: 'rgba(79, 124, 255, 0.1)',
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 30,
  },
  icon: {
    width: 60,
    height: 60,
    borderRadius: 20,
    backgroundColor: COLORS.iconBackground,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 18,
  },
  iconGlyph: {
    fontSize: 26,
    fontWeight: '800',
    color: COLORS.brandBlue,
  },
  message: {
    fontSize: 15.5,
    fontWeight: '800',
    color: COLORS.textPrimary,
    letterSpacing: -0.3,
    textAlign: 'center',
  },
  sub: {
    marginTop: 7,
    fontSize: 13,
    fontWeight: '500',
    color: COLORS.textMuted,
    textAlign: 'center',
  },
  retryButton: {
    marginTop: 20,
    width: '100%',
    maxWidth: 240,
    borderRadius: 16,
    paddingVertical: 14,
    backgroundColor: COLORS.brandBlue,
    alignItems: 'center',
  },
  retryText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#ffffff',
  },
});
