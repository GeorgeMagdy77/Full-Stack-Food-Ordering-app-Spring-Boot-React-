package com.example.food.app.local_storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;

@Service
public class LocalFileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    /* =========================
       واجهات متخصصة للتسمية
       ========================= */

    /** يحفظ صورة البروفايل باسم: username_email.ext داخل profile/ */
    public URL storeProfileImage(MultipartFile file, String username, String email) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String base = sanitize(username) + "_" + sanitize(email);
            String finalName = ensureUniqueName("profile", base + ext);

            Path dir = ensureDir("profile");
            Path target = dir.resolve(finalName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return buildPublicUrl("profile/" + finalName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store profile image", e);
        }
    }

    /** يحفظ صورة المينيو باسم: menuName_categoryId.ext داخل menus/ */
    public URL storeMenuImage(MultipartFile file, String menuName, Long categoryId) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String base = sanitize(menuName) + "_" + (categoryId == null ? "0" : categoryId.toString());
            String finalName = ensureUniqueName("menus", base + ext);

            Path dir = ensureDir("menus");
            Path target = dir.resolve(finalName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return buildPublicUrl("menus/" + finalName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store menu image", e);
        }
    }

    /* =========================================
       نسخة عامة قديمة (للتوافق الخلفي إن احتجتها)
       تحفظ بالاسم الأصلي + منطق تجنّب التعارض
       ========================================= */
    public URL uploadFile(String dirKey, MultipartFile file, String userEmail) {
        try {
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            if (original.isBlank() || original.contains("..")) {
                throw new IOException("Invalid original filename");
            }

            String dir = normalizeDirKey(dirKey);
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetDir = dir.isEmpty() ? root : root.resolve(dir).normalize();
            Files.createDirectories(targetDir);

            String prefix = (userEmail == null ? "" :
                    (userEmail.length() >= 3 ? userEmail.substring(0, 3) : userEmail)).toLowerCase();

            String finalName = original;
            Path target = targetDir.resolve(finalName);
            if (Files.exists(target)) {
                finalName = (prefix.isBlank() ? "" : prefix + "_") + original;
                target = targetDir.resolve(finalName);
                int count = 1;
                while (Files.exists(target)) {
                    finalName = (prefix.isBlank() ? "" : prefix + "_") + count + "_" + original;
                    target = targetDir.resolve(finalName);
                    count++;
                }
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String publicPath = (dir.isEmpty() ? finalName : (dir + "/" + finalName)).replace("//", "/");
            return buildPublicUrl(publicPath);

        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to build file URL", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /* =========================
       حذف ملف (بـ URL أو مفتاح نسبي)
       ========================= */
    public void deleteFile(String key) {
        try {
            String rel = key.replace("\\", "/");
            if (rel.startsWith("/")) rel = rel.substring(1);
            int idx = rel.indexOf("/files/");
            if (idx >= 0) {
                rel = rel.substring(idx + "/files/".length());
            }
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = root.resolve(rel).normalize();
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // ممكن تضيف logging هنا
        }
    }

    /* =========================
       Helpers
       ========================= */

    private Path ensureDir(String sub) throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path dir = (sub == null || sub.isBlank()) ? root : root.resolve(sub).normalize();
        Files.createDirectories(dir);
        return dir;
    }

    private String getExtension(String original) throws IOException {
        String clean = StringUtils.cleanPath(original == null ? "" : original);
        if (clean.isBlank() || clean.contains("..")) throw new IOException("Invalid filename");
        int dot = clean.lastIndexOf('.');
        return (dot >= 0 ? clean.substring(dot) : "");
    }

    private String sanitize(String s) {
        if (s == null) return "unknown";
        String trimmed = s.trim().toLowerCase();
        // اسم آمن: حروف/أرقام/شرطة ونقطة و@ و _
        String safe = trimmed.replaceAll("[^a-z0-9._@-]+", "-");
        // شيل التكرارات
        safe = safe.replaceAll("-{2,}", "-");
        // لو فاضي
        if (safe.isBlank()) safe = "unknown";
        return safe;
    }

    private String normalizeDirKey(String dirKey) {
        String dir = (dirKey == null ? "" : dirKey.replace("\\", "/"));
        if (dir.startsWith("/")) dir = dir.substring(1);
        if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
        return dir;
    }

    private String ensureUniqueName(String subDir, String desiredName) throws IOException {
        Path dir = ensureDir(subDir);
        String name = desiredName;
        Path target = dir.resolve(name);
        int count = 1;
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0 ? name.substring(0, dot) : name);
        String ext  = (dot >= 0 ? name.substring(dot) : "");

        while (Files.exists(target)) {
            name = base + "_" + count + ext;
            target = dir.resolve(name);
            count++;
        }
        return name;
    }

    private URL buildPublicUrl(String relativePath) throws MalformedURLException {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/")
                .path(relativePath.replace("\\", "/"))
                .toUriString();
        return new URL(url);
    }
}
