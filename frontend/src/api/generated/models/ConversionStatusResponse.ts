/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ConversionStatusResponse = {
    jobId: string;
    status: ConversionStatusResponse.status;
    sourceFormat: string;
    targetFormat: string;
    error?: string | null;
};
export namespace ConversionStatusResponse {
    export enum status {
        QUEUED = 'queued',
        CONVERTING = 'converting',
        COMPLETED = 'completed',
        FAILED = 'failed',
    }
}

