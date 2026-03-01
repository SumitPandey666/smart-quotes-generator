package com.smartquotes.quoteservice.controller;

import com.smartquotes.quoteservice.dto.QuoteResponse;
import com.smartquotes.quoteservice.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.smartquotes.quoteservice.dto.InteractionRequest;

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


    @PostMapping("/{quoteId}/interact")
    public ResponseEntity<QuoteResponse> interactWithQuotes(
            @RequestHeader("X-User-Id") String userId, // Simulating a logged-in user or device ID
            @PathVariable Long quoteId,
            @RequestBody InteractionRequest request) {

        QuoteResponse nextQuote = quoteService.processInteraction(userId, quoteId, request);
        return ResponseEntity.ok(nextQuote);
    }
}