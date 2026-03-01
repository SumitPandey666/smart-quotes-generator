package com.smartquotes.quoteservice.repository;

import com.smartquotes.quoteservice.entity.Quote;
import com.smartquotes.quoteservice.entity.Tag;
import org.springframework.data.repository.query.Param; // <-- FIXED: Lettuce import removed
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    @Query(value = "SELECT id FROM quotes ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Long> getRandomQuoteIds(@Param("limit") int limit);

    // STEP 1: Get ONLY the IDs using the paginated tags filter
    @Query("SELECT DISTINCT q.id FROM Quote q JOIN q.tags t WHERE t IN :tags AND q.id != :quoteId")
    Page<Long> findCandidateQuoteIds(@Param("tags") Set<Tag> tags, @Param("quoteId") Long quoteId, Pageable pageable);

    // STEP 2: Fetch the full hydrated entities in ONE query using the IDs
    @Query("SELECT DISTINCT q FROM Quote q JOIN FETCH q.tags JOIN FETCH q.author WHERE q.id IN :quoteIds")
    List<Quote> findQuotesWithTagsAndAuthor(@Param("quoteIds") List<Long> quoteIds);

    // OOM-Safe fallback
    Quote findFirstByOrderByIdAsc();
}