import { translate } from '../utils/i18n';

export const SUPPORTED_CONTRACT_TYPES = [
  { value: 'RENTAL', labelKey: 'contractType.RENTAL' },
  { value: 'PART_TIME_EMPLOYMENT', labelKey: 'contractType.PART_TIME_EMPLOYMENT' },
  { value: 'INTERNSHIP', labelKey: 'contractType.INTERNSHIP' },
  { value: 'COLLABORATOR', labelKey: 'contractType.COLLABORATOR' },
  { value: 'FREELANCE_SERVICE', labelKey: 'contractType.FREELANCE_SERVICE' },
  { value: 'SMALL_ASSET_SALE', labelKey: 'contractType.SMALL_ASSET_SALE' },
  { value: 'PERSONAL_LOAN', labelKey: 'contractType.PERSONAL_LOAN' },
] as const;

export type SupportedContractType = (typeof SUPPORTED_CONTRACT_TYPES)[number]['value'];

export const getSupportedContractTypeLabel = (value: string | null | undefined, language: 'en' | 'vi') => {
  const item = SUPPORTED_CONTRACT_TYPES.find((type) => type.value === value);
  return item ? translate(language, item.labelKey) : null;
};

export const supportedContractScopeText = (language: 'en' | 'vi') =>
  translate(language, 'contracts.scopeNotice');
