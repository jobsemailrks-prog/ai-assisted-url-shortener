package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.UrlResponse;
import com.schwab.urlshortener.exception.ErrorResponse;
import com.schwab.urlshortener.exception.GlobalExceptionHandler;
import com.schwab.urlshortener.model.UrlMapping;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import com.schwab.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Service-layer tests. These were the biggest coverage gap identified in review - the
 * original suite only covered Base62Encoder. Focus here is on the behaviors that are
 * easy to get subtly wrong: cache-aside fallback, expiry/active-flag enforcement on the
 * read path, and TTL math around expiring links.
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository urlRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private Base62Encoder base62Encoder;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UrlShortenerService service;

    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService(urlRepository, clickEventRepository, base62Encoder, redisTemplate);
    }

    @Test
    void shortenUrl_savesEntityTwiceAndCachesResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UrlMapping saved = new UrlMapping();
        saved.setId(42L);
        saved.setOriginalUrl("https://example.com/very/long/path");
        saved.setActive(true);

        when(urlRepository.save(any(UrlMapping.class))).thenReturn(saved);
        when(base62Encoder.encode(42L)).thenReturn("abc123");

        ShortenUrlRequest request = new ShortenUrlRequest("https://example.com/very/long/path", null, null);
        UrlResponse response = service.shortenUrl(request, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("abc123");
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/abc123");
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/very/long/path");

        // save() is called twice by design: once to obtain the generated ID, once to
        // persist the derived short code. Asserting the count documents that intentional
        // two-phase write rather than letting it silently regress to one (which would
        // leave shortCode null) or silently grow to three+.
        verify(urlRepository, times(2)).save(any(UrlMapping.class));

        // Default (non-expiring) links get a 24h cache TTL per cacheUrl()'s else-branch.
        verify(valueOperations).set(eq("url:abc123"), eq(saved.getOriginalUrl()), eq(Duration.ofHours(24)));
    }

    @Test
    void getOriginalUrl_returnsFromCache_withoutHittingRepository() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:cached1")).thenReturn("https://cached-target.example.com");

        Optional<String> result = service.getOriginalUrl("cached1");

        assertThat(result).contains("https://cached-target.example.com");
        verifyNoInteractions(urlRepository);
    }

    @Test
    void getOriginalUrl_cacheMiss_fallsBackToRepositoryAndRepopulatesCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:miss1")).thenReturn(null);

        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("miss1");
        mapping.setOriginalUrl("https://db-target.example.com");
        mapping.setActive(true);
        mapping.setExpiresAt(null);

        when(urlRepository.findByShortCode("miss1")).thenReturn(Optional.of(mapping));

        Optional<String> result = service.getOriginalUrl("miss1");

        assertThat(result).contains("https://db-target.example.com");
        verify(valueOperations).set(eq("url:miss1"), eq("https://db-target.example.com"), eq(Duration.ofHours(24)));
    }

    @Test
    void getOriginalUrl_inactiveLink_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:deactivated")).thenReturn(null);

        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("deactivated");
        mapping.setOriginalUrl("https://example.com");
        mapping.setActive(false);

        when(urlRepository.findByShortCode("deactivated")).thenReturn(Optional.of(mapping));

        Optional<String> result = service.getOriginalUrl("deactivated");

        assertThat(result).isEmpty();
        // Inactive links must not be re-cached - would keep serving a deactivated redirect
        // out of Redis until the (unrelated) TTL expired.
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getOriginalUrl_expiredLink_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:expired1")).thenReturn(null);

        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("expired1");
        mapping.setOriginalUrl("https://example.com");
        mapping.setActive(true);
        mapping.setExpiresAt(OffsetDateTime.now().minusMinutes(5));

        when(urlRepository.findByShortCode("expired1")).thenReturn(Optional.of(mapping));

        Optional<String> result = service.getOriginalUrl("expired1");

        assertThat(result).isEmpty();
    }

    @Test
    void getOriginalUrl_unknownCode_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:doesnotexist")).thenReturn(null);
        when(urlRepository.findByShortCode("doesnotexist")).thenReturn(Optional.empty());

        Optional<String> result = service.getOriginalUrl("doesnotexist");

        assertThat(result).isEmpty();
    }

    @Test
    void recordClick_incrementsCounterAndPersistsClickEvent() {
        service.recordClick("abc123", "Mozilla/5.0", "203.0.113.5", "https://referrer.example.com");

        verify(urlRepository).incrementClickCount("abc123");
        verify(clickEventRepository).save(argThat(event ->
                event.getShortCode().equals("abc123")
                        && event.getUserAgent().equals("Mozilla/5.0")
                        && event.getIpAddress().equals("203.0.113.5")
                        && event.getReferrer().equals("https://referrer.example.com")
        ));
    }

    @Test
    void getAnalytics_unknownCode_returnsEmpty() {
        when(urlRepository.findByShortCode("nope")).thenReturn(Optional.empty());

        Optional<AnalyticsResponse> result = service.getAnalytics("nope");

        assertThat(result).isEmpty();
        verifyNoInteractions(clickEventRepository);
    }

    @Test
    void handleNoResourceFoundException_returnsNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleNoResourceFoundException(new NoResourceFoundException(HttpMethod.GET, "/"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
    }

    @Test
    void getAnalytics_knownCode_returnsAggregatedStats() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("known1");
        mapping.setOriginalUrl("https://example.com");
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(3);
        mapping.setCreatedAt(createdAt);

        when(urlRepository.findByShortCode("known1")).thenReturn(Optional.of(mapping));
        when(clickEventRepository.countByShortCode("known1")).thenReturn(17L);
        OffsetDateTime lastClick = OffsetDateTime.now().minusHours(1);
        when(clickEventRepository.findLastClickedAtByShortCode("known1")).thenReturn(lastClick);

        Optional<AnalyticsResponse> result = service.getAnalytics("known1");

        assertThat(result).isPresent();
        assertThat(result.get().getTotalClicks()).isEqualTo(17L);
        assertThat(result.get().getLastClickedAt()).isEqualTo(lastClick);
        assertThat(result.get().getCreatedAt()).isEqualTo(createdAt);
    }
}
