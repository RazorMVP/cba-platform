package com.cba.batch;

import com.cba.batch.dto.BatchRequest;
import com.cba.batch.dto.BatchResponse;
import com.cba.common.exception.CbaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BatchApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String serverPort;

    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\"\\$\\.([^\"]+)\"");

    public BatchApiService(ObjectMapper objectMapper,
                           @Value("${server.port:8080}") String serverPort) {
        this.objectMapper = objectMapper;
        this.serverPort   = serverPort;
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public List<BatchResponse> executeBatch(List<BatchRequest> requests,
                                            boolean enclosingTransaction,
                                            HttpServletRequest originalRequest) {
        List<BatchRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(BatchRequest::requestId))
                .toList();

        Map<Integer, String> responseBodyMap = new LinkedHashMap<>();
        List<BatchResponse> results = new ArrayList<>();

        for (BatchRequest req : sorted) {
            String resolvedBody = resolveReferences(req.body(), req.reference(), responseBodyMap);
            String resolvedUrl  = resolveReferences(req.relativeUrl(), req.reference(), responseBodyMap);

            BatchResponse response = dispatchSingle(req.requestId(), req.method(), resolvedUrl,
                    resolvedBody, req.headers(), originalRequest);
            results.add(response);
            responseBodyMap.put(req.requestId(), response.body());

            if (enclosingTransaction && response.statusCode() >= 400) {
                throw CbaException.badRequest("BATCH_STEP_FAILED",
                        "Batch step " + req.requestId() + " failed with status " + response.statusCode()
                        + ": " + response.body());
            }
        }
        return results;
    }

    private BatchResponse dispatchSingle(int requestId, String method, String relativeUrl,
                                         String body, List<Map<String, String>> extraHeaders,
                                         HttpServletRequest originalRequest) {
        String baseUrl = "http://localhost:" + serverPort;
        String fullUrl = baseUrl + relativeUrl;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward auth + tenant from the original batch request
        String auth   = originalRequest.getHeader("Authorization");
        String tenant = originalRequest.getHeader("X-Tenant-ID");
        if (auth   != null) headers.set("Authorization", auth);
        if (tenant != null) headers.set("X-Tenant-ID", tenant);

        if (extraHeaders != null) {
            extraHeaders.forEach(h -> h.forEach(headers::set));
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

        try {
            ResponseEntity<String> resp = restTemplate.exchange(fullUrl, httpMethod, entity, String.class);
            return new BatchResponse(requestId, resp.getStatusCode().value(),
                    Collections.emptyList(), resp.getBody());
        } catch (HttpStatusCodeException e) {
            return new BatchResponse(requestId, e.getStatusCode().value(),
                    Collections.emptyList(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Batch step {} failed: {}", requestId, e.getMessage(), e);
            return new BatchResponse(requestId, 500, Collections.emptyList(),
                    "{\"errors\":[{\"code\":\"BATCH_INTERNAL_ERROR\",\"message\":\""
                            + e.getMessage().replace("\"", "'") + "\"}]}");
        }
    }

    private String resolveReferences(String value, Integer reference,
                                     Map<Integer, String> responseMap) {
        if (value == null || reference == null || !responseMap.containsKey(reference)) return value;
        String refBody = responseMap.get(reference);
        if (refBody == null || refBody.isBlank()) return value;
        try {
            JsonNode root = objectMapper.readTree(refBody);
            JsonNode data = root.path("data");
            if (!data.isMissingNode()) root = data;
            final JsonNode resolved = root;
            Matcher m = JSON_PATH_PATTERN.matcher(value);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String field = m.group(1);
                JsonNode node = resolved.path(field);
                String replacement = node.isMissingNode() ? m.group(0) : "\"" + node.asText() + "\"";
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (JsonProcessingException e) {
            log.warn("Could not resolve batch references: {}", e.getMessage());
            return value;
        }
    }
}
