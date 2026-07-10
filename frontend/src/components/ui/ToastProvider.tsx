import {
  AlertTriangle,
  CheckCircle2,
  Info,
  X,
  XCircle,
} from "lucide-react";
import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useI18n } from "../../hooks/useI18n";
import { cn } from "../../utils/cn";

export type ToastVariant = "success" | "error" | "info" | "warning";

export interface ToastInput {
  type?: ToastVariant;
  title?: string;
  message: string;
  durationMs?: number;
  dedupeKey?: string;
}

export interface ToastContextValue {
  showToast: (toast: ToastInput) => void;
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
}

interface ToastItem extends Required<Pick<ToastInput, "type" | "message">> {
  id: string;
  title?: string;
}

export const ToastContext = createContext<ToastContextValue | undefined>(
  undefined,
);

const toastStyles: Record<
  ToastVariant,
  {
    icon: typeof CheckCircle2;
    className: string;
    iconClassName: string;
  }
> = {
  success: {
    icon: CheckCircle2,
    className:
      "border-emerald-200 bg-emerald-50 text-emerald-950 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-100",
    iconClassName: "text-emerald-700 dark:text-emerald-300",
  },
  error: {
    icon: XCircle,
    className:
      "border-red-200 bg-red-50 text-red-950 dark:border-red-900 dark:bg-red-950 dark:text-red-100",
    iconClassName: "text-red-700 dark:text-red-300",
  },
  info: {
    icon: Info,
    className:
      "border-sky-200 bg-sky-50 text-sky-950 dark:border-sky-900 dark:bg-sky-950 dark:text-sky-100",
    iconClassName: "text-sky-700 dark:text-sky-300",
  },
  warning: {
    icon: AlertTriangle,
    className:
      "border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100",
    iconClassName: "text-amber-700 dark:text-amber-300",
  },
};

const makeToastId = () => {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }

  return `toast-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timersRef = useRef(new Map<string, number>());
  const recentToastRef = useRef<{ key: string; time: number } | null>(null);

  const removeToast = useCallback((toastId: string) => {
    const timer = timersRef.current.get(toastId);
    if (timer) {
      window.clearTimeout(timer);
      timersRef.current.delete(toastId);
    }

    setToasts((previous) => previous.filter((toast) => toast.id !== toastId));
  }, []);

  const showToast = useCallback(
    ({
      type = "info",
      title,
      message,
      durationMs = 4500,
      dedupeKey,
    }: ToastInput) => {
      const normalizedMessage = message.trim();
      const normalizedTitle = title?.trim();

      if (!normalizedMessage) {
        return;
      }

      const key = dedupeKey ?? `${type}:${normalizedTitle ?? ""}:${normalizedMessage}`;
      const now = Date.now();
      const recentToast = recentToastRef.current;

      if (recentToast?.key === key && now - recentToast.time < 1000) {
        return;
      }

      recentToastRef.current = { key, time: now };

      const toast: ToastItem = {
        id: makeToastId(),
        type,
        title: normalizedTitle,
        message: normalizedMessage,
      };

      setToasts((previous) => [...previous, toast].slice(-5));

      if (durationMs > 0) {
        const timer = window.setTimeout(() => removeToast(toast.id), durationMs);
        timersRef.current.set(toast.id, timer);
      }
    },
    [removeToast],
  );

  useEffect(
    () => () => {
      timersRef.current.forEach((timer) => window.clearTimeout(timer));
      timersRef.current.clear();
    },
    [],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      showToast,
      success: (message, title) => showToast({ type: "success", message, title }),
      error: (message, title) => showToast({ type: "error", message, title }),
      info: (message, title) => showToast({ type: "info", message, title }),
      warning: (message, title) =>
        showToast({ type: "warning", message, title }),
    }),
    [showToast],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed right-md top-md z-50 flex w-[min(420px,calc(100vw-2rem))] flex-col gap-sm"
        aria-live="polite"
        aria-relevant="additions text"
      >
        {toasts.map((toast) => {
          const style = toastStyles[toast.type];
          const Icon = style.icon;

          return (
            <div
              key={toast.id}
              role={toast.type === "error" ? "alert" : "status"}
              className={cn(
                "pointer-events-auto flex items-start gap-sm rounded-lg border p-md shadow-raised",
                style.className,
              )}
            >
              <Icon className={cn("mt-0.5 h-5 w-5 shrink-0", style.iconClassName)} />
              <div className="min-w-0 flex-1">
                {toast.title && (
                  <p className="text-sm font-bold leading-5">{toast.title}</p>
                )}
                <p className="text-sm leading-5">{toast.message}</p>
              </div>
              <button
                type="button"
                className="rounded-md p-1 opacity-70 transition hover:bg-black/5 hover:opacity-100 dark:hover:bg-white/10"
                aria-label={t("toast.dismissNotification")}
                onClick={() => removeToast(toast.id)}
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}
