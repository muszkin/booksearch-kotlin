/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DownloadStartedResponse } from '../models/DownloadStartedResponse';
import type { DownloadStatusResponse } from '../models/DownloadStatusResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DownloadService {
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
