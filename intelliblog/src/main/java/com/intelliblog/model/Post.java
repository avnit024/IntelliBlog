package com.intelliblog.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long authorId;
    public String authorName;
    public String title;
    @Column(length = 20000)
    public String content;
    public String category = "General";
    public String tags = "";          // stored like ",java,spring,"
    public String status = "DRAFT";   // DRAFT or PUBLISHED
    public int views;
    public String coverImage = "";
    @Column(length = 1000)
    public String sourceUrl;     // set for imported news
    public String sourceName;
    @Column(columnDefinition = "integer default 0 not null")
    public int likes;
    @Column(columnDefinition = "integer default 0 not null")
    public int commentCount;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime publishedAt;
}
