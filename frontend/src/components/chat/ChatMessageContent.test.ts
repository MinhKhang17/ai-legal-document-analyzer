import { describe, expect, it } from 'vitest';
import { normalizeChatMarkdown } from './ChatMessageContent';

describe('normalizeChatMarkdown', () => {
  it('removes legacy inline download links but preserves the citation', () => {
    const input = 'Chứng cứ [[KB-2 📥 Tải về]](/api/v1/workspaces/ws-1/documents/system/download?filename=law.pdf).';

    expect(normalizeChatMarkdown(input)).toBe('Chứng cứ (Nguồn: tài liệu pháp lý của hệ thống).');
  });

  it('repairs citations and separates numbered items returned on one line', () => {
    const normalized = normalizeChatMarkdown('Rủi ro USER-2] 1. Mức bồi thường thấp 2. Miễn trừ bất hợp lý');

    expect(normalized).toContain('(Nguồn: hợp đồng/tài liệu người dùng đã đính kèm)');
    expect(normalized).not.toMatch(/\[(?:USER|KB)-\d+\]/);
    expect(normalized).toContain('\n1. Mức bồi thường thấp\n2. Miễn trừ bất hợp lý');
  });

  it('shows each readable document source only once', () => {
    const normalized = normalizeChatMarkdown(
      'Rủi ro cao (Nguồn hợp đồng/tài liệu người dùng: “hop-dong.docx”), không nên ký '
      + '(Nguồn hợp đồng/tài liệu người dùng: “hop-dong.docx”). Tuân thủ pháp luật '
      + '(Nguồn tài liệu hệ thống: “84.2015.QH13”), bảo đảm an toàn '
      + '(Nguồn tài liệu hệ thống: “84.2015.QH13”).',
    );

    expect(normalized.match(/hop-dong\.docx/g)).toHaveLength(1);
    expect(normalized.match(/84\.2015\.QH13/g)).toHaveLength(1);
    expect(normalized).not.toContain(' ,');
    expect(normalized).not.toContain(' .');
  });
});
