/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TranslationJobState } from './TranslationJobState';
export type TranslationStartedResponse = {
    /**
     * UUID identifying the translation job.
     */
    jobId: string;
    status: TranslationJobState;
};

