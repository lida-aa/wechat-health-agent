package com.Myself.demo.controller;

import com.Myself.demo.security.AdminAccessService;
import com.Myself.demo.service.RagService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/knowledge")
public class HealthKnowledgeController {

    private static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".xlsx", ".txt", ".md", ".csv");

    private final RagService ragService;
    private final AdminAccessService accessService;

    public HealthKnowledgeController(RagService ragService, AdminAccessService accessService) {
        this.ragService = ragService;
        this.accessService = accessService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestHeader("Authorization") String authorization,
                                    @RequestParam(defaultValue = "") String userId) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        return Map.of("code", 200, "data", ragService.listDocuments(userId));
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "") String userId,
                                      @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        if (file == null || file.isEmpty()) {
            return Map.of("code", 400, "msg", "文件为空");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            return Map.of("code", 400, "msg", "文件不能超过 5MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        if (fileName == null || !hasAllowedExtension(fileName)) {
            return Map.of("code", 400, "msg", "仅支持 PDF、DOCX、XLSX、TXT、MD、CSV 健康资料");
        }
        try {
            ragService.ingestDocument(file.getBytes(), fileName, userId.isBlank() ? null : userId.trim());
            return Map.of("code", 200, "msg", "上传成功");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "上传失败");
        }
    }

    @GetMapping("/preview/{docName}")
    public Map<String, Object> preview(@PathVariable String docName,
                                       @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        List<Map<String, Object>> chunks = ragService.getDocumentChunks(docName);
        String content = chunks.stream()
                .map(chunk -> String.valueOf(chunk.getOrDefault("chunk_text", "")))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return Map.of("code", 200, "data", Map.of("name", docName, "content", content));
    }

    @DeleteMapping("/{docName}")
    public Map<String, Object> delete(@PathVariable String docName,
                                      @RequestParam(defaultValue = "") String userId,
                                      @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        int deleted = ragService.deleteDocument(docName, userId);
        return Map.of("code", 200, "msg", "已删除 " + deleted + " 条记录");
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return null;
        }
        try {
            return Path.of(originalName).getFileName().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasAllowedExtension(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private Map<String, Object> forbidden() {
        return Map.of("code", 403, "msg", "仅管理员可访问");
    }
}
