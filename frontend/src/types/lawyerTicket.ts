import type {
  LegalTicket,
  LegalTicketMessage,
  PageResponse,
} from "./legalTicket";

export type LawyerTicket = LegalTicket;

export type LawyerTicketPageResponse = PageResponse<LawyerTicket>;

export type LawyerTicketDetail = LegalTicket;

export type LawyerTicketMessage = LegalTicketMessage;

export interface CreateLawyerTicketMessageRequest {
  message: string;
}

export interface CreateLawyerTicketMessageResponse {
  ticketId: string;
  messageId: string;
  message: string;
}

export interface LawyerTicketFile {
  documentId: string;
  originalFileName: string;
  storedFileName: string;
  filePath: string;
  fileType: string;
  fileSize: number;
  documentPurpose: string;
  visibilityScope: string;
  uploadedAt: string;
}

export interface UploadLawyerTicketFileRequest {
  uploadedById: number;
  originalFileName: string;
  fileType: string;
  contentBase64: string;
  visibilityScope: "CUSTOMER" | "LAWYER" | "ADMIN";
}

export interface CloseLawyerTicketRequest {
  feedback: string;
}

export interface RequestMoreInfoLawyerTicketRequest {
  message: string;
}

export interface ResolveLawyerTicketRequest {
  expert_answer: string;
  expert_internal_note?: string | null;
}
