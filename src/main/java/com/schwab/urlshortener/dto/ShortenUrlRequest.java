package com.schwab.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import java.time.OffsetDateTime;

public class ShortenUrlRequest {

    @NotBlank(message = "Original URL must not be blank")
    @URL(message = "Invalid URL format provided")
    private String originalUrl;

    private OffsetDateTime expiresAt;

    private Long ttlSeconds;

    public ShortenUrlRequest() {
    }

    public ShortenUrlRequest(String originalUrl, OffsetDateTime expiresAt, Long ttlSeconds) {
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.ttlSeconds = ttlSeconds;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}