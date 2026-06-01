import type { Status } from './status';

export interface AuditLog {
  id: string;
  timestamp: string;
  user: string;
  initials: string;
  action: string;
  documentRef: string;
  latency: string;
  status: Status;
  ipAddress: string;
  tokens: number;
  model: string;
}
