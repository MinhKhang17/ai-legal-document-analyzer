export {
  createWorkspace,
  getWorkspaceDetail,
  getWorkspaceDocuments,
  getWorkspaces,
  uploadDocument,
  downloadWorkspaceDocument,
  confirmDocumentContractType,
} from "../services/workspace.service";

export type {
  CreateWorkspaceRequest,
  Document,
  Workspace,
} from "../types/workspace";
