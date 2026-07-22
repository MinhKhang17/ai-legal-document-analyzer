package com.analyzer.api.exception;

import com.analyzer.api.dto.ApiResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.HashMap;
import java.util.Map;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.exception.workspace.*;
import com.analyzer.api.exception.chat.*;
import com.analyzer.api.exception.ai.*;
import com.analyzer.api.exception.validation.*;
import com.analyzer.api.exception.auth.*;

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

        @ExceptionHandler(DisabledException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleDisabledException(DisabledException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.FORBIDDEN.value(),
                                                "Tài khoản chưa được xác thực email hoặc đã bị vô hiệu hóa. Vui lòng kiểm tra email để xác thực tài khoản."),
                                HttpStatus.FORBIDDEN);
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

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()),
                                HttpStatus.NOT_FOUND);
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleForbiddenException(ForbiddenException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.FORBIDDEN.value(), ex.getErrorCode(), ex.getMessage()),
                                HttpStatus.FORBIDDEN);
        }

        @ExceptionHandler(WorkspaceDeletedException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleWorkspaceDeletedException(WorkspaceDeletedException ex) {
                Map<String, String> data = Map.of(
                                "workspaceId", ex.getWorkspaceId(),
                                "status", ex.getStatus()
                );
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidPageException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Integer>>> handleInvalidPageException(InvalidPageException ex) {
                Map<String, Integer> data = Map.of("page", ex.getPage());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidSizeException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Integer>>> handleInvalidSizeException(InvalidSizeException ex) {
                Map<String, Integer> data = Map.of("size", ex.getSize());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidStatusException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Object>>> handleInvalidStatusException(InvalidStatusException ex) {
                Map<String, Object> data = Map.of("allowedValues", ex.getAllowedValues());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(DeletedChatSessionException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleDeletedChatSessionException(DeletedChatSessionException ex) {
                Map<String, String> data = Map.of(
                                "chatSessionId", ex.getChatSessionId(),
                                "status", ex.getStatus()
                );
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidMessageException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Object>>> handleInvalidMessageException(InvalidMessageException ex) {
                Map<String, Object> data = ex.isBlank() ? Map.of() : Map.of("maxLength", ex.getMaxLength());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidTitleException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Object>>> handleInvalidTitleException(InvalidTitleException ex) {
                Map<String, Object> data = ex.isBlank() ? Map.of() : Map.of("maxLength", ex.getMaxLength());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(NoReadyDocumentsException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, Object>>> handleNoReadyDocumentsException(NoReadyDocumentsException ex) {
                Map<String, Object> data;
                if (ex.getProcessingDocumentCount() > 0) {
                        data = Map.of(
                                "workspaceId", ex.getWorkspaceId(),
                                "readyDocumentCount", ex.getReadyDocumentCount(),
                                "processingDocumentCount", ex.getProcessingDocumentCount()
                        );
                } else {
                        data = Map.of(
                                "workspaceId", ex.getWorkspaceId(),
                                "readyDocumentCount", ex.getReadyDocumentCount()
                        );
                }
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), data),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(AiServiceUnavailableException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleAiServiceUnavailableException(AiServiceUnavailableException ex) {
                Map<String, String> data = Map.of("requestId", ex.getRequestId());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage(), data),
                                HttpStatus.SERVICE_UNAVAILABLE);
        }

        @ExceptionHandler(AiServiceTimeoutException.class)
        public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleAiServiceTimeoutException(AiServiceTimeoutException ex) {
                Map<String, String> data = Map.of("requestId", ex.getRequestId());
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.GATEWAY_TIMEOUT.value(), ex.getMessage(), data),
                                HttpStatus.GATEWAY_TIMEOUT);
        }

        @ExceptionHandler(ExpiredVerificationTokenException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleExpiredVerificationTokenException(ExpiredVerificationTokenException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()),
                                HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(InvalidRefreshTokenException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleInvalidRefreshTokenException(
                        InvalidRefreshTokenException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()),
                                HttpStatus.UNAUTHORIZED);
        }

        @ExceptionHandler(ConflictException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleConflictException(ConflictException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.CONFLICT.value(), ex.getErrorCode(), ex.getMessage()),
                                HttpStatus.CONFLICT);
        }

        @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
        public ResponseEntity<ApiResponseDTO<Void>> handleFinancialConcurrency(Exception ex) {
                return new ResponseEntity<>(ApiResponseDTO.error(HttpStatus.CONFLICT.value(), "CONCURRENT_MODIFICATION"), HttpStatus.CONFLICT);
        }

        @ExceptionHandler(TooManyRequestsException.class)
        public ResponseEntity<ApiResponseDTO<Void>> handleTooManyRequestsException(TooManyRequestsException ex) {
                return new ResponseEntity<>(
                                ApiResponseDTO.error(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()),
                                HttpStatus.TOO_MANY_REQUESTS);
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
