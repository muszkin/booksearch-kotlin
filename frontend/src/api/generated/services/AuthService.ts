/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ChangeOwnPasswordRequest } from '../models/ChangeOwnPasswordRequest';
import type { LoginRequest } from '../models/LoginRequest';
import type { LoginResponse } from '../models/LoginResponse';
import type { LogoutRequest } from '../models/LogoutRequest';
import type { PasswordResetBody } from '../models/PasswordResetBody';
import type { PasswordResetRequestBody } from '../models/PasswordResetRequestBody';
import type { RefreshRequest } from '../models/RefreshRequest';
import type { RefreshResponse } from '../models/RefreshResponse';
import type { RegisterRequest } from '../models/RegisterRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AuthService {
    /**
     * Register a new user
     * @param requestBody
     * @returns LoginResponse User registered successfully
     * @throws ApiError
     */
    public static register(
        requestBody: RegisterRequest,
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/register',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Validation error`,
                409: `Email already exists`,
            },
        });
    }
    /**
     * Authenticate user
     * @param requestBody
     * @returns LoginResponse Login successful
     * @throws ApiError
     */
    public static login(
        requestBody: LoginRequest,
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/login',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid credentials`,
            },
        });
    }
    /**
     * Refresh access token
     * @param requestBody
     * @returns RefreshResponse Token refreshed
     * @throws ApiError
     */
    public static refreshToken(
        requestBody: RefreshRequest,
    ): CancelablePromise<RefreshResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/refresh',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid or expired refresh token`,
            },
        });
    }
    /**
     * Request password reset
     * @param requestBody
     * @returns any Reset email sent if account exists
     * @throws ApiError
     */
    public static requestPasswordReset(
        requestBody: PasswordResetRequestBody,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/password-reset-request',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Reset password using token
     * @param requestBody
     * @returns any Password reset successful
     * @throws ApiError
     */
    public static resetPassword(
        requestBody: PasswordResetBody,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/password-reset',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid or expired token`,
            },
        });
    }
    /**
     * Change own password (authenticated)
     * @param requestBody
     * @returns any Password changed
     * @throws ApiError
     */
    public static changeOwnPassword(
        requestBody: ChangeOwnPasswordRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/auth/password',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid current password`,
                401: `Not authenticated`,
            },
        });
    }
    /**
     * Logout and revoke refresh token
     * @param requestBody
     * @returns any Logged out
     * @throws ApiError
     */
    public static logout(
        requestBody: LogoutRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/logout',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
