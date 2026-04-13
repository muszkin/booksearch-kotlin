/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type DeliveryResponse = {
    deliveryId: number;
    status: DeliveryResponse.status;
    error?: string | null;
};
export namespace DeliveryResponse {
    export enum status {
        SENT = 'sent',
        FAILED = 'failed',
    }
}

