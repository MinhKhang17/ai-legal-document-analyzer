export {
  createWorkspace,
  getWorkspaceDetail,
  getWorkspaceDocuments,
  getWorkspaces,
  uploadDocument,
  downloadWorkspaceDocument,
} from "../services/workspace.service";

export { uploadDocumentAxios } from "../services/upload.service";

export type {
  CreateWorkspaceRequest,
  Document,
  Workspace,
} from "../types/workspace";
