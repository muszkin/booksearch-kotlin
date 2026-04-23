/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type UserResponse = {
    id: number;
    email: string;
    displayName: string;
    isSuperAdmin: boolean;
    isActive: boolean;
    forcePasswordChange: boolean;
    createdAt: string;
    /**
     * During an impersonation session, holds the ID of the real admin who started the session.
     * Null when not impersonating. Clients compute isImpersonating = actAsUserId != null.
     *
     */
    actAsUserId?: number | null;
    /**
     * Email of the real admin during an impersonation session. Null otherwise.
     */
    actAsEmail?: string | null;
};

