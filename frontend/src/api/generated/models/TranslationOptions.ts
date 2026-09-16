/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type TranslationOptions = {
    modelId?: string | null;
    autoFallback?: boolean;
    fallbackModelIds?: Array<string>;
    referenceLibraryIds?: Array<number>;
    referenceChapters?: number;
    glossary?: string;
    notes?: string;
    /**
     * Persisted sampled excerpts; ignored in requests
     */
    readonly referenceText?: string;
};

