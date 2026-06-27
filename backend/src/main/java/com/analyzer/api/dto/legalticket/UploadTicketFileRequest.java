package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.DocumentVisibilityScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTicketFileRequest {

    @NotNull
    private Long uploadedById;

    @NotBlank
    private String originalFileName;

    @NotBlank
    private String fileType;

    @NotBlank
    private String contentBase64;

    private DocumentVisibilityScope visibilityScope;
}
