import { useCallback, useEffect } from 'react';
import { Platform } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Stack, useRouter } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import * as SplashScreen from 'expo-splash-screen';
import messaging from '@react-native-firebase/messaging';
import { deepLink } from '../src/utils/deepLink';

SplashScreen.preventAutoHideAsync();

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

// Register at module level to avoid iOS first-load timing bug where
// useEffect-registered listeners miss messages before component mounts
messaging().onMessage(async (remoteMessage) => {
  console.log('[FCM] onMessage received', JSON.stringify(remoteMessage));
  // iOS: firebase.json messaging_ios_foreground_presentation_options handles display natively
  // Android: schedule a local notification manually
  if (Platform.OS !== 'android') return;
  try {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: remoteMessage.notification?.title ?? '',
        body: remoteMessage.notification?.body ?? '',
        data: (remoteMessage.data ?? {}) as Record<string, string>,
        sound: 'default',
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds: 1, channelId: 'default', repeats: false },
    });
  } catch (error) {
    console.error('[FCM] scheduleNotificationAsync failed:', error);
  }
});

export default function RootLayout() {
  const router = useRouter();

  // Android: create HIGH importance channel so foreground local notifications appear as heads-up
  useEffect(() => {
    if (Platform.OS === 'android') {
      void Notifications.setNotificationChannelAsync('default', {
        name: '기본 알림',
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#4F7CFF',
      });
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
    // setTimeout(0) defers until after Expo Router commits the initial /index route,
    // so router.push('/browser') adds on top instead of replacing the stack root.
    messaging().getInitialNotification().then((remoteMessage) => {
      if (remoteMessage) {
        setTimeout(() => {
          handleDeepLink(remoteMessage.data as Record<string, string>);
        }, 0);
      }
    });

    return () => {
      localSub.remove();
      bgUnsub();
    };
  }, [handleDeepLink]);

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" backgroundColor="#ffffff" translucent={false} />
      <Stack screenOptions={{ headerShown: false }} />
    </SafeAreaProvider>
  );
}
