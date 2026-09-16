/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TranslationOptions } from './TranslationOptions';
export type TranslationStartRequest = {
    options?: TranslationOptions;
    /**
     * Must be explicitly true to permit sending EPUB text to OpenRouter.
     */
    externalProcessingConfirmed: boolean;
};

