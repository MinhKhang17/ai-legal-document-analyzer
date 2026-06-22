import { Fragment, type ReactNode } from "react";
import { cn } from "../../utils/cn";

type InlineToken = { type: "text" | "bold"; value: string };

function parseInline(text: string): InlineToken[] {
  const tokens: InlineToken[] = [];
  const regex = /\*\*(.+?)\*\*/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      tokens.push({ type: "text", value: text.slice(lastIndex, match.index) });
    }
    tokens.push({ type: "bold", value: match[1] });
    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    tokens.push({ type: "text", value: text.slice(lastIndex) });
  }

  return tokens.length > 0 ? tokens : [{ type: "text", value: text }];
}

function renderInline(text: string): ReactNode[] {
  return parseInline(text).map((token, index) =>
    token.type === "bold" ? (
      <strong key={`${token.type}-${index}`} className="font-semibold">
        {token.value}
      </strong>
    ) : (
      <Fragment key={`${token.type}-${index}`}>{token.value}</Fragment>
    ),
  );
}

interface ChatMessageContentProps {
  content: string;
  className?: string;
}

export function ChatMessageContent({ content, className }: ChatMessageContentProps) {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];

  let bulletItems: string[] = [];
  let paragraphLines: string[] = [];

  const flushParagraph = () => {
    if (!paragraphLines.length) return;
    const text = paragraphLines.join(" ").trim();
    if (text) {
      blocks.push(
        <p key={`p-${blocks.length}`} className="whitespace-pre-wrap break-words">
          {renderInline(text)}
        </p>,
      );
    }
    paragraphLines = [];
  };

  const flushBullets = () => {
    if (!bulletItems.length) return;
    blocks.push(
      <ul key={`ul-${blocks.length}`} className="ml-5 list-disc space-y-1">
        {bulletItems.map((item, index) => (
          <li key={`${item}-${index}`} className="break-words">
            {renderInline(item)}
          </li>
        ))}
      </ul>,
    );
    bulletItems = [];
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    const trimmed = line.trim();

    if (!trimmed) {
      flushParagraph();
      flushBullets();
      continue;
    }

    if (/^#{1,3}\s+/.test(trimmed)) {
      flushParagraph();
      flushBullets();
      const level = trimmed.match(/^#{1,3}/)?.[0].length ?? 1;
      const headingText = trimmed.replace(/^#{1,3}\s+/, "");
      const HeadingTag = level === 1 ? "h1" : level === 2 ? "h2" : "h3";
      const headingClass =
        level === 1
          ? "text-xl font-bold"
          : level === 2
            ? "text-lg font-semibold"
            : "text-base font-semibold";

      blocks.push(
        <HeadingTag key={`h-${blocks.length}`} className={cn(headingClass, "break-words")}>
          {renderInline(headingText)}
        </HeadingTag>,
      );
      continue;
    }

    if (/^[-*]\s+/.test(trimmed)) {
      flushParagraph();
      bulletItems.push(trimmed.replace(/^[-*]\s+/, ""));
      continue;
    }

    if (/^>\s+/.test(trimmed)) {
      flushParagraph();
      flushBullets();
      blocks.push(
        <blockquote
          key={`q-${blocks.length}`}
          className="border-l-4 border-primary/60 pl-3 italic text-on-surface-variant dark:text-slate-300"
        >
          {renderInline(trimmed.replace(/^>\s+/, ""))}
        </blockquote>,
      );
      continue;
    }

    if (/^-{3,}$/.test(trimmed)) {
      flushParagraph();
      flushBullets();
      blocks.push(<hr key={`hr-${blocks.length}`} className="border-outline-variant/60 dark:border-slate-700" />);
      continue;
    }

    flushBullets();
    paragraphLines.push(trimmed);
  }

  flushParagraph();
  flushBullets();

  if (!blocks.length) {
    return <p className={className}>{content}</p>;
  }

  return <div className={cn("space-y-3", className)}>{blocks}</div>;
}
