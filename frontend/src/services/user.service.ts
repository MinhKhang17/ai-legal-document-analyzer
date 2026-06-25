import { API_ENDPOINTS } from "../config/api";
import { buildAuthHeaders, requestApiData } from "./http";

export interface BackendUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  active: boolean;
}

export const getUsers = async (): Promise<BackendUser[]> =>
  requestApiData<BackendUser[]>(
    API_ENDPOINTS.users.list,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải danh sách người dùng",
  );

export const getUserDetail = async (userId: number | string): Promise<BackendUser> =>
  requestApiData<BackendUser>(
    API_ENDPOINTS.users.detail(userId),
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    "Không thể tải chi tiết người dùng",
  );
