package com.analyzer.api.service.lawyer;

import com.analyzer.api.dto.legalticket.TicketFileResponse;
import com.analyzer.api.dto.legalticket.UploadTicketFileRequest;

import java.util.List;

public interface TicketFileService {

    List<TicketFileResponse> listFiles(String ticketId, Long lawyerId);

    TicketFileResponse uploadFile(String ticketId, Long lawyerId, UploadTicketFileRequest request);
}
