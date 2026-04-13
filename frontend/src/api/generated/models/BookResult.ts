/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type BookResult = {
    md5: string;
    title: string;
    author: string;
    language: string;
    format: string;
    fileSize: string;
    detailUrl: string;
    coverUrl: string;
    publisher: string;
    year: string;
    description: string;
    matchType: BookResult.matchType;
    ownedFormats: Array<string>;
};
export namespace BookResult {
    export enum matchType {
        EXACT = 'exact',
        TITLE = 'title',
        AUTHOR = 'author',
        NONE = 'none',
    }
}

