/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type TranslationAttempt = {
    id: string;
    startedAt: string;
    chapterIndex: number;
    segmentIndex: number;
    requestedModel: string;
    actualModel?: string | null;
    status: string;
    errorCode?: string | null;
    message?: string | null;
    inputTokens: number;
    outputTokens: number;
    itemCount: number;
    finishReason?: string | null;
};

