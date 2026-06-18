import { type ReactNode } from 'react';
import { cn } from '../../utils/cn';

export interface DataTableColumn<T> {
  header: ReactNode;
  cell: (row: T) => ReactNode;
  className?: string;
  headerClassName?: string;
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  data: T[];
  getRowKey: (row: T) => string;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({ columns, data, getRowKey, emptyMessage = 'No records found.', onRowClick }: DataTableProps<T>) {
  return (
    <div className="overflow-hidden rounded-xl border border-legal-border bg-white dark:border-slate-700 dark:bg-slate-900">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-legal-border text-sm dark:divide-slate-700">
          <thead className="bg-surface-container-low dark:bg-slate-800">
            <tr>
              {columns.map((column, index) => (
                <th
                  key={index}
                  scope="col"
                  className={cn('px-md py-sm text-left text-label-md font-bold uppercase tracking-wider text-on-surface-variant dark:text-slate-400', column.headerClassName)}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-legal-border dark:divide-slate-800">
            {data.map((row) => (
              <tr
                key={getRowKey(row)}
                className={cn(onRowClick && 'cursor-pointer hover:bg-surface-container-low dark:hover:bg-slate-800')}
                onClick={() => onRowClick?.(row)}
              >
                {columns.map((column, index) => (
                  <td key={index} className={cn('px-md py-md align-middle text-on-surface dark:text-slate-100', column.className)}>
                    {column.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data.length === 0 && <div className="p-xl text-center text-sm text-on-surface-variant dark:text-slate-400">{emptyMessage}</div>}
    </div>
  );
}
