/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type TranslationEstimateResponse = {
    totalChapters: number;
    estimatedInputTokens: number;
    modelId: string;
    /**
     * Indicative duration category, currently minutes_to_hours.
     */
    indicativeDuration: string;
    limitWarning: string;
};

