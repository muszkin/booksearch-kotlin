/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ChangePasswordRequest } from '../models/ChangePasswordRequest';
import type { CreateUserRequest } from '../models/CreateUserRequest';
import type { ToggleRegistrationRequest } from '../models/ToggleRegistrationRequest';
import type { UserResponse } from '../models/UserResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AdminService {
    /**
     * Enable or disable user registration (super-admin)
     * @param requestBody
     * @returns any Registration setting updated
     * @throws ApiError
     */
    public static toggleRegistration(
        requestBody: ToggleRegistrationRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/admin/registration',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                403: `Not authorized`,
            },
        });
    }
    /**
     * List all users (super-admin)
     * @returns UserResponse List of all users
     * @throws ApiError
     */
    public static listUsers(): CancelablePromise<Array<UserResponse>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/users',
            errors: {
                403: `Not authorized`,
            },
        });
    }
    /**
     * Create a new user (super-admin)
     * @param requestBody
     * @returns UserResponse User created
     * @throws ApiError
     */
    public static createUser(
        requestBody: CreateUserRequest,
    ): CancelablePromise<UserResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/users',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                403: `Not authorized`,
                409: `Email already exists`,
            },
        });
    }
    /**
     * Change a user's password (super-admin)
     * @param id
     * @param requestBody
     * @returns any Password changed
     * @throws ApiError
     */
    public static changeUserPassword(
        id: number,
        requestBody: ChangePasswordRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/admin/users/{id}/password',
            path: {
                'id': id,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                403: `Not authorized`,
                404: `User not found`,
            },
        });
    }
}
