/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ChangePasswordRequest } from '../models/ChangePasswordRequest';
import type { CreateUserRequest } from '../models/CreateUserRequest';
import type { LoginResponse } from '../models/LoginResponse';
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
    /**
     * Start impersonating a user (super-admin only)
     * Admin impersonation start; SUPER_ADMIN only. Returns tokens + UserResponse with actAs* populated.
     * The returned tokens grant an impersonation session (access TTL 30 min, refresh TTL 1 h,
     * non-sliding absolute cap). Rejected if target is self, another super admin, or inactive.
     *
     * @param id
     * @returns LoginResponse Impersonation session started
     * @throws ApiError
     */
    public static impersonateUser(
        id: number,
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/users/{id}/impersonate',
            path: {
                'id': id,
            },
            errors: {
                401: `Not authenticated`,
                403: `Not authorized (not super-admin, or target is super-admin)`,
                404: `Target user not found`,
                422: `Invalid target (self, or inactive)`,
            },
        });
    }
    /**
     * Return to admin identity (end impersonation)
     * End impersonation session, restore admin identity with fresh tokens.
     * Revokes the impersonation refresh token and mints a fresh admin access + refresh pair
     * (standard TTL). Gated by the presence of original_admin_id JWT claim on the current
     * access token. Failing for a non-impersonated session returns 403.
     *
     * @param requestBody
     * @returns LoginResponse Admin identity restored
     * @throws ApiError
     */
    public static stopImpersonation(
        requestBody: {
            /**
             * Current impersonation refresh token to revoke.
             */
            refreshToken: string;
        },
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/impersonate/stop',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Not authenticated or refresh token invalid`,
                403: `Current session is not an impersonation session`,
                404: `Admin user not found`,
            },
        });
    }
}
