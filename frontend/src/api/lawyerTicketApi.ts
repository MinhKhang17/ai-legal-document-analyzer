export {
  getMyLawyerTickets,
  getLawyerTicketDetail,
  getLawyerTicketMessages,
  sendLawyerTicketMessage,
  getLawyerTicketFiles,
} from "../services/lawyerTicket.service";

export type {
  LawyerTicket,
  LawyerTicketDetail,
  LawyerTicketPageResponse,
   LawyerTicketMessage,
  CreateLawyerTicketMessageRequest,
  CreateLawyerTicketMessageResponse,
  LawyerTicketFile,
} from "../types/lawyerTicket";