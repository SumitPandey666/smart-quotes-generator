package com.smartquotes.quoteservice.controller;

import com.smartquotes.quoteservice.dto.QuoteResponse;
import com.smartquotes.quoteservice.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping("/feed")
    public ResponseEntity<QuoteResponse> getRandomFeedQuote() {
        QuoteResponse response = quoteService.getRandomQuote();
        return ResponseEntity.ok(response);
    }
}