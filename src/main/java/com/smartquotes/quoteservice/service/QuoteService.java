package com.smartquotes.quoteservice.service;

import com.smartquotes.quoteservice.annotation.TrackExecutionTime;
import com.smartquotes.quoteservice.dto.QuoteResponse;
import com.smartquotes.quoteservice.entity.Quote;
import com.smartquotes.quoteservice.entity.Tag;
import com.smartquotes.quoteservice.exception.ResourceNotFoundException;
import com.smartquotes.quoteservice.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartquotes.quoteservice.dto.InteractionRequest;
import com.smartquotes.quoteservice.enums.InteractionType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;

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
        Long poppedId = redisTemplate.opsForList().leftPop(REDIS_RANDOM_POOL_KEY);

        // FAANG Fix 1: Effectively final lambda variable & OOM protection
        Long finalQuoteId;
        if (poppedId == null) {
            log.warn("Redis pool is empty! Falling back to database.");
            finalQuoteId = quoteRepository.findFirstByOrderByIdAsc().getId();
        } else {
            finalQuoteId = poppedId;
        }

        Quote quote = quoteRepository.findById(finalQuoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found for ID " + finalQuoteId));

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

    @TrackExecutionTime
    @Transactional(readOnly = true)
    public QuoteResponse processInteraction(String userId, Long quoteId, InteractionRequest request) {
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

        // FAANG Fix 2: Two-Step Fetch to kill N+1 Problem
        Page<Long> candidateIdPage = quoteRepository.findCandidateQuoteIds(
                likedQuote.getTags(),
                quoteId,
                PageRequest.of(0, 100)
        );

        if (candidateIdPage.isEmpty()) {
            return getUnseenRandomQuote(userId);
        }

        // Fetch everything safely in one shot
        List<Quote> candidates = quoteRepository.findQuotesWithTagsAndAuthor(candidateIdPage.getContent());

        List<Quote> unseenCandidates = candidates.stream()
                .filter(candidate -> !hasUserSeenQuote(userId, candidate.getId()))
                .toList();

        if (unseenCandidates.isEmpty()) {
            log.info("User {} has seen all similar quotes. Falling back to random.", userId);
            return getUnseenRandomQuote(userId);
        }

        Quote bestMatch = null;
        double highestScore = -1.0;

        for (Quote candidate : unseenCandidates) {
            double score = calculateJaccardSimilarity(likedQuote.getTags(), candidate.getTags());

            if (score > highestScore) {
                highestScore = score;
                bestMatch = candidate;
            }
        }

        markQuoteAsSeen(userId, bestMatch.getId());
        log.info("Found similar unseen quote {} with Jaccard score: {}", bestMatch.getId(), highestScore);
        return mapToResponse(bestMatch);
    }

    private double calculateJaccardSimilarity(Set<Tag> setA, Set<Tag> setB) {
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;

        Set<Tag> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<Tag> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    private void markQuoteAsSeen(String userId, Long quoteId) {
        String key = REDIS_USER_SEEN_PREFIX + userId;
        redisTemplate.opsForSet().add(key, quoteId);
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    private boolean hasUserSeenQuote(String userId, Long quoteId) {
        String key = REDIS_USER_SEEN_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, quoteId));
    }

    private QuoteResponse getUnseenRandomQuote(String userId) {
        int maxAttempts = 5;

        for (int i = 0; i < maxAttempts; i++) {
            Long randomId = redisTemplate.opsForList().leftPop(REDIS_RANDOM_POOL_KEY);
            if (randomId == null) break;

            if (!hasUserSeenQuote(userId, randomId)) {
                markQuoteAsSeen(userId, randomId);
                Quote quote = quoteRepository.findById(randomId).orElseThrow();
                return mapToResponse(quote);
            }
        }

        log.warn("Failed to find unseen quote in Redis pool. Hitting DB fallback.");
        Quote quote = quoteRepository.findFirstByOrderByIdAsc(); // <-- FAANG Fix 3: OOM protection
        markQuoteAsSeen(userId, quote.getId());
        return mapToResponse(quote);
    }
}