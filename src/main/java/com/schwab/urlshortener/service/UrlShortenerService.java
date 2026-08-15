package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.UrlResponse;
import com.schwab.urlshortener.model.ClickEvent;
import com.schwab.urlshortener.model.UrlMapping;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.util.Base62Encoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private final UrlMappingRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final Base62Encoder base62Encoder;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "url:";

    public UrlShortenerService(UrlMappingRepository urlRepository,
                               ClickEventRepository clickEventRepository,
                               Base62Encoder base62Encoder,
                               StringRedisTemplate redisTemplate) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
        this.base62Encoder = base62Encoder;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public UrlResponse shortenUrl(ShortenUrlRequest request, String baseUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(request.getOriginalUrl());
        mapping.setActive(true);

        OffsetDateTime now = OffsetDateTime.now();
        mapping.setCreatedAt(now);

        if (request.getExpiresAt() != null) {
            mapping.setExpiresAt(request.getExpiresAt());
        } else if (request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
            mapping.setExpiresAt(now.plusSeconds(request.getTtlSeconds()));
        }

        // Save first to obtain generated database ID
        mapping = urlRepository.save(mapping);

        // Encode primary key ID to Base62 short code
        String shortCode = base62Encoder.encode(mapping.getId());
        mapping.setShortCode(shortCode);
        urlRepository.save(mapping);

        // Cache in Redis
        cacheUrl(shortCode, mapping.getOriginalUrl(), mapping.getExpiresAt());

        String fullShortUrl = baseUrl + "/" + shortCode;
        return new UrlResponse(shortCode, fullShortUrl, mapping.getOriginalUrl(),
                mapping.getCreatedAt(), mapping.getExpiresAt(), mapping.isActive());
    }

    public Optional<String> getOriginalUrl(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;

        // 1. Check Redis Cache
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            return Optional.of(cachedUrl);
        }

        // 2. Cache Miss - Query Database
        Optional<UrlMapping> mappingOpt = urlRepository.findByShortCode(shortCode);
        if (mappingOpt.isPresent()) {
            UrlMapping mapping = mappingOpt.get();

            // Check activity and expiration status
            if (!mapping.isActive()) {
                return Optional.empty();
            }
            if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(OffsetDateTime.now())) {
                return Optional.empty();
            }

            // Populate Redis Cache for subsequent requests
            cacheUrl(shortCode, mapping.getOriginalUrl(), mapping.getExpiresAt());
            return Optional.of(mapping.getOriginalUrl());
        }

        return Optional.empty();
    }

    @Async
    @Transactional
    public void recordClick(String shortCode, String userAgent, String ipAddress, String referrer) {
        // Increment aggregate counter on UrlMapping entity
        urlRepository.incrementClickCount(shortCode);

        // Log granular click event for analytics
        ClickEvent clickEvent = new ClickEvent(shortCode, userAgent, ipAddress, referrer);
        clickEventRepository.save(clickEvent);
    }

    public Optional<AnalyticsResponse> getAnalytics(String shortCode) {
        Optional<UrlMapping> mappingOpt = urlRepository.findByShortCode(shortCode);
        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        UrlMapping mapping = mappingOpt.get();
        long totalClicks = clickEventRepository.countByShortCode(shortCode);
        OffsetDateTime lastClicked = clickEventRepository.findLastClickedAtByShortCode(shortCode);

        return Optional.of(new AnalyticsResponse(
                shortCode,
                mapping.getOriginalUrl(),
                totalClicks,
                lastClicked,
                mapping.getCreatedAt()
        ));
    }

    private void cacheUrl(String shortCode, String originalUrl, OffsetDateTime expiresAt) {
        String cacheKey = CACHE_PREFIX + shortCode;
        if (expiresAt != null) {
            long ttlSeconds = Duration.between(OffsetDateTime.now(), expiresAt).getSeconds();
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(cacheKey, originalUrl, Duration.ofSeconds(ttlSeconds));
            }
        } else {
            // Default cache time (e.g., 24 hours) for non-expiring links
            redisTemplate.opsForValue().set(cacheKey, originalUrl, Duration.ofHours(24));
        }
    }
}