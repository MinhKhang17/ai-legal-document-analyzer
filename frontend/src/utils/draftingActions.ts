export const CHATGPT_URL = 'https://chatgpt.com/';

export const copyDraftingPrompt = (
  prompt: string,
  writeText: (value: string) => Promise<void> = (value) => navigator.clipboard.writeText(value),
) => writeText(prompt);

export const openChatGPT = (
  openWindow: (url?: string | URL, target?: string, features?: string) => Window | null = window.open,
) => openWindow(CHATGPT_URL, '_blank', 'noopener,noreferrer');
