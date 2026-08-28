package com.codeinspector.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
public class FileController {

    @GetMapping("/api/files/avatars/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        File file = new File(System.getProperty("java.io.tmpdir") + "/code-inspector/avatars/" + filename);
        if (!file.exists()) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }
}
