package com.intelliblog.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long postId;
    public String authorName;
    @Column(length = 500)
    public String text;
    public LocalDateTime createdAt = LocalDateTime.now();
}
