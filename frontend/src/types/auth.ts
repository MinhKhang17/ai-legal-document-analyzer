export type UserRole = "ADMIN" | "CUSTOMER" | "EXPERT" | "USER" | string;

export interface CurrentUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
  active: boolean;
  emailVerified?: boolean;
  mustChangePassword?: boolean;
  passwordResetDeadline?: string | null;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  acceptedTerms: boolean;
}

export type RegisterResponse = ApiResponse<CurrentUser>;

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginJwtResponse {
  accessToken: string;
  tokenType?: string;
  id: number;
  email: string;
  role: UserRole;
}

export type LoginResponse = ApiResponse<LoginJwtResponse>;

export interface RefreshResponse extends ApiResponse<LoginJwtResponse> {}

export type AuthMeResponse = ApiResponse<CurrentUser>;

export interface ApiErrorResponse {
  message?: string;
  error?: string;
}

