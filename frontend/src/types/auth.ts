export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
  acceptedTerms: boolean;
}

export interface RegisterResponse {
  accessToken?: string;
  refreshToken?: string;
  user?: {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    workspaceId?: string;
    workspaceName?: string;
  };
  message?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type LoginResponse = {
  code: number;
  message: string;
  data: {
    accessToken: string;
    tokenType: string;
    id: number;
    email: string;
    role: string;
  };
};

export interface ApiErrorResponse {
  message?: string;
  error?: string;
}

