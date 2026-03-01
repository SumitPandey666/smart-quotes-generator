package com.smartquotes.quoteservice.service;

import com.smartquotes.quoteservice.dto.QuoteResponse;
import com.smartquotes.quoteservice.entity.Quote;
import com.smartquotes.quoteservice.entity.Tag;
import com.smartquotes.quoteservice.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final RedisTemplate<String, Long> redisTemplate;

    private static final String REDIS_RANDOM_POOL_KEY = "quote:random_pool";

    @Transactional(readOnly = true)
    public QuoteResponse getRandomQuote() {
        // 1. Pop an ID instantly from the Redis Queue in O(1) time
        Long quoteId = redisTemplate.opsForList().leftPop(REDIS_RANDOM_POOL_KEY);

        if (quoteId == null) {
            log.warn("Redis pool is empty! Falling back to database.");
            // Fallback: Just grabbing the first ID we can find to prevent a crash
            quoteId = quoteRepository.findAll().stream().findFirst().orElseThrow().getId();
        }

        // 2. Fetch the actual Quote entity by Primary Key (Extremely fast)
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found for ID "));

        // 3. Map to DTO
        return mapToResponse(quote);
    }

    private QuoteResponse mapToResponse(Quote quote) {
        Set<String> tagNames = quote.getTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());

        return new QuoteResponse(
                quote.getId(),
                quote.getText(),
                quote.getAuthor().getName(),
                tagNames
        );
    }
}