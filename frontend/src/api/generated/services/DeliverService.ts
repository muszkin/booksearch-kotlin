/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DeliveryRecord } from '../models/DeliveryRecord';
import type { DeliveryResponse } from '../models/DeliveryResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DeliverService {
    /**
     * Deliver a library book to a device via email
     * @param libraryId
     * @param device Target device for delivery
     * @returns DeliveryResponse Delivery result
     * @throws ApiError
     */
    public static deliverBook(
        libraryId: number,
        device: 'kindle' | 'pocketbook',
    ): CancelablePromise<DeliveryResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/deliver/{libraryId}',
            path: {
                'libraryId': libraryId,
            },
            query: {
                'device': device,
            },
            errors: {
                401: `Not authenticated`,
                404: `Library entry not found`,
                422: `SMTP not configured for device`,
            },
        });
    }
    /**
     * Get all deliveries for authenticated user
     * @returns DeliveryRecord List of deliveries
     * @throws ApiError
     */
    public static getUserDeliveries(): CancelablePromise<Array<DeliveryRecord>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/deliveries',
            errors: {
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Get deliveries for a specific book
     * @param bookMd5
     * @returns DeliveryRecord Deliveries for this book
     * @throws ApiError
     */
    public static getDeliveriesForBook(
        bookMd5: string,
    ): CancelablePromise<Array<DeliveryRecord>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/deliveries/{bookMd5}',
            path: {
                'bookMd5': bookMd5,
            },
            errors: {
                401: `Not authenticated`,
            },
        });
    }
}
