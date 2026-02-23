package com.smartquotes.quoteservice.entity;

import com.smartquotes.quoteservice.enums.InteractionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_interactions", indexes = {
        @Index(name = "idx_interaction_user", columnList = "user_id"),
        @Index(name = "idx_interaction_quote", columnList = "quote_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId; // Can be a UUID from your auth provider

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private InteractionType interactionType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
