package com.smartquotes.quoteservice.bootstrap;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.smartquotes.quoteservice.entity.Author;
import com.smartquotes.quoteservice.entity.Quote;
import com.smartquotes.quoteservice.entity.Tag;
import com.smartquotes.quoteservice.repository.AuthorRepository;
import com.smartquotes.quoteservice.repository.QuoteRepository;
import com.smartquotes.quoteservice.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteDataLoader implements CommandLineRunner {

    private final QuoteRepository quoteRepository;
    private final AuthorRepository authorRepository;
    private final TagRepository tagRepository;

    // Local memory caches to prevent massive N+1 database lookups
    private final Map<String, Author> authorCache = new HashMap<>();
    private final Map<String, Tag> tagCache = new HashMap<>();

    private static final int BATCH_SIZE = 1000;

    @Override
    public void run(String... args) throws Exception {
        if (quoteRepository.count() > 0) {
            log.info("Database already populated. Skipping CSV parsing.");
            return;
        }

        log.info("Starting optimized CSV Data Loading process...");
        long startTime = System.nanoTime();

        // Use ClassPathResource to read from src/main/resources
        try (Reader reader = new InputStreamReader(new ClassPathResource("quotes.csv").getInputStream());
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] line;
            List<Quote> quoteBatch = new ArrayList<>();
            int totalProcessed = 0;

            // Stream line by line to save heap memory
            while ((line = csvReader.readNext()) != null) {
                // Defensive check for malformed rows
                if (line.length < 3) continue;

                try {
                    String text = line[0].trim();
                    String authorName = line[1].trim();
                    String tagsString = line[2].trim();

                    Author author = resolveAuthor(authorName);

                    Quote quote = new Quote();
                    quote.setText(text);
                    quote.setAuthor(author);

                    if (!tagsString.isEmpty()) {
                        String[] tagNames = tagsString.split(",");
                        for (String tagName : tagNames) {
                            Tag tag = resolveTag(tagName.trim());
                            if (tag != null) {
                                quote.addTag(tag);
                            }
                        }
                    }

                    quoteBatch.add(quote);
                    totalProcessed++;

                    if (quoteBatch.size() >= BATCH_SIZE) {
                        quoteRepository.saveAll(quoteBatch);
                        log.info("Saved batch of {}. Total processed: {}", BATCH_SIZE, totalProcessed);
                        quoteBatch.clear();
                    }
                } catch (Exception e) {
                    // Catch row-level errors so the whole job doesn't crash
                    log.error("Failed to process row, skipping. Data: {} | Error: {}", line[0], e.getMessage());
                }
            }

            // Save the remaining records
            if (!quoteBatch.isEmpty()) {
                quoteRepository.saveAll(quoteBatch);
                log.info("Saved final batch. Total processed: {}", totalProcessed);
            }

        } catch (Exception e) {
            log.error("Error parsing CSV file", e);
        }

        long endTime = System.nanoTime();
        log.info("Data loading completed in {} seconds.", (endTime - startTime) / 1000000000);
    }

    private Author resolveAuthor(String name) {
        String finalName = name.isEmpty() ? "Unknown" : name;

        // Defensive truncation to match DB schema
        if (finalName.length() > 255) {
            finalName = finalName.substring(0, 255);
        }

        if (authorCache.containsKey(finalName)) {
            return authorCache.get(finalName);
        }

        // Using final variable for lambda
        final String authorNameToSave = finalName;
        Author author = authorRepository.findByName(authorNameToSave).orElseGet(() -> {
            Author newAuthor = new Author();
            newAuthor.setName(authorNameToSave);
            return authorRepository.save(newAuthor);
        });

        authorCache.put(authorNameToSave, author);
        return author;
    }

    private Tag resolveTag(String name) {
        if (name.isEmpty()) return null;

        // Defensive truncation to match DB schema
        String finalName = name.length() > 255 ? name.substring(0, 255) : name;

        if (tagCache.containsKey(finalName)) {
            return tagCache.get(finalName);
        }

        Tag tag = tagRepository.findByName(finalName).orElseGet(() -> {
            Tag newTag = new Tag();
            newTag.setName(finalName);
            return tagRepository.save(newTag);
        });

        tagCache.put(finalName, tag);
        return tag;
    }
}