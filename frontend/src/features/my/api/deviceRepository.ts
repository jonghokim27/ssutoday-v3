import { apiClient } from '../../../shared/api/apiClient';
import { type ApiResult } from '../../../shared/api/types';
import { nativeBridge } from '../../../shared/native/nativeBridge';

export type NotificationOptions = {
  notice: boolean;
  reserve: boolean;
  lms: boolean;
};

export type DeviceRepository = {
  register(): Promise<ApiResult<null>>;
  unregister(): Promise<ApiResult<null>>;
  getOptions(): Promise<ApiResult<NotificationOptions>>;
  updateOption(option: keyof NotificationOptions, value: boolean): Promise<ApiResult<null>>;
};

export class ApiDeviceRepository implements DeviceRepository {
  async register() {
    const device = await nativeBridge.getDeviceInfo();
    await nativeBridge.requestPushPermission();
    const pushToken = await nativeBridge.getPushToken();
    return apiClient.post<{ osType: string; uuid: string; pushToken: string }, null>(
      'device/register',
      { osType: device.osType, uuid: device.uuid, pushToken: pushToken ?? '' },
      { authenticated: true },
    );
  }

  async unregister() {
    const device = await nativeBridge.getDeviceInfo();
    return apiClient.post<{ osType: string; uuid: string }, null>(
      'device/unregister',
      { osType: device.osType, uuid: device.uuid },
      { authenticated: true },
    );
  }

  async getOptions() {
    const device = await nativeBridge.getDeviceInfo();
    return apiClient.post<{ osType: string; uuid: string }, NotificationOptions>(
      'device/getOption',
      { osType: device.osType, uuid: device.uuid },
      { authenticated: true },
    );
  }

  async updateOption(option: keyof NotificationOptions, value: boolean) {
    const device = await nativeBridge.getDeviceInfo();
    return apiClient.post<{ osType: string; uuid: string; option: keyof NotificationOptions; value: boolean }, null>(
      'device/updateOption',
      { osType: device.osType, uuid: device.uuid, option, value },
      { authenticated: true },
    );
  }
}

export const deviceRepository: DeviceRepository = new ApiDeviceRepository();
