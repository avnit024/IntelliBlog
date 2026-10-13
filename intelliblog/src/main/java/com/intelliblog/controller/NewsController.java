package com.intelliblog.controller;

import com.intelliblog.model.Post;
import com.intelliblog.repository.PostRepository;
import com.intelliblog.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NewsController {
    private final PostRepository posts;
    private final NewsService news;

    public NewsController(PostRepository posts, NewsService news) { this.posts = posts; this.news = news; }

    /** Latest imported headlines (public). */
    @GetMapping("/public/news")
    public Map<String, Object> latest(@RequestParam(defaultValue = "8") int size) {
        List<Post> all = posts.findTop30BySourceUrlIsNotNullAndStatusOrderByPublishedAtDesc("PUBLISHED");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", all.stream().limit(Math.min(Math.max(size, 1), 30)).map(PostController::card).toList());
        m.put("updatedAt", news.lastRun());
        return m;
    }

    /** Fetch the feeds right now (login required). */
    @PostMapping("/news/refresh")
    public Map<String, Object> refresh() { return Map.of("added", news.refresh()); }
}
