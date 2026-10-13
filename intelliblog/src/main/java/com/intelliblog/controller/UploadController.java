package com.intelliblog.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cover-image upload (login required). Files are saved in ./uploads and served from /uploads/**. */
@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "gif", "webp");

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (file.isEmpty() || !ALLOWED.contains(ext))
            return ResponseEntity.badRequest().body(Map.of("error", "Only png, jpg, gif or webp images are allowed"));
        Path dir = Path.of("uploads");
        Files.createDirectories(dir);
        String saved = UUID.randomUUID() + "." + ext;   // random name: no path tricks from the original filename
        Files.copy(file.getInputStream(), dir.resolve(saved));
        return ResponseEntity.ok(Map.of("url", "/uploads/" + saved));
    }
}
