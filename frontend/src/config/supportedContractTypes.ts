export const SUPPORTED_CONTRACT_TYPES = [
  { value: 'RENTAL', vi: 'Thuê phòng hoặc thuê nhà', en: 'Room or house rental' },
  { value: 'PART_TIME_EMPLOYMENT', vi: 'Lao động bán thời gian', en: 'Part-time employment' },
  { value: 'INTERNSHIP', vi: 'Thực tập', en: 'Internship' },
  { value: 'COLLABORATOR', vi: 'Cộng tác viên', en: 'Collaborator' },
  { value: 'FREELANCE_SERVICE', vi: 'Dịch vụ hoặc freelance quy mô nhỏ', en: 'Small service or freelance' },
  { value: 'SMALL_ASSET_SALE', vi: 'Mua bán tài sản cá nhân nhỏ (laptop, điện thoại, xe)', en: 'Small personal asset sale (laptop, phone, vehicle)' },
  { value: 'PERSONAL_LOAN', vi: 'Vay cá nhân đơn giản', en: 'Simple personal loan' },
] as const;

export type SupportedContractType = (typeof SUPPORTED_CONTRACT_TYPES)[number]['value'];

export const getSupportedContractTypeLabel = (value: string | null | undefined, language: 'en' | 'vi') => {
  const item = SUPPORTED_CONTRACT_TYPES.find((type) => type.value === value);
  return item ? (language === 'vi' ? item.vi : item.en) : null;
};

export const supportedContractScopeText = (language: 'en' | 'vi') =>
  language === 'vi'
    ? 'LexiGuard AI chỉ hỗ trợ rà soát hợp đồng cá nhân đơn giản. Sản phẩm không thay thế luật sư hoặc tư vấn pháp lý chuyên nghiệp.'
    : 'LexiGuard AI reviews simple personal contracts only. It does not replace a lawyer or professional legal advice.';
