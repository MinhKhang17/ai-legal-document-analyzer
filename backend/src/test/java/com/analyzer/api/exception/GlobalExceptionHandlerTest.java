package com.analyzer.api.exception;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.exception.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void conflictResponseContainsStableMachineReadableErrorCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponseDTO<Void>> response = handler.handleConflictException(
                new ConflictException("WORKSPACE_LIMIT_REACHED", "Workspace limit reached for the current plan"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().errorCode()).isEqualTo("WORKSPACE_LIMIT_REACHED");
        assertThat(response.getBody().message()).isEqualTo("Workspace limit reached for the current plan");
    }
}
