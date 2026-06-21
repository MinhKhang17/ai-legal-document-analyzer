export {
  createWorkspace,
  getWorkspaceDetail,
  getWorkspaceDocuments,
  getWorkspaces,
  uploadDocument,
} from "../services/workspace.service";

export type {
  CreateWorkspaceRequest,
  Document,
  Workspace,
} from "../types/workspace";