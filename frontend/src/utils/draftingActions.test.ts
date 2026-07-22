import { describe, expect, it, vi } from 'vitest';
import { CHATGPT_URL, copyDraftingPrompt, openChatGPT } from './draftingActions';

describe('drafting external actions', () => {
  it('copies only after the user invokes the copy action', async () => {
    const writeText = vi.fn(async () => undefined);
    expect(writeText).not.toHaveBeenCalled();

    await copyDraftingPrompt('safe editable prompt', writeText);

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith('safe editable prompt');
  });

  it('opens a blank ChatGPT tab without putting the prompt in the URL', () => {
    const openWindow = vi.fn(
      (_url?: string | URL, _target?: string, _features?: string): Window | null => null,
    );

    openChatGPT(openWindow);

    expect(openWindow).toHaveBeenCalledWith(CHATGPT_URL, '_blank', 'noopener,noreferrer');
    expect(openWindow.mock.calls[0][0]).not.toContain('safe editable prompt');
  });
});
