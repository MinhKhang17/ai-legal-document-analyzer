export const DOCUMENT_UPLOAD_CONFIG = {
  maxFiles: 1,
  maxFileSizeBytes: 50 * 1024 * 1024,
  extensions: ["pdf", "docx"],
  mimeTypes: [
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  ],
} as const;

export const DOCUMENT_UPLOAD_ACCEPT = [
  ...DOCUMENT_UPLOAD_CONFIG.extensions.map((extension) => `.${extension}`),
  ...DOCUMENT_UPLOAD_CONFIG.mimeTypes,
].join(",");

export type FileValidationResult = { valid: true } | { valid: false; messageKey: string };

export const validateDocumentFiles = (files: FileList | File[]): FileValidationResult => {
  const candidates = Array.from(files);
  if (candidates.length === 0) return { valid: false, messageKey: "validation.file.required" };
  if (candidates.length > DOCUMENT_UPLOAD_CONFIG.maxFiles) return { valid: false, messageKey: "validation.file.singleOnly" };

  const file = candidates[0];
  if (file.size === 0) return { valid: false, messageKey: "validation.file.empty" };
  if (file.size > DOCUMENT_UPLOAD_CONFIG.maxFileSizeBytes) return { valid: false, messageKey: "validation.file.documentMaximum" };

  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  const extensionAllowed = DOCUMENT_UPLOAD_CONFIG.extensions.includes(extension as "pdf" | "docx");
  const mimeAllowed = !file.type || DOCUMENT_UPLOAD_CONFIG.mimeTypes.includes(file.type as typeof DOCUMENT_UPLOAD_CONFIG.mimeTypes[number]);
  if (!extensionAllowed || !mimeAllowed) return { valid: false, messageKey: "validation.file.pdfDocxOnly" };
  return { valid: true };
};
