/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddToLibraryRequest } from '../models/AddToLibraryRequest';
import type { BatchDownloadRequest } from '../models/BatchDownloadRequest';
import type { LibraryBook } from '../models/LibraryBook';
import type { LibraryListResponse } from '../models/LibraryListResponse';
import type { OwnershipInfo } from '../models/OwnershipInfo';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class LibraryService {
    /**
     * Get user's library (paginated)
     * @param page
     * @param pageSize
     * @returns LibraryListResponse Paginated library list
     * @throws ApiError
     */
    public static getUserLibrary(
        page: number = 1,
        pageSize: number = 20,
    ): CancelablePromise<LibraryListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/library',
            query: {
                'page': page,
                'pageSize': pageSize,
            },
            errors: {
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Add a book to user's library
     * @param requestBody
     * @returns LibraryBook Book added to library
     * @throws ApiError
     */
    public static addToLibrary(
        requestBody: AddToLibraryRequest,
    ): CancelablePromise<LibraryBook> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/library',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Book not found`,
                409: `Book already in library with this format`,
            },
        });
    }
    /**
     * Remove a book from user's library
     * @param id
     * @returns any Entry removed
     * @throws ApiError
     */
    public static removeFromLibrary(
        id: number,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/library/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Entry not found`,
            },
        });
    }
    /**
     * Download multiple library books as a ZIP archive
     * @param requestBody
     * @returns binary ZIP file stream
     * @throws ApiError
     */
    public static batchDownload(
        requestBody: BatchDownloadRequest,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/library/batch-download',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Not authenticated`,
                422: `Empty ID list`,
            },
        });
    }
    /**
     * Check ownership for given book MD5s
     * @param md5 Comma-separated list of book MD5 hashes
     * @returns OwnershipInfo Ownership info per MD5
     * @throws ApiError
     */
    public static checkOwnership(
        md5: string,
    ): CancelablePromise<Record<string, OwnershipInfo>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/library/check',
            query: {
                'md5': md5,
            },
            errors: {
                401: `Not authenticated`,
            },
        });
    }
}
