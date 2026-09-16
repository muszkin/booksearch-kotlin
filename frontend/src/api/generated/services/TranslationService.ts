/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DownloadStartedResponse } from '../models/DownloadStartedResponse';
import type { LibraryBook } from '../models/LibraryBook';
import type { TranslationDetails } from '../models/TranslationDetails';
import type { TranslationEstimateResponse } from '../models/TranslationEstimateResponse';
import type { TranslationModel } from '../models/TranslationModel';
import type { TranslationOptions } from '../models/TranslationOptions';
import type { TranslationStartedResponse } from '../models/TranslationStartedResponse';
import type { TranslationStartRequest } from '../models/TranslationStartRequest';
import type { TranslationStatusResponse } from '../models/TranslationStatusResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class TranslationService {
    /**
     * List currently free text models
     * @returns TranslationModel Successful response
     * @throws ApiError
     */
    public static listTranslationModels(): CancelablePromise<Array<TranslationModel>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/models',
            errors: {
                401: `Missing or invalid JWT`,
                422: `Invalid request, missing confirmation, unavailable configuration, or ineligible source or model`,
            },
        });
    }
    /**
     * Estimate translation of an owned downloaded English EPUB
     * @param libraryId
     * @param modelId Explicit free model instead of the administrator default
     * @returns TranslationEstimateResponse Successful response
     * @throws ApiError
     */
    public static estimateTranslation(
        libraryId: number,
        modelId?: string,
    ): CancelablePromise<TranslationEstimateResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/{libraryId}/estimate',
            path: {
                'libraryId': libraryId,
            },
            query: {
                'modelId': modelId,
            },
            errors: {
                401: `Missing or invalid JWT`,
                404: `Resource not found or not owned by the authenticated user`,
                422: `Invalid request, missing confirmation, unavailable configuration, or ineligible source or model`,
            },
        });
    }
    /**
     * Confirm external processing and queue a translation
     * @param libraryId
     * @param requestBody
     * @returns TranslationStartedResponse Translation queued
     * @throws ApiError
     */
    public static startTranslation(
        libraryId: number,
        requestBody: TranslationStartRequest,
    ): CancelablePromise<TranslationStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/{libraryId}',
            path: {
                'libraryId': libraryId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Missing or invalid JWT`,
                404: `Resource not found or not owned by the authenticated user`,
                409: `An active translation already exists or the job state does not allow this operation`,
                422: `Invalid request, missing confirmation, unavailable configuration, or ineligible source or model`,
            },
        });
    }
    /**
     * Preview stable reference samples before external processing
     * @param libraryId
     * @param requestBody
     * @returns TranslationOptions Options with server-generated referenceText; nothing sent to a model
     * @throws ApiError
     */
    public static previewTranslationContext(
        libraryId: number,
        requestBody: TranslationOptions,
    ): CancelablePromise<TranslationOptions> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/{libraryId}/context-preview',
            path: {
                'libraryId': libraryId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                422: `Invalid reference context`,
            },
        });
    }
    /**
     * Download an indexed Polish EPUB by the source author without device delivery
     * @param libraryId
     * @param md5
     * @returns DownloadStartedResponse Download queued
     * @throws ApiError
     */
    public static downloadTranslationReference(
        libraryId: number,
        md5: string,
    ): CancelablePromise<DownloadStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/{libraryId}/references/{md5}/download',
            path: {
                'libraryId': libraryId,
                'md5': md5,
            },
            errors: {
                404: `Source not owned or reference not indexed`,
                422: `Reference is not a Polish EPUB by the same author`,
            },
        });
    }
    /**
     * @param libraryId
     * @returns LibraryBook Owned Polish EPUBs by the same author
     * @throws ApiError
     */
    public static listTranslationReferences(
        libraryId: number,
    ): CancelablePromise<Array<LibraryBook>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/{libraryId}/references',
            path: {
                'libraryId': libraryId,
            },
            errors: {
                401: `Authentication required`,
                404: `Source not owned or not found`,
            },
        });
    }
    /**
     * @param jobId
     * @returns TranslationDetails Owner-only persisted context, chapter progress and attempt history
     * @throws ApiError
     */
    public static getTranslationDetails(
        jobId: string,
    ): CancelablePromise<TranslationDetails> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/jobs/{jobId}/details',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Authentication required`,
                404: `Job not owned or not found`,
            },
        });
    }
    /**
     * @param jobId
     * @param index
     * @param format
     * @returns string Translated chapter; incomplete chapters are clearly marked
     * @throws ApiError
     */
    public static exportTranslationChapter(
        jobId: string,
        index: number,
        format: 'txt' | 'md' = 'txt',
    ): CancelablePromise<string> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/jobs/{jobId}/chapters/{index}/export',
            path: {
                'jobId': jobId,
                'index': index,
            },
            query: {
                'format': format,
            },
            errors: {
                401: `Authentication required`,
                404: `Chapter or owned job not found`,
                422: `No translated text or invalid format`,
            },
        });
    }
    /**
     * Rediscover owned active, paused, and completed translations
     * Returns active and resumable jobs plus completed translations so chapter exports remain accessible after reloading. Failed and cancelled jobs are omitted.
     * @returns TranslationStatusResponse Owned active, paused, or completed jobs, newest first
     * @throws ApiError
     */
    public static listTranslationJobs(): CancelablePromise<Array<TranslationStatusResponse>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/jobs',
            errors: {
                401: `Missing or invalid JWT`,
            },
        });
    }
    /**
     * Get an owned translation job
     * @param jobId
     * @returns TranslationStatusResponse Successful response
     * @throws ApiError
     */
    public static getTranslationStatus(
        jobId: string,
    ): CancelablePromise<TranslationStatusResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/jobs/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Missing or invalid JWT`,
                404: `Resource not found or not owned by the authenticated user`,
            },
        });
    }
    /**
     * Resume a paused translation after revalidating its source and model
     * @param jobId
     * @param requestBody
     * @returns TranslationStartedResponse Translation queued
     * @throws ApiError
     */
    public static resumeTranslation(
        jobId: string,
        requestBody?: TranslationOptions,
    ): CancelablePromise<TranslationStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/jobs/{jobId}/resume',
            path: {
                'jobId': jobId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Missing or invalid JWT`,
                404: `Resource not found or not owned by the authenticated user`,
                409: `An active translation already exists or the job state does not allow this operation`,
                422: `Invalid request, missing confirmation, unavailable configuration, or ineligible source or model`,
            },
        });
    }
    /**
     * Cancel queued or paused work and remove its private workspace
     * @param jobId
     * @returns void
     * @throws ApiError
     */
    public static cancelTranslation(
        jobId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/jobs/{jobId}/cancel',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Missing or invalid JWT`,
                404: `Resource not found or not owned by the authenticated user`,
                409: `An active translation already exists or the job state does not allow this operation`,
            },
        });
    }
}
