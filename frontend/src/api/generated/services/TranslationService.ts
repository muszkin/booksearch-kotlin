/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TranslationEstimateResponse } from '../models/TranslationEstimateResponse';
import type { TranslationModel } from '../models/TranslationModel';
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
     * @returns TranslationEstimateResponse Successful response
     * @throws ApiError
     */
    public static estimateTranslation(
        libraryId: number,
    ): CancelablePromise<TranslationEstimateResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/translation/{libraryId}/estimate',
            path: {
                'libraryId': libraryId,
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
     * @returns TranslationStartedResponse Translation queued
     * @throws ApiError
     */
    public static resumeTranslation(
        jobId: string,
    ): CancelablePromise<TranslationStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/translation/jobs/{jobId}/resume',
            path: {
                'jobId': jobId,
            },
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
