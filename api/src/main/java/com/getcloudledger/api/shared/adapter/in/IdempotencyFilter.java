package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.adapter.out.repository.JpaIdempotencyKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final String MISSING_KEY = "{\"error\":\"missing_idempotency_key\"}";
    private static final String KEY_MISMATCH = "{\"error\":\"idempotency_key_reuse_mismatch\"}";
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JpaIdempotencyKeyRepository repository;
    private final IdempotencyService idempotencyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MUTATION_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeJson(response, 400, MISSING_KEY);
            return;
        }

        var cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256(cachedRequest.getCachedBody());

        var existing = repository.findById(key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                writeJson(response, 422, KEY_MISMATCH);
                return;
            }
            response.setStatus(record.getResponseStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (!"null".equals(record.getResponseBody())) {
                response.getWriter().write(record.getResponseBody());
            }
            return;
        }

        var responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedRequest, responseWrapper);
        } finally {
            int status = responseWrapper.getStatus();
            byte[] bytes = responseWrapper.getContentAsByteArray();
            String body = bytes.length > 0 ? new String(bytes, StandardCharsets.UTF_8) : null;
            if (status >= 200 && status < 300) {
                idempotencyService.store(key, requestHash, status, body);
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
