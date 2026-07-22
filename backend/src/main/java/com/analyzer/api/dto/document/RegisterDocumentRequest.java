package com.analyzer.api.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDocumentRequest {
    @NotBlank(message = "workspaceId khong duoc de trong")
    private String workspaceId;

    @NotBlank(message = "userId khong duoc de trong")
    private String userId;

    @NotBlank(message = "originalFileName khong duoc de trong")
    private String originalFileName;

    @NotBlank(message = "storedFileName khong duoc de trong")
    private String storedFileName;

    @NotBlank(message = "filePath khong duoc de trong")
    private String filePath;

    @NotNull(message = "fileSize khong duoc de trong")
    @PositiveOrZero(message = "fileSize khong duoc am")
    private Long fileSize;
}
