package com.smartquotes.quoteservice.repository;

import com.smartquotes.quoteservice.entity.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

}