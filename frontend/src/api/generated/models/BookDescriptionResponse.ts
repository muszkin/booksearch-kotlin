/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type BookDescriptionResponse = {
    description: string;
    /**
     * Where the text came from; "openrouter" means it was generated
     */
    source: BookDescriptionResponse.source;
    isbn?: string | null;
};
export namespace BookDescriptionResponse {
    /**
     * Where the text came from; "openrouter" means it was generated
     */
    export enum source {
        ANNAS_ARCHIVE = 'annas-archive',
        OPENROUTER = 'openrouter',
    }
}

