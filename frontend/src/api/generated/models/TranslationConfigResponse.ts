/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type TranslationConfigResponse = {
    defaultModelId: string | null;
    /**
     * Whether server credentials and a default model are configured.
     */
    configured: boolean;
    /**
     * Live free-text eligibility; null when the model list could not be checked.
     */
    eligible: boolean | null;
};

