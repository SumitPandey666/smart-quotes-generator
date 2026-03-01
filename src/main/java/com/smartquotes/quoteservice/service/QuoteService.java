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
import com.smartquotes.quoteservice.dto.InteractionRequest;
import com.smartquotes.quoteservice.enums.InteractionType;
import org.springframework.data.domain.PageRequest;


import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final RedisTemplate<String, Long> redisTemplate;
    private static final String REDIS_USER_SEEN_PREFIX = "user:seen:";

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

    @Transactional(readOnly = true)
    public QuoteResponse processInteraction(String userId, Long quoteId, InteractionRequest request) {
        // 1. Mark the current quote as seen so we never return it again
        markQuoteAsSeen(userId, quoteId);

        if (request.interactionType() == InteractionType.DISLIKES) {
            return getUnseenRandomQuote(userId);
        }

        log.info("User {} liked quote {}. Engaging Similarity Engine...", userId, quoteId);

        Quote likedQuote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found"));

        if (likedQuote.getTags().isEmpty()) {
            return getUnseenRandomQuote(userId);
        }

        // 2. Fetch up to 100 candidate quotes to give us a buffer for filtered ones
        List<Quote> candidates = quoteRepository.findCandidateQuotes(
                likedQuote.getTags(),
                quoteId,
                PageRequest.of(0, 100)
        ).getContent();

        // 3. The Filter Bubble Breaker: Remove candidates the user has already seen today
        List<Quote> unseenCandidates = candidates.stream()
                .filter(candidate -> !hasUserSeenQuote(userId, candidate.getId()))
                .toList();

        if (unseenCandidates.isEmpty()) {
            log.info("User {} has seen all similar quotes. Falling back to random.", userId);
            return getUnseenRandomQuote(userId);
        }

        // 4. Find the best match from the UNSEEN candidates
        Quote bestMatch = null;
        double highestScore = -1.0;

        for (Quote candidate : unseenCandidates) {
            double score = calculateJaccardSimilarity(likedQuote.getTags(), candidate.getTags());

            if (score > highestScore) {
                highestScore = score;
                bestMatch = candidate;
            }
        }

        // Mark the recommended quote as seen before sending it back
        markQuoteAsSeen(userId, bestMatch.getId());

        log.info("Found similar unseen quote {} with Jaccard score: {}", bestMatch.getId(), highestScore);
        return mapToResponse(bestMatch);
    }

    /**
     * The core Data Structures & Algorithms (DSA) logic.
     * Calculates the Jaccard Similarity index between two sets of tags.
     */
    private double calculateJaccardSimilarity(Set<Tag> setA, Set<Tag> setB) {
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;

        // Intersection: Tags present in BOTH sets
        Set<Tag> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        // Union: All unique tags across BOTH sets
        Set<Tag> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    private void markQuoteAsSeen(String userId, Long quoteId) {
        String key = REDIS_USER_SEEN_PREFIX + userId;
        redisTemplate.opsForSet().add(key, quoteId);
        // Reset the 24-hour expiration every time they interact
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    private boolean hasUserSeenQuote(String userId, Long quoteId) {
        String key = REDIS_USER_SEEN_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, quoteId));
    }

    private QuoteResponse getUnseenRandomQuote(String userId) {
        int maxAttempts = 5; // Prevent infinite loops if the pool is heavily exhausted

        for (int i = 0; i < maxAttempts; i++) {
            Long randomId = redisTemplate.opsForList().leftPop(REDIS_RANDOM_POOL_KEY);

            if (randomId == null) {
                break; // Pool is empty, break out and use DB fallback
            }

            if (!hasUserSeenQuote(userId, randomId)) {
                markQuoteAsSeen(userId, randomId);
                Quote quote = quoteRepository.findById(randomId).orElseThrow();
                return mapToResponse(quote);
            }
            // If seen, the loop continues and pops another one
        }

        // Ultimate fallback if Redis pool is empty or user has seen everything in the pool
        log.warn("Failed to find unseen quote in Redis pool. Hitting DB fallback.");
        Quote quote = quoteRepository.findAll().stream().findFirst().orElseThrow();
        markQuoteAsSeen(userId, quote.getId());
        return mapToResponse(quote);
    }
}