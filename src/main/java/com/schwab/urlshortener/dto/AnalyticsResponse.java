package com.schwab.urlshortener.dto;

import java.time.OffsetDateTime;

public class AnalyticsResponse {

    private String shortCode;
    private String originalUrl;
    private long totalClicks;
    private OffsetDateTime lastClickedAt;
    private OffsetDateTime createdAt;

    public AnalyticsResponse() {
    }

    public AnalyticsResponse(String shortCode, String originalUrl, long totalClicks, OffsetDateTime lastClickedAt, OffsetDateTime createdAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.totalClicks = totalClicks;
        this.lastClickedAt = lastClickedAt;
        this.createdAt = createdAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public void setTotalClicks(long totalClicks) {
        this.totalClicks = totalClicks;
    }

    public OffsetDateTime getLastClickedAt() {
        return lastClickedAt;
    }

    public void setLastClickedAt(OffsetDateTime lastClickedAt) {
        this.lastClickedAt = lastClickedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}