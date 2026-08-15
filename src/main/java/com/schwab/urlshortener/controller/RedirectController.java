package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    public RedirectController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortCode,
                                                       HttpServletRequest request) {
        Optional<String> originalUrlOpt = urlShortenerService.getOriginalUrl(shortCode);

        if (originalUrlOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Asynchronously log click event details for analytics
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String ipAddress = request.getRemoteAddr();
        String referrer = request.getHeader(HttpHeaders.REFERER);
        urlShortenerService.recordClick(shortCode, userAgent, ipAddress, referrer);

        // Perform HTTP 302 Found redirect
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrlOpt.get()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}