/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TranslationJobState } from './TranslationJobState';
export type TranslationStatusResponse = {
    /**
     * Active shared cooldown deadline in UTC.
     */
    retryAt?: string | null;
    rateLimitScope?: TranslationStatusResponse.rateLimitScope | null;
    /**
     * UUID identifying the translation job.
     */
    jobId: string;
    status: TranslationJobState;
    sourceLibraryEntryId: number;
    modelId: string;
    totalChapters: number;
    completedChapters: number;
    failedChapterIndex?: number | null;
    estimatedInputTokens: number;
    actualInputTokens: number;
    actualOutputTokens: number;
    outputLibraryEntryId?: number | null;
    /**
     * Safe error code; never source text or an upstream response.
     */
    error?: string | null;
    resumable: boolean;
};
export namespace TranslationStatusResponse {
    export enum rateLimitScope {
        PLATFORM = 'platform',
        PROVIDER = 'provider',
        UNKNOWN = 'unknown',
    }
}

