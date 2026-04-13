/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DeviceSettingsRequest } from '../models/DeviceSettingsRequest';
import type { DeviceSettingsResponse } from '../models/DeviceSettingsResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class SettingsService {
    /**
     * Get all device settings for authenticated user
     * @returns DeviceSettingsResponse All device settings (passwords redacted)
     * @throws ApiError
     */
    public static getAllSettings(): CancelablePromise<Record<string, DeviceSettingsResponse>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/settings',
            errors: {
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Get SMTP settings for a specific device
     * @param device
     * @returns DeviceSettingsResponse Device settings (password redacted)
     * @throws ApiError
     */
    public static getDeviceSettings(
        device: 'kindle' | 'pocketbook',
    ): CancelablePromise<DeviceSettingsResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/settings/{device}',
            path: {
                'device': device,
            },
            errors: {
                401: `Not authenticated`,
                404: `Settings not found for device`,
                422: `Invalid device name`,
            },
        });
    }
    /**
     * Save SMTP settings for a specific device
     * @param device
     * @param requestBody
     * @returns any Settings saved
     * @throws ApiError
     */
    public static saveDeviceSettings(
        device: 'kindle' | 'pocketbook',
        requestBody: DeviceSettingsRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/settings/{device}',
            path: {
                'device': device,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Not authenticated`,
                422: `Invalid device name`,
            },
        });
    }
    /**
     * Delete SMTP settings for a specific device
     * @param device
     * @returns any Settings deleted
     * @throws ApiError
     */
    public static deleteDeviceSettings(
        device: 'kindle' | 'pocketbook',
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/settings/{device}',
            path: {
                'device': device,
            },
            errors: {
                401: `Not authenticated`,
                422: `Invalid device name`,
            },
        });
    }
}
