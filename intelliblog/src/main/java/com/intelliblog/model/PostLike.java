package com.intelliblog.model;

import jakarta.persistence.*;

@Entity
public class PostLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long postId;
    public Long userId;
}
