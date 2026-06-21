package com.analyzer.api.client;

import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.exception.ai.AiServiceTimeoutException;
import com.analyzer.api.exception.ai.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PythonAiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    public RagQueryResponse query(RagQueryRequest request) {
        String url = aiServiceBaseUrl + "/internal/rag/query";
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 504) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiServiceUnavailableException("AI service is currently unavailable", request.getRequestId());
            }

            return objectMapper.readValue(response.body(), RagQueryResponse.class);
        } catch (HttpConnectTimeoutException e) {
            throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
        } catch (ConnectException e) {
            throw new AiServiceUnavailableException("AI service is currently unavailable", request.getRequestId());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AiServiceUnavailableException("AI service is currently unavailable", request.getRequestId());
        }
    }
}
