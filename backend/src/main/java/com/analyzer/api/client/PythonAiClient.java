package com.analyzer.api.client;

import com.analyzer.api.dto.ai.AiLegalQueryRequest;
import com.analyzer.api.dto.ai.AiLegalQueryResponse;
import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.ai.GenerateContractApiRequest;
import com.analyzer.api.dto.ai.GenerateContractApiResponse;
import com.analyzer.api.service.ai.AiClient;
import com.analyzer.api.exception.ai.AiServiceTimeoutException;
import com.analyzer.api.exception.ai.AiServiceUnavailableException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class PythonAiClient implements AiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public PythonAiClient(
            ObjectMapper objectMapper,
            @Value("${app.ai-service.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ai-service.read-timeout:130s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

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
        } catch (ResourceAccessException e) {
            if (hasTimeoutCause(e)) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
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
        } catch (ResourceAccessException e) {
            if (hasTimeoutCause(e)) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
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
        } catch (ResourceAccessException e) {
            if (hasTimeoutCause(e)) {
                throw new AiServiceTimeoutException("AI service timeout. Please try again later", request.getRequestId());
            }
            throw new AiServiceUnavailableException(
                    "AI service is currently unavailable. " + e.getMessage(),
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

    private boolean hasTimeoutCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
