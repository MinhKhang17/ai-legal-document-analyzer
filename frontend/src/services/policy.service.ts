import { buildBearerHeaders, requestApiData } from "./http";

export interface PolicyStatus {
  termsVersion: string;
  privacyPolicyVersion: string;
  accepted: boolean;
  acceptedAt?: string | null;
}

export const getCurrentPolicyStatus = (accessToken: string) => requestApiData<PolicyStatus>(
  "/api/v1/policies/current",
  { method: "GET", headers: buildBearerHeaders(accessToken), credentials: "include" },
  "Không thể kiểm tra điều khoản hiện hành",
);

export const acceptCurrentPolicies = (accessToken: string) => requestApiData<PolicyStatus>(
  "/api/v1/policies/accept",
  {
    method: "POST",
    headers: buildBearerHeaders(accessToken, { "Content-Type": "application/json" }),
    credentials: "include",
    body: JSON.stringify({ accepted: true }),
  },
  "Không thể lưu chấp thuận điều khoản",
);
