package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for the redirect hot path. This was the single biggest test
 * coverage gap flagged in review: the service layer's active/expiry logic was already
 * covered (see UrlShortenerServiceTest), but nothing exercised the actual HTTP behavior -
 * status codes and headers - of the endpoint real traffic hits.
 */
@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService urlShortenerService;

    @Test
    void redirect_validShortCode_returns302WithLocationHeader() throws Exception {
        when(urlShortenerService.getOriginalUrl("abc123"))
                .thenReturn(Optional.of("https://example.com/target"));

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));

        // Redirect must trigger click recording - this is the behavior that makes
        // analytics work at all, so it's worth asserting explicitly rather than just
        // checking the redirect status.
        verify(urlShortenerService).recordClick(eq("abc123"), any(), any(), any());
    }

    @Test
    void redirect_unknownShortCode_returns404() throws Exception {
        when(urlShortenerService.getOriginalUrl("doesNotExist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/doesNotExist"))
                .andExpect(status().isNotFound());

        // No click should be recorded for a code that doesn't resolve to anything.
        verify(urlShortenerService, never()).recordClick(anyString(), any(), any(), any());
    }

    @Test
    void redirect_expiredShortCode_returns404() throws Exception {
        // The service layer already encodes "expired" as an empty Optional (see
        // UrlShortenerServiceTest#getOriginalUrl_expiredLink_returnsEmpty) - this test
        // confirms the controller correctly surfaces that as a 404, not a 500 or a
        // redirect to a stale URL.
        when(urlShortenerService.getOriginalUrl("expiredCode")).thenReturn(Optional.empty());

        mockMvc.perform(get("/expiredCode"))
                .andExpect(status().isNotFound());
    }
}
