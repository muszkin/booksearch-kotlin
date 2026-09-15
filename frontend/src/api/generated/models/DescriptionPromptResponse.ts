/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type DescriptionPromptResponse = {
    /**
     * The editable half — how a book should be described
     */
    style: string;
    /**
     * Answers shorter than this are discarded as unreliable
     */
    minLength: number;
    isDefault: boolean;
    /**
     * Read-only; always appended, forbids invented plots
     */
    guard: string;
};

