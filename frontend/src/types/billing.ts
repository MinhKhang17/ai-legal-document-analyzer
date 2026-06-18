import type { Status } from './status';

export interface BillingUsage {
  id: string;
  label: string;
  used: number;
  limit: number;
  unit: string;
}

export interface Invoice {
  id: string;
  date: string;
  amount: number;
  status: Status;
}
