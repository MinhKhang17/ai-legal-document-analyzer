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

export function sanitizeAndExtractContent(text: string): string {
  if (!text) return "";

  let cleaned = text.trim();

  // 1. Clean markdown fences:
  // ```json or ``` at the start
  if (cleaned.startsWith("```")) {
    cleaned = cleaned.replace(/^```[a-zA-Z0-9]*\s*/, "");
    // ``` at the end
    if (cleaned.endsWith("```")) {
      cleaned = cleaned.slice(0, -3).trim();
    }
  }

  // 2. Try to parse as JSON if it looks like JSON
  if (
    (cleaned.startsWith("{") && cleaned.endsWith("}")) ||
    (cleaned.startsWith("[") && cleaned.endsWith("]"))
  ) {
    try {
      const parsed = JSON.parse(cleaned);
      if (parsed && typeof parsed === "object") {
        const candidate = parsed.answer || parsed.response || parsed.content || parsed.message;
        if (candidate && typeof candidate === "string") {
          return sanitizeAndExtractContent(candidate);
        }
        // If it's a valid JSON dict but has no specific string field, find the first string value
        for (const value of Object.values(parsed)) {
          if (typeof value === "string" && value.trim()) {
            return sanitizeAndExtractContent(value);
          }
        }
      }
    } catch {
      // If parsing fails, fall back to regex extraction for unclosed/malformed JSON
    }
  }

  // If it's not valid JSON, but has something like "answer": "...", try extracting with regex
  if (cleaned.includes('"answer"') || cleaned.includes('"response"') || cleaned.includes('"content"')) {
    const regex = /"(?:answer|response|content)"\s*:\s*"((?:[^"\\]|\\.)*)"/;
    const match = regex.exec(cleaned);
    if (match) {
      try {
        const parsedStr = JSON.parse(`"${match[1]}"`);
        return sanitizeAndExtractContent(parsedStr);
      } catch {
        return sanitizeAndExtractContent(match[1]);
      }
    }

    // Try unclosed key regex: `"answer": "..."` but unclosed at the end
    const unclosedRegex = /"(?:answer|response|content)"\s*:\s*"\s*([\s\S]*)/;
    const unclosedMatch = unclosedRegex.exec(cleaned);
    if (unclosedMatch) {
      let val = unclosedMatch[1].trim();
      val = val.replace(/"\s*,\s*"[^"]*"\s*:\s*[\s\S]*$/, ""); // remove subsequent fields
      val = val.replace(/"\s*\}\s*$/, "");
      val = val.replace(/"\s*$/, "");
      try {
        const parsedStr = JSON.parse(`"${val}"`);
        return sanitizeAndExtractContent(parsedStr);
      } catch {
        return sanitizeAndExtractContent(val);
      }
    }
  }

  // Double quotes strip if surrounding
  if (
    cleaned.length >= 2 &&
    ((cleaned.startsWith('"') && cleaned.endsWith('"')) ||
      (cleaned.startsWith("'") && cleaned.endsWith("'")))
  ) {
    cleaned = cleaned.slice(1, -1).trim();
  }

  // Remove final trailing/leading code blocks or ticks if they surround the content
  cleaned = cleaned.replace(/^```[a-zA-Z0-9]*\s*/, "").replace(/```$/, "");

  return cleaned.trim();
}

export function ChatMessageContent({ content, className }: ChatMessageContentProps) {
  const sanitizedContent = sanitizeAndExtractContent(content);
  const lines = sanitizedContent.replace(/\r\n/g, "\n").split("\n");
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
