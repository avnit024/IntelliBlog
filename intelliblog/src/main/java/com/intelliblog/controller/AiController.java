package com.intelliblog.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Gemini features: summarize, translate, chatbot (with short memory), tag suggestions, writing improvement.
 * The API key comes from GEMINI_API_KEY, ./local.properties, or the in-app "AI settings" box (saved in data/gemini.key).
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final Path KEY_FILE = Path.of("data", "gemini.key");
    private final RestTemplate rest = new RestTemplate();
    private final List<String> models;
    private volatile String key;
    private volatile String working;   // last model that answered, tried first next time

    public AiController(@Value("${app.gemini.key}") String key, @Value("${app.gemini.model}") String model) {
        String k = key == null ? "" : key.trim();
        if (k.startsWith("PASTE_")) k = "";
        if (k.isEmpty()) {
            try { if (Files.exists(KEY_FILE)) k = Files.readString(KEY_FILE).trim(); } catch (IOException ignored) { }
        }
        this.key = k;
        // Model names change often. Several current names are tried in order, then the app asks Google
        // which models this key can use (see discover()). "gemini-flash-latest" always points at the current Flash model.
        this.models = new ArrayList<>(new LinkedHashSet<>(List.of(model, "gemini-flash-latest", "gemini-3.6-flash",
                "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview", "gemini-2.5-flash")));
    }

    private static String cut(String s, int max) {
        s = s == null ? "" : s;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String googleMessage(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }

    /** Asks Google which models this key can use for generateContent (newest Flash models first). */
    @SuppressWarnings("unchecked")
    private List<String> discover() {
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("x-goog-api-key", key);
            Map<String, Object> res = rest.exchange("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100",
                    HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody();
            List<String> out = new ArrayList<>();
            for (Map<String, Object> m : (List<Map<String, Object>>) res.get("models")) {
                String n = ((String) m.get("name")).replace("models/", "");
                List<String> methods = (List<String>) m.getOrDefault("supportedGenerationMethods", List.of());
                if (methods.contains("generateContent") && n.contains("flash")
                        && !n.matches(".*(image|tts|live|audio|computer|robotics|embedding).*")) out.add(n);
            }
            out.sort(Comparator.reverseOrder());
            return out.size() > 6 ? out.subList(0, 6) : out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Calls Gemini and returns the text, or throws IllegalStateException with a user-friendly message. */
    private String call(String prompt) {
        List<String> order = new ArrayList<>(models);
        String w = working;
        if (w != null) { order.remove(w); order.add(0, w); }
        boolean discovered = false, rateLimited = false;
        List<String> tried = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String m = order.get(i);
            tried.add(m);
            try {
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.APPLICATION_JSON);
                h.set("x-goog-api-key", key);
                Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
                Map<?, ?> res = rest.postForObject("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent",
                        new HttpEntity<>(body, h), Map.class);
                List<?> cands = res == null ? null : (List<?>) res.get("candidates");
                if (cands == null || cands.isEmpty()) throw new IllegalStateException("Gemini returned no answer. Try rephrasing.");
                Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) cands.get(0)).get("content");
                if (content == null) throw new IllegalStateException("Gemini returned no answer. Try rephrasing.");
                String text = (String) ((Map<?, ?>) ((List<?>) content.get("parts")).get(0)).get("text");
                working = m;
                return text.trim();
            } catch (HttpStatusCodeException e) {
                int s = e.getStatusCode().value();
                String msg = googleMessage(e.getResponseBodyAsString());
                if (s == 429) rateLimited = true;
                else if (s == 400 || s == 403)
                    throw new IllegalStateException(msg.contains("API key") ? "The API key looks invalid. Check it in AI settings."
                            : "Google rejected the request: " + msg);
                // 404 (model retired), 429 (quota for this model) and 5xx: move on to the next model
            }
            if (i == order.size() - 1 && !discovered) {   // nothing worked yet: ask Google which models this key can use
                discovered = true;
                for (String d : discover()) if (!order.contains(d)) order.add(d);
            }
        }
        if (rateLimited) throw new IllegalStateException("Gemini rate limit reached for this key. Wait a minute and try again.");
        throw new IllegalStateException("No Gemini model worked for this key (tried " + String.join(", ", tried)
                + "). Set the GEMINI_MODEL environment variable to a model listed at aistudio.google.com.");
    }

    private ResponseEntity<?> run(String prompt, String field) {
        if (key.isBlank())
            return ResponseEntity.status(503).body(Map.of("error", "AI is not set up yet. Click the AI button in the header and add your Gemini API key."));
        try {
            return ResponseEntity.ok(Map.of(field, call(prompt)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not reach Gemini: " + e.getMessage()));
        }
    }

    // ----- setup -----
    @GetMapping("/status")
    public Map<String, Object> status() { return Map.of("configured", !key.isBlank()); }

    /** Saves a key after testing it with a tiny request. */
    @PostMapping("/key")
    public ResponseEntity<?> setKey(@RequestBody Map<String, String> b) {
        String k = b.getOrDefault("key", "").trim();
        if (k.length() < 20) return ResponseEntity.badRequest().body(Map.of("error", "That does not look like a Gemini API key"));
        String old = key;
        key = k;
        try {
            call("Reply with the single word OK.");
            Files.createDirectories(KEY_FILE.getParent());
            Files.writeString(KEY_FILE, k);
            return ResponseEntity.ok(Map.of("configured", true));
        } catch (Exception e) {
            key = old;
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @DeleteMapping("/key")
    public Map<String, Object> removeKey() throws IOException {
        key = "";
        Files.deleteIfExists(KEY_FILE);
        return Map.of("configured", false);
    }

    // ----- features -----
    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody Map<String, String> b) {
        return run("Summarize this blog post in 3 to 4 short sentences:\n\n" + cut(b.get("text"), 8000), "result");
    }

    @PostMapping("/translate")
    public ResponseEntity<?> translate(@RequestBody Map<String, String> b) {
        return run("Translate the following text into " + b.getOrDefault("language", "Hindi")
                + ". Keep any Markdown formatting. Return only the translation:\n\n" + cut(b.get("text"), 8000), "result");
    }

    @PostMapping("/tags")
    public ResponseEntity<?> tags(@RequestBody Map<String, String> b) {
        return run("Suggest 4 to 5 short lowercase tags for this blog post. Return ONLY the tags separated by commas, nothing else:\n\n"
                + cut(b.get("text"), 6000), "result");
    }

    @PostMapping("/improve")
    public ResponseEntity<?> improve(@RequestBody Map<String, String> b) {
        return run("Improve the clarity, grammar and flow of this blog post. Keep the meaning and keep Markdown formatting. "
                + "Return only the improved post:\n\n" + cut(b.get("text"), 8000), "result");
    }

    @PostMapping("/briefing")
    public ResponseEntity<?> briefing(@RequestBody Map<String, String> b) {
        return run("You are a news editor. Write a short daily briefing from these headlines: 5 bullet points, one sentence each, neutral tone. "
                + "Do not add facts that are not in the headlines.\n\n" + cut(b.get("text"), 4000), "result");
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> b) {
        String ctx = cut(b.get("context"), 6000), hist = cut(b.get("history"), 3000);
        String prompt = "You are IntelliBot, a friendly assistant inside a blogging platform. Answer concisely.\n"
                + (ctx.isBlank() ? "" : "The reader is currently viewing this post:\n" + ctx + "\n\n")
                + (hist.isBlank() ? "" : "Conversation so far:\n" + hist + "\n\n")
                + "User: " + cut(b.get("message"), 1500);
        return run(prompt, "reply");
    }
}
