package com.intelliblog.service;

import com.intelliblog.model.Post;
import com.intelliblog.model.User;
import com.intelliblog.repository.PostRepository;
import com.intelliblog.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports the newest headlines from public RSS feeds as published posts.
 * Only the publisher's short summary is stored, with the source name and a link to the full story.
 */
@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private final PostRepository posts;
    private final UserRepository users;
    private final String[] feeds;
    private final int perFeed, keepDays;
    private final RestTemplate rest;
    private volatile LocalDateTime lastRun;

    public NewsService(PostRepository posts, UserRepository users, @Value("${app.news.feeds}") String feeds,
                       @Value("${app.news.per-feed}") int perFeed, @Value("${app.news.keep-days}") int keepDays) {
        this.posts = posts;
        this.users = users;
        this.feeds = feeds.split(",");
        this.perFeed = perFeed;
        this.keepDays = keepDays;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(8000);
        rf.setReadTimeout(10000);
        this.rest = new RestTemplate(rf);
    }

    public LocalDateTime lastRun() { return lastRun; }

    /** Runs 5 seconds after start-up and then every app.news.refresh-ms (default 3 hours). */
    @Scheduled(initialDelay = 5000, fixedDelayString = "${app.news.refresh-ms}")
    public synchronized int refresh() {
        int added = 0;
        User bot = users.findByUsername("newsbot").orElseGet(() -> {
            User u = new User();
            u.username = "newsbot";
            u.password = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()); // nobody can log in as the bot
            return users.save(u);
        });
        for (String spec : feeds) {
            String[] p = spec.trim().split("\\|");
            if (p.length < 3) continue;
            try {
                added += importFeed(bot, p[0].trim(), p[1].trim(), p[2].trim());
            } catch (Exception e) {
                log.warn("News feed skipped: {} ({})", p[1], e.toString());   // one broken feed never stops the others
            }
        }
        LocalDateTime cut = LocalDateTime.now().minusDays(keepDays);
        posts.findBySourceUrlIsNotNullAndPublishedAtBefore(cut).stream()
                .filter(x -> x.likes == 0 && x.commentCount == 0).forEach(posts::delete);   // keep the database small
        lastRun = LocalDateTime.now();
        log.info("News refresh finished: {} new articles", added);
        return added;
    }

    private int importFeed(User bot, String category, String source, String url) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "Mozilla/5.0 (compatible; IntelliBlogNews/1.0)");
        h.set("Accept", "application/rss+xml, application/xml, text/xml");
        String xml = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // blocks XML external entity attacks
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList items = doc.getElementsByTagName("item");
        int added = 0;
        for (int i = 0; i < items.getLength() && added < perFeed; i++) {
            Element it = (Element) items.item(i);
            String link = text(it, "link"), title = clean(text(it, "title"));
            if (title.isEmpty() || !link.startsWith("http") || posts.existsBySourceUrl(link)) continue;
            LocalDateTime when;
            try {
                when = ZonedDateTime.parse(text(it, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception e) {
                when = LocalDateTime.now();
            }
            if (when.isBefore(LocalDateTime.now().minusDays(keepDays))) continue;

            String raw = text(it, "description") + text(it, "content:encoded");
            String summary = cap(clean(text(it, "description")), 400);
            Post p = new Post();
            p.authorId = bot.id;
            p.authorName = "IntelliBlog News";
            p.title = cap(title, 190);
            p.content = (summary.isEmpty() ? p.title : summary) + "\n\n**Source:** " + source + " · [Read the full story](" + link + ")";
            p.category = category;
            p.tags = ",news," + category.toLowerCase() + "," + source.toLowerCase().replace(' ', '-') + ",";
            p.coverImage = image(it, raw);
            p.status = "PUBLISHED";
            p.sourceUrl = link;
            p.sourceName = source;
            p.createdAt = when;
            p.publishedAt = when;
            posts.save(p);
            added++;
        }
        return added;
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }

    private static String clean(String s) {
        return HtmlUtils.htmlUnescape(s.replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
    }

    private static String cap(String s, int n) {
        if (s.length() <= n) return s;
        int i = s.lastIndexOf(' ', n);
        return s.substring(0, i > 0 ? i : n) + "...";
    }

    /** Picks a picture from media tags, an enclosure, or the first <img> in the description. */
    private static String image(Element it, String raw) {
        for (String tag : new String[]{"media:content", "media:thumbnail", "enclosure"}) {
            NodeList n = it.getElementsByTagName(tag);
            for (int i = 0; i < n.getLength(); i++) {
                Element e = (Element) n.item(i);
                String u = e.getAttribute("url"), type = e.getAttribute("type");
                if (u.startsWith("http") && (type.isEmpty() || type.startsWith("image")) && !u.matches("(?i).*\\.(mp3|mp4|m4a)(\\?.*)?$"))
                    return u.contains("ichef.bbci.co.uk") ? u.replace("/240/", "/800/") : u;   // BBC feeds give tiny thumbnails
            }
        }
        Matcher m = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']").matcher(raw);
        return m.find() && m.group(1).startsWith("http") ? m.group(1) : "";
    }
}
