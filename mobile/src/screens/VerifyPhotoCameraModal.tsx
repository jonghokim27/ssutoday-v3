import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { CameraView } from 'expo-camera';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type Props = { onCapture: (uri: string) => void; onCancel: () => void; onError: () => void };

export default function VerifyPhotoCameraModal({ onCapture, onCancel, onError }: Props) {
  const camera = useRef<CameraView>(null);
  const busy = useRef(false);
  const settled = useRef(false);
  const [ready, setReady] = useState(false);
  const [taking, setTaking] = useState(false);
  const insets = useSafeAreaInsets();

  useEffect(() => {
    settled.current = false;
    return () => { settled.current = true; };
  }, []);

  function finish(callback: () => void) {
    if (settled.current) return;
    settled.current = true;
    callback();
  }

  async function capture() {
    if (!ready || busy.current || settled.current || !camera.current) return;
    busy.current = true;
    setTaking(true);
    try {
      const photo = await camera.current.takePictureAsync({ quality: 1, exif: false, base64: false });
      if (!photo?.uri) throw new Error('No capture');
      finish(() => onCapture(photo.uri));
    } catch {
      finish(onError);
    }
  }

  return (
    <Modal animationType="slide" onRequestClose={() => finish(onCancel)}>
      <View style={styles.container}>
        <CameraView
          ref={camera}
          style={StyleSheet.absoluteFill}
          facing="back"
          mode="picture"
          onCameraReady={() => setReady(true)}
          onMountError={() => finish(onError)}
        />
        <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
          <Pressable onPress={() => finish(onCancel)} accessibilityLabel="촬영 취소" hitSlop={12}>
            <Text style={styles.close}>닫기</Text>
          </Pressable>
          <Text style={styles.title}>스터디룸 인증샷</Text>
        </View>
        <View style={[styles.footer, { paddingBottom: insets.bottom + 24 }]}>
          <Text style={styles.hint}>방 안의 모습이 잘 보이도록 촬영해 주세요</Text>
          <Pressable
            style={[styles.shutter, (!ready || taking) && styles.disabled]}
            onPress={() => void capture()}
            disabled={!ready || taking}
            accessibilityRole="button"
            accessibilityLabel="사진 촬영"
          >
            {taking ? <ActivityIndicator color="#4f7cff" /> : <View style={styles.shutterInner} />}
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  header: { paddingHorizontal: 24, paddingBottom: 20, backgroundColor: '#0009', gap: 20 },
  close: { color: '#fff', fontSize: 16 },
  title: { color: '#fff', fontSize: 22, fontWeight: '600' },
  footer: { position: 'absolute', bottom: 0, left: 0, right: 0, alignItems: 'center', paddingTop: 24, gap: 20, backgroundColor: '#0009' },
  hint: { color: '#fff', fontSize: 14 },
  shutter: { width: 76, height: 76, borderRadius: 38, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  shutterInner: { width: 64, height: 64, borderRadius: 32, borderWidth: 2, borderColor: '#222' },
  disabled: { opacity: 0.6 },
});
