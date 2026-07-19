type ProgressiveRenderOptions = {
  delayMs?: number;
  maxChunks?: number;
};

const waitForNextChunk = (delayMs: number, signal: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException("Aborted", "AbortError"));
      return;
    }

    const handleAbort = () => {
      window.clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    };
    const timer = window.setTimeout(() => {
      signal.removeEventListener("abort", handleAbort);
      resolve();
    }, delayMs);
    signal.addEventListener("abort", handleAbort, { once: true });
  });

/** Progressive-render fallback for the current non-streaming JSON chat API. */
export async function simulateStreaming(
  fullText: string,
  onText: (visibleText: string) => void,
  signal: AbortSignal,
  options: ProgressiveRenderOptions = {},
) {
  const tokens = fullText.match(/\S+\s*/g) ?? [];
  if (tokens.length === 0) {
    onText(fullText);
    return;
  }

  const maxChunks = options.maxChunks ?? 140;
  const chunkSize = Math.max(5, Math.ceil(tokens.length / maxChunks));
  const delayMs = options.delayMs ?? 28;
  let visibleText = "";

  for (let index = 0; index < tokens.length; index += chunkSize) {
    if (signal.aborted) throw new DOMException("Aborted", "AbortError");
    visibleText += tokens.slice(index, index + chunkSize).join("");
    onText(visibleText);
    if (index + chunkSize < tokens.length) {
      await waitForNextChunk(delayMs, signal);
    }
  }
}
