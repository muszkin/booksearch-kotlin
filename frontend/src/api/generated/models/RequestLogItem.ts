/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type RequestLogItem = {
    id: number;
    method: string;
    path: string;
    statusCode: number;
    durationMs: number;
    requestId?: string | null;
    userId?: number | null;
    createdAt: string;
};

