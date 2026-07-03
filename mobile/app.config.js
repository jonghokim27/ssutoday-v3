const { withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

function withAndroidCookieFlush(config) {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      const mainActivityPath = path.join(
        config.modRequest.platformProjectRoot,
        'app/src/main/java/com/ssutoday/MainActivity.kt',
      );

      if (!fs.existsSync(mainActivityPath)) {
        return config;
      }

      let contents = fs.readFileSync(mainActivityPath, 'utf8');

      if (contents.includes('CookieManager.getInstance().flush()')) {
        return config;
      }

      if (!contents.includes('import android.webkit.CookieManager')) {
        contents = contents.replace(
          'import android.os.Bundle',
          'import android.os.Bundle\nimport android.webkit.CookieManager',
        );
      }

      const lastBrace = contents.lastIndexOf('}');
      const onPause =
        '\n  override fun onPause() {\n    super.onPause()\n    CookieManager.getInstance().flush()\n  }\n';
      contents = contents.slice(0, lastBrace) + onPause + contents.slice(lastBrace);

      fs.writeFileSync(mainActivityPath, contents);
      return config;
    },
  ]);
}

function withGoogleUtilitiesModularHeaders(config) {
  return withDangerousMod(config, [
    'ios',
    async (config) => {
      const podfilePath = path.join(config.modRequest.platformProjectRoot, 'Podfile');
      let contents = fs.readFileSync(podfilePath, 'utf8');

      if (!contents.includes("pod 'GoogleUtilities', :modular_headers => true")) {
        contents = contents.replace(
          /(\n\s+use_react_native!)/,
          `\n  pod 'GoogleUtilities', :modular_headers => true$1`,
        );
        fs.writeFileSync(podfilePath, contents);
      }

      return config;
    },
  ]);
}

/** @type {import('@expo/config').ExpoConfig} */
const config = {
  name: '슈투데이',
  slug: 'ssutoday',
  scheme: 'ssutoday',
  version: '3.0.1',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'light',
  newArchEnabled: true,
  ios: {
    ...(process.env.GOOGLE_SERVICE_INFO_PLIST && {
      googleServicesFile: process.env.GOOGLE_SERVICE_INFO_PLIST,
    }),
    icon: './assets/icon-ios.png',
    bundleIdentifier: 'com.ssutoday',
    supportsTablet: false,
    infoPlist: {
      NSCameraUsageDescription: '슈투데이에서 인증샷을 촬영하기 위해 카메라 권한을 허용해주세요',
      NSFaceIDUsageDescription: '슈투데이에서 본인임을 확인하기 위해 FaceID 인증이 필요해요',
      ITSAppUsesNonExemptEncryption: false,
    },
  },
  android: {
    ...(process.env.GOOGLE_SERVICES_JSON && {
      googleServicesFile: process.env.GOOGLE_SERVICES_JSON,
    }),
    package: 'com.ssutoday',
    adaptiveIcon: {
      backgroundColor: '#4F7CFF',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    permissions: ['CAMERA', 'ACCESS_NOTIFICATION_POLICY'],
    predictiveBackGestureEnabled: false,
  },
  web: {
    favicon: './assets/favicon.png',
  },
  plugins: [
    'expo-router',
    'expo-notifications',
    '@react-native-firebase/app',
    '@react-native-firebase/messaging',
    [
      'expo-splash-screen',
      {
        backgroundColor: '#FFFFFF',
        image: './assets/splash-icon.png',
        imageWidth: 120,
      },
    ],
  ],
  extra: {
    router: {},
    eas: {
      projectId: 'bab41217-d412-490f-b208-c5b9cc2f5dab',
    },
  },
  updates: {
    url: "https://u.expo.dev/bab41217-d412-490f-b208-c5b9cc2f5dab"
  },
  runtimeVersion: {
    policy: "appVersion"
  }
};

module.exports = withAndroidCookieFlush(withGoogleUtilitiesModularHeaders(config));
