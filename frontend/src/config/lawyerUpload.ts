export const LAWYER_UPLOAD_MAX_BYTES = 5 * 1024 * 1024;
export const LAWYER_UPLOAD_ACCEPT = '.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document';

export type LawyerUploadValidation = { valid: true } | { valid: false; messageKey: string };
export const validateLawyerUpload = (file?: File): LawyerUploadValidation => {
  if (!file) return { valid: false, messageKey: 'validation.file.required' };
  if (file.size === 0) return { valid: false, messageKey: 'validation.file.empty' };
  if (file.size > LAWYER_UPLOAD_MAX_BYTES) return { valid: false, messageKey: 'validation.file.lawyerMaximum' };
  const extension = file.name.split('.').pop()?.toLowerCase();
  const extensionAllowed = extension === 'pdf' || extension === 'docx';
  const mimeAllowed = !file.type || file.type === 'application/pdf' || file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  return extensionAllowed && mimeAllowed ? { valid: true } : { valid: false, messageKey: 'validation.file.pdfDocxOnly' };
};
