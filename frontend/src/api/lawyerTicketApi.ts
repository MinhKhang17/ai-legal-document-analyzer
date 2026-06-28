export {
  getMyLawyerTickets,
  getLawyerTicketDetail,
  getLawyerTicketMessages,
  sendLawyerTicketMessage,
  getLawyerTicketFiles,
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
} from "../types/lawyerTicket";