/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type OwnershipInfo = {
    matchType: OwnershipInfo.matchType;
    ownedFormats: Array<string>;
};
export namespace OwnershipInfo {
    export enum matchType {
        EXACT = 'exact',
        TITLE = 'title',
        AUTHOR = 'author',
        NONE = 'none',
    }
}

