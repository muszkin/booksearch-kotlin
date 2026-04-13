/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ConversionStartedResponse } from '../models/ConversionStartedResponse';
import type { ConversionStatusResponse } from '../models/ConversionStatusResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ConvertService {
    /**
     * Start format conversion for a library book
     * @param libraryId
     * @param target Target format for conversion
     * @returns ConversionStartedResponse Conversion queued
     * @throws ApiError
     */
    public static startConversion(
        libraryId: number,
        target: 'epub' | 'mobi' | 'pdf',
    ): CancelablePromise<ConversionStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/convert/{libraryId}',
            path: {
                'libraryId': libraryId,
            },
            query: {
                'target': target,
            },
            errors: {
                401: `Not authenticated`,
                404: `Library entry not found`,
                422: `Validation error (same format, unsupported format)`,
            },
        });
    }
    /**
     * Get conversion job status
     * @param jobId
     * @returns ConversionStatusResponse Conversion status
     * @throws ApiError
     */
    public static getConversionStatus(
        jobId: string,
    ): CancelablePromise<ConversionStatusResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/convert/status/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Not authenticated`,
                404: `Conversion job not found`,
            },
        });
    }
}
