/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ActivityLogListResponse } from '../models/ActivityLogListResponse';
import type { RequestLogListResponse } from '../models/RequestLogListResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class LogsService {
    /**
     * Get activity logs for authenticated user
     * @param page
     * @param pageSize
     * @param type Filter by action type
     * @returns ActivityLogListResponse Paginated activity log list
     * @throws ApiError
     */
    public static getActivityLogs(
        page: number = 1,
        pageSize: number = 20,
        type?: string,
    ): CancelablePromise<ActivityLogListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/logs/activity',
            query: {
                'page': page,
                'pageSize': pageSize,
                'type': type,
            },
            errors: {
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Get request logs (admin only)
     * @param page
     * @param pageSize
     * @param method
     * @param path
     * @param statusCode
     * @param from ISO-8601 date-time filter (from)
     * @param to ISO-8601 date-time filter (to)
     * @returns RequestLogListResponse Paginated request log list
     * @throws ApiError
     */
    public static getRequestLogs(
        page: number = 1,
        pageSize: number = 20,
        method?: string,
        path?: string,
        statusCode?: number,
        from?: string,
        to?: string,
    ): CancelablePromise<RequestLogListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/logs/requests',
            query: {
                'page': page,
                'pageSize': pageSize,
                'method': method,
                'path': path,
                'statusCode': statusCode,
                'from': from,
                'to': to,
            },
            errors: {
                401: `Not authenticated`,
                403: `Not authorized (admin only)`,
            },
        });
    }
}
