package com.smartquotes.quoteservice.repository;

import com.smartquotes.quoteservice.entity.Quote;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    @Query(value = "SELECT id FROM quotes ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Long> getRandomQuoteIds(@Param("limit") int limit);
}