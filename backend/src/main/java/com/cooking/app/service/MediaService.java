package com.cooking.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Pattern NAME = Pattern.compile("[0-9]+-[0-9a-f-]{36}\\.(jpg|png)");
    private final Path root;

    public MediaService(@Value("${UPLOAD_DIR:uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public String store(long user, MultipartFile part) throws IOException {
        if (part.isEmpty() || part.getSize() > 5 * 1024 * 1024) throw ApiException.bad("Ảnh tối đa 5 MB");
        byte[] data = part.getBytes();
        String extension;
        if (data.length >= 8 && (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71) {
            extension = "png";
        } else if (data.length >= 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255) {
            extension = "jpg";
        } else throw ApiException.bad("Chỉ hỗ trợ ảnh JPEG hoặc PNG");
        Files.createDirectories(root);
        String filename = user + "-" + UUID.randomUUID() + "." + extension;
        Files.write(root.resolve(filename), data, java.nio.file.StandardOpenOption.CREATE_NEW);
        return "/api/media/" + filename;
    }

    public void validateOwner(String url, long user) {
        String prefix = "/api/media/" + user + "-";
        if (url == null || !url.startsWith(prefix)) throw ApiException.bad("Ảnh đính kèm không hợp lệ");
        try {
            resource(url.substring("/api/media/".length()));
        } catch (IOException | ApiException exception) {
            throw ApiException.bad("Ảnh đính kèm không hợp lệ");
        }
    }

    public Resource resource(String filename) throws IOException {
        if (!NAME.matcher(filename).matches()) throw ApiException.missing("Không tìm thấy ảnh");
        Path file = root.resolve(filename);
        if (!Files.isRegularFile(file)) throw ApiException.missing("Không tìm thấy ảnh");
        return new UrlResource(file.toUri());
    }
}
