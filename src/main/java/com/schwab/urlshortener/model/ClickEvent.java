package com.schwab.urlshortener.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "click_events", indexes = {
    @Index(name = "idx_click_events_code_time", columnList = "short_code, clicked_at")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "referrer", length = 512)
    private String referrer;

    @Column(name = "clicked_at", nullable = false, updatable = false)
    private OffsetDateTime clickedAt;

    public ClickEvent() {
    }

    public ClickEvent(String shortCode, String userAgent, String ipAddress, String referrer) {
        this.shortCode = shortCode;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.referrer = referrer;
    }

    @PrePersist
    protected void onCreate() {
        if (this.clickedAt == null) {
            this.clickedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public OffsetDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(OffsetDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }
}