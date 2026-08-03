package com.uttam.urlshortener.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_mappings")
@Data
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String originalUrl;

    @Column(nullable = false, unique = true)
    private String shortCode;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private int clickCount = 0;

    // Owner of this URL (nullable — anonymous users can create URLs too)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    // Use this to initialize the timestamp automatically
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}