package com.analyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public record ApiResponseDTO<T>(
        @Schema(description = "HTTP status code", example = "200")
        int code,

        @Schema(description = "Response message", example = "Success")
        String message,

        @Schema(description = "Response data payload")
        T data
) {

    public static <T> ApiResponseDTO<T> success(T data) {
        return new ApiResponseDTO<>(200, "Success", data);
    }

    public static <T> ApiResponseDTO<T> success(String message, T data) {
        return new ApiResponseDTO<>(200, message, data);
    }

    public static <T> ApiResponseDTO<T> created(T data) {
        return new ApiResponseDTO<>(201, "Created successfully", data);
    }

    public static <T> ApiResponseDTO<T> created(String message, T data) {
        return new ApiResponseDTO<>(201, message, data);
    }

    public static <T> ApiResponseDTO<T> accepted(String message, T data) {
        return new ApiResponseDTO<>(202, message, data);
    }

    public static ApiResponseDTO<Void> success(String message) {
        return new ApiResponseDTO<>(200, message, null);
    }

    public static ApiResponseDTO<Void> error(int code, String message) {
        return new ApiResponseDTO<>(code, message, null);
    }

    public static <T> ApiResponseDTO<T> error(int code, String message, T data) {
        return new ApiResponseDTO<>(code, message, data);
    }
}
