import type { Status } from './status';

export interface SystemService {
  id: string;
  name: string;
  status: Status;
  value: string;
  detail: string;
  metric?: number;
}

export interface ProcessingJob {
  id: string;
  resource: string;
  description: string;
  status: Status;
  progress: number;
  errorType?: string;
  timestamp?: string;
}
