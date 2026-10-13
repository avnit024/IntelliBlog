package com.intelliblog.repository;

import com.intelliblog.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Public feed: published posts filtered by search text, category and tag (empty = no filter). */
    @Query("select p from Post p where p.status = 'PUBLISHED' "
            + "and (lower(p.title) like :q or lower(p.content) like :q) "
            + "and (:cat = '' or p.category = :cat) and p.tags like :tag")
    Page<Post> publicSearch(@Param("q") String q, @Param("cat") String cat, @Param("tag") String tag, Pageable pg);

    /** An author's own posts (drafts and published), optionally filtered by status. */
    @Query("select p from Post p where p.authorId = :uid and (:st = '' or p.status = :st) "
            + "and (lower(p.title) like :q or lower(p.content) like :q)")
    Page<Post> mine(@Param("uid") Long uid, @Param("st") String st, @Param("q") String q, Pageable pg);

    List<Post> findByAuthorId(Long authorId);
    boolean existsBySourceUrl(String sourceUrl);
    List<Post> findTop30BySourceUrlIsNotNullAndStatusOrderByPublishedAtDesc(String status);
    List<Post> findBySourceUrlIsNotNullAndPublishedAtBefore(java.time.LocalDateTime time);
    List<Post> findByStatus(String status);
}
