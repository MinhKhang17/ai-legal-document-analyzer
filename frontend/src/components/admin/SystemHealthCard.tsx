import { Activity, Database, HardDrive, MemoryStick, Server } from 'lucide-react';
import { Card } from '../common/Card';
import { ProgressBar } from '../common/ProgressBar';
import { StatusBadge } from '../common/StatusBadge';
import type { SystemService } from '../../types/system';

const iconByService: Record<string, typeof Server> = {
  ollama: MemoryStick,
  llama: Activity,
  pinecone: Database,
  storage: HardDrive,
};

interface SystemHealthCardProps {
  service: SystemService;
}

export function SystemHealthCard({ service }: SystemHealthCardProps) {
  const Icon = iconByService[service.id] ?? Server;

  return (
    <Card className="bg-white dark:bg-slate-900">
      <div className="flex items-start justify-between gap-md">
        <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
          <Icon className="h-6 w-6" aria-hidden="true" />
        </div>
        <StatusBadge status={service.status} />
      </div>
      <p className="label-uppercase mt-lg">{service.name}</p>
      <p className="mt-xs text-2xl font-bold text-on-surface dark:text-slate-100">{service.value}</p>
      <p className="mt-md text-sm text-on-surface-variant dark:text-slate-400">{service.detail}</p>
      {typeof service.metric === 'number' && <ProgressBar className="mt-md" value={service.metric} />}
    </Card>
  );
}
