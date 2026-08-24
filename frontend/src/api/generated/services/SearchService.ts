/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SearchJobStatusResponse } from '../models/SearchJobStatusResponse';
import type { SearchStartedResponse } from '../models/SearchStartedResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class SearchService {
    /**
     * Submit an asynchronous book search
     * Scraping Anna's Archive runs through a headless browser and regularly exceeds the 120 second proxy read timeout, so the search is queued and polled instead of being answered inline.
     *
     * @param q Search query
     * @param lang Narrow the search to one language, or "any" to leave it unconstrained
     * @param ext Narrow the search to one format, or "any" to leave it unconstrained
     * @param maxPages Maximum pages to scrape
     * @returns SearchStartedResponse Search job accepted
     * @throws ApiError
     */
    public static submitSearch(
        q: string,
        lang: 'pl' | 'en' | 'de' | 'any' = 'pl',
        ext: 'epub' | 'mobi' | 'pdf' | 'any' = 'epub',
        maxPages: number = 3,
    ): CancelablePromise<SearchStartedResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/search',
            query: {
                'q': q,
                'lang': lang,
                'ext': ext,
                'maxPages': maxPages,
            },
            errors: {
                400: `Missing or invalid query parameters`,
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Poll the status and results of a search job
     * @param jobId Search job identifier
     * @returns SearchJobStatusResponse Current search job state
     * @throws ApiError
     */
    public static getSearchStatus(
        jobId: number,
    ): CancelablePromise<SearchJobStatusResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/search/status/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                401: `Not authenticated`,
                404: `Search job not found`,
            },
        });
    }
}
