package com.analyzer.api.exception;

import com.analyzer.api.dto.ApiResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(DocumentProcessingDispatchException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleDocumentProcessingDispatchException(
                        DocumentProcessingDispatchException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()),
                                HttpStatus.BAD_GATEWAY);
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleBadCredentials(BadCredentialsException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.UNAUTHORIZED.value(), "Email hoặc mật khẩu không đúng"),
                                HttpStatus.UNAUTHORIZED);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleValidationExceptions(
                        MethodArgumentNotValidException ex) {
                Map<String, String> errors = new HashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), "Validation failed", errors),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleRuntimeException(RuntimeException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleGlobalException(Exception ex) {
                ex.printStackTrace();
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                "An unexpected error occurred"),
                                HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
