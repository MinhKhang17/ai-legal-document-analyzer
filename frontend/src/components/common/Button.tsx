import { type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../utils/cn';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'gold' | 'subtle';
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-white shadow-navy hover:bg-primary-container focus-visible:outline-primary disabled:bg-outline-variant disabled:text-on-surface-variant',
  secondary:
    'border border-legal-border bg-white text-primary hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-inverse-primary dark:hover:bg-slate-800',
  ghost:
    'text-on-surface-variant hover:bg-surface-container-low hover:text-primary dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-inverse-primary',
  danger: 'bg-error text-white hover:bg-red-800 disabled:bg-red-200',
  gold: 'bg-secondary-container text-on-surface hover:bg-secondary-container/80 dark:bg-accent-gold dark:text-slate-950',
  subtle:
    'bg-surface-container-low text-on-surface hover:bg-surface-container-high dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-sm py-xs text-label-md',
  md: 'px-md py-sm text-sm font-semibold',
  lg: 'px-lg py-md text-base font-semibold',
  icon: 'h-10 w-10 p-0',
};

export function Button({
  className,
  variant = 'primary',
  size = 'md',
  leftIcon,
  rightIcon,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center gap-sm rounded-lg transition duration-200 active:scale-[0.98] disabled:cursor-not-allowed',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {leftIcon}
      {children}
      {rightIcon}
    </button>
  );
}
