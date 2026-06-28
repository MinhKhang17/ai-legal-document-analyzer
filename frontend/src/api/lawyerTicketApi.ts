export {
  getMyLawyerTickets,
  getLawyerTicketDetail,
  getLawyerTicketMessages,
  sendLawyerTicketMessage,
} from "../services/lawyerTicket.service";

export type {
  LawyerTicket,
  LawyerTicketDetail,
  LawyerTicketPageResponse,
   LawyerTicketMessage,
  CreateLawyerTicketMessageRequest,
  CreateLawyerTicketMessageResponse,
} from "../types/lawyerTicket";