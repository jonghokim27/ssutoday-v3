export type StoredProfile = {
  studentId: number | string;
  name: string;
  major: 'cse' | 'sw' | 'media' | 'mediamba' | 'infocom' | 'aix' | 'sec' | string;
  isAdmin?: boolean;
};

export type ArticleProvider = 'ssucatch' | 'stu' | 'major' | string;

export type StorageState = {
  accessToken?: string;
  refreshToken?: string;
  profile?: string;
  provider?: string;
  notificationEnabled?: 'true' | 'false';
};

export type StorageKey = keyof StorageState;

export interface AppStorage {
  getItem(key: StorageKey): Promise<string | null>;
  setItem(key: StorageKey, value: string): Promise<void>;
  removeItem(key: StorageKey): Promise<void>;
  clearAuth(): Promise<void>;
  getProfile(): Promise<StoredProfile | null>;
  setProfile(profile: StoredProfile): Promise<void>;
  getProviders(): Promise<ArticleProvider[]>;
  setProviders(providers: ArticleProvider[]): Promise<void>;
}

export const defaultProviders: ArticleProvider[] = ['ssucatch', 'stu', 'major'];

export function normalizeProviders(providers: ArticleProvider[]) {
  return providers.map((provider) => (provider === 'ssuCatch' ? 'ssucatch' : provider));
}

class LocalAppStorage implements AppStorage {
  async getItem(key: StorageKey) {
    return window.localStorage.getItem(key);
  }

  async setItem(key: StorageKey, value: string) {
    window.localStorage.setItem(key, value);
  }

  async removeItem(key: StorageKey) {
    window.localStorage.removeItem(key);
  }

  async clearAuth() {
    await Promise.all([
      this.removeItem('accessToken'),
      this.removeItem('refreshToken'),
      this.removeItem('profile'),
      this.removeItem('notificationEnabled'),
    ]);
  }

  async getProfile() {
    const raw = await this.getItem('profile');
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as StoredProfile;
    } catch {
      await this.removeItem('profile');
      return null;
    }
  }

  async setProfile(profile: StoredProfile) {
    await this.setItem('profile', JSON.stringify(profile));
  }

  async getProviders() {
    const raw = await this.getItem('provider');
    if (!raw) {
      await this.setProviders(defaultProviders);
      return defaultProviders;
    }

    try {
      const providers = normalizeProviders(JSON.parse(raw) as ArticleProvider[]);
      if (JSON.stringify(providers) !== raw) {
        await this.setProviders(providers);
      }
      return providers;
    } catch {
      await this.setProviders(defaultProviders);
      return defaultProviders;
    }
  }

  async setProviders(providers: ArticleProvider[]) {
    await this.setItem('provider', JSON.stringify(normalizeProviders(providers)));
  }
}

export const appStorage: AppStorage = new LocalAppStorage();
