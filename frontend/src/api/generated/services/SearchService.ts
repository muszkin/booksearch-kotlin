/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SearchResponse } from '../models/SearchResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class SearchService {
    /**
     * Search for books
     * @param q Search query
     * @param lang Book language filter
     * @param ext Book format filter
     * @param page Page number
     * @param maxPages Maximum pages to scrape
     * @returns SearchResponse Search results
     * @throws ApiError
     */
    public static searchBooks(
        q: string,
        lang: string = 'pl',
        ext: string = 'epub',
        page: number = 1,
        maxPages: number = 3,
    ): CancelablePromise<SearchResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/search',
            query: {
                'q': q,
                'lang': lang,
                'ext': ext,
                'page': page,
                'maxPages': maxPages,
            },
            errors: {
                400: `Missing or invalid query parameters`,
                401: `Not authenticated`,
            },
        });
    }
}
