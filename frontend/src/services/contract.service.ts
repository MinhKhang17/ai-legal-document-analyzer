import { API_ENDPOINTS } from "../config/api";
import type {
  ContractGenerationJob,
  ContractTemplate,
  ContractVersion,
  CreateContractTemplateRequest,
  GenerateContractRequest,
  PageResponse,
  RevertContractVersionRequest,
  SaveContractRequest,
  UpdateContractTemplateRequest,
  UserContract,
} from "../types/contract";
import { buildAuthHeaders, requestApiData } from "./http";

const jsonHeaders = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

const buildPageQuery = (page: number, size: number) =>
  new URLSearchParams({ page: String(page), size: String(size) }).toString();

const getJson = <TResponse>(endpoint: string, errorMessage: string) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "GET",
      headers: buildAuthHeaders({ Accept: "application/json" }),
      credentials: "include",
    },
    errorMessage,
  );

const postJson = <TResponse>(
  endpoint: string,
  payload: object,
  errorMessage: string,
) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "POST",
      headers: buildAuthHeaders(jsonHeaders),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

const putJson = <TResponse>(
  endpoint: string,
  payload: object,
  errorMessage: string,
) =>
  requestApiData<TResponse>(
    endpoint,
    {
      method: "PUT",
      headers: buildAuthHeaders(jsonHeaders),
      credentials: "include",
      body: JSON.stringify(payload),
    },
    errorMessage,
  );

export const getContractTemplates = async (
  page = 0,
  size = 20,
): Promise<PageResponse<ContractTemplate>> =>
  getJson<PageResponse<ContractTemplate>>(
    `${API_ENDPOINTS.contracts.templates}?${buildPageQuery(page, size)}`,
    "Không thể tải danh sách contract template",
  );

export const createContractTemplate = async (
  payload: CreateContractTemplateRequest,
): Promise<ContractTemplate> =>
  postJson<ContractTemplate>(
    API_ENDPOINTS.contracts.templates,
    payload,
    "Không thể tạo contract template",
  );

export const updateContractTemplate = async (
  templateId: number | string,
  payload: UpdateContractTemplateRequest,
): Promise<ContractTemplate> =>
  putJson<ContractTemplate>(
    API_ENDPOINTS.contracts.templateDetail(templateId),
    payload,
    "Không thể cập nhật contract template",
  );

export const generateContract = async (
  payload: GenerateContractRequest,
): Promise<ContractGenerationJob> =>
  postJson<ContractGenerationJob>(
    API_ENDPOINTS.contracts.generate,
    payload,
    "Không thể tạo job sinh hợp đồng",
  );

export const saveContract = async (
  payload: SaveContractRequest,
): Promise<UserContract> =>
  postJson<UserContract>(
    API_ENDPOINTS.contracts.base,
    payload,
    "Không thể lưu hợp đồng",
  );

export const getMyContracts = async (
  page = 0,
  size = 20,
): Promise<PageResponse<UserContract>> =>
  getJson<PageResponse<UserContract>>(
    `${API_ENDPOINTS.contracts.my}?${buildPageQuery(page, size)}`,
    "Không thể tải danh sách hợp đồng của tôi",
  );

export const getContract = async (contractId: string): Promise<UserContract> =>
  getJson<UserContract>(
    API_ENDPOINTS.contracts.detail(contractId),
    "Không thể tải chi tiết hợp đồng",
  );

export const getContractVersions = async (
  contractId: string,
): Promise<ContractVersion[]> =>
  getJson<ContractVersion[]>(
    API_ENDPOINTS.contracts.versions(contractId),
    "Không thể tải lịch sử phiên bản hợp đồng",
  );

export const revertContractVersion = async (
  contractId: string,
  versionNo: number,
  payload: RevertContractVersionRequest,
): Promise<UserContract> =>
  postJson<UserContract>(
    API_ENDPOINTS.contracts.revertVersion(contractId, versionNo),
    payload,
    "Không thể khôi phục phiên bản hợp đồng",
  );
