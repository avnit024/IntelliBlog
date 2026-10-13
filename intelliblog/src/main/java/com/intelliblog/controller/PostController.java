package com.intelliblog.controller;

import com.intelliblog.model.Comment;
import com.intelliblog.model.Post;
import com.intelliblog.model.PostLike;
import com.intelliblog.repository.CommentRepository;
import com.intelliblog.repository.LikeRepository;
import com.intelliblog.repository.PostRepository;
import com.intelliblog.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PostController {
    private final PostRepository posts;
    private final UserRepository users;
    private final CommentRepository comments;
    private final LikeRepository likes;

    public PostController(PostRepository posts, UserRepository users, CommentRepository comments, LikeRepository likes) {
        this.posts = posts; this.users = users; this.comments = comments; this.likes = likes;
    }

    private static Long uid(HttpServletRequest rq) { return (Long) rq.getAttribute("userId"); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("error", m)); }
    private static ResponseEntity<?> notFound() { return ResponseEntity.status(404).body(Map.of("error", "Post not found")); }
    private static Pageable pg(int page, int size) { return pg(page, size, "id"); }
    private static Pageable pg(int page, int size, String sortBy) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, sortBy));
    }

    /** "Java, Spring" -> ",java,spring," so tags can be matched with LIKE. */
    private static String tagStr(String t) {
        String r = Arrays.stream(clean(t).toLowerCase().split(",")).map(String::trim)
                .filter(x -> !x.isEmpty()).distinct().collect(Collectors.joining(","));
        return r.isEmpty() ? "" : "," + r + ",";
    }

    static Map<String, Object> card(Post p) {
        String flat = p.content.replaceAll("[#*`]", "").replace("\n", " ");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id); m.put("title", p.title);
        m.put("excerpt", flat.length() > 160 ? flat.substring(0, 160) + "..." : flat);
        m.put("category", p.category); m.put("tags", p.tags); m.put("authorName", p.authorName);
        m.put("views", p.views); m.put("status", p.status);
        m.put("sourceName", p.sourceName == null ? "" : p.sourceName);
        m.put("coverImage", p.coverImage == null ? "" : p.coverImage); m.put("likes", p.likes); m.put("commentCount", p.commentCount);
        m.put("createdAt", p.createdAt); m.put("publishedAt", p.publishedAt);
        return m;
    }

    private Map<String, Object> toPage(Page<Post> pg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", pg.getContent().stream().map(PostController::card).toList());
        m.put("page", pg.getNumber()); m.put("pages", pg.getTotalPages()); m.put("total", pg.getTotalElements());
        return m;
    }

    /** Validates the request body and copies it onto the post. Returns an error message or null. */
    private String apply(Post p, Map<String, String> b) {
        String t = clean(b.get("title")), c = clean(b.get("content")), cat = clean(b.get("category")), st = clean(b.get("status"));
        if (t.isEmpty() || t.length() > 200) return "Title is required (max 200 characters)";
        if (c.isEmpty() || c.length() > 20000) return "Content is required (max 20000 characters)";
        p.title = t; p.content = c; p.category = cat.isEmpty() ? "General" : cat; p.tags = tagStr(b.get("tags"));
        String img = clean(b.get("coverImage"));
        boolean okImg = img.isEmpty() || ((img.startsWith("http://") || img.startsWith("https://") || img.startsWith("/uploads/")) && img.length() <= 500);
        if (!okImg) return "Cover image must be an http(s) URL or an uploaded image";
        p.coverImage = img;
        if (st.equals("PUBLISHED") || st.equals("DRAFT")) {
            p.status = st;
            if (st.equals("PUBLISHED") && p.publishedAt == null) p.publishedAt = LocalDateTime.now();
        }
        return null;
    }

    private Optional<Post> own(Long id, HttpServletRequest rq) {
        return posts.findById(id).filter(p -> p.authorId.equals(uid(rq)));
    }

    // ---------- public (no login) ----------
    @GetMapping("/public/posts")
    public Map<String, Object> publicPosts(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String category,
                                           @RequestParam(defaultValue = "") String tag, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "6") int size, @RequestParam(defaultValue = "latest") String sort) {
        String tagLike = tag.isBlank() ? "%" : "%," + tag.toLowerCase() + ",%";
        return toPage(posts.publicSearch("%" + search.toLowerCase() + "%", category, tagLike, pg(page, size, sort.equals("views") ? "views" : sort.equals("likes") ? "likes" : "id")));
    }

    @GetMapping("/public/posts/{id}")
    public ResponseEntity<?> publicPost(@PathVariable Long id) {
        Optional<Post> f = posts.findById(id).filter(p -> p.status.equals("PUBLISHED"));
        if (f.isEmpty()) return notFound();
        Post p = f.get();
        p.views++;                      // analytics: count a view
        return ResponseEntity.ok(posts.save(p));
    }

    @GetMapping("/public/meta")
    public Map<String, Object> meta() {
        List<Post> all = posts.findByStatus("PUBLISHED");
        Map<String, Long> cats = all.stream().collect(Collectors.groupingBy(p -> p.category, TreeMap::new, Collectors.counting()));
        Map<String, Long> tags = new TreeMap<>();
        all.forEach(p -> Arrays.stream(p.tags.split(",")).filter(s -> !s.isBlank()).forEach(t -> tags.merge(t, 1L, Long::sum)));
        return Map.of("categories", cats, "tags", tags);
    }

    // ---------- author (JWT required) ----------
    @GetMapping("/posts")
    public Map<String, Object> mine(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String status,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "8") int size, HttpServletRequest rq) {
        return toPage(posts.mine(uid(rq), status, "%" + search.toLowerCase() + "%", pg(page, size)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> one(@PathVariable Long id, HttpServletRequest rq) {
        return own(id, rq).<ResponseEntity<?>>map(ResponseEntity::ok).orElse(notFound());
    }

    @PostMapping("/posts")
    public ResponseEntity<?> create(@RequestBody Map<String, String> b, HttpServletRequest rq) {
        Post p = new Post();
        p.authorId = uid(rq);
        p.authorName = users.findById(p.authorId).map(u -> u.username).orElse("unknown");
        String err = apply(p, b);
        return err != null ? bad(err) : ResponseEntity.ok(posts.save(p));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> b, HttpServletRequest rq) {
        Optional<Post> f = own(id, rq);
        if (f.isEmpty()) return notFound();
        String err = apply(f.get(), b);
        return err != null ? bad(err) : ResponseEntity.ok(posts.save(f.get()));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest rq) {
        Optional<Post> f = own(id, rq);
        if (f.isEmpty()) return notFound();
        posts.delete(f.get());
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    /** Toggles between DRAFT and PUBLISHED. */
    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable Long id, HttpServletRequest rq) {
        Optional<Post> f = own(id, rq);
        if (f.isEmpty()) return notFound();
        Post p = f.get();
        if (p.status.equals("PUBLISHED")) p.status = "DRAFT";
        else { p.status = "PUBLISHED"; if (p.publishedAt == null) p.publishedAt = LocalDateTime.now(); }
        return ResponseEntity.ok(card(posts.save(p)));
    }

    // ---------- comments and likes ----------
    @GetMapping("/public/posts/{id}/comments")
    public List<Comment> listComments(@PathVariable Long id) { return comments.findByPostIdOrderByIdDesc(id); }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, String> b, HttpServletRequest rq) {
        Optional<Post> f = posts.findById(id).filter(p -> p.status.equals("PUBLISHED"));
        if (f.isEmpty()) return notFound();
        String text = clean(b.get("text"));
        if (text.isEmpty() || text.length() > 500) return bad("Comment must be 1-500 characters");
        Comment c = new Comment();
        c.postId = id; c.text = text;
        c.authorName = users.findById(uid(rq)).map(u -> u.username).orElse("user");
        Post p = f.get();
        p.commentCount++;
        posts.save(p);
        return ResponseEntity.ok(comments.save(c));
    }

    @GetMapping("/posts/{id}/like")
    public Map<String, Boolean> liked(@PathVariable Long id, HttpServletRequest rq) {
        return Map.of("liked", likes.existsByPostIdAndUserId(id, uid(rq)));
    }

    /** Like / unlike toggle: one like per user per post. */
    @PostMapping("/posts/{id}/like")
    @Transactional
    public ResponseEntity<?> like(@PathVariable Long id, HttpServletRequest rq) {
        Optional<Post> f = posts.findById(id).filter(p -> p.status.equals("PUBLISHED"));
        if (f.isEmpty()) return notFound();
        Post p = f.get();
        Long u = uid(rq);
        boolean nowLiked;
        if (likes.existsByPostIdAndUserId(id, u)) { likes.deleteByPostIdAndUserId(id, u); p.likes = Math.max(0, p.likes - 1); nowLiked = false; }
        else { PostLike l = new PostLike(); l.postId = id; l.userId = u; likes.save(l); p.likes++; nowLiked = true; }
        posts.save(p);
        return ResponseEntity.ok(Map.of("liked", nowLiked, "likes", p.likes));
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(HttpServletRequest rq) {
        List<Post> mine = posts.findByAuthorId(uid(rq));
        long published = mine.stream().filter(p -> p.status.equals("PUBLISHED")).count();
        List<Map<String, Object>> top = mine.stream().sorted(Comparator.comparingInt((Post p) -> p.views).reversed()).limit(5)
                .map(p -> Map.<String, Object>of("id", p.id, "title", p.title, "views", p.views)).toList();
        Map<String, Long> byCat = mine.stream().collect(Collectors.groupingBy(p -> p.category, TreeMap::new, Collectors.counting()));
        return Map.of("total", mine.size(), "published", published, "drafts", mine.size() - published,
                "views", mine.stream().mapToLong(p -> p.views).sum(), "likes", mine.stream().mapToLong(p -> p.likes).sum(), "top", top, "byCategory", byCat);
    }
}
