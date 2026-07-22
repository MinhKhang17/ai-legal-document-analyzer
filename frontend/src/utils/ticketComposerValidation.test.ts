import { describe, expect, it } from 'vitest';
import { getMissingExpertTicketFields } from './ticketComposerValidation';

describe('getMissingExpertTicketFields', () => {
  it('does not report title or description when they were entered', () => {
    expect(getMissingExpertTicketFields({
      title: 'Rà soát điều khoản bồi thường',
      description: 'Tôi cần hỗ trợ đánh giá Điều 9.1',
      legalIssueCategory: '',
      userExpectedOutcome: '',
    })).toEqual(['legalIssueCategory', 'userExpectedOutcome']);
  });

  it('accepts a complete expert ticket form', () => {
    expect(getMissingExpertTicketFields({
      title: 'Rà soát điều khoản bồi thường',
      description: 'Tôi cần hỗ trợ đánh giá Điều 9.1',
      legalIssueCategory: 'Tranh chấp hợp đồng thuê',
      userExpectedOutcome: 'Đề xuất nội dung sửa đổi',
    })).toEqual([]);
  });
});
