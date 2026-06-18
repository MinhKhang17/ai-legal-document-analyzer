package com.analyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponseDTO<T> {

    @Schema(description = "HTTP status code", example = "200")
    private int code;

    @Schema(description = "Response message", example = "Success")
    private String message;

    @Schema(description = "Response data payload")
    private T data;

    // --- Factory methods for common responses ---

    public static <T> ApiResponseDTO<T> success(T data) {
        return ApiResponseDTO.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponseDTO<T> success(String message, T data) {
        return ApiResponseDTO.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponseDTO<T> created(T data) {
        return ApiResponseDTO.<T>builder()
                .code(201)
                .message("Created successfully")
                .data(data)
                .build();
    }

    public static <T> ApiResponseDTO<T> created(String message, T data) {
        return ApiResponseDTO.<T>builder()
                .code(201)
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponseDTO<Void> success(String message) {
        return ApiResponseDTO.<Void>builder()
                .code(200)
                .message(message)
                .build();
    }

    public static ApiResponseDTO<Void> error(int code, String message) {
        return ApiResponseDTO.<Void>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ApiResponseDTO<T> error(int code, String message, T data) {
        return ApiResponseDTO.<T>builder()
                .code(code)
                .message(message)
                .data(data)
                .build();
    }
}
