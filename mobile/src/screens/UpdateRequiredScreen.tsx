import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

const STORE_URL = 'https://r2.ssu.today/install.html';

export default function UpdateRequiredScreen() {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.icon}>
        <Text style={styles.iconGlyph}>!</Text>
      </View>
      <Text style={styles.message}>업데이트가 필요합니다</Text>
      <Text style={styles.sub}>원활한 서비스 이용을 위해 슈투데이 앱을{"\n"}최신 버전으로 업데이트해 주세요</Text>
      <Pressable style={styles.updateButton} onPress={() => Linking.openURL(STORE_URL)}>
        <Text style={styles.updateText}>업데이트 하기</Text>
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
  updateButton: {
    marginTop: 20,
    width: '100%',
    maxWidth: 240,
    borderRadius: 16,
    paddingVertical: 14,
    backgroundColor: COLORS.brandBlue,
    alignItems: 'center',
  },
  updateText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#ffffff',
  },
});
