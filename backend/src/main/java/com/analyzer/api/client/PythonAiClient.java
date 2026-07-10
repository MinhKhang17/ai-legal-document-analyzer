package com.analyzer.api.client;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;
import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.ai.GenerateContractApiRequest;
import com.analyzer.api.dto.ai.GenerateContractApiResponse;
import com.analyzer.api.service.AiClient;
import com.analyzer.api.exception.ai.AiServiceTimeoutException;
import com.analyzer.api.exception.ai.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
public class PythonAiClient implements AiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    public RagQueryResponse query(RagQueryRequest request) {
        String url = aiServiceBaseUrl + "/internal/rag/query";
        try {
            return postForObject(url, request, request.getRequestId(), RagQueryResponse.class);
        } catch (AiServiceTimeoutException | AiServiceUnavailableException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 504) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. status=" + e.getStatusCode().value()
                            + ", body=" + e.getResponseBodyAsString(),
                    request.getRequestId());
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
                    request.getRequestId());
        } catch (Exception e) {
            throw new AiServiceTimeoutException(
                    "AI service timeout. Please try again later. " + e.getMessage(),
                    request.getRequestId());
        }
    }

    @Override
    public AiLegalQueryResponse queryLegal(AiLegalQueryRequest request) {
        try {
            return postForObject(aiServiceBaseUrl + "/internal/rag/query", request, request.getRequestId(), AiLegalQueryResponse.class);
        } catch (AiServiceTimeoutException | AiServiceUnavailableException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 504) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. status=" + e.getStatusCode().value()
                            + ", body=" + e.getResponseBodyAsString(),
                    request.getRequestId());
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
                    request.getRequestId());
        } catch (Exception e) {
            throw new AiServiceTimeoutException(
                    "AI service timeout. Please try again later. " + e.getMessage(),
                    request.getRequestId());
        }
    }

    @Override
    public GenerateContractApiResponse generateContract(GenerateContractApiRequest request) {
        try {
            return postForObject(aiServiceBaseUrl + "/v2/contracts/generate", request, request.getRequestId(), GenerateContractApiResponse.class);
        } catch (AiServiceTimeoutException | AiServiceUnavailableException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 504) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. status=" + e.getStatusCode().value()
                            + ", body=" + e.getResponseBodyAsString(),
                    request.getRequestId());
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
                    request.getRequestId());
        } catch (Exception e) {
            throw new AiServiceTimeoutException(
                    "AI service timeout. Please try again later. " + e.getMessage(),
                    request.getRequestId());
        }
    }

    private <T> T postForObject(String url, Object request, String requestId, Class<T> responseType) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(request);
        HttpHeaders headers = new HttpHeaders();
        headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class
        );

        if (response.getStatusCode().value() == 504) {
            throw new AiServiceTimeoutException("AI service timeout. Please try again later", requestId);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. status=" + response.getStatusCode().value()
                            + ", body=" + response.getBody(),
                    requestId);
        }

        return objectMapper.readValue(response.getBody(), responseType);
    }
}
