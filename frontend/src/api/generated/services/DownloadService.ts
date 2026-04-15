/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelJobResponse } from '../models/CancelJobResponse';
import type { DownloadJobListResponse } from '../models/DownloadJobListResponse';
import type { DownloadStartedResponse } from '../models/DownloadStartedResponse';
import type { DownloadStatusResponse } from '../models/DownloadStatusResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DownloadService {
    /**
     * Get paginated list of download jobs for authenticated user
     * @param status Filter by job status
     * @param page
     * @param pageSize
     * @returns DownloadJobListResponse Paginated download job list
     * @throws ApiError
     */
    public static getDownloadJobs(
        status?: string,
        page: number = 1,
        pageSize: number = 20,
    ): CancelablePromise<DownloadJobListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/download/jobs',
            query: {
                'status': status,
                'page': page,
                'pageSize': pageSize,
            },
            errors: {
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Cancel a queued or active download job
     * @param jobId
     * @returns CancelJobResponse Job cancelled
     * @throws ApiError
     */
    public static cancelDownloadJob(
        jobId: number,
    ): CancelablePromise<CancelJobResponse> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/download/{jobId}/cancel',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Not authenticated`,
                404: `Job not found or not owned by user`,
                422: `Job cannot be cancelled (not in cancellable state)`,
            },
        });
    }
    /**
     * Start downloading a book by MD5
     * @param md5
     * @returns DownloadStartedResponse Download queued
     * @throws ApiError
     */
    public static startDownload(
        md5: string,
    ): CancelablePromise<DownloadStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/download/{md5}',
            path: {
                'md5': md5,
            },
            errors: {
                401: `Not authenticated`,
                404: `Book not found`,
            },
        });
    }
    /**
     * Get download job status
     * @param jobId
     * @returns DownloadStatusResponse Download job status
     * @throws ApiError
     */
    public static getDownloadStatus(
        jobId: number,
    ): CancelablePromise<DownloadStatusResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/download/status/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Not authenticated`,
                404: `Download job not found`,
            },
        });
    }
}
