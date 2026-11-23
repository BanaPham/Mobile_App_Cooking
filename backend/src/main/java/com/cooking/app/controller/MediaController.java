package com.cooking.app.controller;

import com.cooking.app.service.MediaService;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, String> upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) throws IOException {
        return Map.of("url", media.store(Long.parseLong(jwt.getSubject()), file));
    }

    @GetMapping("/{filename}")
    ResponseEntity<Resource> image(@PathVariable String filename) throws IOException {
        return ResponseEntity.ok().contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
            .header("X-Content-Type-Options", "nosniff").body(media.resource(filename));
    }
}
