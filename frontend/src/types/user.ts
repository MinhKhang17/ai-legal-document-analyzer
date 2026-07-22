import type { Status } from './status';

export interface WorkspaceUser {
  id: string;
  name: string;
  email: string;
  role: string;
  status: Status;
  initials: string;
}
