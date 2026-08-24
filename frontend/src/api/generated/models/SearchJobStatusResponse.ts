/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BookResult } from './BookResult';
export type SearchJobStatusResponse = {
    jobId: number;
    query: string;
    status: SearchJobStatusResponse.status;
    results: Array<BookResult>;
    totalResults: number;
    error?: string | null;
};
export namespace SearchJobStatusResponse {
    export enum status {
        QUEUED = 'queued',
        SCRAPING = 'scraping',
        COMPLETED = 'completed',
        FAILED = 'failed',
    }
}

