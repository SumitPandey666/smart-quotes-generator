package com.smartquotes.quoteservice.scheduler;

import com.smartquotes.quoteservice.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotePoolRefresher {

    private final QuoteRepository quoteRepository;
    private final RedisTemplate<String, Long> redisTemplate;

    private static final String REDIS_RANDOM_POOL_KEY = "quote:random_pool";
    private static final int REFILL_THRESHOLD = 100;
    private static final int BATCH_SIZE = 500;

    @Scheduled(fixedRate = 60000)
    @Transactional(readOnly = true)
    public void replenishRandomQuotePool() {
        Long currentSize = redisTemplate.opsForList().size(REDIS_RANDOM_POOL_KEY);

        if (currentSize == null || currentSize < REFILL_THRESHOLD) {
            log.info("Redis random pool running low (Current size: {}). Replenishing...", currentSize);

            List<Long> randomIds = quoteRepository.getRandomQuoteIds(BATCH_SIZE);

            for (Long id : randomIds) {
                redisTemplate.opsForList().rightPush(REDIS_RANDOM_POOL_KEY, id);
            }

            log.info("Successfully added {} new quotes to the Redis pool.", randomIds.size());
        } else {
            log.debug("Redis random pool is healthy (Current size: {}).", currentSize);
        }
    }
}