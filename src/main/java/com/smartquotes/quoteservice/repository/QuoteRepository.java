package com.smartquotes.quoteservice.repository;

import com.smartquotes.quoteservice.entity.Quote;
import com.smartquotes.quoteservice.entity.Tag;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    @Query(value = "SELECT id FROM quotes ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Long> getRandomQuoteIds(@Param("limit") int limit);

    @Query("SELECT DISTINCT q FROM Quote q JOIN q.tags t WHERE t IN :tags AND q.id != :quoteId")
    Page<Quote> findCandidateQuotes(@Param("tags") Set<Tag> tags, @Param("quoteId") Long quoteId, Pageable pageable);
}