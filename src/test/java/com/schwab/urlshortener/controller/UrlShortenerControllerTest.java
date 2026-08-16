package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.dto.UrlResponse;
import com.schwab.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlShortenerController.class)
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService urlShortenerService;

    @Test
    void healthCheck_returnsOk() throws Exception {
        mockMvc.perform(get("/api/urls"))
                .andExpect(status().isOk())
                .andExpect(content().string("URL shortener API is running"));
    }

    @Test
    void shortenUrl_validRequest_returns201WithBody() throws Exception {
        UrlResponse response = new UrlResponse(
                "abc123", "http://localhost:8080/abc123", "https://example.com",
                OffsetDateTime.now(), null, true);
        when(urlShortenerService.shortenUrl(any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void shortenUrl_blankUrl_returns400() throws Exception {
        // @NotBlank on ShortenUrlRequest.originalUrl should reject this before the
        // service is ever called - this was previously untested, meaning a regression
        // in the validation annotation would have shipped silently.
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortenUrl_malformedUrl_returns400() throws Exception {
        // @URL on ShortenUrlRequest.originalUrl should reject non-URL strings.
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"not-a-valid-url\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalytics_unknownShortCode_returns404() throws Exception {
        when(urlShortenerService.getAnalytics("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/urls/missing/analytics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAnalytics_knownShortCode_returns200WithStats() throws Exception {
        AnalyticsResponse stats = new AnalyticsResponse(
                "abc123", "https://example.com", 5L,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(urlShortenerService.getAnalytics("abc123")).thenReturn(Optional.of(stats));

        mockMvc.perform(get("/api/urls/abc123/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(5));
    }
}
