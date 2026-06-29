import { useCallback, useEffect } from 'react';
import { Platform } from 'react-native';
import { Stack, useRouter } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import messaging from '@react-native-firebase/messaging';
import { deepLink } from '../src/utils/deepLink';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

export default function RootLayout() {
  const router = useRouter();

  // Android: create HIGH importance channel so foreground local notifications appear as heads-up
  // iOS: ensure Firebase SDK is initialized for foreground message delivery
  useEffect(() => {
    if (Platform.OS === 'android') {
      void Notifications.setNotificationChannelAsync('default', {
        name: '기본 알림',
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#4F7CFF',
      });
    } else {
      void messaging().requestPermission();
    }
  }, []);

  const handleDeepLink = useCallback(
    (data: Record<string, string | undefined> | undefined) => {
      if (!data?.type) return;
      if (data.type === 'notice' && data.url) {
        router.push({ pathname: '/browser', params: { url: data.url } });
      } else if (data.type === 'reservation') {
        deepLink.emit({ type: 'reservation' });
      }
    },
    [router],
  );

  // Foreground FCM message → local notification
  useEffect(() => {
    return messaging().onMessage(async (remoteMessage) => {
      console.log('[FCM] onMessage received', JSON.stringify(remoteMessage));
      try {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: remoteMessage.notification?.title ?? '',
            body: remoteMessage.notification?.body ?? '',
            data: (remoteMessage.data ?? {}) as Record<string, string>,
            sound: 'default',
          },
          trigger: Platform.OS === 'android'
            ? { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds: 1, channelId: 'default', repeats: false }
            : null,
        });
      } catch (error) {
        console.error('[FCM] scheduleNotificationAsync failed:', error);
      }
    });
  }, []);

  useEffect(() => {
    // Foreground: local notification tap
    const localSub = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data as Record<string, string>;
      handleDeepLink(data);
    });

    // Background: FCM notification tap
    const bgUnsub = messaging().onNotificationOpenedApp((remoteMessage) => {
      handleDeepLink(remoteMessage.data as Record<string, string>);
    });

    // Cold start / quit state: FCM notification tap
    messaging().getInitialNotification().then((remoteMessage) => {
      if (remoteMessage) {
        handleDeepLink(remoteMessage.data as Record<string, string>);
      }
    });

    return () => {
      localSub.remove();
      bgUnsub();
    };
  }, [handleDeepLink]);

  return (
    <SafeAreaProvider>
      <Stack screenOptions={{ headerShown: false }} />
    </SafeAreaProvider>
  );
}
