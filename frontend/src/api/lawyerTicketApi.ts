export {
  getMyLawyerTickets,
  getProposedLawyerTickets,
  getLawyerTicketDetail,
  getLawyerTicketMessages,
  requestMoreInfoLawyerTicket,
  resolveLawyerTicket,
  sendLawyerTicketMessage,
  startReviewLawyerTicket,
  getLawyerTicketFiles,
  downloadLawyerTicketFile,
  uploadLawyerTicketFile,
  closeLawyerTicket,
} from "../services/lawyerTicket.service";

export type {
  LawyerTicket,
  LawyerTicketDetail,
  LawyerTicketPageResponse,
  LawyerTicketMessage,
  CreateLawyerTicketMessageRequest,
  CreateLawyerTicketMessageResponse,
  LawyerTicketFile,
  UploadLawyerTicketFileRequest,
  CloseLawyerTicketRequest,
  RequestMoreInfoLawyerTicketRequest,
  ResolveLawyerTicketRequest,
} from "../types/lawyerTicket";
