import { Search } from 'lucide-react';
import { type InputHTMLAttributes } from 'react';
import { cn } from '../../utils/cn';

interface SearchInputProps extends InputHTMLAttributes<HTMLInputElement> {
  containerClassName?: string;
}

export function SearchInput({ className, containerClassName, ...props }: SearchInputProps) {
  return (
    <div className={cn('relative', containerClassName)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline" aria-hidden="true" />
      <input
        className={cn(
          'w-full rounded-lg border border-outline-variant bg-white py-sm pl-10 pr-md text-sm text-on-surface outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100',
          className,
        )}
        type="search"
        {...props}
      />
    </div>
  );
}
