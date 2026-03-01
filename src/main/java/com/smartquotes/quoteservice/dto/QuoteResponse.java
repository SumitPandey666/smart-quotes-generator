package com.smartquotes.quoteservice.dto;

import java.util.Set;

public record QuoteResponse(
        Long id,
        String text,
        String authorName,
        Set<String> tags
) {}