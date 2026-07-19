package com.analyzer.api.client;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.knowledge.KnowledgeBaseIngestedDocumentResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeBaseAiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    public void submitIngest(
            Resource sourceFile,
            String title,
            String jobId,
            String callbackUrl,
            String knowledgeBaseId,
            Long ingestedByUserId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", sourceFile);
        body.add("title", title);
        body.add("job_id", jobId);
        body.add("callback_url", callbackUrl);
        body.add("knowledge_base_id", knowledgeBaseId);
        body.add("ingested_by_user_id", ingestedByUserId == null ? "SYSTEM_ADMIN" : String.valueOf(ingestedByUserId));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(aiServiceBaseUrl + "/api/knowledge/ingest-v2/async"),
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "AI knowledge ingest rejected. status=" + response.getStatusCode().value()
                            + ", body=" + response.getBody());
        }
    }

    public PageResponse<KnowledgeBaseIngestedDocumentResponse> getIngestedDocuments(
            String kbId,
            String keyword,
            String ingestStatus,
            String visibility,
            int page,
            int size) {
        try {
            URI uri = buildUri(kbId, keyword, ingestStatus, visibility, page, size);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return emptyPage(page, size);
            }

            return objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<PageResponse<KnowledgeBaseIngestedDocumentResponse>>() {}
            );
        } catch (RestClientException ex) {
            log.warn("AI service unavailable for ingested documents kbId={}: {}", kbId, ex.getMessage());
            return emptyPage(page, size);
        } catch (Exception ex) {
            log.warn("AI service returned invalid ingested documents payload for kbId={}: {}", kbId, ex.getMessage());
            return emptyPage(page, size);
        }
    }

    public boolean updateLifecycle(String kbId, String documentId, boolean makePublic) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(aiServiceBaseUrl)
                    .path("/ai/admin/knowledge-bases/{kbId}/ingested-documents/{documentId}/lifecycle")
                    .buildAndExpand(kbId, documentId)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    org.springframework.http.HttpMethod.PUT,
                    new HttpEntity<>(Map.of("public", makePublic), headers),
                    String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            log.warn("Unable to update AI KB lifecycle kbId={} documentId={}: {}", kbId, documentId, ex.getMessage());
            return false;
        }
    }

    private URI buildUri(
            String kbId,
            String keyword,
            String ingestStatus,
            String visibility,
            int page,
            int size) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (keyword != null && !keyword.isBlank()) {
            params.add("keyword", keyword.trim());
        }
        if (ingestStatus != null && !ingestStatus.isBlank()) {
            params.add("ingestStatus", ingestStatus.trim());
        }
        if (visibility != null && !visibility.isBlank()) {
            params.add("visibility", visibility.trim());
        }
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(size));

        return UriComponentsBuilder.fromUriString(aiServiceBaseUrl)
                .path("/ai/admin/knowledge-bases/{kbId}/ingested-documents")
                .queryParams(params)
                .buildAndExpand(kbId)
                .toUri();
    }

    private PageResponse<KnowledgeBaseIngestedDocumentResponse> emptyPage(int page, int size) {
        return PageResponse.<KnowledgeBaseIngestedDocumentResponse>builder()
                .items(java.util.List.of())
                .page(page)
                .size(size)
                .totalItems(0)
                .totalPages(0)
                .build();
    }
}
