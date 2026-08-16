package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.UrlResponse;
import com.schwab.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urls")
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Basic liveness check - confirms the app process is up and routing requests.
     * Does NOT verify DB/Redis connectivity; a deeper health check would use Spring Boot
     * Actuator's /actuator/health, which is not wired in for this prototype.
     */
    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("URL shortener API is running");
    }

    @PostMapping
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody ShortenUrlRequest request,
                                                  HttpServletRequest servletRequest) {
        String domainUrl = baseUrl;
        if (domainUrl == null || domainUrl.isBlank()) {
            domainUrl = servletRequest.getScheme() + "://" + servletRequest.getServerName() 
                    + ":" + servletRequest.getServerPort();
        }

        UrlResponse response = urlShortenerService.shortenUrl(request, domainUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        return urlShortenerService.getAnalytics(shortCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}